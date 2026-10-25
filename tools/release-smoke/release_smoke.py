"""Release-environment boot smoke test for CurseForge profiles.

Launches a CurseForge Minecraft profile directly (no CurseForge app, no Minecraft launcher) in a
scratch copy of its game directory, quick-plays a template world, and reports whether the client
reached the world without crashing. The mod jars under test are the exact release artifacts: the
self-test harness is excluded from them, so nothing in the jar drives this - the world is entered
via vanilla Quick Play (--quickPlaySingleplayer) and the run is judged from the log.

The owner's profile is only read. Everything is written under --work (default
<repo>/build/release-smoke/<profile>/).

Usage:
  python tools/release-smoke/release_smoke.py --profile "F2 26.1.2 Fabric" \
      --jar fabric/build/libs/fishtastic-fabric-2.1.6+26.1.2.jar \
      --world tools/release-smoke/worlds/26.1.2

Exit code 0 = PASS, 1 = FAIL, 2 = setup error, 3 = PASS_WITH_FINDINGS (booted only after
--accept-experimental confirmed a screen).
"""
from __future__ import annotations

import argparse
import json
import os
import platform
import re
import shutil
import subprocess
import sys
import time
import uuid
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
WORLD_NAME = "release_smoke"

# A log line meaning the player is in the world (integrated server accepted the login).
JOINED = re.compile(r"\b(\w+) joined the game\b")
# Fatal regardless of where they appear: loading/linkage failures that a dev run would not show.
FATAL = [
    re.compile(p) for p in (
        r"Mixin apply (for mod \S+ )?failed",
        r"InvalidMixinException",
        r"MixinTransformerError",
        r"MixinApplyError",
        r"No refMap loaded",
        r"InvalidInjectionException",
        r"Critical injection failure",
        r"InjectionError",
        r"(NoClassDefFoundError|ClassNotFoundException|NoSuchMethodError|NoSuchFieldError|AbstractMethodError|IncompatibleClassChangeError)",
        r"Incompatible mods? found",
        r"Missing block model",
        r"Reported exception thrown",
        r"---- Minecraft Crash Report ----",
        r'Uncaught exception in thread "main"',
        r'Exception in thread "main"',
    )
]
# Informational only. Mixin prints it for any config whose declared refmap is absent: empty
# platform configs, current Loom (which remaps mixin bytecode instead of writing refmaps), and all
# of 26.1+ (unobfuscated). A refmap that is really needed fails loudly at apply time instead
# ("Mixin apply failed ... No refMap loaded", seen on Forge 1.20.1), which FATAL catches.
REFMAP = re.compile(r"Reference map '[^']*' .*could not be read")
# Mixin scanners probe optional compatibility targets and log missing classes at WARN level.
# These do not stop the loader; a genuinely required missing class will surface as a fatal
# linkage error or a Mixin apply failure later in startup.
OPTIONAL_CLASS_WARNING = re.compile(r"\[.*?/WARN\].*Error loading class: .*ClassNotFoundException")
# Our code in a stack trace or logger name: any ERROR that mentions it fails the run.
OURS = re.compile(r"fishtastic|grill24|gelatinui", re.IGNORECASE)
ERROR_LINE = re.compile(r"/(ERROR|FATAL)\]")


def die(msg: str) -> None:
    print(f"setup error: {msg}", file=sys.stderr)
    sys.exit(2)


def minecraft_root() -> Path:
    """CurseForge's Minecraft root, as the app records it; CURSEFORGE_MINECRAFT_ROOT overrides."""
    if os.environ.get("CURSEFORGE_MINECRAFT_ROOT"):
        return Path(os.environ["CURSEFORGE_MINECRAFT_ROOT"])
    try:
        import winreg
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, r"Software\Overwolf\CurseForge") as k:
            return Path(winreg.QueryValueEx(k, "minecraft_root")[0])
    except OSError:
        die("CurseForge Minecraft root not found in the registry; set CURSEFORGE_MINECRAFT_ROOT")


