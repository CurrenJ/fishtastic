"""Average colour of block textures from the client jar: python swatch.py name[:face] ..."""
import colorsys, io, os, sys, zipfile
from PIL import Image
JAR = os.path.expanduser("~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-clientonly-deobf/26.1.2/minecraft-clientonly-deobf-26.1.2.jar")
z = zipfile.ZipFile(JAR)
names = set(z.namelist())
def avg(tex):
    for cand in (tex, tex + "_side", tex + "_top"):
        p = f"assets/minecraft/textures/block/{cand}.png"
        if p in names:
            im = Image.open(io.BytesIO(z.read(p))).convert("RGBA")
            w, h = im.size
            im = im.crop((0, 0, w, w))
            px = [c for c in im.getdata() if c[3] > 0]
            r = sum(c[0] for c in px) / len(px); g = sum(c[1] for c in px) / len(px); b = sum(c[2] for c in px) / len(px)
            return cand, (r, g, b)
    return None, None
for t in sys.argv[1:]:
    cand, c = avg(t)
    if not c: print(f"{t:28s} missing"); continue
    r, g, b = c
    L = 0.2126 * r + 0.7152 * g + 0.0722 * b
    h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
    print(f"{t:28s} #{int(r):02x}{int(g):02x}{int(b):02x}  luma {L:5.1f}  hue {h*360:5.0f}  sat {s:.2f}  ({cand})")
