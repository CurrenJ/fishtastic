"""Run isolated solo or two-client rendering scenes; require checks and completion."""
import argparse
import os
from pathlib import Path
import re
import signal
import socket
import struct
import subprocess
import time
import uuid

ROOT = Path(__file__).resolve().parent.parent


def read_log(path):
    return path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""


def rcon_stop(port, password):
    def packet(stream, kind, body):
        payload = struct.pack("<ii", 1, kind) + body.encode() + b"\0\0"
        stream.sendall(struct.pack("<i", len(payload)) + payload)
        size = stream.recv(4)
        if len(size) != 4:
            raise OSError("Incomplete RCON reply")
        remaining = struct.unpack("<i", size)[0]
        while remaining:
            data = stream.recv(remaining)
            if not data:
                raise OSError("Incomplete RCON reply")
            remaining -= len(data)
    with socket.create_connection(("127.0.0.1", port), timeout=5) as stream:
        packet(stream, 3, password)
        packet(stream, 2, "stop")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("loader", choices=["fabric", "neoforge", "forge"])
    parser.add_argument("--solo", action="store_true", help="One integrated-server client instead of a dedicated server and two clients")
    parser.add_argument("--scenes", help="Comma-separated scene names; defaults to companionscenes (solo) or twoclient")
    parser.add_argument("--port", type=int, default=25600)
    parser.add_argument("--timeout", type=int, default=600)
    parser.add_argument("--prepare-only", action="store_true", help="Print commands without starting the game")
    args = parser.parse_args()
    if not 1024 <= args.port <= 65534 or args.timeout < 1:
        parser.error("Use a port in 1024..65534 and a positive timeout")
    if args.scenes and not re.fullmatch(r"[a-zA-Z0-9_,]+", args.scenes):
        parser.error("Scenes must be comma-separated names")
    run_id, password = uuid.uuid4().hex, uuid.uuid4().hex
    base = ROOT / "build/verification/two-client" / args.loader / run_id
    base.mkdir(parents=True)
    print(f"Evidence: {base}", flush=True)
    roles = ["solo"] if args.solo else ["server", "a", "b"]
    if not args.solo and not args.prepare_only:
        # Refuse a collision; never stop a process merely because it owns a port.
        for port in (args.port, args.port + 1):
            with socket.socket() as probe:
                probe.bind(("127.0.0.1", port))
    env = os.environ.copy()
    if not env.get("JAVA_HOME"):
        properties = (ROOT / "gradle.properties").read_text(encoding="utf-8")
        pin = re.search(r"^org.gradle.java.home=(.+)$", properties, re.MULTILINE)
        if pin:
            env["JAVA_HOME"] = pin.group(1).strip()
    processes, logs = {}, {}
    def launch(role):
        task = "runServer" if role == "server" else "runClient"
        command = [str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")),
                   f":{args.loader}:{task}", "--no-parallel", "-I", "scripts/twoclient.init.gradle",
                   f"-PtwoLoader={args.loader}", f"-PtwoRole={role}", f"-PtwoRunId={run_id}",
                   f"-PtwoPort={args.port}", f"-PtwoPassword={password}"]
        if args.scenes:
            command.append(f"-PtwoScenes={args.scenes}")
        if args.prepare_only:
            print(" ".join(x for x in command if not x.startswith("-PtwoPassword=")))
            return
        logs[role] = base / f"{role}.log"
        with logs[role].open("w", encoding="utf-8") as output:
            processes[role] = subprocess.Popen(command, cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT,
                                               creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
                                               start_new_session=os.name != "nt")
    def wait_for(role, predicate, timeout):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if predicate(read_log(logs[role])):
                return
            if processes[role].poll() is not None:
                raise RuntimeError(f"{role} exited before readiness; see {logs[role]}")
            time.sleep(1)
        raise RuntimeError(f"Timed out waiting for {role}; see {logs[role]}")
    try:
        for role in roles:
            launch(role)
            if args.prepare_only:
                continue
            if role == "server":
                wait_for(role, lambda t: "Done (" in t, min(args.timeout, 180))
            elif role == "a":
                # Serialize launch preparation before starting B (shared Architectury agent jar).
                wait_for(role, lambda t: "[selftest] armed on" in t, min(args.timeout, 180))
        if args.prepare_only:
            return
        for role in roles:
            if role == "server":
                continue
            wait_for(role, lambda t: "[selftest] complete, stopping" in t, args.timeout)
            result = processes[role].wait(timeout=60)
            text = read_log(logs[role])
            checks = re.findall(r"\[selftest\] CHECK .+?: (PASS|FAIL)", text)
            expected = "companionscenes" if args.solo else "twoclient"
            missing_default_checks = not args.scenes and not re.search(r"\[selftest\] CHECK " + expected + r"\.", text)
            if (result or not checks or "FAIL" in checks or "[selftest] unknown scene" in text
                    or missing_default_checks or text.count("[selftest] complete, stopping") != 1):
                raise RuntimeError(f"{role}: exit={result}, checks={len(checks)}, failures={checks.count('FAIL')}; see {logs[role]}")
            print(f"{role}: {len(checks)} checks passed", flush=True)
    finally:
        server = processes.get("server")
        if server and server.poll() is None:
            try:
                rcon_stop(args.port + 1, password)
                server.wait(timeout=30)
            except (OSError, subprocess.TimeoutExpired):
                pass
        for process in processes.values():
            if process.poll() is None:
                if os.name == "nt":
                    subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                   creationflags=subprocess.CREATE_NO_WINDOW)
                else:
                    os.killpg(process.pid, signal.SIGTERM)
                process.wait(timeout=30)


if __name__ == "__main__":
    main()