# ── Version JSON resolution (vanilla launcher semantics) ─────────────────────

def rules_allow(rules, features) -> bool:
    if not rules:
        return True
    allowed = False
    for r in rules:
        ok = True
        os_rule = r.get("os", {})
        if "name" in os_rule and os_rule["name"] != "windows":
            ok = False
        if "arch" in os_rule and os_rule["arch"] != ("x86" if platform.architecture()[0] == "32bit" else "x64"):
            ok = False
        if "versionRange" in os_rule:
            ver = tuple(int(x) for x in platform.version().split("."))
            lo = os_rule["versionRange"].get("min")
            hi = os_rule["versionRange"].get("max")
            if lo and ver < tuple(int(x) for x in lo.split(".")):
                ok = False
            if hi and ver > tuple(int(x) for x in hi.split(".")):
                ok = False
        if "version" in os_rule and not re.match(os_rule["version"], platform.version()):
            ok = False
        for f, want in r.get("features", {}).items():
            if bool(features.get(f)) != want:
                ok = False
        if ok:
            allowed = r["action"] == "allow"
    return allowed


def load_chain(versions: Path, vid: str) -> list[dict]:
    chain = []
    while vid:
        f = versions / vid / f"{vid}.json"
        if not f.exists():
            die(f"version json missing: {f} (launch the profile once from CurseForge to install it)")
        d = json.loads(f.read_text(encoding="utf-8"))
        chain.append(d)
        vid = d.get("inheritsFrom")
    return chain  # child first


def maven_path(name: str) -> str:
    parts = name.split(":")
    group, art, ver = parts[0], parts[1], parts[2]
    cls = f"-{parts[3]}" if len(parts) > 3 else ""
    ext = "jar"
    if "@" in ver:
        ver, ext = ver.split("@")
    return f"{group.replace('.', '/')}/{art}/{ver}/{art}-{ver}{cls}.{ext}"


def lib_key(name: str) -> str:
    p = name.split(":")
    return ":".join([p[0], p[1]] + p[3:])  # group:artifact[:classifier], version dropped


def classpath(chain, libraries: Path, versions: Path, features) -> list[str]:
    seen, cp = set(), []
    for d in chain:  # child libraries win over parent ones of the same artifact
        for lib in d.get("libraries", []):
            if not rules_allow(lib.get("rules"), features):
                continue
            key = lib_key(lib["name"])
            if key in seen:
                continue
            seen.add(key)
            art = lib.get("downloads", {}).get("artifact")
            rel = art["path"] if art and art.get("path") else maven_path(lib["name"])
            p = libraries / rel
            if not p.exists():
                die(f"library missing: {p}")
            cp.append(str(p))
    # The launcher puts the *launched* version's jar on the classpath (loaders keep a copy of the
    # client jar in their own version dir); BootstrapLauncher's ignoreList filters it by that name.
    client = next((versions / d["id"] / f"{d['id']}.jar" for d in chain
                   if (versions / d["id"] / f"{d['id']}.jar").exists()), None)
    if client is None:
        die(f"client jar missing for {chain[0]['id']}")
    cp.append(str(client))
    return cp


def expand(args, features, subst) -> list[str]:
    out = []
    for a in args:
        if isinstance(a, dict):
            if not rules_allow(a.get("rules"), features):
                continue
            vals = a["value"] if isinstance(a["value"], list) else [a["value"]]
        else:
            vals = [a]
        out.extend(re.sub(r"\$\{(\w+)\}", lambda m: subst.get(m.group(1), m.group(0)), v) for v in vals)
    return out


def drop_empty_options(args: list[str]) -> list[str]:
    """`--clientId ""` style pairs: the launcher sends them, but an empty value adds nothing."""
    out, i = [], 0
    while i < len(args):
        if args[i].startswith("--") and i + 1 < len(args) and args[i + 1] == "":
            i += 2
            continue
        out.append(args[i])
        i += 1
    return out


# ── Scratch game directory ───────────────────────────────────────────────────

