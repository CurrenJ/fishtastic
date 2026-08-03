import shutil
import os

# Merge fabric's datagen output into the common module's resources.
# Unlike copy_assets.py (fabric -> neoforge, which is a clean wipe-and-copy
# of a directory that holds *only* generated content), common/src/main/resources
# also holds hand-authored files (textures, lang, sounds, mixins.json, ...)
# interleaved with generated subtrees, so we merge/overwrite instead of wiping.
src_dir = "fabric/src/main/generated"
dst_dir = "common/src/main/resources"

# Paths (relative to src_dir/dst_dir) where the fabric datagen Java providers
# are known to produce output that is stale/incomplete compared to what's
# hand-committed in common. Overwriting these has previously deleted real
# data. Fix the underlying datagen provider before removing an entry here.
EXCLUDE = {
    # declareCustomModelItem (FishtasticModelProvider) always stubs a generic
    # "minecraft:model" reference here, clobbering the hand-authored
    # "fishtastic:fish_tank_composite" custom item model type that drives the
    # tank's dynamic frame/glass/sand inventory icon. Nothing to fix upstream -
    # fabric-api's datagen helper has no way to express a custom model type.
    "assets/fishtastic/items/fish_tank.json",
}

skipped = []
for top in ("assets", "data"):
    src_top = os.path.join(src_dir, top)
    if not os.path.exists(src_top):
        continue
    for root, _dirs, files in os.walk(src_top):
        rel_root = os.path.relpath(root, src_dir)
        for name in files:
            rel_path = os.path.join(rel_root, name).replace(os.sep, "/")
            if rel_path in EXCLUDE:
                skipped.append(rel_path)
                continue
            dst_path = os.path.join(dst_dir, rel_path)
            os.makedirs(os.path.dirname(dst_path), exist_ok=True)
            shutil.copy2(os.path.join(src_dir, rel_path), dst_path)

if skipped:
    print("Skipped (excluded, see EXCLUDE in this script):")
    for path in skipped:
        print(f"  {path}")

print("Generated assets copied from Fabric to common successfully.")
