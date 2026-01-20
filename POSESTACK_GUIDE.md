# Complete Guide to PoseStack Transformations in Minecraft

## Table of Contents
1. [Introduction](#introduction)
2. [Core Concepts](#core-concepts)
3. [Transformation Methods](#transformation-methods)
4. [Order of Operations](#order-of-operations)
5. [Best Practices](#best-practices)
6. [Common Pitfalls](#common-pitfalls)
7. [Practical Examples](#practical-examples)
8. [Advanced Techniques](#advanced-techniques)

---

## Introduction

`PoseStack` is Minecraft's transformation system for rendering. It manages a stack of transformation matrices that control how items, blocks, entities, and other elements are positioned, rotated, and scaled in 3D space.

### What is a PoseStack?

A `PoseStack` maintains:
- **Pose Matrix (Matrix4f)**: A 4x4 matrix controlling position, rotation, and scale
- **Normal Matrix (Matrix3f)**: A 3x3 matrix for transforming surface normals (for lighting)
- **Stack Structure**: A deque of transformation states that can be pushed/popped

---

## Core Concepts

### The Transformation Stack

Think of PoseStack like a stack of papers where each paper represents a coordinate system:
- **Bottom**: The original world/screen coordinate system
- **Each Layer**: A modified coordinate system based on transformations
- **Top**: The current active coordinate system

```java
poseStack.pushPose();  // Save current state (add new layer)
// ... do transformations and rendering ...
poseStack.popPose();   // Restore previous state (remove layer)
```

### Matrix Mathematics

Transformations are **cumulative** and applied in **reverse order** (right-to-left multiplication):
```java
poseStack.translate(x, y, z);  // Applied FIRST
poseStack.rotate(quaternion);   // Applied SECOND  
poseStack.scale(sx, sy, sz);    // Applied THIRD
```

The final transformation is: `Scale × Rotation × Translation × Original`

---

## Transformation Methods

### 1. `translate(x, y, z)` - Position

Moves the coordinate system origin.

```java
// Move 1 block up
poseStack.translate(0, 1, 0);

// Move to specific position
poseStack.translate(x, y, z);

// Accepts double or float
poseStack.translate(0.5, 0.5, 0.5);  // Center of block
```

**Effect**: All subsequent rendering appears at this offset position.

---

### 2. `scale(sx, sy, sz)` - Size

Changes the size of rendered objects.

```java
// Make 50% smaller
poseStack.scale(0.5F, 0.5F, 0.5F);

// Make twice as large
poseStack.scale(2.0F, 2.0F, 2.0F);

// Non-uniform scaling (stretch)
poseStack.scale(2.0F, 1.0F, 1.0F);  // Stretch along X-axis

// Mirror/flip
poseStack.scale(-1.0F, 1.0F, 1.0F);  // Mirror across X-axis
```

**Important Notes**:
- Negative scales flip/mirror the object
- Non-uniform scaling (different values for x/y/z) affects normal calculations
- Very small scales can cause z-fighting issues

---

### 3. `mulPose(Quaternionf)` - Rotation

Rotates around the current origin using quaternions.

```java
// Rotate around X-axis (pitch)
poseStack.mulPose(Axis.XP.rotationDegrees(90));

// Rotate around Y-axis (yaw)
poseStack.mulPose(Axis.YP.rotationDegrees(180));

// Rotate around Z-axis (roll)
poseStack.mulPose(Axis.ZP.rotationDegrees(45));

// Custom quaternion
Quaternionf quat = new Quaternionf().rotationXYZ(
    (float)Math.toRadians(x),
    (float)Math.toRadians(y),
    (float)Math.toRadians(z)
);
poseStack.mulPose(quat);
```

**Common Axes**:
- `Axis.XP` / `Axis.XN` - Positive/Negative X-axis
- `Axis.YP` / `Axis.YN` - Positive/Negative Y-axis  
- `Axis.ZP` / `Axis.ZN` - Positive/Negative Z-axis

---

### 4. `rotateAround(Quaternionf, x, y, z)` - Rotation Around Point

Rotates around a specific pivot point instead of the origin.

```java
// Rotate around point (0.5, 0.5, 0.5) - block center
poseStack.rotateAround(
    Axis.YP.rotationDegrees(45),
    0.5F, 0.5F, 0.5F
);
```

**Equivalent to**:
```java
poseStack.translate(0.5F, 0.5F, 0.5F);
poseStack.mulPose(Axis.YP.rotationDegrees(45));
poseStack.translate(-0.5F, -0.5F, -0.5F);
```

---

### 5. Stack Management

#### `pushPose()` - Save State
Creates a copy of the current transformation and adds it to the stack.

```java
poseStack.pushPose();
// Current state is saved and duplicated
```

#### `popPose()` - Restore State
Removes the current transformation and restores the previous one.

```java
poseStack.popPose();
// Returns to state before last pushPose()
```

**Critical**: Every `pushPose()` MUST have a matching `popPose()`!

---

### 6. Advanced Methods

#### `mulPose(Matrix4f)` - Direct Matrix Multiplication
Apply a custom transformation matrix.

```java
Matrix4f customMatrix = new Matrix4f();
// ... configure matrix ...
poseStack.mulPose(customMatrix);
```

#### `setIdentity()` - Reset Current Pose
Resets the current transformation to identity (no transformation).

```java
poseStack.setIdentity();
// Current pose is now identity matrix
```

#### `last()` - Access Current Pose
Get the current transformation matrices.

```java
PoseStack.Pose pose = poseStack.last();
Matrix4f poseMatrix = pose.pose();
Matrix3f normalMatrix = pose.normal();
```

---

## Order of Operations

### Understanding Transformation Order

**CRITICAL CONCEPT**: Transformations are applied in **reverse order** of calls.

```java
poseStack.translate(1, 0, 0);  // Step 1 in code
poseStack.rotate(Axis.YP.rotationDegrees(90));  // Step 2 in code
poseStack.scale(2, 2, 2);  // Step 3 in code

// Actual application order:
// 1. Scale by 2x
// 2. Rotate 90° around Y
// 3. Translate by (1, 0, 0)
```

### Why Does Order Matter?

Example: Rotating a cube that's offset from origin

#### Method A: Translate then Rotate
```java
poseStack.translate(5, 0, 0);  // Move 5 units right
poseStack.mulPose(Axis.YP.rotationDegrees(90));  // Rotate
// Result: Cube rotates in place at (5, 0, 0)
```

#### Method B: Rotate then Translate
```java
poseStack.mulPose(Axis.YP.rotationDegrees(90));  // Rotate first
poseStack.translate(5, 0, 0);  // Move 5 units right
// Result: Cube orbits around origin and ends at (0, 0, -5)!
```

### The Rule

Think in terms of **local vs world space**:
1. **Later operations** affect **local space** (object's coordinate system)
2. **Earlier operations** affect **world space** (scene coordinate system)

---

## Best Practices

### 1. Always Use Push/Pop Pairs

```java
// ✅ CORRECT
poseStack.pushPose();
try {
    poseStack.translate(x, y, z);
    renderSomething(poseStack);
} finally {
    poseStack.popPose();
}

// ❌ WRONG - Can crash if exception occurs
poseStack.pushPose();
poseStack.translate(x, y, z);
renderSomething(poseStack);
poseStack.popPose();
```

### 2. Keep Transformations Isolated

```java
// ✅ CORRECT - Each object has its own transformation
for (BlockPos pos : positions) {
    poseStack.pushPose();
    poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
    renderBlock(poseStack);
    poseStack.popPose();
}

// ❌ WRONG - Transformations accumulate
for (BlockPos pos : positions) {
    poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
    renderBlock(poseStack);
    // Missing popPose() - transformations stack up!
}
```

### 3. Translate Before Rotating (Usually)

```java
// ✅ CORRECT - Rotate around object's center
poseStack.translate(x, y, z);  // Move to position
poseStack.translate(0.5, 0.5, 0.5);  // Move to block center
poseStack.mulPose(Axis.YP.rotationDegrees(angle));  // Rotate in place
poseStack.translate(-0.5, -0.5, -0.5);  // Move back
// Render at origin
```

### 4. Use Consistent Scale Values

```java
// ✅ CORRECT - Uniform scaling preserves shape
poseStack.scale(0.5F, 0.5F, 0.5F);

// ⚠️ CAUTION - Non-uniform scaling can distort lighting
poseStack.scale(2.0F, 1.0F, 1.0F);
// The normal matrix will be recalculated (expensive)
```

### 5. Center Objects Before Rotating

```java
// Rotate a block around its center
poseStack.pushPose();
poseStack.translate(blockX + 0.5, blockY + 0.5, blockZ + 0.5);
poseStack.mulPose(Axis.YP.rotationDegrees(angle));
poseStack.translate(-0.5, -0.5, -0.5);
renderBlock(poseStack);
poseStack.popPose();
```

### 6. Apply Transformations in Logical Order

Standard order for most rendering:
```java
poseStack.pushPose();

// 1. Position in world
poseStack.translate(worldX, worldY, worldZ);

// 2. Move to pivot point
poseStack.translate(pivotX, pivotY, pivotZ);

// 3. Apply rotation
poseStack.mulPose(rotation);

// 4. Move back from pivot
poseStack.translate(-pivotX, -pivotY, -pivotZ);

// 5. Apply scale
poseStack.scale(scaleX, scaleY, scaleZ);

// 6. Render at origin (0, 0, 0)
render(poseStack);

poseStack.popPose();
```

---

## Common Pitfalls

### ❌ Pitfall 1: Forgetting to Pop

```java
// WRONG - Stack grows forever, causes crashes
public void render(PoseStack poseStack) {
    poseStack.pushPose();
    poseStack.translate(1, 2, 3);
    renderModel(poseStack);
    // Missing popPose()!
}

// CORRECT
public void render(PoseStack poseStack) {
    poseStack.pushPose();
    try {
        poseStack.translate(1, 2, 3);
        renderModel(poseStack);
    } finally {
        poseStack.popPose();
    }
}
```

### ❌ Pitfall 2: Wrong Transformation Order

```java
// WRONG - Object orbits instead of rotating in place
poseStack.mulPose(Axis.YP.rotationDegrees(45));
poseStack.translate(5, 0, 0);

// CORRECT - Object rotates in place at offset
poseStack.translate(5, 0, 0);
poseStack.mulPose(Axis.YP.rotationDegrees(45));
```

### ❌ Pitfall 3: Not Centering Rotation

```java
// WRONG - Rotates around corner (0, 0, 0)
poseStack.translate(x, y, z);
poseStack.mulPose(Axis.YP.rotationDegrees(90));
// Block corner stays at (x, y, z) but block rotates around it

// CORRECT - Rotates around center
poseStack.translate(x + 0.5, y + 0.5, z + 0.5);
poseStack.mulPose(Axis.YP.rotationDegrees(90));
poseStack.translate(-0.5, -0.5, -0.5);
```

### ❌ Pitfall 4: Mixing Degrees and Radians

```java
// WRONG - Using radians with rotationDegrees
poseStack.mulPose(Axis.YP.rotationDegrees(Math.PI));  // Wrong!

// CORRECT - Use degrees
poseStack.mulPose(Axis.YP.rotationDegrees(180));

// OR use radians properly
Quaternionf quat = new Quaternionf().rotateY((float)Math.PI);
poseStack.mulPose(quat);
```

### ❌ Pitfall 5: Modifying Matrices Directly

```java
// WRONG - Don't modify the matrix directly unless you know what you're doing
Matrix4f matrix = poseStack.last().pose();
matrix.m03 = 5.0F;  // Breaks normal matrix sync!

// CORRECT - Use PoseStack methods
poseStack.translate(5, 0, 0);
```

### ❌ Pitfall 6: Negative Scales Without Culling Awareness

```java
// WRONG - Flipping can invert face culling
poseStack.scale(-1, 1, 1);
renderModel(poseStack);  // Faces might render inside-out!

// CORRECT - Disable culling or fix winding order
RenderSystem.disableCull();
poseStack.scale(-1, 1, 1);
renderModel(poseStack);
RenderSystem.enableCull();
```

### ❌ Pitfall 7: Accumulating Transformations in Loops

```java
// WRONG - Transformations accumulate
for (int i = 0; i < 10; i++) {
    poseStack.translate(1, 0, 0);
    render(poseStack);
    // Each iteration adds to previous translation!
}

// CORRECT - Isolate each iteration
for (int i = 0; i < 10; i++) {
    poseStack.pushPose();
    poseStack.translate(i, 0, 0);
    render(poseStack);
    poseStack.popPose();
}
```

---

## Practical Examples

### Example 1: Rendering an Item Floating and Spinning

```java
public void renderFloatingItem(PoseStack poseStack, ItemStack stack, 
                                float x, float y, float z, 
                                float time, float partialTick) {
    poseStack.pushPose();
    
    // 1. Position in world
    poseStack.translate(x, y, z);
    
    // 2. Bobbing animation
    float bob = Math.sin(time * 0.1F) * 0.1F;
    poseStack.translate(0, bob, 0);
    
    // 3. Move to center for rotation
    poseStack.translate(0.5, 0.5, 0.5);
    
    // 4. Spin around Y-axis
    float angle = (time + partialTick) * 2.0F;
    poseStack.mulPose(Axis.YP.rotationDegrees(angle));
    
    // 5. Slight tilt for visual interest
    poseStack.mulPose(Axis.XP.rotationDegrees(15));
    
    // 6. Scale to desired size
    poseStack.scale(0.5F, 0.5F, 0.5F);
    
    // 7. Move back so item renders centered
    poseStack.translate(-0.5, -0.5, -0.5);
    
    // 8. Render the item
    itemRenderer.renderStatic(stack, ItemDisplayContext.GROUND, 
                              light, overlay, poseStack, buffer, level, 0);
    
    poseStack.popPose();
}
```

### Example 2: Rendering a Block with Custom Orientation

```java
public void renderOrientedBlock(PoseStack poseStack, BlockPos pos, 
                                 Direction facing, BlockState state) {
    poseStack.pushPose();
    
    // 1. Move to block position
    poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
    
    // 2. Move to center
    poseStack.translate(0.5, 0.5, 0.5);
    
    // 3. Rotate to face direction
    poseStack.mulPose(Axis.YP.rotationDegrees(facing.toYRot()));
    
    // 4. Move back to corner
    poseStack.translate(-0.5, -0.5, -0.5);
    
    // 5. Render block
    blockRenderer.renderSingleBlock(state, poseStack, buffer, light, overlay);
    
    poseStack.popPose();
}
```

### Example 3: GUI Item Rendering (2D)

```java
public void renderGuiItem(PoseStack poseStack, ItemStack stack, 
                          int x, int y, float scale) {
    poseStack.pushPose();
    
    // 1. Move to GUI position (2D coordinates)
    poseStack.translate(x, y, 100);  // Z=100 for depth sorting
    
    // 2. GUI items need special transforms
    poseStack.translate(8, 8, 0);  // Center of 16x16 icon
    
    // 3. Scale
    poseStack.scale(scale, scale, scale);
    
    // 4. Standard GUI item rotation (to face camera)
    poseStack.mulPose(Axis.ZP.rotationDegrees(180));
    poseStack.mulPose(Axis.YP.rotationDegrees(180));
    
    // 5. Render
    itemRenderer.renderStatic(stack, ItemDisplayContext.GUI, 
                              light, overlay, poseStack, buffer, level, 0);
    
    poseStack.popPose();
}
```

### Example 4: Rendering Multiple Objects in Formation

```java
public void renderCircleOfBlocks(PoseStack poseStack, Vec3 center, 
                                  float radius, int count, float time) {
    for (int i = 0; i < count; i++) {
        poseStack.pushPose();
        
        // 1. Calculate position in circle
        float angle = (float)(i * 2 * Math.PI / count + time * 0.05);
        float offsetX = (float)(Math.cos(angle) * radius);
        float offsetZ = (float)(Math.sin(angle) * radius);
        
        // 2. Position in world
        poseStack.translate(
            center.x + offsetX,
            center.y,
            center.z + offsetZ
        );
        
        // 3. Face toward center
        poseStack.translate(0.5, 0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees((float)Math.toDegrees(-angle)));
        poseStack.translate(-0.5, 0, -0.5);
        
        // 4. Render block
        renderBlock(poseStack);
        
        poseStack.popPose();
    }
}
```

### Example 5: Block Entity Renderer with Animation

```java
public class MyBlockEntityRenderer implements BlockEntityRenderer<MyBlockEntity> {
    
    @Override
    public void render(MyBlockEntity blockEntity, float partialTick, 
                       PoseStack poseStack, MultiBufferSource buffer, 
                       int light, int overlay) {
        poseStack.pushPose();
        
        // 1. Center in block
        poseStack.translate(0.5, 0.5, 0.5);
        
        // 2. Animated rotation
        float rotation = (blockEntity.tickCount + partialTick) * 2.0F;
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        
        // 3. Scale effect (pulsing)
        float scale = 1.0F + (float)Math.sin(rotation * 0.1) * 0.1F;
        poseStack.scale(scale, scale, scale);
        
        // 4. Render model at origin
        VertexConsumer consumer = buffer.getBuffer(RenderType.solid());
        renderModel(poseStack, consumer, light, overlay);
        
        poseStack.popPose();
    }
}
```

### Example 6: Entity Rendering with Proper Orientation

```java
public void renderEntity(PoseStack poseStack, Entity entity, 
                         float yaw, float pitch, float partialTick) {
    poseStack.pushPose();
    
    // 1. Position
    Vec3 pos = entity.getPosition(partialTick);
    poseStack.translate(pos.x, pos.y, pos.z);
    
    // 2. Entity height offset
    poseStack.translate(0, entity.getBbHeight() * 0.5, 0);
    
    // 3. Yaw rotation (horizontal facing)
    poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
    
    // 4. Pitch rotation (vertical facing)
    poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
    
    // 5. Scale based on entity size
    float size = entity.getBbWidth();
    poseStack.scale(size, size, size);
    
    // 6. Render model
    entityModel.renderToBuffer(poseStack, buffer, light, overlay, 
                               1.0F, 1.0F, 1.0F, 1.0F);
    
    poseStack.popPose();
}
```

---

## Advanced Techniques

### Technique 1: Billboard Effect (Face Camera)

Make an object always face the camera:

```java
public void renderBillboard(PoseStack poseStack, Vec3 pos, Camera camera) {
    poseStack.pushPose();
    
    // Position
    poseStack.translate(pos.x, pos.y, pos.z);
    
    // Get camera rotation
    Quaternionf cameraRotation = camera.rotation();
    
    // Apply inverse camera rotation (faces camera)
    poseStack.mulPose(cameraRotation);
    
    // Render flat quad or model
    renderQuad(poseStack);
    
    poseStack.popPose();
}
```

### Technique 2: Smooth Interpolation Between Poses

```java
public void renderInterpolated(PoseStack poseStack, 
                               Vec3 prevPos, Vec3 currentPos,
                               Quaternionf prevRot, Quaternionf currentRot,
                               float partialTick) {
    poseStack.pushPose();
    
    // Interpolate position
    double x = Mth.lerp(partialTick, prevPos.x, currentPos.x);
    double y = Mth.lerp(partialTick, prevPos.y, currentPos.y);
    double z = Mth.lerp(partialTick, prevPos.z, currentPos.z);
    poseStack.translate(x, y, z);
    
    // Interpolate rotation (slerp for smooth rotation)
    Quaternionf interpolatedRot = new Quaternionf(prevRot);
    interpolatedRot.slerp(currentRot, partialTick);
    poseStack.mulPose(interpolatedRot);
    
    // Render
    renderModel(poseStack);
    
    poseStack.popPose();
}
```

### Technique 3: Constraint to Parent Object

```java
public void renderChildObject(PoseStack poseStack, 
                              Vec3 parentPos, Quaternionf parentRot,
                              Vec3 localOffset, Quaternionf localRot) {
    poseStack.pushPose();
    
    // 1. Apply parent transformation
    poseStack.translate(parentPos.x, parentPos.y, parentPos.z);
    poseStack.mulPose(parentRot);
    
    // 2. Apply local offset (in parent's space)
    poseStack.translate(localOffset.x, localOffset.y, localOffset.z);
    
    // 3. Apply local rotation
    poseStack.mulPose(localRot);
    
    // Now child follows parent's transformation
    renderChild(poseStack);
    
    poseStack.popPose();
}
```

### Technique 4: Multi-Part Model with Individual Part Animations

```java
public void renderMultiPartModel(PoseStack poseStack, float time) {
    poseStack.pushPose();
    
    // Main body
    poseStack.pushPose();
    renderBodyPart(poseStack);
    poseStack.popPose();
    
    // Left arm (attached to body, animated)
    poseStack.pushPose();
    poseStack.translate(-0.5, 0.5, 0);  // Shoulder position
    float armAngle = (float)Math.sin(time * 0.1) * 45;
    poseStack.mulPose(Axis.XP.rotationDegrees(armAngle));
    renderArmPart(poseStack);
    poseStack.popPose();
    
    // Right arm
    poseStack.pushPose();
    poseStack.translate(0.5, 0.5, 0);
    poseStack.mulPose(Axis.XP.rotationDegrees(-armAngle));
    renderArmPart(poseStack);
    poseStack.popPose();
    
    poseStack.popPose();
}
```

### Technique 5: Converting World Space to Local Space

```java
public Vec3 worldToLocal(PoseStack poseStack, Vec3 worldPos) {
    Matrix4f matrix = poseStack.last().pose();
    
    // Create inverse matrix
    Matrix4f inverse = new Matrix4f(matrix);
    inverse.invert();
    
    // Transform point
    Vector3f local = inverse.transformPosition(
        (float)worldPos.x, 
        (float)worldPos.y, 
        (float)worldPos.z, 
        new Vector3f()
    );
    
    return new Vec3(local.x, local.y, local.z);
}
```

### Technique 6: Dynamic Camera-Relative Scaling

Keep object at constant screen size regardless of distance:

```java
public void renderConstantScreenSize(PoseStack poseStack, 
                                     Vec3 pos, Camera camera,
                                     float desiredScreenSize) {
    poseStack.pushPose();
    
    // Position
    poseStack.translate(pos.x, pos.y, pos.z);
    
    // Calculate distance to camera
    Vec3 camPos = camera.getPosition();
    double distance = pos.distanceTo(camPos);
    
    // Scale based on distance
    float scale = (float)(distance * desiredScreenSize * 0.1);
    poseStack.scale(scale, scale, scale);
    
    // Face camera
    poseStack.mulPose(camera.rotation());
    
    // Render
    renderQuad(poseStack);
    
    poseStack.popPose();
}
```

---

## Quick Reference Card

### Essential Pattern for All Rendering

```java
poseStack.pushPose();
try {
    // 1. Translate to position
    poseStack.translate(x, y, z);
    
    // 2. Move to pivot point (usually center)
    poseStack.translate(0.5, 0.5, 0.5);
    
    // 3. Apply rotation(s)
    poseStack.mulPose(rotation);
    
    // 4. Move back from pivot
    poseStack.translate(-0.5, -0.5, -0.5);
    
    // 5. Apply scale
    poseStack.scale(sx, sy, sz);
    
    // 6. Render at origin
    render(poseStack);
} finally {
    poseStack.popPose();
}
```

### Common Rotations

```java
// Face North: no rotation (0°)
// Face East: 
poseStack.mulPose(Axis.YP.rotationDegrees(-90));
// Face South:
poseStack.mulPose(Axis.YP.rotationDegrees(180));
// Face West:
poseStack.mulPose(Axis.YP.rotationDegrees(90));

// Face Up:
poseStack.mulPose(Axis.XP.rotationDegrees(-90));
// Face Down:
poseStack.mulPose(Axis.XP.rotationDegrees(90));
```

### Debugging Tips

```java
// Print current matrix for debugging
Matrix4f matrix = poseStack.last().pose();
System.out.println("Current transform: " + matrix);

// Check stack depth
int depth = 0;
PoseStack test = new PoseStack();
while (!test.clear()) { depth++; test.popPose(); }
System.out.println("Stack depth: " + depth);

// Visualize transformation by rendering coordinate axes
renderDebugAxes(poseStack);
```

---

## Summary

### Key Takeaways

1. **Always push/pop**: Isolate transformations with `pushPose()` and `popPose()`
2. **Order matters**: Later calls affect earlier transformations
3. **Center rotations**: Move to pivot, rotate, move back
4. **Use try-finally**: Ensure popPose() always executes
5. **Think in spaces**: Understand local vs world transformations
6. **Uniform scaling preferred**: Non-uniform scales affect normals and lighting
7. **Degrees vs Radians**: Be consistent with angle units

### Mental Model

Think of PoseStack as telling the GPU "where you are standing and which way you're looking" before you draw something. Each transformation changes your viewpoint:
- **Translate**: Move to a new location
- **Rotate**: Turn to face a new direction  
- **Scale**: Zoom in or out
- **Push/Pop**: Remember/restore where you were

Every object is drawn at the origin (0,0,0), but by transforming the viewpoint first, it appears in the right place!

---

## Additional Resources

- JOML Documentation: https://github.com/JOML-CI/JOML
- Matrix Mathematics: https://learnopengl.com/Getting-started/Transformations
- Minecraft Rendering Pipeline: https://fabricmc.net/wiki/tutorial:rendering

---

*This guide covers Minecraft 1.20+ with JOML matrices. Earlier versions used different matrix libraries.*

