---
name: build-screen
description: Use when the user wants to build, create, or scaffold a new Gelatin UI screen. Triggers on descriptions like "create a screen", "build a UI screen", "make a screen that shows X", "scaffold a new screen", or when given a layout description and data model to render.
version: 1.0.0
---

# Build a Gelatin UI Screen

Create a new screen class by extending `GelatinUIScreen<GelatinMenu>` and implementing `buildUI()`. All UI is constructed in `buildUI()` and set as the root of `uiScreen`.

## Screen Skeleton

```java
public class MyScreen extends GelatinUIScreen<GelatinMenu> {

    public MyScreen(GelatinMenu menu, Inventory inv) {
        super(menu, inv, Component.literal("My Screen"));
    }

    @Override
    protected void buildUI() {
        MinecraftRenderContext tempContext = new MinecraftRenderContext(
            new GuiGraphics(this.minecraft, this.minecraft.renderBuffers().bufferSource()),
            this.font
        );

        // Build layout here...
        VBox root = UI.vbox()
            .spacing(10)
            .padding(20)
            .alignment(VBox.Alignment.CENTER)
            .fillWidth(true);

        // Add children...

        uiScreen.setRoot(root);
    }

    @Override
    protected void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Optional: draw raw GuiGraphics content (background fills, etc.)
    }

    @Override
    protected void updateComponentSizes(MinecraftRenderContext context) {
        // Optional: call label.updateSize(context) for dynamically-changing labels
    }
}
```

Key rules:
- Always call `uiScreen.setRoot(...)` at the end of `buildUI()`.
- Create a `MinecraftRenderContext tempContext` whenever you need to create `Label` via `UI.label(context, ...)` — labels need a context to measure text width.
- Alternatively, construct labels directly with `new Label(text, color)` if you don't need immediate sizing.

---

## Layout Containers

### VBox — Vertical stack

```java
VBox vbox = UI.vbox()
    .spacing(10)            // px between children
    .padding(20)            // uniform padding (all sides)
    .padding(top, bottom, left, right)  // per-side padding
    .alignment(VBox.Alignment.LEFT)     // LEFT | CENTER | RIGHT
    .fillWidth(true)        // stretch to parent/screen width
    .fillHeight(true)       // stretch to parent/screen height
    .scaleToWidth(200f)     // uniformly scale children to fit width
    .scaleToHeight(100f);   // uniformly scale children to fit height
```

### HBox — Horizontal stack

```java
HBox hbox = UI.hbox()
    .spacing(8)
    .padding(5)
    .alignment(HBox.Alignment.TOP)      // TOP | CENTER | BOTTOM
    .fillWidth(true)
    .fillHeight(true)
    .scaleToWidth(300f)
    .scaleToHeight(40f);
```

### Panel — Free-position container with optional background

```java
Panel panel = UI.panel()
    .backgroundColor(0xFF1A1A2E)   // solid color (ARGB)
    .backgroundSprite(spriteData)  // sprite background
    .autoSizeToChildren(true);     // resize to fit children bounds
```

### ManualContainer — Fixed-position children

```java
ManualContainer container = UI.manualContainer();
Label lbl = new Label("Hello", 0xFFFFFFFF);
lbl.setPosition(new Vector2f(10, 20));
container.addChild(lbl);
```

Use `ManualContainer` when you need pixel-precise placement rather than flow layout.

---

## Adding Children

All containers use `addChild()`:

```java
VBox root = UI.vbox().spacing(10);
root.addChild(new Label("Title", 0xFFFFFFFF));
root.addChild(UI.spriteButton(100, 20, 0xFF446688).text("Click Me", 0xFFFFFFFF));
```

---

## Components

### Label

Renders text. Auto-sizes to the text dimensions.

```java
// Preferred: init with context so size is immediately available for layout
Label label = UI.label(tempContext, "Hello World", 0xFFFFFFFF);

// Or direct construction (size computed at first render)
Label label = new Label("Hello", 0xFFFFFFFF);
Label label = new Label("Hello", 0xFFFFFFFF, true); // centered

// Fluent API
label.text("New text")
     .color(0xFFFF5500)
     .centered(true)
     .scale(1.5f);
```

