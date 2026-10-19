#!/bin/sh
# Sweep GroundMind.BEAT_LOOK_AHEAD (tweakA): sampling cost and per-seed walking seen vs beats off.
cd "$(dirname "$0")"
for cap in "$@"; do
  echo "##### cap=$cap"
  ./gradlew :fishsim:test --tests 'grill24.fishsim.companion.BenthicSamplingProbe' -PsimStdout -PtweakA=$cap --console=plain 2>&1 \
    | grep "SAMPLES" | grep -v "swimmer\|percher" | sed 's/^ *SAMPLES //;s/ticks over.*//'
  for cfg in "walk_flat sprinter 0.3" "walk_meander sprinter 0.3" "walk_flat baseline 0.6"; do
    set -- $cfg
    printf "%s %s %s: " $1 $2 $3
    ./gradlew :fishsim:test --tests 'grill24.fishsim.companion.BenthicBeatProbe.perSeed' -PsimStdout -PsweepSeeds=12 \
      -Ptrace=$1 -PsweepTemperament=$2 -PsweepLength=$3 -PtweakA=$cap --console=plain 2>&1 | grep "SEEDS"
  done
done
