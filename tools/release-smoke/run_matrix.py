"""Run release_smoke.py over every profile in matrix.json, one client at a time.

  python tools/release-smoke/run_matrix.py              # all runs
  python tools/release-smoke/run_matrix.py 1.20.1 Forge # only runs whose profile contains every word

Each run names a branch; its checkout is found with `git worktree list` (so the branch must be
checked out in this repo or one of its worktrees). Jars come from that checkout's
gradle.properties (archives_name, mod_version, minecraft_version), so build them first (the
publish flow does). Exit code 0 only if every run is PASS; 3 if the worst
result is PASS_WITH_FINDINGS; 1 if anything failed.
"""
import json
import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
LABEL = {0: "PASS", 3: "PASS_WITH_FINDINGS", 1: "FAIL", 2: "SETUP ERROR"}


def props(checkout: Path) -> dict:
    out = {}
    for line in (checkout / "gradle.properties").read_text(encoding="utf-8").splitlines():
        m = re.match(r"\s*([\w.]+)\s*=\s*(.*?)\s*$", line)
        if m:
            out[m.group(1)] = m.group(2)
    return out


def worktrees() -> dict:
    """branch name -> checkout path, for this repo and all its worktrees."""
    out = subprocess.run(["git", "-C", str(REPO), "worktree", "list", "--porcelain"],
                         capture_output=True, text=True, check=True).stdout
    trees, path = {}, None
    for line in out.splitlines():
        if line.startswith("worktree "):
            path = Path(line[len("worktree "):])
        elif line.startswith("branch refs/heads/"):
            trees[line[len("branch refs/heads/"):]] = path
    return trees


def main() -> int:
    words = sys.argv[1:]
    runs = [r for r in json.loads((HERE / "matrix.json").read_text(encoding="utf-8"))["runs"]
            if all(w.lower() in r["profile"].lower() for w in words)]
    trees = worktrees()
    results = []
    for r in runs:
        checkout = trees.get(r["branch"])
        if checkout is None:
            print(f"\n=== {r['profile']}: branch {r['branch']} is not checked out in any worktree")
            results.append((r["profile"], 2))
            continue
        p = props(checkout)
        name = p.get("archives_name") or p.get("archives_base_name") or "fishtastic"
        jar = checkout / r["loader"] / "build" / "libs" / f"{name}-{r['loader']}-{p['mod_version']}+{p['minecraft_version']}.jar"
        cmd = [sys.executable, str(HERE / "release_smoke.py"), "--profile", r["profile"], "--jar", str(jar),
               "--world", str(HERE / "worlds" / r["world"])]
        if r.get("accept_experimental"):
            cmd.append("--accept-experimental")
        print(f"\n=== {r['profile']}  ({jar.name})", flush=True)
        code = subprocess.run(cmd).returncode
        results.append((r["profile"], code))
    print("\n=== summary")
    for profile, code in results:
        print(f"  {LABEL.get(code, code):<20} {profile}")
    codes = {c for _, c in results}
    return 1 if codes - {0, 3} else 3 if 3 in codes else 0


if __name__ == "__main__":
    sys.exit(main())
