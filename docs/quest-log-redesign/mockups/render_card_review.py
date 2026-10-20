"""Reviewed quest-book proposal. Run: python -I docs/quest-log-redesign/mockups/render_mockups.py

Static interaction fixtures, not game implementation. Quest names, tiers, targets and
rewards are loaded from authored data. The matched baseline preserves the live fixture.
"""
import importlib.util
import hashlib
import json
import os
from PIL import Image, ImageChops, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
spec = importlib.util.spec_from_file_location("quest_renderer", os.path.join(HERE, "render_mockups.py"))
q = importlib.util.module_from_spec(spec)
spec.loader.exec_module(q)
FONT, text, panel, button = q.FONT, q.text, q.panel, q.button
WHITE, MUTED, GOLD, READY = q.WHITE, q.MUTED, q.GOLD, q.READY
DATA = os.path.join(ROOT, "common", "src", "main", "resources", "data", "fishtastic")
ROW_H, PITCH, TOP, VIEW_H = 36, 36, 48, 180
TIER_PANELS = {
    "bronze": "generic_item_panel.png",
    "silver": "generic_item_panel_silver_border.png",
    "gold": "generic_item_panel_gold_border.png",
}


def authored(path, summary, current=0, state="progress", icon="item/fish/angler_fish.png", target=None):
    with open(os.path.join(DATA, "fishtastic", "quest", path + ".json"), encoding="utf-8") as f:
        data = json.load(f)
    objective = data["objective"]
    return dict(key=path, title=data["display_name"], summary=summary,
                current=current, target=target or objective.get("target_count", 1),
                reward=data["reward"].get("quest_tokens", 0), items=data["reward"].get("items", []),
                tier=data.get("difficulty", "bronze"), state=state, icon=icon)


def baseline():
    return [authored("tutorial/first_catch", "Catch any fish"),
            authored("daily/cave_dweller_bronze", "Catch 5 fish in cave water", icon="item/fish/bluegill.png"),
            authored("daily/heavy_hitter_bronze", "Catch 2 large fish"),
            authored("daily/treasure_haul_bronze", "Catch 3 treasure items"),
            authored("daily/bright_catch_gold", "Catch 8 yellow-hued fish")]


def daily():
    rows = baseline()
    for i in (0, 4):
        rows[i]["current"] = rows[i]["target"]
        rows[i]["state"] = "ready"
    return [rows[0], rows[4], *rows[1:4]]


def clip(c, x, y, w, h, draw):
    base, base_draw = c.img, c.draw
    layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    c.img, c.draw = layer, ImageDraw.Draw(layer, "RGBA")
    draw()
    mask = Image.new("L", layer.size, 0)
    ImageDraw.Draw(mask).rectangle((x*c.s, y*c.s, (x+w)*c.s-1, (y+h)*c.s-1), fill=255)
    layer.putalpha(ImageChops.multiply(layer.getchannel("A"), mask))
    c.img, c.draw = base, base_draw
    base.alpha_composite(layer)


def scrollbar(c, x, y, h, content):
    if content <= h:
        return
    # Original rework palette: recessed mauve track and warm gold thumb/highlight.
    thumb_h = max(18, int(h*h/content))
    c.rect(x, y, 3, h, 0x352A32)
    c.rect(x, y, 1, h, 0x72505B)
    c.rect(x, y, 3, thumb_h, 0xC58A58)
    c.rect(x, y, 1, thumb_h, 0xFFE0A0)


def neutral(c, x, y, w, h):
    # Preserve the original warm mauve panel texture; neutral refers to state,
    # not a dark ink wash over the card artwork.
    panel(c, x, y, w, h)


def quest_panel(c, x, y, w, h, tier):
    """Use the shipped tier-specific artwork; keep focus a separate UI treatment."""
    path = os.path.join(q.pixel.TEX, "gui", TIER_PANELS[tier])
    c.nine(path, x, y, w, h, border=4)


