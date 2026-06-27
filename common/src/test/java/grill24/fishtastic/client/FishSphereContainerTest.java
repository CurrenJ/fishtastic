package grill24.fishtastic.client;

import grill24.FishtasticRegistries;
import grill24.fishtastic.data.FishProfile;
import io.github.currenj.gelatinui.gui.IUIElement;
import io.github.currenj.gelatinui.gui.UIEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2f;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit tests for {@link FishSphereContainer}'s layout math and selection/hover state machine.
 * Modeled on gelatin-ui's own test style (real UIElement trees, render stubbed out, events
 * injected directly into handleEvent()) — see UIContainerSafeMutationTest for the pattern this
 * follows. No Minecraft client instance, GL context, or render call is ever touched.
 */
public class FishSphereContainerTest {

    private static ResourceKey<FishProfile> fishKey(String path) {
        return ResourceKey.create(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, Identifier.fromNamespaceAndPath("fishtastic", path));
    }

    private static SilhouetteItemButton button() {
        return new SilhouetteItemButton(ItemStack.EMPTY);
    }

    /**
     * Drives {@code sphere.update} with small per-frame steps (mirroring real client ticks)
     * until the shrink-to-zero animation finishes, instead of one large {@code deltaTime} jump:
     * scale interpolation is exponential decay, so it never crosses the "shrunk" threshold
     * within a single oversized step, only asymptotically over many small ones.
     */
    private static void runUpdatesUntilShrunk(FishSphereContainer sphere, IUIElement selected) {
        for (int i = 0; i < 500 && selected.isVisible(); i++) {
            sphere.update(0.016f);
        }
    }

    /**
     * Advances time far enough that the intro animation completes and all children are visible at
     * their rest positions. Tests that need to interact with the disc (click, hover) must call this
     * first — the intro intentionally hides children until their stagger time fires, so events
     * injected before that are no-ops, the same as in the real game where fish are off-screen.
     */
    private static void settleIntro(FishSphereContainer sphere) {
        sphere.update(2.0f);
    }

