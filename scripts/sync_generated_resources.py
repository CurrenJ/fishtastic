"""Merge successful Fabric datagen without deleting authored resources.

Assets and vanilla data overwrite generated counterparts, except the custom tank
item model. Custom registry entries map to runtime paths; existing entries win
unless --overwrite-registry explicitly names their registry. Nothing is pruned.
"""
import argparse
from pathlib import Path
import shutil

REPO_ROOT = Path(__file__).resolve().parent.parent
REGISTRIES = frozenset({"cosmetic_structure", "fish_profile", "fish_encyclopedia_entry",
                        "temperament", "quest", "shop_entry", "item_effect"})
PROTECTED = frozenset({"assets/fishtastic/items/fish_tank.json"})


def destination_path(relative):
    parts = relative.parts
    if len(parts) >= 4 and parts[0] == "data":
        offset = 3 if parts[2] == "fishtastic" else 2
        if len(parts) > offset + 1 and parts[offset] in REGISTRIES:
            return Path("data", parts[1], "fishtastic", *parts[offset:]), parts[offset]
    return relative, None


def sync(source, destination, overwrite_registries=(), dry_run=False):
    if not source.is_dir() or not any((source / name).is_dir() for name in ("assets", "data")):
        raise ValueError(f"No assets/data output in {source}; run :fabric:runDatagen first")
    overwrite_registries = set(overwrite_registries)
    if overwrite_registries - REGISTRIES:
        raise ValueError("Unknown registry overwrite request")
    source, destination = source.resolve(), destination.resolve()
    if source == destination or source in destination.parents or destination in source.parents:
        raise ValueError("Generated and authored resource roots must be separate")
    copied = preserved = unchanged = 0
    # Validate the complete plan before writes, including conflicting path aliases.
    plan = {}
    for top in ("assets", "data"):
        for file in sorted((source / top).rglob("*")):
            if not file.is_file():
                continue
            if source not in file.resolve().parents:
                raise ValueError(f"Generated input escapes source root: {file}")
            relative, registry = destination_path(file.relative_to(source))
            target = (destination / relative).resolve()
            if destination not in target.parents:
                raise ValueError(f"Output escapes destination root: {target}")
            if relative.as_posix() in PROTECTED or (registry and target.exists() and registry not in overwrite_registries):
                preserved += 1
                continue
            if target in plan and plan[target].read_bytes() != file.read_bytes():
                raise ValueError(f"Conflicting generated entries for {relative}")
            plan[target] = file
    for target, file in plan.items():
        if target.is_file() and target.read_bytes() == file.read_bytes():
            unchanged += 1
            continue
        if not dry_run:
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(file, target)
        copied += 1
    return copied, preserved, unchanged


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=REPO_ROOT / "fabric/src/main/generated")
    parser.add_argument("--destination", type=Path, default=REPO_ROOT / "common/src/main/resources")
    parser.add_argument("--overwrite-registry", action="append", choices=sorted(REGISTRIES), default=[],
                        help="Replace existing entries emitted for this registry; never prune")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    try:
        copied, preserved, unchanged = sync(args.source, args.destination, args.overwrite_registry, args.dry_run)
    except ValueError as error:
        parser.exit(1, f"{error}\n")
    print(f"{'Would copy' if args.dry_run else 'Copied'} {copied}; preserved {preserved} authored entries; "
          f"unchanged {unchanged}. No files pruned.")


if __name__ == "__main__":
    main()
