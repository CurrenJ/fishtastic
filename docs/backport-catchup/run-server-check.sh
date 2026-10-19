#!/bin/bash
# Usage: run-server-check.sh <worktree> <fabric|neoforge|forge> <log-name>
# Starts the loader's dedicated server (no clients). No time cap. Ends on evidence: "Done (" in the log
# = started OK, or the first ERROR/Exception/crash line = failed. Then stops only this worktree's java.
WT=$1; LOADER=$2; NAME=$3
LOGDIR=/d/GitHub/fishtastic/build/backport-catchup/session-03; LOG="$LOGDIR/$NAME.log"
rm -f "$LOGDIR/$NAME.summary"; mkdir -p "$WT/$LOADER/run"
echo "eula=true" > "$WT/$LOADER/run/eula.txt"
cd "$WT"
./gradlew "-Dorg.gradle.java.home=C:/Program Files/Java/jdk-21" ":$LOADER:runServer" --no-parallel --console=plain > "$LOG" 2>&1 &
GPID=$!; VERDICT=""
while kill -0 $GPID 2>/dev/null; do
  if grep -q -E "Done \([0-9.]+s\)!" "$LOG"; then VERDICT="STARTED: $(grep -m1 -E 'Done \([0-9.]+s\)!' "$LOG" | cut -c1-120)"; break; fi
  E=$(grep -m1 -E "/ERROR\]|Exception|Mixin apply failed|NoClassDefFoundError|Task .*FAILED|Crash" "$LOG")
  if [ -n "$E" ]; then VERDICT="FAILED: $(echo "$E" | cut -c1-250)"; break; fi
  sleep 2
done
[ -z "$VERDICT" ] && VERDICT="EXITED before Done (exit of gradle); see log"
export WTWIN; WTWIN=$(cygpath -w "$WT" 2>/dev/null || echo "$WT")
powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -like '*'+\$env:WTWIN.Replace('\','/')+'*' -or \$_.CommandLine -like '*'+\$env:WTWIN+'*' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }" >/dev/null 2>&1
echo "$VERDICT" > "$LOGDIR/$NAME.summary"