Colors are ARGB integers. Use `UI.rgb(r, g, b)` or `0xFFRRGGBB` literals.

### Rectangle

Simple filled rectangle — no interactivity.

```java
Rectangle rect = UI.rectangle(100, 20, 0xFF222222);
rect.color(0xFF334455); // update color
```

### SpriteRectangle

Textured or colored rectangle. Supports hover/pressed textures and optional centered text.

```java
// Color-backed
SpriteRectangle.SpriteRectangleImpl box = UI.spriteRectangle(120, 30, 0xFF446688)
    .text("Label text", 0xFFFFFFFF)
    .autoSize(true)          // resize to fit text
    .padding(6, 4)           // horizontal, vertical padding when autoSize
    .outline(true)           // draws bevelled border
    .outlineColors(darkColor, lightColor)
    .color(0xFF557799);      // change fill color

// Texture-backed
SpriteRectangle.SpriteRectangleImpl box = UI.spriteRectangle(64, 64, myTexture);

// Hover/pressed textures
box.texture(SpriteData.texture(normalTex))
   .hoverTexture(SpriteData.texture(hoverTex))
   .pressedTexture(SpriteData.texture(pressedTex));
```

### SpriteButton

Interactive button extending SpriteRectangle. Automatically plays a click-bounce animation and fires `onClick`.

```java
SpriteButton btn = UI.spriteButton(120, 20, 0xFF3366AA)
    .text("Submit", 0xFFFFFFFF)
    .onClick(event -> handleSubmit());

// Texture-based button
SpriteButton btn = new SpriteButton(32, 32, myTexture);
btn.onClick(e -> doSomething());
```

**Event callbacks available on all elements:**
```java
element.onClick(e -> ...)
       .onMouseEnter(e -> ...)
       .onMouseExit(e -> ...)
       .addEventListener(event -> {
           if (event.getType() == UIEvent.Type.HOVER_ENTER) { ... }
           if (event.getType() == UIEvent.Type.HOVER_EXIT) { ... }
           if (event.getType() == UIEvent.Type.CLICK) { ... }
       });
```

### TextInput

A text field with placeholder, focus handling, and cursor.

```java
TextInput input = new TextInput(200, 20)
    .placeholder("Enter text...")
    .maxLength(32)
    .textColor(0xFFFFFFFF)
    .placeholderColor(0xFF888888)
    .backgroundColor(0xFF111111)
    .borderColor(0xFF404040)
    .focusedBorderColor(0xFF00AAFF)
    .alignment(TextInput.TextAlignment.LEFT)  // LEFT | CENTER | RIGHT
    .onTextChange(text -> System.out.println("Changed: " + text))
    .registerGlobalClickListener(this);  // 'this' = GelatinUIScreen; required for focus

// In the screen class, forward keyboard events to focused inputs:
@Override
public boolean charTyped(char character, int modifiers) {
    if (input.isFocused()) { input.charTyped(character); return true; }
    return super.charTyped(character, modifiers);
}

@Override
public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    if (input.isFocused()) { input.keyPressed(keyCode); return true; }
    return super.keyPressed(keyCode, scanCode, modifiers);
}

// Read current value:
String value = input.getText();
```

### Checkbox

Toggleable checkbox with optional inline label.

```java
Checkbox cb = new Checkbox()           // default 16x16
    .label("Enable feature")
    .labelColor(0xFFFFFFFF)
    .labelSpacing(6)
    .checkColor(0xFF00FF00)
    .boxColor(0xFF333333)
    .borderColor(0xFF666666)
    .hoverBorderColor(0xFFFFFFFF)
    .checked(true)
    .onCheckChange(isChecked -> System.out.println("Checked: " + isChecked));

// Custom size
Checkbox cb = new Checkbox(20, 20);

// Read state
boolean state = cb.isChecked();

// Custom textures (optional)
cb.uncheckedTexture(ResourceLocation.fromNamespaceAndPath("mymod", "textures/gui/checkbox_off.png"))
  .checkedTexture(ResourceLocation.fromNamespaceAndPath("mymod", "textures/gui/checkbox_on.png"));
```

