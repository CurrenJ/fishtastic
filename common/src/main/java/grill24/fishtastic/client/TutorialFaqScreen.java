package grill24.fishtastic.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.FittingMultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/** Plain scrolling Q&A text, opened from {@link TutorialHelpScreen}. */
public final class TutorialFaqScreen extends Screen {
    private static final String KEY_IMPULSE = "key.fishtastic.fishing_minigame_impulse";
    private static final String KEY_QUESTS = "key.fishtastic.open_quest_log";
    private static final String KEY_FISHOPEDIA = "key.fishtastic.open_fish_encyclopedia";
    private static final String KEY_LEADERBOARDS = "key.fishtastic.open_leaderboards";
    private static final String KEY_BOND = "key.fishtastic.bond_companion";

    /** Key bindings are passed as arguments so an answer always names the player's current key. */
    private record Entry(String id, String... keys) {}

    private static final List<Entry> ENTRIES = List.of(
            new Entry("rod"),
            new Entry("bait"),
            new Entry("no_bait"),
            new Entry("load_bait"),
            new Entry("minigame", KEY_IMPULSE),
            new Entry("species", KEY_FISHOPEDIA),
            new Entry("nether"),
            new Entry("books", KEY_QUESTS, KEY_FISHOPEDIA, KEY_LEADERBOARDS),
            new Entry("tokens", KEY_QUESTS),
            new Entry("gear"),
            new Entry("tank"),
            new Entry("tank_use"),
            new Entry("piles"),
            new Entry("companion", KEY_BOND),
            new Entry("wild_fish"));

    private final Screen parent;

    public TutorialFaqScreen(Screen parent) {
        super(Component.translatable("tutorial.fishtastic.faq.title"));
        this.parent = parent;
    }

    private static Component faqText() {
        MutableComponent text = Component.empty();
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            String prefix = "tutorial.fishtastic.faq." + entry.id();
            Object[] keys = new Object[entry.keys().length];
            for (int k = 0; k < keys.length; k++) keys[k] = Component.keybind(entry.keys()[k]);
            if (i > 0) text.append("\n\n");
            text.append(Component.translatable("tutorial.fishtastic.faq.question",
                    Component.translatable(prefix + ".q")).withStyle(ChatFormatting.GOLD));
            text.append("\n");
            text.append(Component.translatable("tutorial.fishtastic.faq.answer",
                    Component.translatable(prefix + ".a", keys)));
        }
        return text;
    }

    @Override protected void init() {
        int areaWidth = Math.min(400, width - 40);
        int areaTop = 30;
        FittingMultiLineTextWidget area = addRenderableWidget(new FittingMultiLineTextWidget(
                (width - areaWidth) / 2, areaTop, areaWidth, height - areaTop - 36, faqText(), font));
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 105, height - 28, 210, 20).build());
        setInitialFocus(area); // arrow keys scroll straight away
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