def reward_sprite(item):
    """Resolve an actual Minecraft GUI item capture by item ID + components."""
    appearance = {"id": item["id"], "components": item.get("components", {})}
    key = hashlib.sha256(json.dumps(appearance, sort_keys=True, separators=(",", ":")).encode()).hexdigest()[:16]
    path = os.path.join(HERE, "assets", "items", key + ".png")
    if not os.path.isfile(path):
        raise ValueError(f"Missing actual ItemStack capture for {appearance}; rerun item-export.init.gradle and extract_sprites.py")
    return path


def reward_stack(c, x, y, items):
    """Three real item sprites with 6px overlap; excess is an explicit +N badge.

    Quantities/components belong in hover and expanded rewards, not on overlapping sprites.
    Every sprite is a GUI ItemStack render captured in-game, including tank components.
    """
    for i, item in enumerate(items[:3]):
        # Back-to-front draw order leaves 10px of each rear sprite readable.
        c.tex(reward_sprite(item), x + 10*i, y, 16, 16)
    if len(items) > 3:
        label = f"+{len(items)-3}"
        badge_w = FONT.width(label)+4
        # Tuck the indicator into the stack with a 2px overlap on the last sprite.
        badge_x = x+34
        c.rect(badge_x, y+3, badge_w, 10, 0x201A24, 225)
        text(c, badge_x+2, y+4, label, WHITE, box=badge_w-4, label="additional reward entries")


def row(c, x, y, w, r, opened=False):
    # Reserve one fixed preview column, including overflow space, in every quest row.
    title_width = 126 if r["items"] else 172
    titles = FONT.wrap(r["title"], title_width)
    objective_width = 126 if r["items"] else 192
    objectives = FONT.wrap(r["summary"], objective_width)
    extra_title = 9 * (len(titles)-1)
    extra = extra_title + 9 * (len(objectives)-1)
    height = ROW_H + extra
    quest_panel(c, x, y, w, height, r["tier"])
    state = r["state"]
    c.rect(x+4, y+4, 2, height-8, READY if state == "ready" else (0x777077 if state == "done" else q.PROGRESS))
    c.tex(r["icon"], x+10, y+7, 16, 16)
    for i, line in enumerate(titles):
        text(c, x+32, y+4+9*i, line, WHITE, box=title_width, label="row title")
    # Row height grows instead of clipping or shrinking title/condition text.
    for i, line in enumerate(objectives):
        text(c, x+32, y+14+extra_title+9*i, line, MUTED, box=objective_width, label="row objective")
    q.progress(c, x+32, y+24+extra, 76, r["current"]/r["target"], READY if state=="ready" else (0x777077 if state=="done" else q.PROGRESS))
    text(c, x+114, y+23+extra, f'{r["current"]} / {r["target"]}', WHITE, box=44 if r["items"] else 65, label="row count")
    if state == "ready":
        claim_h = 19
        claim_y = y + (height-claim_h)//2
        q.claim_button_inset(c, x+w-18, claim_y, r["reward"], f'Claim {r["reward"]}', h=claim_h, min_w=65, padding=4)
    elif state == "done":
        text(c, x+w-78, y+6, "Claimed", MUTED, box=55, label="claimed state")
    else:
        reward_icon_y = y + (height-16)//2
        reward_text_y = y + (height-8)//2
        q.coin(c, r["reward"], x+w-81, reward_icon_y)
        text(c, x+w-60, reward_text_y, str(r["reward"]), GOLD, box=32, label="row reward")
    if r["items"]:
        stack_y = y + (height-16)//2
        reward_stack(c, x+w-152, stack_y, r["items"])
    # Disclosure remains a separate visible target for ready rows too.
    disclosure_y = y + (height-8)//2
    text(c, x+w-12, disclosure_y, "^" if opened else ">", WHITE, box=8, label="disclosure")
    return height