### SpriteProgressBar

Animated sprite-based progress bar. Has built-in embellishments for skill levels.

```java
SpriteProgressBar bar = UI.progressBar()         // default 63x19
    .progress(0.75f)   // 0.0–1.0
    .skillLevel(30);   // 0=plain, 15=gold outline, 30/45/60=embellishments

SpriteProgressBar bar = UI.progressBar(100, 16)  // custom size
    .progress(0.5f);

// Programmatic update (animates smoothly to new value):
bar.progress(newValue);
```

### ItemRenderer

Renders a Minecraft `ItemStack` (no click interaction).

```java
ItemRenderer.ItemRendererImpl item = UI.itemRenderer(new ItemStack(Items.DIAMOND));
item.itemScale(1.5f)      // visual scale of the item icon
    .showCount(true);     // show stack count badge

// Custom size container
ItemRenderer.ItemRendererImpl item = UI.itemRenderer(32f, 32f, new ItemStack(Items.DIAMOND, 64));
```

### ItemButton

Clickable item renderer with hover scale animation (auto-scales to 1.5x on hover).

```java
ItemButton btn = UI.itemButton(new ItemStack(Items.GOLD_INGOT));
btn.onClick(e -> selectItem());

// Custom size
ItemButton btn = new ItemButton(24f, 24f, new ItemStack(Items.EMERALD));
```

### RotatingItemRing

A circular ring of items that rotates, responds to hover (pauses rotation, scales item), and click (centers selected item).

```java
RotatingItemRing ring = UI.rotatingItemRing()
    .radius(60f)                  // ring radius in pixels
    .defaultAngularSpeed(0.8f)    // rad/s while idle
    .defaultItemScale(1.0f)
    .hoverItemScale(1.3f)
    .selectedItemScale(1.5f);

List<ItemStack> items = List.of(
    new ItemStack(Items.DIAMOND),
    new ItemStack(Items.EMERALD),
    new ItemStack(Items.GOLD_INGOT)
);
ring.setItems(items);
ring.setSize(new Vector2f(160f, 160f)); // container size (ring draws inside)
```

### ItemTabs

Tab bar where each tab is represented by an item icon. Switching tabs swaps content with an animated transition.

```java
ItemTabs tabs = UI.itemTabs()
    .tabSpacing(4)
    .onSelectionChanged(index -> System.out.println("Tab: " + index));

// Build content for each tab
VBox tab1 = UI.vbox().spacing(6);
tab1.addChild(new Label("Tab 1 content", 0xFFFFFFFF));

VBox tab2 = UI.vbox().spacing(6);
tab2.addChild(new Label("Tab 2 content", 0xFFFFFFFF));

// addTab returns the button element (for chaining .tooltip())
UIElement<?> tab1Btn = tabs.addTab(new ItemStack(Items.DIAMOND), tab1);
UIElement<?> tab2Btn = tabs.addTab(new ItemStack(Items.EMERALD), tab2);

// Optional per-tab tooltip
SpriteRectangle.SpriteRectangleImpl tooltip = UI.spriteRectangle(120, 20, 0xFF003388)
    .text("Tooltip text", 0xFFFFFF00)
    .autoSize(true).padding(5, 3).outline(true);
tab1Btn.tooltip(uiScreen, tooltip);

// Force initial layout (call after adding all tabs)
tabs.update(0);
```

### VerticalScrollBar

Automatically managed by `UIScreen`. Enable/disable scrolling on the screen level:

```java
uiScreen.setScrollEnabled(true);   // default: true
uiScreen.setScrollSensitivity(15f); // pixels per scroll notch
```

No manual instantiation needed — the scrollbar appears automatically when content exceeds the viewport height.

---

## Tooltips

Any element can show a tooltip on hover using the `UIScreen` tooltip system:

