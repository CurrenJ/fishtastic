"""Regression coverage for authored-resource preservation during datagen sync."""
from pathlib import Path
import tempfile
import unittest
from sync_generated_resources import sync


class ResourceSyncTest(unittest.TestCase):
    def setUp(self):
        self.workspace = tempfile.TemporaryDirectory()
        self.addCleanup(self.workspace.cleanup)
        self.source = Path(self.workspace.name) / "generated"
        self.destination = Path(self.workspace.name) / "authored"

    @staticmethod
    def write(root, name, text):
        file = root / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(text, encoding="utf-8")
        return file

    def test_merge_preserves_authored_registry_custom_model_and_destination_only_files(self):
        model = "assets/fishtastic/items/fish_tank.json"
        registry = "data/fishtastic/fishtastic/cosmetic_structure/arch.json"
        self.write(self.source, model, "stub")
        self.write(self.destination, model, "custom model")
        self.write(self.source, "data/fishtastic/cosmetic_structure/arch.json", "incomplete arch")
        self.write(self.destination, registry, "arch with shelter")
        retained = self.write(self.destination, registry.replace("arch.json", "shipwreck.json"), "hand built")
        self.write(self.source, "assets/fishtastic/models/block/frame.json", "frame")
        self.write(self.source, "data/fishtastic/tags/item/fish.json", "tag")
        sync(self.source, self.destination)
        self.assertEqual("custom model", (self.destination / model).read_text())
        self.assertEqual("arch with shelter", (self.destination / registry).read_text())
        self.assertEqual("hand built", retained.read_text())
        self.assertEqual("frame", (self.destination / "assets/fishtastic/models/block/frame.json").read_text())
        self.assertEqual("tag", (self.destination / "data/fishtastic/tags/item/fish.json").read_text())
        self.assertFalse((self.destination / "data/fishtastic/cosmetic_structure").exists())

    def test_new_registry_paths_are_mapped_and_overwrite_is_explicit(self):
        generated = "data/fishtastic/cosmetic_structure/new.json"
        runtime = "data/fishtastic/fishtastic/cosmetic_structure/new.json"
        self.write(self.source, generated, "first")
        sync(self.source, self.destination)
        self.write(self.source, generated, "replacement")
        sync(self.source, self.destination)
        self.assertEqual("first", (self.destination / runtime).read_text())
        sync(self.source, self.destination, ["cosmetic_structure"])
        self.assertEqual("replacement", (self.destination / runtime).read_text())

    def test_nested_registry_path_and_dry_run(self):
        name = "data/minecraft/fishtastic/quest/explorer/example.json"
        self.write(self.source, name, "quest")
        sync(self.source, self.destination, dry_run=True)
        self.assertFalse(self.destination.exists())
        sync(self.source, self.destination)
        self.assertEqual("quest", (self.destination / name).read_text())

    def test_conflicting_aliases_fail_before_writes(self):
        self.write(self.source, "data/fishtastic/quest/example.json", "one")
        self.write(self.source, "data/fishtastic/fishtastic/quest/example.json", "two")
        with self.assertRaises(ValueError):
            sync(self.source, self.destination)
        self.assertFalse(self.destination.exists())

    def test_missing_source_and_overlapping_roots_fail(self):
        with self.assertRaises(ValueError):
            sync(self.source, self.destination)
        self.write(self.source, "assets/example.json", "asset")
        with self.assertRaises(ValueError):
            sync(self.source, self.source / "authored")


if __name__ == "__main__":
    unittest.main()
