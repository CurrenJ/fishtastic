package grill24.fishtastic.client;

import grill24.fishtastic.data.FishProfile;
import io.github.currenj.gelatinui.gui.DirtyFlag;
import io.github.currenj.gelatinui.gui.IRenderContext;
import io.github.currenj.gelatinui.gui.IUIElement;
import io.github.currenj.gelatinui.gui.UIContainer;
import io.github.currenj.gelatinui.gui.UIElement;
import io.github.currenj.gelatinui.gui.UIEvent;
import net.minecraft.resources.ResourceKey;
import org.joml.Vector2f;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Home-screen disc for the fish encyclopedia: every fish rendered as an itemstack icon packed
 * into a filled circle (sunflower/Fibonacci spiral layout), with hover-grow + neighbor-jiggle
 * and click-to-select. Modeled directly on {@link io.github.currenj.gelatinui.gui.components.RotatingItemRing}
 * — see that class for the animation technique this is built on (continuous per-frame
 * {@code setTargetPosition}/{@code setTargetScale} calls, never a one-shot {@code setPosition}).
 *
 * <p>Deliberately NOT a {@code ManualContainer}: that container's {@code performLayout()} calls
 * the instant {@code setPosition} setter on every layout pass, which stomps any in-flight
 * {@code setTargetPosition} animation before it can play.
 *
 * <p>Rest positions are keyed by child identity (not list index): the selected child is removed
 * from {@link #children} for the duration of the info-page visit and later re-added via
 * {@link #reinstateAfterClose}, which would land at the end of the list — an index-keyed lookup
 * would then desync from every child whose index shifted when the selected one was removed.
 * Identity keys make re-insertion order irrelevant.
 */
public class FishSphereContainer extends UIContainer<FishSphereContainer> {
    private static final float GOLDEN_ANGLE = (float) (Math.PI * (3.0 - Math.sqrt(5.0)));

    private float discRadius = 64f;
    private float defaultItemScale = 1.0f;
    private float hoverItemScale = 1.6f;
    private static final float MAX_JIGGLE_DISPLACEMENT = 14f;
    /**
     * Exponential falloff distance for the hover push: displacement at distance {@code d} from
     * the hovered icon's rest position is {@code MAX_JIGGLE_DISPLACEMENT * exp(-d / FALLOFF)}.
     * Every icon on the disc is pushed by some (eventually imperceptible but never exactly zero)
     * amount — there's no hard cutoff radius — so the whole disc settles, not just the icons
     * immediately next to the cursor.
     */
    private static final float HOVER_FALLOFF_DISTANCE = 40f;

    private static final float INTRO_STAGGER_DELAY = 0.025f;
    private static final float INTRO_LAUNCH_DISTANCE = 140f;

    // Rest-position bookkeeping, computed once per setFishItems() call. Keyed by child identity
    // (see class doc) so it survives the selected child temporarily leaving `children`.
    private final Map<IUIElement, Vector2f> restPositions = new IdentityHashMap<>();
    private final Map<IUIElement, ResourceKey<FishProfile>> childFishKeys = new IdentityHashMap<>();
    private final Map<IUIElement, Float> introStartTimes = new IdentityHashMap<>();
    private final Map<IUIElement, Vector2f> outroTargetPositions = new IdentityHashMap<>();

    private IUIElement hoveredChild = null;
    private IUIElement selectedChild = null;
    private boolean shrinking = false;
    private boolean introActive = false;
    private float introElapsed = 0f;
    private boolean outroActive = false;

    private BiConsumer<IUIElement, ResourceKey<FishProfile>> onFishSelected = (icon, key) -> {};

    public FishSphereContainer() {
        this.size.set(160, 160);
    }

    public FishSphereContainer discRadius(float radius) {
        this.discRadius = Math.max(0f, radius);
        markDirty(DirtyFlag.LAYOUT);
        return this;
    }

    public FishSphereContainer hoverItemScale(float scale) {
        this.hoverItemScale = Math.max(0.01f, scale);
        return this;
    }

    public FishSphereContainer onFishSelected(BiConsumer<IUIElement, ResourceKey<FishProfile>> callback) {
        this.onFishSelected = callback != null ? callback : (icon, key) -> {};
        return this;
    }

    public float getDefaultItemScale() {
        return defaultItemScale;
    }

    /** Rest position (sunflower-spiral center point, in this container's local space) for {@code child}, or {@code null} if unknown. */
    public Vector2f getRestCenter(IUIElement child) {
        Vector2f p = restPositions.get(child);
        return p != null ? new Vector2f(p) : null;
    }

    /** Top-left position (in this container's local space) {@code child} would sit at, at rest, if rendered at {@code scale}. */
    public Vector2f getRestTopLeft(IUIElement child, float scale) {
        Vector2f center = restPositions.get(child);
        if (center == null) return null;
        Vector2f size = child.getSize();
        return new Vector2f(center.x - 0.5f * size.x * scale, center.y - 0.5f * size.y * scale);
    }

    /**
     * Replace the disc's contents. {@code items} order determines slot index (and therefore the
     * sunflower-spiral position) — keep it stable across rebuilds so existing fish don't visually
     * reshuffle every time a new fish is appended to the registry.
     */
    public FishSphereContainer setFishItems(List<Map.Entry<ResourceKey<FishProfile>, SilhouetteItemButton>> items) {
        clearChildren();
        childFishKeys.clear();
        restPositions.clear();
        hoveredChild = null;
        selectedChild = null;
        shrinking = false;

        for (Map.Entry<ResourceKey<FishProfile>, SilhouetteItemButton> item : items) {
            SilhouetteItemButton button = item.getValue();
            button.setTargetScale(defaultItemScale, false);
            addChild(button);
            childFishKeys.put(button, item.getKey());
        }

        computeRestLayout();
        startIntroAnimation();
        return this;
    }

    private void computeRestLayout() {
        restPositions.clear();
        int count = children.size();

        float cx = size.x * 0.5f;
        float cy = size.y * 0.5f;
        for (int i = 0; i < count; i++) {
            // Sunflower/Fibonacci spiral: index density falls off as sqrt, giving an even packing.
            float r = discRadius * (float) Math.sqrt((i + 0.5) / count);
            float theta = i * GOLDEN_ANGLE;
            Vector2f center = new Vector2f(cx + (float) Math.cos(theta) * r, cy + (float) Math.sin(theta) * r);
            restPositions.put(children.get(i), center);
        }
    }

    private void startIntroAnimation() {
        introActive = true;
        introElapsed = 0f;
        introStartTimes.clear();

        float cx = size.x * 0.5f;
        float cy = size.y * 0.5f;

        for (int i = 0; i < children.size(); i++) {
            IUIElement child = children.get(i);
            introStartTimes.put(child, i * INTRO_STAGGER_DELAY);

            if (child instanceof UIElement<?> uiChild) {
                Vector2f rest = restPositions.get(child);
                if (rest != null) {
                    // Launch point: same radial direction as rest position, but further from center.
                    Vector2f dir = new Vector2f(rest.x - cx, rest.y - cy);
                    float len = dir.length();
                    if (len < 0.001f) dir.set(1f, 0f);
                    else dir.normalize();

                    Vector2f launchCenter = new Vector2f(
                        cx + dir.x * INTRO_LAUNCH_DISTANCE,
                        cy + dir.y * INTRO_LAUNCH_DISTANCE
                    );
                    Vector2f childSize = child.getSize();
                    uiChild.setPosition(new Vector2f(
                        launchCenter.x - 0.5f * childSize.x * defaultItemScale,
                        launchCenter.y - 0.5f * childSize.y * defaultItemScale
                    ));
                }
                uiChild.setVisible(false);
            }
        }
    }

    /** Drives all visible disc icons outward along their radial lines — the reverse of the entry animation. */
    public void startOutroAnimation() {
        if (outroActive) return;
        introActive = false;
        introStartTimes.clear();
        outroActive = true;
        outroTargetPositions.clear();

        float cx = size.x * 0.5f;
        float cy = size.y * 0.5f;

        for (IUIElement child : children) {
            if (!child.isVisible()) continue;
            Vector2f rest = restPositions.get(child);
            if (rest == null) continue;

            Vector2f dir = new Vector2f(rest.x - cx, rest.y - cy);
            float len = dir.length();
            if (len < 0.001f) dir.set(1f, 0f);
            else dir.normalize();

            Vector2f launchCenter = new Vector2f(
                cx + dir.x * INTRO_LAUNCH_DISTANCE,
                cy + dir.y * INTRO_LAUNCH_DISTANCE
            );
            Vector2f childSize = child.getSize();
            outroTargetPositions.put(child, new Vector2f(
                launchCenter.x - 0.5f * childSize.x * defaultItemScale,
                launchCenter.y - 0.5f * childSize.y * defaultItemScale
            ));
        }
    }

    @Override
    protected void performLayout() {
        // Position management happens entirely in onUpdate; nothing to do on a layout pass.
    }

    /**
     * Overridden (rather than relying solely on {@code onUpdate}) so the all-shrunk check below
     * runs against this frame's child scales, not last frame's: {@code UIContainer.update} calls
     * {@code onUpdate} on this container *before* it loops over children to advance their own
     * {@code currentScale} via {@code animate()}, so a check made from inside {@code onUpdate}
     * would always be reading the scale as it stood at the end of the previous frame.
     */
    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);

        if (shrinking) {
            boolean allShrunk = true;
            for (IUIElement child : children) {
                if (child != selectedChild && child instanceof UIElement<?> uiChild
                        && uiChild.getCurrentScale() > 0.001f) {
                    allShrunk = false;
                    break;
                }
            }
            if (allShrunk) {
                for (IUIElement child : children) {
                    child.setVisible(false);
                }
                shrinking = false;
            }
        }
    }

    @Override
    protected void onUpdate(float deltaTime) {
        if (selectedChild != null) {
            return;
        }

        if (outroActive) {
            for (IUIElement child : children) {
                if (!(child instanceof UIElement uiChild)) continue;
                if (!child.isVisible()) continue;
                Vector2f launchPos = outroTargetPositions.get(child);
                if (launchPos != null) {
                    uiChild.setTargetPosition(launchPos, true);
                    uiChild.setTargetScale(defaultItemScale, true);
                }
            }
            return;
        }

        if (introActive) {
            introElapsed += deltaTime;
            for (IUIElement child : children) {
                Float startTime = introStartTimes.get(child);
                if (startTime != null && introElapsed >= startTime && !child.isVisible()) {
                    child.setVisible(true);
                }
            }
            float lastStart = children.isEmpty() ? 0f : (children.size() - 1) * INTRO_STAGGER_DELAY;
            if (introElapsed > lastStart + 0.5f) {
                introActive = false;
                introStartTimes.clear();
            }
        }

        Vector2f hoveredCenter = hoveredChild != null ? restPositions.get(hoveredChild) : null;

        for (IUIElement child : children) {
            if (!(child instanceof UIElement uiChild)) continue;
            if (!child.isVisible()) continue;
            Vector2f rest = restPositions.get(child);
            if (rest == null) continue;

            Vector2f center = new Vector2f(rest);
            float targetScale = (child == hoveredChild) ? hoverItemScale : defaultItemScale;

            if (hoveredCenter != null && child != hoveredChild) {
                float distance = hoveredCenter.distance(rest);
                float displacement = MAX_JIGGLE_DISPLACEMENT * (float) Math.exp(-distance / HOVER_FALLOFF_DISTANCE);
                Vector2f direction = new Vector2f(rest).sub(hoveredCenter);
                if (direction.lengthSquared() < 1e-6f) {
                    direction.set(1f, 0f);
                } else {
                    direction.normalize();
                }
                center.add(direction.mul(displacement));
            }

            Vector2f childSize = child.getSize();
            Vector2f targetPos = new Vector2f(
                    center.x - 0.5f * childSize.x * targetScale,
                    center.y - 0.5f * childSize.y * targetScale);
            uiChild.setTargetPosition(targetPos, true);
            uiChild.setTargetScale(targetScale, true);
        }
    }

    @Override
    protected void renderSelf(IRenderContext context) {
        // The disc itself draws nothing; children render themselves.
    }

    /** Second pip-only render pass over reward icons so pips always sit above overlapping neighbors. */
    @Override
    protected void renderChildren(IRenderContext context, Rectangle2D viewport) {
        super.renderChildren(context, viewport);

        for (IUIElement child : new ArrayList<>(children)) {
            if (child.isVisible() && child instanceof SilhouetteItemButton icon && icon.hasUnclaimedReward()) {
                icon.setPipOverlayOnly(true);
                icon.render(context, viewport);
                icon.setPipOverlayOnly(false);
            }
        }
    }

    @Override
    protected boolean onEvent(UIEvent event) {
        return false;
    }

    @Override
    public void addChild(IUIElement child) {
        super.addChild(child);
        wireChildEvents(child);
    }

    /**
     * Wires hover/click via the public {@code onClick}/{@code onMouseEnter}/{@code onMouseExit}
     * action-list API rather than {@code addEventListener}. {@code ItemButton.onEvent} (the
     * runtime type of every child here) unconditionally returns {@code true} for CLICK — and
     * effectively for HOVER_ENTER/EXIT too, since {@code ItemButton.init()} registers its own
     * hover-scale actions — which consumes the event in {@code UIElement.handleEvent} before it
     * ever reaches {@code eventListeners}. The action-list API is dispatched from inside
     * {@code UIElement.onEvent} itself (called via {@code super.onEvent(event)} from
     * {@code ItemButton.onEvent}), so it always fires regardless of what the subclass does
     * afterward — unlike {@code RotatingItemRing}, which can use {@code addEventListener} safely
     * because its children are plain {@code ItemRenderer}s, not {@code ItemButton}s.
     */
    private void wireChildEvents(IUIElement child) {
        if (!(child instanceof UIElement uiElem)) return;
        uiElem.onMouseEnter(event -> {
            if (selectedChild != null) return;
            hoveredChild = child;
        });
        uiElem.onMouseExit(event -> {
            if (selectedChild != null) return;
            if (hoveredChild == child) hoveredChild = null;
        });
        uiElem.onClick(event -> {
            if (selectedChild != null) return;
            triggerSelect(child);
        });
    }

    /**
     * Public entry point for selecting a disc icon programmatically (e.g. auto-opening the
     * encyclopedia straight to a fish navigated to from elsewhere), without a real click event.
     * Goes through the same path as an actual click so the rest of the disc still shrinks/hides —
     * calling {@code onFishSelected} directly instead skips that and leaves the whole disc visible
     * behind the info page.
     */
    public void selectChild(IUIElement child) {
        triggerSelect(child);
    }

    private void triggerSelect(IUIElement clicked) {
        if (selectedChild != null) return;
        if (introActive) {
            introActive = false;
            introStartTimes.clear();
        }
        selectedChild = clicked;
        hoveredChild = null;
        shrinking = true;

        // A real click can only ever land on an already-visible icon, but a programmatic
        // selectChild() (see #selectChild) can fire before the clicked icon's own staggered intro
        // reveal has happened — it would otherwise travel to the info page still invisible.
        if (!clicked.isVisible()) {
            clicked.setVisible(true);
        }

        for (IUIElement child : children) {
            if (child != clicked && child instanceof UIElement uiChild) {
                if (!child.isVisible()) {
                    // Still-hidden intro fish: snap to 0 so the all-shrunk check passes immediately.
                    child.setVisible(true);
                    uiChild.setTargetScale(0f, false);
                } else {
                    uiChild.setTargetScale(0f, true);
                }
            }
        }

        ResourceKey<FishProfile> key = childFishKeys.get(clicked);
        onFishSelected.accept(clicked, key);
    }

    /**
     * Reverses {@link #triggerSelect}: re-adds {@code child} (the icon that just finished
     * traveling back from the info page) if it isn't already a child, then makes every disc icon
     * visible again and eases them all (including the returning one) back to
     * {@link #defaultItemScale}. Every other icon is still sitting at its own rest position
     * (only hidden, never moved, while selected) so they grow back in place rather than flying
     * in from anywhere.
     */
    public void reinstateAfterClose(IUIElement child) {
        if (!children.contains(child) && child instanceof UIElement<?> uiChild) {
            // The closing icon's position was left in root-relative coordinates by the
            // travel-back animation; reparentTo converts it to this container's local space so
            // it doesn't visually jump by this container's screen offset.
            uiChild.reparentTo(this, true);
        }
        selectedChild = null;
        shrinking = false;

        for (IUIElement c : children) {
            c.setVisible(true);
            if (c instanceof UIElement uiChild) {
                uiChild.setTargetScale(defaultItemScale, true);
            }
        }
    }

    @Override
    protected FishSphereContainer self() {
        return this;
    }

    @Override
    public boolean needsUpdate() {
        return super.needsUpdate() || hoveredChild != null || shrinking || introActive || outroActive;
    }
}