```java
// Global tooltip (follows mouse)
Panel tooltip = UI.panel().backgroundColor(0xCC000033);
tooltip.addChild(new Label("Tooltip text", 0xFFFFFFFF));
uiScreen.setTooltip(tooltip);
uiScreen.clearTooltip();

// Per-element tooltip (attach to any UIElement)
SpriteRectangle.SpriteRectangleImpl tooltipPanel = UI.spriteRectangle(100, 20, 0xFF002244)
    .text("Info", 0xFFFFFFFF).autoSize(true).padding(4, 3).outline(true);
myButton.tooltip(uiScreen, tooltipPanel);
```

---

## SpriteData — Texture Configuration

Used for sprite-based backgrounds and button textures:

```java
// Full texture stretched
SpriteData sprite = SpriteData.texture(ResourceLocation.fromNamespaceAndPath("mymod", "textures/gui/panel.png"));

// UV region from atlas
SpriteData sprite = SpriteData.texture(myTex)
    .uv(10, 5)               // source U, V offset
    .uv(10, 5, 63, 19)       // U, V, regionW, regionH
    .textureSize(128)        // atlas size (square)
    .textureSize(256, 128)   // atlas width, height
    .actualSize(63, 19);     // rendered pixel size

// Nine-slice (for resizable panels)
SpriteData sliced = new SlicedSpriteData(texture, borderSize);
panel.backgroundSprite(sliced);
```

Render modes are auto-selected based on SpriteData type (STRETCH, REPEAT, SLICE).

---

## Colors

```java
// ARGB hex literal
int color = 0xFFRRGGBB;

// Utility methods
int color = UI.rgb(255, 128, 0);           // fully opaque RGB
int color = UI.argb(200, 255, 128, 0);    // custom alpha
int color = UI.hex("FF80FF00");            // from hex string

// Common patterns
int white       = 0xFFFFFFFF;
int black       = 0xFF000000;
int transparent = 0x00000000;
int semiBlack   = 0xCC000000;  // 80% opaque black overlay
```

---

## Scaling and Positioning

All elements support fluent scale/position for layout and animation:

```java
element.scale(1.5f);                          // immediate scale
element.setTargetScale(1.5f, true);           // animate to scale
element.setPosition(new Vector2f(x, y));      // immediate position
element.setTargetPosition(new Vector2f(x, y), true); // animate to position
element.setVisible(false);                    // hide/show
element.setDebugName("MyElement");            // name shown in debug overlay
```

Auto-center root in viewport:
```java
uiScreen.setAutoCenterRoot(true);   // centers root element automatically
```

---

## Effects System

Effects apply animated transforms (position delta, scale, rotation, alpha) to elements:

```java
// Available effect classes:
BreatheEffect breathe = new BreatheEffect();
ShakeEffect shake = new ShakeEffect();
SpinEffect spin = new SpinEffect();
PulseGlowEffect pulse = new PulseGlowEffect();
DriftEffect drift = new DriftEffect();
WanderEffect wander = new WanderEffect();
JumpBounceEffect jumpBounce = new JumpBounceEffect();
FallBounceEffect fallBounce = new FallBounceEffect();
CoinSpinEffect coinSpin = new CoinSpinEffect();
FlipEffect flip = new FlipEffect();
ClickBounceEffect clickBounce = new ClickBounceEffect(); // built into buttons

// Apply effect via EffectAnimationBinder or UIElement.addEffect()
element.addEffect(new BreatheEffect());
element.addEffect(new ShakeEffect());
```

---

## Common Layout Patterns

### Centered header + content + button row

```java
VBox root = UI.vbox()
    .spacing(12)
    .padding(24)
    .alignment(VBox.Alignment.CENTER)
    .fillWidth(true);

Label title = UI.label(tempContext, "My Screen", UI.rgb(255, 255, 255));
title.scale(1.5f);
root.addChild(title);

// Content area
VBox content = UI.vbox().spacing(8).alignment(VBox.Alignment.CENTER);
content.addChild(new Label("Some info", 0xFFCCCCCC));
root.addChild(content);

// Button row
HBox buttons = UI.hbox().spacing(8).alignment(HBox.Alignment.CENTER);
buttons.addChild(UI.spriteButton(80, 20, 0xFF224488).text("OK", 0xFFFFFFFF).onClick(e -> onClose()));
buttons.addChild(UI.spriteButton(80, 20, 0xFF882222).text("Cancel", 0xFFFFFFFF).onClick(e -> onClose()));
root.addChild(buttons);

uiScreen.setRoot(root);
```

