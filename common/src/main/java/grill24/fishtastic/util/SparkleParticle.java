package grill24.fishtastic.util;

import grill24.fishtastic.FishtasticItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.Random;

public class SparkleParticle {
    // Positive Y = upward in bar-space (same convention as PhysicsSimulation)
    private static final float GRAVITY = 0.0015f;

    private final Vector2f position;
    private final Vector2f previousPosition;
    private final Vector2f velocity;
    private final Vector3f rotation;
    private final Vector3f previousRotation;
    private final float rotZVelocity;
    private final ItemStack itemStack;

    private int age;
    private final int lifetime;

    public SparkleParticle(float x, float y, Random random, int lifetime) {
        this.position = new Vector2f(x, y);
        this.previousPosition = new Vector2f(x, y);
        this.lifetime = lifetime;
        this.age = 0;

        float angle = random.nextFloat() * (float) (2 * Math.PI);
        float speed = 0.015f + random.nextFloat() * 0.04f;
        this.velocity = new Vector2f((float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed);

        float initRotZ = random.nextFloat() * 360f;
        this.rotation = new Vector3f(0, 0, initRotZ);
        this.previousRotation = new Vector3f(0, 0, initRotZ);
        this.rotZVelocity = (random.nextBoolean() ? 1f : -1f) * (10f + random.nextFloat() * 20f);

        this.itemStack = new ItemStack(FishtasticItems.SPARKLE);
    }

    public void tick() {
        previousPosition.set(position);
        previousRotation.set(rotation);
        velocity.y -= GRAVITY;
        position.add(velocity);
        rotation.z += rotZVelocity;
        age++;
    }

    public boolean isAlive() {
        return age < lifetime;
    }

    /** 0 at spawn, 1 at death. */
    public float getLifetimeProgress() {
        return (float) age / lifetime;
    }

    public Vector2f getInterpolatedPosition(float partialTick) {
        return new Vector2f(
            previousPosition.x + (position.x - previousPosition.x) * partialTick,
            previousPosition.y + (position.y - previousPosition.y) * partialTick
        );
    }

    public float getInterpolatedRotationZ(float partialTick) {
        return previousRotation.z + (rotation.z - previousRotation.z) * partialTick;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }
}
