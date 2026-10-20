"""
Disc -> portrait transition storyboard: the selected fish is ONE continuous object that flies from
its disc slot into the portrait plate while the page fades in around it (the owner's requirement).

Built from the real disc capture (current/s4_disc, 1080p Auto) and the real page render, with the
fish moved on an exponential ease like the screen's existing setTargetPosition/setTargetScale. The
plate's bubbles are already running when the fish lands (warm start), and the welcome plume follows.

Run: python -I docs/encyclopedia-redesign/mockups/storyboard_transition.py
"""
import math
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "bubbles"))
import render_mockups as R  # noqa: E402
import prototype_bubbles as P  # noqa: E402

S = 4                                   # 1080p Auto: 480x270 logical
W, H = 480, 270
DISC = os.path.join(HERE, "..", "current", "selftest-fabric-encyclopedia-s4_disc.png")
FISH_TEX = os.path.join(R.TEX, "item", "fish", "betta.png")

# Measured on the disc capture: the betta's centre, at disc scale 1 (hover makes it 1.5).
START_CENTER = (294.25, 173.0)
START_SCALE = 1.5
# The portrait slot in the page layout at 480x270: canvas origin (32,19), plate (38,64), fish +46,+1.
PLATE = (38, 64)
END_TOPLEFT = (PLATE[0] + 46, PLATE[1] + 1)
END_SCALE = 4.0
EASE = 11.0                             # 1/s, exponential approach; illustrative of the existing ease
FADE = 0.2                              # s: disc out / page in


def eased(t):
    return 1.0 - math.exp(-EASE * t)


def frame(t, disc_img, page_img, plate):
    f = eased(t)
    a = min(1.0, t / FADE)
    base = Image.blend(disc_img, page_img, a)
    # live plate (bubbles already running) under the flying fish
    plate_img = plate.render(None, button="idle")
    plate_layer = base.copy()
    plate_layer.alpha_composite(plate_img, (PLATE[0] * S, PLATE[1] * S))
    base = Image.blend(base, plate_layer, a)
    # the one continuous fish: lerp centre and scale on the same ease
    sx = START_CENTER[0] - 8 * START_SCALE
    sy = START_CENTER[1] - 8 * START_SCALE
    x = sx + (END_TOPLEFT[0] - sx) * f
    y = sy + (END_TOPLEFT[1] - sy) * f
    scale = START_SCALE + (END_SCALE - START_SCALE) * f
    size = 16 * scale
    fish = Image.open(FISH_TEX).convert("RGBA").resize((round(size * S), round(size * S)), Image.NEAREST)
    base.alpha_composite(fish, (round(x * S), round(y * S)))
    # front-layer bubbles pass over the fish once it has landed
    return base


def caption(img, text):
    c = R.Canvas(img.width // S, 14, S, backdrop=False)
    c.img = Image.new("RGBA", (img.width, 14 * S), (16, 16, 20, 255))
    c.draw = None
    c.text(4, 3, text, R.GOLD)
    out = Image.new("RGBA", (img.width, img.height + 14 * S))
    out.paste(c.img, (0, 0))
    out.paste(img, (0, 14 * S))
    return out


def main():
    disc = Image.open(DISC).convert("RGBA").resize((W * S, H * S))
    # The capture still shows the betta in its slot; in game the icon itself leaves the slot (one
    # object, no ghost), so paint it out: interpolate each row between the colours just outside it.
    x0, y0, x1, y1 = 1140, 656, 1214, 728          # measured betta bbox on the capture, padded
    px = disc.load()
    for y in range(y0, y1):
        lc, rc = px[x0 - 2, y], px[x1 + 2, y]
        for x in range(x0, x1):
            f = (x - x0) / (x1 - x0)
            px[x, y] = tuple(int(lc[c] + (rc[c] - lc[c]) * f) for c in range(3)) + (255,)
    page = R.concept_a(R.BETTA, dict(zone="river", biome=None, time="night", weather="clear", moon=2),
                       screen=(W, H), scale=S, show_fish=False).convert("RGBA")
    plate = P.Plate("river", seed=11)
    for _ in range(int(P.WARMUP * P.FPS)):
        plate.step(1.0 / P.FPS)
    plate.on_open()

    fps = 25
    frames, stills = [], []
    beats = {0.0: "t=0.00  click: the fish lifts off its disc slot",
             0.08: "t=0.08  same object in flight, page fading in",
             0.18: "t=0.18  growing toward 4x, page nearly in",
             0.40: "t=0.40  landed in the plate; bubbles already live",
             1.40: "t=1.40  welcome plume rises past the fish"}
    t = 0.0
    landed_plate_fish = None
    while t <= 2.0 + 1e-6:
        plate.step(1.0 / fps)
        if t < 0.45:
            img = frame(t, disc, page, plate)
        else:                                       # landed: the plate owns the fish (normal layering)
            img = page.copy()
            img.alpha_composite(plate.render(FISH_TEX, button="idle"), (PLATE[0] * S, PLATE[1] * S))
        for bt, label in beats.items():
            if abs(t - bt) < 0.5 / fps:
                stills.append(caption(img, label))
        frames.append(img.convert("RGB").resize((W * 2, H * 2), Image.NEAREST))
        t += 1.0 / fps

    gif = [f.convert("P", palette=Image.ADAPTIVE, colors=255) for f in frames]
    gif[0].save(os.path.join(HERE, "transition_disc_to_portrait.gif"), save_all=True, append_images=gif[1:],
                duration=int(1000 / fps), loop=0, disposal=2)
    sheet_w = stills[0].width // 2
    sheet = Image.new("RGB", (sheet_w, sum(s.height // 2 for s in stills)), (16, 16, 20))
    y = 0
    for s_img in stills:
        small = s_img.convert("RGB").resize((s_img.width // 2, s_img.height // 2), Image.NEAREST)
        sheet.paste(small, (0, y))
        y += small.height
    sheet.save(os.path.join(HERE, "transition_storyboard.png"))
    print(f"wrote transition_disc_to_portrait.gif ({len(frames)} frames) + transition_storyboard.png ({len(stills)} beats)")


if __name__ == "__main__":
    main()