def heading(active, title, counts=None, balance=42):
    c, px, py, pw = q.shell(active, title, token_label=f"{balance} tokens", ready_counts=counts or {})
    return c, px, py, pw, px+11, pw-22


def filters(c, x, py, ready=0):
    q.filter_row(c, x, py+q.FILTER_TOP, ("All", "In progress", f"Ready ({ready})"), "All")


def daily_screen(name="mockup_daily_board.png", matched=False):
    rows = baseline() if matched else daily()
    counts = {} if matched else {"Today": 2, "Goals": 1}
    c, px, py, pw, x, w = heading("Today", "Today's quests", counts, 0 if matched else 42)
    filters(c, x, py, 0 if matched else 2)
    text(c, px+pw-105, py+q.FILTER_TOP+4, "Resets 19:41", q.BLUE, box=94, label="daily reset")
    y = py+TOP
    for r in rows:
        y += row(c, x, y, w, r)
    return q.save(c, name)


def tag_members(name):
    with open(os.path.join(DATA, "tags", "item", name + ".json"), encoding="utf-8") as f:
        values = json.load(f)["values"]
    result = set()
    for value in values:
        value = value if isinstance(value, str) else value["id"]
        if value.startswith("#fishtastic:"):
            result.update(tag_members(value.split(":",1)[1]))
        else:
            result.add(value)
    return result


def tag_target(name):
    return len(tag_members(name) - tag_members("unlisted_fish"))


def river():
    target = tag_target("zone_river")
    return authored("collector/river_collector", f"Discover {target} river species", 6,
                    icon="item/fish/bluegill.png", target=target)


def expanded_rewards(c, x, y, w, r):
    """The same reward block for every expanded quest, regardless of category/state."""
    text(c, x+12, y, "Rewards", WHITE, box=80, label="rewards heading")
    if r["reward"]:
        q.coin(c, r["reward"], x+12, y+12)
        text(c, x+32, y+16, f'{r["reward"]} tokens', GOLD, box=92, label="token reward")
    for i, item in enumerate(r["items"]):
        if item["id"] == "fishtastic:worms":
            name = "Worms"
        elif item["id"] == "fishtastic:fish_tank":
            components = item.get("components", {})
            shape = components.get("fishtastic:fish_tank_shape")
            frame = components.get("fishtastic:fish_tank_materials", {}).get("frame")
            name = "Sturdy tank" if shape == "sturdy" else ("Oak tank" if frame == "minecraft:oak_planks" else "Fish tank")
        else:
            name = item["id"].split(":", 1)[1].replace("_", " ").capitalize()
        c.tex(reward_sprite(item), x+135, y+11+i*15, 12, 12)
        text(c, x+151, y+13+i*15, f'{name} x{item["count"]}', WHITE, box=w-163, label="item reward")
    return max(30, 11+15*len(r["items"]))


# Illustrative caught set. Every other eligible river species retains its real silhouette.
CAUGHT_RIVER = {"bluegill", "betta", "bridle_shiner", "clown_loach", "glass_catfish", "neon_tetra"}


def river_species():
    eligible = tag_members("zone_river") - tag_members("unlisted_fish")
    assert {"fishtastic:"+name for name in CAUGHT_RIVER} <= eligible
    def weight(species):
        profile = os.path.join(DATA, "fishtastic", "fish_profile", species.split(":",1)[1]+".json")
        with open(profile, encoding="utf-8") as f:
            return json.load(f).get("base_weight", 0)
    # Match the existing collection list's descending base-weight ordering.
    return sorted(eligible, key=lambda species: (-weight(species), species))


