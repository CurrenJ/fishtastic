package grill24.fishtastic.client.encyclopedia.page;

import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageButtonTest {
    @Test
    void hitPaddingExpandsBoundsWithoutChangingTheVisualSize() {
        PageButton button = new PageButton(10, 14, ">", () -> {});
        button.setPosition(new org.joml.Vector2f(20, 30));
        button.hitPadding(1, 2, 3, 4);

        Rectangle2D bounds = button.getBounds();
        assertEquals(19f, bounds.getX(), 0.001f);
        assertEquals(28f, bounds.getY(), 0.001f);
        assertEquals(14f, bounds.getWidth(), 0.001f);
        assertEquals(20f, bounds.getHeight(), 0.001f);
    }
}
