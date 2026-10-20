package grill24.fishtastic.client.encyclopedia.portrait;

import grill24.fishtastic.client.encyclopedia.portrait.PortraitBubbles.Bubble;
import io.github.currenj.gelatinui.gui.IRenderContext;
import io.github.currenj.gelatinui.gui.minecraft.MinecraftRenderContext;
import com.mojang.blaze3d.systems.RenderSystem;
import grill24.fishtastic.util.Ids;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws {@link PortraitBubbles} particles into the current element's local space (plate-local
 * logical px). Everything is rounded to the logical pixel grid and clipped to the plate interior
 * with {@code pushScissor}, as the prototype's {@code _draw}/{@code _px}/{@code _blit} do.
 */
final class PortraitRenderer {
    private static final ResourceLocation SMALL = Ids.of("fishtastic", "textures/particle/small_bubble.png");
    private static final ResourceLocation MEDIUM = Ids.of("fishtastic", "textures/particle/medium_bubble.png");
    private static final ResourceLocation[] POPS = new ResourceLocation[5];

    static {
        for (int i = 0; i < POPS.length; i++) {
            POPS[i] = Ids.withDefaultNamespace("textures/particle/bubble_pop_" + i + ".png");
        }
    }

    private PortraitRenderer() {
    }

    /** Draws the bubbles of one depth layer, clipped to the plate interior (1px frame excluded). */
    static void drawBubbles(IRenderContext ctx, PortraitBubbles model, boolean front) {
        ctx.pushScissor(1, 1, PortraitBubbles.W - 2, PortraitBubbles.H - 2);
        float mute = model.opacity();
        for (Bubble b : model.bubbles()) {
            if (b.front == front) draw(ctx, b, mute);
        }
        ctx.popScissor();
    }

    private static void draw(IRenderContext ctx, Bubble b, float mute) {
        int x = b.drawX();
        int y = b.drawY();
        float a = b.alpha * mute;
        if (b.popping) {
            blit(ctx, POPS[b.popFrame()], x - 4, y - 4, 8, 8, 0, 0, 16, 16, 16, 16, a);
            return;
        }
        switch (b.kind) {
            case MICRO -> {
                float fade = Math.min(1f, (b.life - b.age) / 0.4f);
                px(ctx, x, y, b.colour, a * fade);
            }
            case SMALL -> {
                if (b.life - b.age < 0.6f) {            // dissolves: shrinks to 1px, then gone
                    px(ctx, x, y, 0xE6F0FF, a * 0.8f);
                } else {
                    blit(ctx, SMALL, x, y, 2, 2, 0, 0, 2, 2, 2, 2, a);
                }
            }
            case MEDIUM -> blit(ctx, MEDIUM, x - b.size / 2, y - b.size / 2, b.size, b.size, 2, 2, 6, 6, 8, 8, a);
            case SNOW -> px(ctx, x, y, b.colour, a);
            case EMBER -> {
                float f = b.age / b.life;
                boolean hot = b.size == 2 && f < 0.35f;
                int col = hot ? 0xFFFAD2 : f < 0.3f ? 0xFFE678 : f < 0.7f ? 0xFF9628 : 0xC83C14;
                float flicker = hot ? 1f : 0.6f + 0.4f * (float) Math.sin(b.age * 23 + b.phase);
                float alpha = a * flicker * Math.min(1f, (b.life - b.age) / 0.5f);
                if (hot) {
                    px(ctx, x, y, col, alpha);
                    px(ctx, x + 1, y, col, alpha);
                    px(ctx, x, y + 1, col, alpha);
                    px(ctx, x + 1, y + 1, col, alpha);
                } else {
                    px(ctx, x, y, col, alpha);
                }
            }
            case BLOB -> {
                int s = Math.max(1, Math.round(b.size * (1 - b.age / b.life * 0.6f)));
                for (int dx = 0; dx < s; dx++) {
                    for (int dy = 0; dy < s; dy++) px(ctx, x + dx, y + dy, 0xFF781E, 0.75f * mute);
                }
            }
        }
    }

    private static void px(IRenderContext ctx, int x, int y, int rgb, float a) {
        if (a <= 0.02f) return;
        ctx.fill(x, y, x + 1, y + 1, argb(a, rgb));
    }

    static int argb(float a, int rgb) {
        return (Math.round(255f * Math.min(1f, a)) << 24) | (rgb & 0xFFFFFF);
    }

    private static void blit(IRenderContext ctx, ResourceLocation tex, int x, int y, int w, int h,
                             float u, float v, int regionW, int regionH, int texW, int texH, float a) {
        if (a <= 0.02f) return;
        if (ctx instanceof MinecraftRenderContext mc) {
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, Math.min(1f, a));
            mc.getGraphics().blit(tex, x, y, w, h, u, v, regionW, regionH, texW, texH);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        } else {
            ctx.drawTexture(tex, x, y, w, h, u, v, regionW, regionH, texW, texH);
        }
    }
}