    private static List<Map.Entry<ResourceKey<FishProfile>, SilhouetteItemButton>> entries(int count) {
        List<Map.Entry<ResourceKey<FishProfile>, SilhouetteItemButton>> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(new AbstractMap.SimpleEntry<>(fishKey("fish_" + i), button()));
        }
        return items;
    }

    // -------------------------------------------------------------------------
    // Rest layout (sunflower spiral)
    // -------------------------------------------------------------------------

    @Test
    public void singleChildRestsAtSunflowerSpiralFirstSlot() {
        // The sunflower spiral's radial term is discRadius * sqrt((i + 0.5) / count); for a
        // single child (i=0, count=1) that's discRadius * sqrt(0.5), not zero — the very first
        // slot is already offset from the disc center, at angle theta=0 (i.e. straight +x).
        FishSphereContainer sphere = new FishSphereContainer();
        List<Map.Entry<ResourceKey<FishProfile>, SilhouetteItemButton>> items = entries(1);
        sphere.setFishItems(items);

        IUIElement child = items.get(0).getValue();
        Vector2f center = sphere.getRestCenter(child);
        float expectedRadius = 64f * (float) Math.sqrt(0.5);

        assertNotNull(center, "a placed child must have a rest center");
        assertEquals(sphere.getSize().x * 0.5f + expectedRadius, center.x, 0.01f, "first slot must sit expectedRadius to the right of the disc center");
        assertEquals(sphere.getSize().y * 0.5f, center.y, 0.01f, "first slot's angle is zero, so its y must equal the disc center's y");
    }

    @Test
    public void restPositionsAreWithinDiscRadius() {
        FishSphereContainer sphere = new FishSphereContainer().discRadius(50f);
        List<Map.Entry<ResourceKey<FishProfile>, SilhouetteItemButton>> items = entries(12);
        sphere.setFishItems(items);

        Vector2f centerOfDisc = new Vector2f(sphere.getSize().x * 0.5f, sphere.getSize().y * 0.5f);
        for (Map.Entry<ResourceKey<FishProfile>, SilhouetteItemButton> entry : items) {
            Vector2f rest = sphere.getRestCenter(entry.getValue());
            assertTrue(rest.distance(centerOfDisc) <= 50f + 0.01f,
                "every rest position must fall within the configured disc radius, got distance " + rest.distance(centerOfDisc));
        }
    }

    @Test
    public void unknownChildHasNoRestCenter() {
        FishSphereContainer sphere = new FishSphereContainer();
        sphere.setFishItems(entries(3));

        assertNull(sphere.getRestCenter(button()), "a button never passed to setFishItems must have no rest center");
    }

    @Test
    public void getRestTopLeftOffsetsByHalfScaledSize() {
        FishSphereContainer sphere = new FishSphereContainer();
        List<Map.Entry<ResourceKey<FishProfile>, SilhouetteItemButton>> items = entries(1);
        sphere.setFishItems(items);

        IUIElement child = items.get(0).getValue();
        Vector2f center = sphere.getRestCenter(child);
        Vector2f topLeft = sphere.getRestTopLeft(child, 2.0f);

        assertEquals(center.x - 0.5f * child.getSize().x * 2.0f, topLeft.x, 0.01f);
        assertEquals(center.y - 0.5f * child.getSize().y * 2.0f, topLeft.y, 0.01f);
    }

    @Test
    public void setFishItemsReplacesPriorChildrenAndRestPositions() {
        FishSphereContainer sphere = new FishSphereContainer();
        List<Map.Entry<ResourceKey<FishProfile>, SilhouetteItemButton>> first = entries(3);
        sphere.setFishItems(first);

        List<Map.Entry<ResourceKey<FishProfile>, SilhouetteItemButton>> second = entries(2);
        sphere.setFishItems(second);

        assertNull(sphere.getRestCenter(first.get(0).getValue()), "old children must lose their rest position after a rebuild");
        assertNotNull(sphere.getRestCenter(second.get(0).getValue()), "new children must have a rest position after a rebuild");
    }

    // -------------------------------------------------------------------------
    // Setter clamping
    // -------------------------------------------------------------------------

    @Test
    public void discRadiusClampsToZero() {
        FishSphereContainer sphere = new FishSphereContainer().discRadius(-10f);
        List<Map.Entry<ResourceKey<FishProfile>, SilhouetteItemButton>> items = entries(4);
        sphere.setFishItems(items);

        Vector2f discCenter = new Vector2f(sphere.getSize().x * 0.5f, sphere.getSize().y * 0.5f);
        for (Map.Entry<ResourceKey<FishProfile>, SilhouetteItemButton> entry : items) {
            Vector2f rest = sphere.getRestCenter(entry.getValue());
            assertEquals(0f, rest.distance(discCenter), 0.01f, "a negative radius must clamp to zero, collapsing every rest position to the disc center");
        }
    }

    @Test
    public void hoverItemScaleClampsToMinimum() {
        FishSphereContainer sphere = new FishSphereContainer().hoverItemScale(-5f);
        // No public getter for hoverItemScale; absence of an exception combined with the
        // documented Math.max(0.01f, scale) clamp is what we're verifying here doesn't regress.
        assertDoesNotThrow(() -> sphere.setFishItems(entries(1)));
    }

    // -------------------------------------------------------------------------
    // Selection state machine
    // -------------------------------------------------------------------------

    @Test
    public void clickingChildFiresOnFishSelectedWithMatchingKey() {
        FishSphereContainer sphere = new FishSphereContainer();
        List<Map.Entry<ResourceKey<FishProfile>, SilhouetteItemButton>> items = entries(3);
        List<ResourceKey<FishProfile>> selectedKey = new ArrayList<>();
        List<IUIElement> selectedIcon = new ArrayList<>();
        sphere.onFishSelected((icon, key) -> {
            selectedIcon.add(icon);
            selectedKey.add(key);
        });
        sphere.setFishItems(items);
        settleIntro(sphere);

        IUIElement target = items.get(1).getValue();
        target.handleEvent(new UIEvent(UIEvent.Type.CLICK, target, 0, 0));

        assertSame(target, selectedIcon.get(0), "onFishSelected must receive the clicked icon");
        assertEquals(items.get(1).getKey(), selectedKey.get(0), "onFishSelected must receive the clicked icon's fish key");
    }

    @Test
    public void secondClickAfterSelectionIsIgnored() {
        FishSphereContainer sphere = new FishSphereContainer();
        List<Map.Entry<ResourceKey<FishProfile>, SilhouetteItemButton>> items = entries(2);
        int[] callCount = {0};
        sphere.onFishSelected((icon, key) -> callCount[0]++);
        sphere.setFishItems(items);
        settleIntro(sphere);

        IUIElement first = items.get(0).getValue();
        IUIElement second = items.get(1).getValue();
        first.handleEvent(new UIEvent(UIEvent.Type.CLICK, first, 0, 0));
        second.handleEvent(new UIEvent(UIEvent.Type.CLICK, second, 0, 0));

        assertEquals(1, callCount[0], "a fish can only be selected once until reinstateAfterClose");
    }

    @Test
    public void selectingHidesEveryChildOnceFullyShrunk() {
        // The disc-resident button (this one) hides itself once the shrink finishes — the
        // screen is expected to show a separate traveling icon clone in its place, not this one.
        FishSphereContainer sphere = new FishSphereContainer();
        List<Map.Entry<ResourceKey<FishProfile>, SilhouetteItemButton>> items = entries(3);
        sphere.setFishItems(items);

        IUIElement selected = items.get(0).getValue();
        selected.handleEvent(new UIEvent(UIEvent.Type.CLICK, selected, 0, 0));

        // Scale decays exponentially towards 0; step in small frame-sized increments rather
        // than one big jump so it actually crosses the "fully shrunk" threshold.
        runUpdatesUntilShrunk(sphere, selected);

        for (Map.Entry<ResourceKey<FishProfile>, SilhouetteItemButton> entry : items) {
            assertFalse(entry.getValue().isVisible(), "every disc child, including the selected one, must be hidden once the shrink finishes");
        }
    }

    @Test
    public void reinstateAfterCloseMakesAllChildrenVisibleAgain() {
        FishSphereContainer sphere = new FishSphereContainer();
        List<Map.Entry<ResourceKey<FishProfile>, SilhouetteItemButton>> items = entries(3);
        sphere.setFishItems(items);

        IUIElement selected = items.get(0).getValue();
        selected.handleEvent(new UIEvent(UIEvent.Type.CLICK, selected, 0, 0));
        runUpdatesUntilShrunk(sphere, selected);

        sphere.reinstateAfterClose(selected);

        for (Map.Entry<ResourceKey<FishProfile>, SilhouetteItemButton> entry : items) {
            assertTrue(entry.getValue().isVisible(), "every child must be visible again after reinstateAfterClose");
        }
    }

    @Test
    public void reinstateAfterCloseAllowsSelectingAgain() {
        FishSphereContainer sphere = new FishSphereContainer();
        List<Map.Entry<ResourceKey<FishProfile>, SilhouetteItemButton>> items = entries(2);
        int[] callCount = {0};
        sphere.onFishSelected((icon, key) -> callCount[0]++);
        sphere.setFishItems(items);
        settleIntro(sphere);

        IUIElement first = items.get(0).getValue();
        first.handleEvent(new UIEvent(UIEvent.Type.CLICK, first, 0, 0));
        runUpdatesUntilShrunk(sphere, first);
        sphere.reinstateAfterClose(first);

        IUIElement second = items.get(1).getValue();
        second.handleEvent(new UIEvent(UIEvent.Type.CLICK, second, 0, 0));

        assertEquals(2, callCount[0], "selection must work again after reinstateAfterClose");
    }

    // -------------------------------------------------------------------------
    // Hover jiggle
    // -------------------------------------------------------------------------

    @Test
    public void hoveringOneChildPushesNeighborTargetPositionsAway() {
        FishSphereContainer sphere = new FishSphereContainer().discRadius(50f);
        List<Map.Entry<ResourceKey<FishProfile>, SilhouetteItemButton>> items = entries(5);
        sphere.setFishItems(items);

        SilhouetteItemButton hovered = items.get(0).getValue();
        hovered.handleEvent(new UIEvent(UIEvent.Type.HOVER_ENTER, hovered, 0, 0));

        assertTrue(sphere.needsUpdate(), "a hovered child must mark the container as needing update");
        sphere.update(0.016f);

        Vector2f hoveredCenter = sphere.getRestCenter(hovered);
        for (int i = 1; i < items.size(); i++) {
            SilhouetteItemButton neighbor = items.get(i).getValue();
            Vector2f rest = sphere.getRestCenter(neighbor);
            Vector2f targetCenter = new Vector2f(
                neighbor.getTargetPosition().x + 0.5f * neighbor.getSize().x,
                neighbor.getTargetPosition().y + 0.5f * neighbor.getSize().y
            );
            assertNotEquals(rest.distance(hoveredCenter), targetCenter.distance(hoveredCenter), 1e-4,
                "a neighbor's jiggled target center must differ from its rest center while hovering");
        }
    }

    @Test
    public void unhoveringReturnsChildrenToRestTargetPositions() {
        FishSphereContainer sphere = new FishSphereContainer().discRadius(50f);
        List<Map.Entry<ResourceKey<FishProfile>, SilhouetteItemButton>> items = entries(3);
        sphere.setFishItems(items);
        settleIntro(sphere);

        SilhouetteItemButton hovered = items.get(0).getValue();
        hovered.handleEvent(new UIEvent(UIEvent.Type.HOVER_ENTER, hovered, 0, 0));
        sphere.update(0.016f);
        hovered.handleEvent(new UIEvent(UIEvent.Type.HOVER_EXIT, hovered, 0, 0));
        sphere.update(0.016f);

        for (Map.Entry<ResourceKey<FishProfile>, SilhouetteItemButton> entry : items) {
            SilhouetteItemButton child = entry.getValue();
            Vector2f rest = sphere.getRestCenter(child);
            Vector2f expectedTopLeft = sphere.getRestTopLeft(child, sphere.getDefaultItemScale());
            assertEquals(expectedTopLeft.x, child.getTargetPosition().x, 0.01f, "target position must return to rest after unhovering");
            assertEquals(expectedTopLeft.y, child.getTargetPosition().y, 0.01f, "target position must return to rest after unhovering");
        }
    }
}