def prepare_gamedir(instance: Path, work: Path, jars: list[Path], world: Path | None, drop: list[str]) -> Path:
    game = work / "game"
    if game.exists():
        shutil.rmtree(game)
    game.mkdir(parents=True)
    for sub in ("mods", "config", "defaultconfigs"):
        if (instance / sub).is_dir():
            shutil.copytree(instance / sub, game / sub)
    (game / "mods").mkdir(exist_ok=True)
    for pattern in drop:  # A/B diagnosis: run the profile without a mod
        for old in (game / "mods").glob(pattern):
            old.unlink()
    for jar in jars:
        # Replace the profile's copy of this mod+loader (e.g. fishtastic-fabric-*.jar) with the jar under test.
        m = re.match(r"(.+?-(?:fabric|neoforge|forge))-", jar.name)
        if not m:
            die(f"cannot derive mod/loader prefix from {jar.name}")
        for old in (game / "mods").glob(m.group(1) + "-*.jar"):
            old.unlink()
        shutil.copy2(jar, game / "mods" / jar.name)
    opts = instance / "options.txt"
    lines = opts.read_text(encoding="utf-8").splitlines() if opts.exists() else []
    forced = {"pauseOnLostFocus": "false", "onboardAccessibility": "false", "tutorialStep": "none",
              "skipMultiplayerWarning": "true", "joinedFirstServer": "true"}
    lines = [l for l in lines if l.split(":", 1)[0] not in forced]
    lines += [f"{k}:{v}" for k, v in forced.items()]
    (game / "options.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    if world:
        shutil.copytree(world, game / "saves" / WORLD_NAME)
    return game


# ── Run and judge ────────────────────────────────────────────────────────────

def window_shot(pid: int, out: Path, keys: str = "") -> None:
    """What screen is the client parked on? (Confirmation screens log nothing.) Optionally post keys
    (comma-separated TAB/ENTER/ESCAPE) to that window after."""
    script = Path(__file__).with_name("window_shot.ps1")
    cmd = ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(script),
           "-ProcessId", str(pid), "-Out", str(out)]
    r = subprocess.run(cmd + (["-Keys", keys] if keys else []), capture_output=True, text=True, timeout=60)
    if r.returncode != 0:
        print(f"[smoke] window_shot failed: {(r.stderr or r.stdout).strip()[:300]}")