### Form with inputs and submit

```java
VBox form = UI.vbox().spacing(12).padding(20);

// Field helper pattern
VBox nameField = UI.vbox().spacing(4);
nameField.addChild(new Label("Name:", 0xFFAAAAAA));
TextInput nameInput = new TextInput(220, 20)
    .placeholder("Your name")
    .registerGlobalClickListener(this);
nameField.addChild(nameInput);
form.addChild(nameField);

Checkbox agree = new Checkbox().label("I agree").onCheckChange(c -> {});
form.addChild(agree);

Label status = new Label("", 0xFF00FF88);
form.addChild(status);

SpriteButton submit = UI.spriteButton(100, 22, 0xFF116622)
    .text("Submit", 0xFFFFFFFF)
    .onClick(e -> {
        if (nameInput.getText().isEmpty()) {
            status.text("Name required").color(0xFFFF4444);
        } else if (!agree.isChecked()) {
            status.text("Must agree").color(0xFFFF4444);
        } else {
            status.text("Submitted!").color(0xFF44FF44);
        }
    });
form.addChild(submit);

uiScreen.setRoot(form);
```

### Tabbed content

```java
VBox root = UI.vbox().spacing(10).padding(20).fillWidth(true);

ItemTabs tabs = UI.itemTabs().tabSpacing(4);

VBox infoTab = UI.vbox().spacing(6);
infoTab.addChild(new Label("Info content", 0xFFFFFFFF));

VBox statsTab = UI.vbox().spacing(6);
statsTab.addChild(UI.progressBar().progress(0.8f).skillLevel(30));

tabs.addTab(new ItemStack(Items.BOOK), infoTab);
tabs.addTab(new ItemStack(Items.DIAMOND), statsTab);
tabs.update(0);

root.addChild(tabs);
uiScreen.setRoot(root);
```

### Item grid display

```java
// 3-column grid using VBox of HBoxes
VBox grid = UI.vbox().spacing(4);
List<ItemStack> items = getMyItems();
for (int row = 0; row < (items.size() + 2) / 3; row++) {
    HBox rowBox = UI.hbox().spacing(4);
    for (int col = 0; col < 3; col++) {
        int i = row * 3 + col;
        if (i < items.size()) {
            ItemButton btn = UI.itemButton(items.get(i));
            btn.onClick(e -> selectItem(items.get(i)));
            rowBox.addChild(btn);
        }
    }
    grid.addChild(rowBox);
}
```

---

## Checklist When Building a Screen

1. Extend `GelatinUIScreen<GelatinMenu>` and implement `buildUI()`.
2. Create `MinecraftRenderContext tempContext` if using `UI.label(context, ...)`.
3. Build layout root (`VBox` or `HBox`) with `.fillWidth(true)` if it should span the screen.
4. Add all children recursively via `addChild()`.
5. Wire event handlers (`.onClick(...)`, `.onMouseEnter(...)`, `onCheckChange(...)`, `onTextChange(...)`).
6. For `TextInput`, call `.registerGlobalClickListener(this)` and forward `charTyped` / `keyPressed` from the screen.
7. Call `uiScreen.setRoot(root)` last.
8. If using `ItemTabs`, call `tabs.update(0)` after adding all tabs.
9. Register the screen in your mod's screen registry (platform-specific).

---

## Imports Reference

```java
import io.github.currenj.gelatinui.GelatinUIScreen;
import io.github.currenj.gelatinui.gui.UI;
import io.github.currenj.gelatinui.gui.UIEvent;
import io.github.currenj.gelatinui.gui.components.*;
import io.github.currenj.gelatinui.gui.minecraft.MinecraftRenderContext;
import io.github.currenj.gelatinui.gui.GelatinMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector2f;
```
