#!/bin/bash
# The two-client self-test end to end (party spec §28.5): a dedicated server, client A, then client
# B, each in its own isolated run directory; stops everything by port/worktree and prints every
# [selftest] CHECK line. Usage: run-twoclient.sh fabric|forge [cap-seconds]
# FAIL-FAST: a watcher aborts on the first server/client error or disconnect, or when the cap
# (default 180 s) is reached; the verdict line is the last line printed.
set -u
loader=${1:-fabric}
cap=${2:-180}
port=$([ "$loader" = fabric ] && echo 25600 || echo 25601)
cd "$(dirname "$0")/../.."
dir=docs/fish-companions-twoclient
init=$dir/twoclient.init.gradle
G() { ./gradlew "-Dorg.gradle.java.home=C:/Program Files/Java/jdk-21" "$@"; }
start=$(date +%s)
elapsed() { echo $(( $(date +%s) - start )); }
strip() { sed 's/\x1b\[[0-9;]*m//g'; }
stop_all() {
  powershell.exe -NoProfile -Command "\$c = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue; if (\$c) { Stop-Process -Id \$c.OwningProcess -Force }; \$wt=(Get-Location).Path; Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -like ('*'+\$wt+'*') -or \$_.CommandLine -like ('*'+\$wt.Replace('\\','/')+'*') } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }" >/dev/null 2>&1
}
fail() { echo "VERDICT: ABORT ($(elapsed)s): $1"; stop_all; exit 1; }
BAD='Error receiving packet|EncoderException|No key layers|Internal Exception|Exception in server tick|Crash report|FAILED|FAILURE:|What went wrong'
serverlog=$dir/server-$loader.log
G :$loader:runServer -I $init -PtwoRole=server -PtwoLoader=$loader > $serverlog 2>&1 &
until grep -aq "Done (" $serverlog 2>/dev/null; do
  [ "$(elapsed)" -ge "$cap" ] && fail "server did not finish starting"
  grep -aqE "$BAD" $serverlog 2>/dev/null && fail "server startup error: $(grep -aE "$BAD" $serverlog | strip | head -1 | cut -c1-200)"
  sleep 2
done
G :$loader:runClient -I $init -PtwoRole=a -PtwoLoader=$loader > $dir/client-a-$loader.log 2>&1 &
sleep 20
G :$loader:runClient -I $init -PtwoRole=b -PtwoLoader=$loader > $dir/client-b-$loader.log 2>&1 &
la=$dir/client-a-$loader.log; lb=$dir/client-b-$loader.log
while :; do
  [ "$(elapsed)" -ge "$cap" ] && fail "cap reached before both clients completed"
  grep -aqE "$BAD" $serverlog 2>/dev/null && fail "server: $(grep -aE "$BAD" $serverlog | strip | head -1 | cut -c1-200)"
  grep -aqE "lost connection|Disconnected" $la $lb 2>/dev/null && fail "client disconnect: $(grep -aE 'lost connection|Disconnected' $la $lb | strip | head -1 | cut -c1-200)"
  grep -aq "\[selftest\] complete" $la && grep -aq "\[selftest\] complete" $lb && break
  grep -aq "BUILD" $la $lb 2>/dev/null && ! { grep -aq "\[selftest\] complete" $la && grep -aq "\[selftest\] complete" $lb; } && { sleep 5; grep -aq "\[selftest\] complete" $la && grep -aq "\[selftest\] complete" $lb || fail "a client exited without completing"; }
  sleep 3
done
stop_all
for f in client-a-$loader client-b-$loader; do
  echo "== $f"
  grep -aE "\[selftest\] (CHECK|complete)|BUILD" $dir/$f.log | strip | cut -c1-220
done
fails=$(cat $la $lb | grep -ac "CHECK.*FAIL")
echo "VERDICT: DONE ($(elapsed)s) fails=$fails"
