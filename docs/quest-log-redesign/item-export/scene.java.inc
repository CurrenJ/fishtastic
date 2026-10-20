    // Design-only export scene, compiled through item-export.init.gradle.
    private static void queueQuestSpritesScene() {
        List<ItemStack> stacks = new java.util.ArrayList<>();
        queue(1, mc -> {
            var registry = mc.level.registryAccess().lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);
            String[] ids = {"tutorial/first_catch", "daily/bright_catch_gold", "collector/river_collector",
                "collector/ocean_collector", "collector/cave_collector", "mastery/tetra_scholar",
                "mastery/angler_apprentice", "mastery/tetra_tracker"};
            for (String id : ids) {
                var quest = registry.getValue(Ids.of("fishtastic", id));
                if (quest == null) throw new IllegalStateException("Missing export quest " + id);
                for (int i = 0; i < quest.reward().items().size(); i++) {
                    var stack = quest.reward().items().get(i).toStack();
                    note("questsprites.slot " + stacks.size() + " " + id + " " + i + " " + stack);
                    stacks.add(stack);
                }
            }
            check("questsprites.rewardStacks", stacks.size() == 10, "stacks=" + stacks.size());
        });
        for (int color : new int[]{0xFF000000, 0xFFFFFFFF}) {
            queue(1, mc -> {
                mc.getToastManager().clear();
                mc.setScreen(new net.minecraft.client.gui.screens.Screen(Component.literal("Reward item export")) {
                    @Override public boolean isPauseScreen() { return false; }
                    @Override public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int mx, int my, float dt) {
                        g.fill(0, 0, width, height, color);
                        g.pose().pushMatrix();
                        g.pose().scale(2f, 2f);
                        for (int i = 0; i < stacks.size(); i++) {
                            g.item(stacks.get(i), 16 + (i % 6) * 32, 20 + (i / 6) * 32);
                        }
                        g.pose().popMatrix();
                    }
                });
            });
            queue(40, mc -> screenshot(mc, "questsprites", color == 0xFF000000 ? "black" : "white"));
        }
        queue(1, mc -> mc.setScreen(null));
    }