def thread_dump(pid: int, out: Path) -> None:
    jhome = os.environ.get("JAVA_HOME")
    jstack = Path(jhome, "bin", "jstack.exe") if jhome else None
    if jstack and jstack.exists():
        with open(out, "w", encoding="utf-8") as f:
            subprocess.run([str(jstack), str(pid)], stdout=f, stderr=subprocess.STDOUT, timeout=60)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--profile", required=True, help="instance folder name under Instances/")
    ap.add_argument("--jar", action="append", default=[], help="release jar to swap into the scratch mods/ (repeatable)")
    ap.add_argument("--accept-experimental", action="store_true",
                    help="on the first stall, press 'I know what I'm doing!' (Tab, Tab, Enter) and keep going; "
                         "the screen is still reported as a finding")
    ap.add_argument("--drop", action="append", default=[], help="glob of profile mods to remove (A/B diagnosis)")
    ap.add_argument("--world", help="template save directory to quick-play (omit: boot to title screen only)")
    ap.add_argument("--work", help="scratch directory")
    ap.add_argument("--timeout", type=int, default=300, help="seconds allowed to reach the world")
    ap.add_argument("--settle", type=int, default=30, help="seconds to stay in the world after joining")
    ap.add_argument("--stall", type=int, default=60,
                    help="seconds of log silence before the world loads that count as stuck on a screen")
    a = ap.parse_args()

    root = minecraft_root()
    install, instance = root / "Install", root / "Instances" / a.profile
    manifest = instance / "minecraftinstance.json"
    if not manifest.exists():
        die(f"no profile at {instance}")
    inst = json.loads(manifest.read_text(encoding="utf-8"))
    base = inst["baseModLoader"]
    vid = json.loads(base["versionJson"])["id"] if base.get("versionJson") else base["name"]
    versions = install / "versions"
    chain = load_chain(versions, vid)
    merged_java = next((d["javaVersion"] for d in chain if "javaVersion" in d), None)
    if not merged_java:
        die("no javaVersion in the version chain")
    java = install / "runtime" / merged_java["component"] / "windows-x64" / merged_java["component"] / "bin" / "java.exe"
    if not java.exists():
        die(f"java runtime missing: {java}")

    jars = [Path(j) if Path(j).is_absolute() else REPO / j for j in a.jar]
    for j in jars:
        if not j.exists():
            die(f"jar not found: {j}")
    world = (Path(a.world) if Path(a.world).is_absolute() else REPO / a.world) if a.world else None
    if world and not (world / "level.dat").exists():
        die(f"template world has no level.dat: {world}")

    # Absolute: the game runs with cwd = its game dir, so a relative path would resolve twice.
    work = (Path(a.work) if a.work else REPO / "build" / "release-smoke" / re.sub(r"[^\w.-]+", "_", a.profile)).resolve()
    work.mkdir(parents=True, exist_ok=True)
    game = prepare_gamedir(instance, work, jars, world, a.drop)
    natives = work / "natives"
    shutil.rmtree(natives, ignore_errors=True)
    natives.mkdir()

    features = {"has_quick_plays_support": False, "is_quick_play_singleplayer": world is not None}
    name = "SmokeTest"
    subst = {
        "auth_player_name": name, "version_name": vid, "game_directory": str(game),
        "assets_root": str(install / "assets"), "game_assets": str(install / "assets"),
        "assets_index_name": next(d["assetIndex"]["id"] for d in chain if "assetIndex" in d),
        "auth_uuid": str(uuid.uuid3(uuid.NAMESPACE_OID, "OfflinePlayer:" + name)).replace("-", ""),
        "auth_access_token": "0", "auth_session": "0", "clientid": "", "auth_xuid": "",
        "user_type": "legacy", "user_properties": "{}", "version_type": chain[0].get("type", "release"),
        "natives_directory": str(natives), "launcher_name": "release-smoke", "launcher_version": "1",
        "library_directory": str(install / "libraries"), "classpath_separator": os.pathsep,
        "quickPlaySingleplayer": WORLD_NAME,
    }
    subst["classpath"] = os.pathsep.join(classpath(chain, install / "libraries", versions, features))

    jvm, game_args = [f"-Xmx{inst.get('allocatedMemory') or 4096}m"], []
    # Fabric Loader shows a blocking Swing dialog on load errors; nobody is there to dismiss it.
    jvm.append("-Dfabric.noGui=true")
    for d in reversed(chain):  # parent first, as the launcher does
        args = d.get("arguments", {})
        jvm += expand(args.get("jvm", []), features, subst)
        game_args += expand(args.get("game", []), features, subst)
        if "minecraftArguments" in d:  # pre-1.13 style
            game_args = expand(d["minecraftArguments"].split(), features, subst)
    main_class = chain[0]["mainClass"]
    cmd = [str(java)] + jvm + [main_class] + drop_empty_options(game_args)
    (work / "command.txt").write_text("\n".join(cmd), encoding="utf-8")

    out_log = work / "stdout.log"
    print(f"[smoke] {a.profile}: {vid}, java {merged_java['majorVersion']}, jars {[j.name for j in jars]}")
    print(f"[smoke] game dir {game}")
    start = time.time()
    with open(out_log, "w", encoding="utf-8", errors="replace") as log:
        proc = subprocess.Popen(cmd, cwd=game, stdout=log, stderr=subprocess.STDOUT)
    crash_dir = game / "crash-reports"

    def failures(text: str) -> tuple[list[str], list[str], list[Path]]:
        lines = text.splitlines()
        fatal = [l for l in lines if any(p.search(l) for p in FATAL)
                 and not OPTIONAL_CLASS_WARNING.search(l)]
        ours = [l for l in lines if ERROR_LINE.search(l) and OURS.search(l)]
        crashes = sorted(crash_dir.glob("*.txt")) if crash_dir.is_dir() else []
        return fatal, ours, crashes

    joined_at, result, reason = None, None, ""
    last_len, last_growth = 0, time.time()
    findings: list[str] = []
    while True:
        time.sleep(2)
        text = out_log.read_text(encoding="utf-8", errors="replace")
        if len(text) != last_len:
            last_len, last_growth = len(text), time.time()
        # Fail fast: a load error usually leaves the JVM parked on an error screen or dialog
        # (Forge/NeoForge loading-error screen, a crash screen) rather than exiting.
        fatal, ours, crashes = failures(text)
        if fatal or ours or crashes:
            time.sleep(3)  # let the stack trace finish flushing into the log
            result = "FAIL"
            reason = ("crash report written" if crashes and not (fatal or ours) else "error patterns in the log")                 + (" before reaching the world" if not joined_at else "")
            break
        if joined_at is None and JOINED.search(text):
            joined_at = time.time()
            print(f"[smoke] joined world after {joined_at - start:.0f}s; settling {a.settle}s")
        if proc.poll() is not None:
            result, reason = "FAIL", f"process exited with code {proc.returncode}" + ("" if joined_at else " before reaching the world")
            break
        if joined_at and time.time() - joined_at >= a.settle:
            result = "PASS"
            break
        if not world and time.time() - start >= a.timeout:
            result = "PASS"  # title-screen-only mode: survived the window
            break
        stalled = world and not joined_at and time.time() - last_growth >= a.stall
        if stalled and a.accept_experimental and not findings:
            # Vanilla's "Worlds using Experimental Settings" screen logs nothing; button order is
            # [Create Backup and Load] [I know what I'm doing!] [Cancel].
            window_shot(proc.pid, work / "experimental.png", keys="TAB,TAB,ENTER")
            findings.append(f"parked on a screen for {a.stall}s before the world (experimental-settings "
                            "warning?); confirmed it via --accept-experimental, see experimental.png")
            print(f"[smoke] {findings[-1]}")
            last_growth = time.time()
            continue
        if stalled or (world and not joined_at and time.time() - start >= a.timeout):
            window_shot(proc.pid, work / "stuck.png")
            thread_dump(proc.pid, work / "jstack.txt")
            result = "FAIL"
            reason = (f"log silent for {a.stall}s before reaching the world (parked on a screen? see stuck.png)"
                      if stalled else f"did not reach the world within {a.timeout}s")
            break
    if proc.poll() is None:
        subprocess.run(["taskkill", "/F", "/T", "/PID", str(proc.pid)], capture_output=True)
        proc.wait(timeout=30)

    text = out_log.read_text(encoding="utf-8", errors="replace")
    fatal, ours, crashes = failures(text)
    if result == "PASS" and (fatal or ours or crashes):
        result, reason = "FAIL", "error patterns in the log" if (fatal or ours) else "crash report written"
    errors = [l for l in text.splitlines() if ERROR_LINE.search(l)]

    summary = {
        "profile": a.profile, "version": vid, "jars": [j.name for j in jars], "result": result,
        "reason": reason, "seconds_to_world": round(joined_at - start) if joined_at else None,
        "fatal_lines": fatal[:50], "our_error_lines": ours[:50], "all_error_lines": len(errors),
        "findings": findings,
        "notes": [l for l in text.splitlines() if REFMAP.search(l)],
        "crash_reports": [str(c) for c in crashes], "log": str(out_log),
    }
    (work / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    if result == "PASS" and findings:
        result = "PASS_WITH_FINDINGS"
    print(f"[smoke] {result}" + (f": {reason}" if reason else ""))
    for l in (fatal + ours)[:20]:
        print("   ", l)
    print(f"[smoke] {len(errors)} ERROR lines total; summary {work / 'summary.json'}")
    return 0 if result == "PASS" else 3 if result == "PASS_WITH_FINDINGS" else 1


if __name__ == "__main__":
    sys.exit(main())