def collection_icons(c, x, y, w):
    members = river_species()
    columns, pitch, size = 10, 26, 20
    for offset in range(0, len(members), columns):
        group = members[offset:offset+columns]
        start = x + (w - ((len(group)-1)*pitch+size))//2
        for col, species in enumerate(group):
            name = species.split(":",1)[1]
            c.tex("item/fish/"+name+".png", start+col*pitch, y+(offset//columns)*24,
                  size, size, silhouette=name not in CAUGHT_RIVER)
    return ((len(members)+columns-1)//columns-1)*24+size


def expanded_collection(c, x, y, w):
    r = river()
    grid_h = ((r["target"]+9)//10-1)*24+20
    header_h = row(c, x, y, w, r, opened=True)
    extra = header_h-ROW_H
    rewards_y = 64+extra+grid_h+8
    height = rewards_y+40
    quest_panel(c, x, y+header_h-3, w, height-header_h+1, r["tier"])
    text(c, x+12, y+39+extra, "Catch each river species once.", WHITE, box=w-24, label="collection instruction")
    text(c, x+12, y+51+extra, "Each species counts once.", MUTED, box=w-24, label="distinct explanation")
    collection_icons(c, x+12, y+64+extra, w-24)
    expanded_rewards(c, x, y+rewards_y, w, r)
    return height


def render_expanded():
    c, px, py, pw, x, w = heading("Collect", "Species collections", {"Today": 2, "Goals": 1})
    filters(c, x, py)
    content_height = [0]
    def content():
        h = expanded_collection(c, x, py+TOP, w)
        r = authored("collector/ocean_collector", "Discover every ocean species", 0, target=tag_target("zone_ocean"))
        next_y = py+TOP+h
        next_y += row(c, x, next_y, w, r)
        r = authored("collector/cave_collector", "Discover every cave species", 0, target=tag_target("zone_cave"))
        next_y += row(c, x, next_y, w, r)
        content_height[0] = next_y-(py+TOP)
    clip(c, px+5, py+TOP, pw-10, VIEW_H, content)
    scrollbar(c, px+pw-8, py+TOP, VIEW_H, content_height[0])
    q.save(c, "mockup_expanded_row.png")


def expanded_items(c, x, y, w):
    r = daily()[0]
    row(c, x, y, w, r, opened=True)
    quest_panel(c, x, y+ROW_H-3, w, 89, r["tier"])
    text(c, x+12, y+40, "Tutorial: no reset. Caught 1 fish.", WHITE, box=w-24, label="completion evidence")
    expanded_rewards(c, x, y+54, w, r)
    return 126


def render_items():
    c, px, py, pw, x, w = heading("Today", "Today's quests", {"Today": 2, "Goals": 1})
    filters(c, x, py, 2)
    text(c, px+pw-105, py+q.FILTER_TOP+4, "Resets 19:41", q.BLUE, box=94, label="item reset")
    def content():
        expanded_items(c, x, py+TOP, w)
        for i, r in enumerate(daily()[1:]):
            row(c, x, py+TOP+126+i*PITCH, w, r)
    clip(c, px+5, py+TOP, pw-10, VIEW_H, content)
    scrollbar(c, px+pw-8, py+TOP, VIEW_H, 126+4*PITCH)
    q.save(c, "mockup_item_rewards.png")


def render_goals():
    c, px, py, pw, x, w = heading("Goals", "Lifetime goals", {"Today": 2, "Goals": 1})
    filters(c, x, py, 1)
    rows = [authored("mastery/tetra_scholar", "Catch 25 Neon Tetra in total", 25, "ready"),
            authored("mastery/angler_apprentice", "Catch 50 fish in total", 12, icon="item/fish/bluegill.png")]
    next_y = py+TOP
    for r in rows:
        next_y += row(c, x, next_y, w, r)
    claimed = authored("mastery/tetra_tracker", "Catch 10 Neon Tetra in total", state="done")
    claimed["current"] = claimed["target"]
    claimed["summary"] = f'Catch {claimed["target"]} Neon Tetra in total'
    row(c, x, next_y, w, claimed)
    text(c, x+10, py+170, "Lifetime catches count toward these goals.", MUTED, box=w-20, label="lifetime rule")
    q.save(c, "mockup_lifetime_goals.png")


def shop_card(c, x, y, w, icon, name, cost):
    neutral(c, x, y, w, 63)
    c.tex(icon, x+7, y+8, 22, 22)
    text(c, x+35, y+8, name, WHITE, box=w-43, label="shop item")
    q.coin(c, cost, x+33, y+22)
    text(c, x+52, y+26, str(cost), GOLD, box=48, label="price")
    if cost > 42:
        text(c, x+8, y+46, f'Need {cost-42} more', MUTED, box=85, label="shortfall")
        c.rect(x+w-51, y+43, 43, 15, 0x493843)
        q.centered(c, x+w-51, y+47, 43, "Buy", MUTED, box=39, label="disabled buy")
    else:
        button(c, x+w-51, y+43, 43, 15, "Buy", WHITE)


def render_market():
    c, px, py, pw, x, w = heading("Market", "Token market", {"Today": 2, "Goals": 1})
    text(c, x, py+32, "New stock in 19:41", q.BLUE, box=w, label="automatic rotation")
    stock = [
        ("item/bait/worms.png", "Worms x4", 6, 1),
        ("item/copper_fishing_rod.png", "Calm charm", 18, 1),
        (reward_sprite(daily()[0]["items"][2]), "Sturdy tank", 100, 1),
        ("item/quest_token.png", "Bait Buddy", 35, 0),
    ]
    # Filter remaining stock before assigning grid cells: no sold-out panel or placeholder.
    available = [entry for entry in stock if entry[3] > 0]
    for index, (icon, name, cost, remaining) in enumerate(available):
        shop_card(c, x+(index%2)*161, py+48+(index//2)*67, 153, icon, name, cost)
    if not available:
        text(c, x+12, py+72, "All stock sold out.", WHITE, box=w-24, label="empty market")
        text(c, x+12, py+86, "Wait for new stock or reroll below.", MUTED, box=w-24, label="empty market action")
    neutral(c, x, py+183, w, 43)
    text(c, x+8, py+191, "Replace today's stock", WHITE, box=174, label="reroll meaning")
    text(c, x+8, py+207, "Next reroll: 50 tokens", MUTED, box=174, label="next cost")
    q.coin(c, 25, x+w-132, py+191)
    text(c, x+w-111, py+195, "25", GOLD, box=23, label="reroll cost")
    button(c, x+w-82, py+193, 74, 20, "Reroll stock", WHITE)
    q.save(c, "mockup_market.png")


def render_cleanup():
    c, px, py, pw, x, w = heading("Cleanup", "Clean up the waters", {"Today": 2, "Goals": 1})
    neutral(c, x, py+32, w, 60)
    text(c, x+10, py+40, "Next milestone: 200 trash", WHITE, box=w-20, label="threshold")
    text(c, x+10, py+53, "125 / 200 removed", WHITE, box=150, label="cycle progress")
    text(c, x+w-66, py+53, "75 left", MUTED, box=56, label="remaining")
    q.progress(c, x+10, py+69, w-20, 125/200)
    text(c, x+10, py+81, "50-token pool; paid automatically", GOLD, box=w-20, label="pool")
    text(c, x, py+100, "Contribute this cycle to receive a share.", WHITE, box=w, label="eligibility")
    text(c, x, py+112, "Proportional payout; at least 1 token each.", MUTED, box=w, label="payout rule")
    neutral(c, x, py+127, w, 34)
    text(c, x+10, py+135, "Your contribution: 10 trash (8%)", WHITE, box=w-20, label="personal contribution")
    text(c, x+10, py+148, "Cycle restarts at 200; excess carries over.", MUTED, box=w-20, label="cycle rule")
    text(c, x, py+170, "Top contributors this cycle", WHITE, box=w, label="contributors")
    for i, (name, amount) in enumerate([("Mira",55), ("Jo",39), ("Ari",21)]):
        cy=py+184+i*13
        text(c, x+4, cy, name, MUTED, box=60, label="contributor")
        q.progress(c, x+77, cy+1, 151, amount/125)
        text(c, x+239, cy, f'{amount} ({round(amount/125*100)}%)', MUTED, box=75, label="contributor amount")
    q.save(c, "mockup_cleanup_goal.png")


def render_reward_stacks():
    c, px, py, pw, x, w = heading("Today", "Reward preview fixtures")
    filters(c, x, py, 2)
    first, bright = daily()[:2]
    row(c, x, py+TOP, w, first)
    row(c, x, py+TOP+PITCH, w, bright)
    overflow = dict(first)
    overflow["title"] = "Overflow example"
    overflow["state"] = "progress"
    overflow["current"] = 0
    overflow["items"] = first["items"] + bright["items"] + first["items"][:1]
    row(c, x, py+TOP+2*PITCH, w, overflow)
    text(c, x+10, py+172, "First two rows use authored rewards.", MUTED, box=w-20, label="preview fixture scope")
    text(c, x+10, py+185, "Third row: synthetic five-entry reward.", MUTED, box=w-20, label="overflow fixture scope")
    q.save(c, "mockup_reward_stacks.png")


def render_wrapping():
    c, px, py, pw, x, w = heading("Today", "Layout stress fixture")
    filters(c, x, py)
    fixture = authored("daily/cave_dweller_bronze", "Catch 5 fish in cave waters", icon="item/fish/bluegill.png")
    # Synthetic copy deliberately exceeds the normal row width, without asserting a new quest.
    fixture["title"] = "An unusually long translated quest title"
    fixture["summary"] = "Catch 5 fish in cave water while all required conditions are satisfied"
    h = row(c, x, py+TOP, w, fixture)
    assert h > ROW_H
    row(c, x, py+TOP+h, w, baseline()[2])
    text(c, x+10, py+180, "Synthetic copy: wrapping demonstration only.", MUTED, box=w-20, label="fixture scope")
    q.save(c, "mockup_wrapped_objective.png")


def compare(name, right_name):
    current = Image.open(q.CURRENT).convert("RGB")
    proposed = Image.open(os.path.join(HERE, right_name)).convert("RGB")
    sheet = Image.new("RGB", (1944, 576), (18,16,20))
    sheet.paste(current, (0,36))
    sheet.paste(proposed, (984,36))
    d=ImageDraw.Draw(sheet)
    d.text((12,10), "CURRENT: live capture | 5 quests, 0 ready, 0 tokens", fill="white")
    d.text((996,10), "PROPOSED: same quests, targets, rewards and state | visual comparison only", fill="white")
    sheet.save(os.path.join(HERE,name))
    print("wrote",name)


def main():
    daily_screen()
    daily_screen("mockup_compact_rows.png")
    daily_screen("mockup_matched_baseline.png", matched=True)
    render_expanded()
    render_items()
    render_goals()
    render_market()
    render_cleanup()
    render_wrapping()
    render_reward_stacks()
    compare("compare_current_vs_proposed.png", "mockup_matched_baseline.png")
    compare("compare_current_vs_compact_rows.png", "mockup_matched_baseline.png")
    # Matched layout comparison isolates density from content and state changes.
    compare_rows = baseline()
    assert len(compare_rows)==5 and all(r["current"]==0 for r in compare_rows)
    assert sum(r["state"]=="ready" for r in daily())==2
    assert sum(len(r["items"]) for r in compare_rows)==4
    if q.pixel.OVERFLOWS:
        for issue in q.pixel.OVERFLOWS:
            print("LAYOUT OVERFLOW:",issue)
        raise SystemExit(1)
    print("All declared text boxes fit; baseline fixture and ready counts checked.")


if __name__ == "__main__":
    main()
