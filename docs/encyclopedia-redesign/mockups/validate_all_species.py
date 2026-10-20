"""
Runs every real species (fish_profile + lore + group tags + lang names) through the
mockup layout code at 426x240 and reports any overflow - the "fits every fish" check.
Run: python -I docs/encyclopedia-redesign/mockups/validate_all_species.py
"""
import glob, json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render_mockups as R

DATA = os.path.join(R.ROOT, "common", "src", "main", "resources", "data")
LANG = json.load(open(os.path.join(R.ROOT, "common", "src", "main", "resources", "assets", "fishtastic", "lang", "en_us.json"), encoding="utf-8"))
MOON = ["full_moon", "waning_gibbous", "third_quarter", "waning_crescent", "new_moon", "waxing_crescent", "first_quarter", "waxing_gibbous"]
GROUPS = {g: set(json.load(open(os.path.join(DATA, "fishtastic", "tags", "item", f"{g}_fish.json")))["values"]) for g in ["small", "big", "calm", "steady", "frenzy", "exotic"]}


def biome_name(b):
    """Display rule the design assumes: tag path minus 'is_', or the first listed biome."""
    first = b if isinstance(b, str) else b[0]
    path = first.lstrip("#").split(":")[1]
    if path.startswith("is_"):
        path = path[3:]
    return path.replace("_", " ").title()


def species():
    for ns_dir in glob.glob(os.path.join(DATA, "*", "fishtastic", "fish_profile")):
        for f in sorted(glob.glob(os.path.join(ns_dir, "*.json"))):
            sid = os.path.basename(f)[:-5]
            ns = ns_dir.split(os.sep)[-3]
            p = json.load(open(f))
            tex = os.path.join(R.TEX, "item", "fish", f"{sid}.png")
            lore_f = os.path.join(DATA, "fishtastic", "fishtastic", "fish_encyclopedia_entry", f"{sid}.json")
            yield dict(
                id=sid if os.path.exists(tex) else "betta",   # vanilla fish have no mod texture; art isn't under test
                key=f"{ns}:{sid}",
                name=LANG.get(f"item.fishtastic.{sid}", sid.replace("_", " ").title()),
                rarity="Very rare", pool="~1 in 999 cave bites",   # widest realistic string
                zones=p.get("zones", ["river"]), size=(p.get("size", {}).get("mean", 50), p.get("size", {}).get("std_dev", 15)),
                you=(999, "Legendary"), server=(999, "Abcdefghijklmnop"),   # 16-char player name: vanilla max
                temperament=("Metronome", 0.9), catches=99,
                biomes=[(biome_name(b["biome"]), b["multiplier"]) for b in p.get("biome_weights", [])],
                time={t["time"]: t["multiplier"] for t in p.get("time_weights", [])},
                weather={w["weather"]: w["multiplier"] for w in p.get("weather_weights", [])},
                moon={MOON.index(m["phase"]): (m["multiplier"], m.get("tier", "primary")) for m in p.get("moon_weights", [])},
                groups=[g for g, members in GROUPS.items() if f"fishtastic:{sid}" in members],
                lore=json.load(open(lore_f)).get("lore", "") if os.path.exists(lore_f) else "",
                thresholds=dict(name=1, stats=1, types=1, spawn=1, lore=1),
            )


def main():
    env = dict(zone="river", biome=None, time="night", weather="clear", moon=0)
    n = 0
    worst = []
    verdicts = []
    for fish in species():
        before = len(R.OVERFLOWS)
        for tab in ("catch", "notes"):
            R.concept_a(fish, env, tab=tab, claimed=False, screen=(426, 240), scale=1)
        lines = len(R.FONT.wrap(fish["lore"], R.CANVAS_W - 168 - 4 - 12 - 10))
        worst.append((lines, fish["key"]))
        for o in R.OVERFLOWS[before:]:
            print(f"  {fish['key']}: {o}")
        n += 1
        # every environment the verdict can face, for this fish
        for zone in fish["zones"]:
            for time in ["dawn", "day", "dusk", "night"]:
                for weather in ["clear", "rain", "snow", "thunder"]:
                    for biome in [None] + [b for b, _ in fish["biomes"]]:
                        for moon in ([0, 1, 7] if fish["moon"] else [None]):
                            e = dict(zone=zone, biome=biome, time=time, weather=weather, moon=moon)
                            _, _, sub = R.verdict(fish, e, True)
                            wv = R.FONT.width(sub) - 1
                            verdicts.append((wv, fish["key"], sub))
                            if wv > R.VERDICT_SUB_W:
                                R.OVERFLOWS.append(f"verdict {fish['key']} {e}: {sub!r} {wv}px > {R.VERDICT_SUB_W}px")
                                print(f"  {fish['key']}: verdict {sub!r} {wv}px > {R.VERDICT_SUB_W}px")
    worst.sort(reverse=True)
    verdicts.sort(reverse=True)
    print(f"verdict lines checked: {len(verdicts)}; widest {verdicts[0][0]}px of {R.VERDICT_SUB_W}px: {verdicts[0][2]!r} ({verdicts[0][1]})")
    print(f"\nchecked {n} species x 2 tabs at 426x240 (worst-case records/player-name strings)")
    print("longest lore:", worst[:3], "lines (page holds 14)")
    print("FAIL" if R.OVERFLOWS else "PASS: every species fits")
    sys.exit(1 if R.OVERFLOWS else 0)


if __name__ == "__main__":
    main()
