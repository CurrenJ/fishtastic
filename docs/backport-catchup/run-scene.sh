#!/bin/bash
# Usage: run-scene.sh <worktree> <fabric|neoforge|forge> <log-name> <ignored-legacy-budget> <marker line>...
# Runs one self-test launch with NO time cap. A run ends when (a) Gradle exits, or (b) the log shows
# evidence of failure: the first ERROR/Exception/FAIL line or a disconnect. Then the client/wrapper of
# THIS worktree are stopped, the marker is removed, and the first failure line goes in the summary.
# Intended to run in the background; read only <log-name>.summary (also <log-name>.first-error).
WT=$1; LOADER=$2; NAME=$3; shift 4
LOGDIR=/d/GitHub/fishtastic/build/backport-catchup/session-03
LOG="$LOGDIR/$NAME.log"
mkdir -p "$LOGDIR"; rm -f "$LOGDIR/$NAME.summary" "$LOGDIR/$NAME.first-error"
printf '%s\n' "$@" > "$WT/$LOADER/run/fishtastic_render_selftest"
cd "$WT"
./gradlew "-Dorg.gradle.java.home=C:/Program Files/Java/jdk-21" ":$LOADER:runClient" --no-parallel --console=plain > "$LOG" 2>&1 &
GPID=$!
# Failure evidence: scene check FAIL, uncaught exceptions, server-side encode errors, disconnects, mixin errors.
PATTERN='CHECK.*FAIL|/ERROR\]|\] \[.*/ERROR\]|Exception in thread|EncoderException|DecoderException|lost connection: (Internal|Timed|Encod|Decod)|Mixin apply failed|InvalidMixinException|NoClassDefFoundError|Task .*FAILED'
ABORTED=0
while kill -0 $GPID 2>/dev/null; do
  if grep -m1 -E "$PATTERN" "$LOG" > "$LOGDIR/$NAME.first-error" 2>/dev/null && [ -s "$LOGDIR/$NAME.first-error" ]; then
    # Let the scene finish its current check lines for a few seconds, then stop.
    sleep 5; ABORTED=1
    export WTWIN; WTWIN=$(cygpath -w "$WT" 2>/dev/null || echo "$WT")
    powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -like '*'+\$env:WTWIN.Replace('\','/')+'*' -or \$_.CommandLine -like '*'+\$env:WTWIN+'*' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }" >/dev/null 2>&1
    break
  fi
  sleep 2
done
wait $GPID 2>/dev/null; RC=$?
rm -f "$WT/$LOADER/run/fishtastic_render_selftest"
PASS=$(grep -c 'CHECK.*PASS' "$LOG"); FAIL=$(grep -c 'CHECK.*FAIL' "$LOG")
if [ $ABORTED -eq 1 ]; then
  echo "FAILFAST PASS=$PASS FAIL=$FAIL first-error: $(head -c 300 "$LOGDIR/$NAME.first-error")" > "$LOGDIR/$NAME.summary"
else
  echo "exit=$RC PASS=$PASS FAIL=$FAIL" > "$LOGDIR/$NAME.summary"
fi
