#!/bin/bash
# The two-client self-test end to end (party spec §28.5): a dedicated server, client A, then client
# B, each in its own isolated run directory; waits for both clients, stops the server by its port,
# and prints every [selftest] CHECK line. Usage: run-twoclient.sh fabric|forge
set -u
loader=${1:-fabric}
port=$([ "$loader" = fabric ] && echo 25600 || echo 25601)
cd "$(dirname "$0")/../.."
dir=docs/fish-companions-twoclient
init=$dir/twoclient.init.gradle
./gradlew "-Dorg.gradle.java.home=C:/Program Files/Java/jdk-21" :$loader:runServer -I $init -PtwoRole=server -PtwoLoader=$loader > $dir/server-$loader.log 2>&1 &
for i in $(seq 1 300); do grep -q "Done (" $dir/server-$loader.log 2>/dev/null && break; sleep 1; done
./gradlew "-Dorg.gradle.java.home=C:/Program Files/Java/jdk-21" :$loader:runClient -I $init -PtwoRole=a -PtwoLoader=$loader > $dir/client-a-$loader.log 2>&1 &
pa=$!
sleep 20
./gradlew "-Dorg.gradle.java.home=C:/Program Files/Java/jdk-21" :$loader:runClient -I $init -PtwoRole=b -PtwoLoader=$loader > $dir/client-b-$loader.log 2>&1 &
pb=$!
wait $pa $pb
# The server JVM outlives its Gradle task: stop whatever listens on the port.
powershell.exe -NoProfile -Command "\$c = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue; if (\$c) { Stop-Process -Id \$c.OwningProcess -Force }" >/dev/null 2>&1
for f in client-a-$loader client-b-$loader; do
  echo "== $f"
  grep -aE "\[selftest\] (CHECK|complete)|BUILD" $dir/$f.log | sed 's/\x1b\[[0-9;]*m//g' | cut -c1-220
done
