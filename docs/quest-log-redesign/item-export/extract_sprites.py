"""Extract transparent GUI item captures from the isolated black/white render pair.

Run after item-export.init.gradle's capture. This recovers the item layer from two
actual game renders; it does not reconstruct tank geometry from a GUI icon.
"""
import hashlib
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
BASE = ROOT / 'docs/quest-log-redesign'
ITEMS = BASE / 'mockups/assets/items'
QUESTS = ROOT / 'common/src/main/resources/data/fishtastic/fishtastic/quest'
IDS = ['tutorial/first_catch', 'daily/bright_catch_gold', 'collector/river_collector',
       'collector/ocean_collector', 'collector/cave_collector', 'mastery/tetra_scholar',
       'mastery/angler_apprentice', 'mastery/tetra_tracker']


def item_key(item):
    appearance = {'id': item['id'], 'components': item.get('components', {})}
    return hashlib.sha256(json.dumps(appearance, sort_keys=True, separators=(',', ':')).encode()).hexdigest()[:16]


def main():
    screenshots = BASE / 'run-item-export/screenshots'
    black = Image.open(screenshots / 'selftest-fabric-questsprites-black.png').convert('RGB')
    white = Image.open(screenshots / 'selftest-fabric-questsprites-white.png').convert('RGB')
    assert black.size == white.size == (960, 540)
    ITEMS.mkdir(parents=True, exist_ok=True)
    entries = {}
    slot = 0
    for quest_id in IDS:
        data = json.loads((QUESTS / (quest_id + '.json')).read_text(encoding='utf-8'))
        for index, item in enumerate(data['reward'].get('items', [])):
            x, y = 64 + (slot % 6)*128, 80 + (slot // 6)*128
            b = black.crop((x, y, x+64, y+64))
            w = white.crop((x, y, x+64, y+64))
            pixels = []
            for bp, wp in zip(b.getdata(), w.getdata()):
                # Cblack = alpha*Citem; Cwhite = alpha*Citem + (1-alpha)*255.
                delta = sorted(max(0, min(255, wc-bc)) for wc, bc in zip(wp, bp))[1]
                alpha = 255-delta
                pixels.append(tuple(min(255, round(v*255/alpha)) for v in bp)+(alpha,) if alpha else (0,0,0,0))
            image = Image.new('RGBA', (64,64))
            image.putdata(pixels)
            assert image.getbbox() and image.getbbox() != (0,0,64,64), (quest_id,index,'invalid transparency')
            key = item_key(item)
            image.save(ITEMS / (key+'.png'))
            entries[key] = dict(file=key+'.png', quest=quest_id, reward_index=index, item=item, capture_slot=slot)
            slot += 1
    assert slot == 10
    manifest = dict(source='Minecraft 26.1.2 Fabric GUI item renderer',
                    capture_size=[64,64], logical_slot=[16,16], entries=entries)
    (ITEMS/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    print(f'Extracted {slot} actual reward ItemStack renders into {len(entries)} appearance assets.')


if __name__ == '__main__':
    main()
