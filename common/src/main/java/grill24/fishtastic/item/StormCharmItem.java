package grill24.fishtastic.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.function.Consumer;

/**
 * A single-use consumable that summons a real thunderstorm, rather than a rod-slot charm.
 *
 * <p>Used from the hand it is a held charge ({@link #CHARGE_TICKS}) that builds sparks and a rising
 * rumble before the sky breaks, and releasing early costs nothing. Fired from the rod's charm slot
 * there is no charge — the cast itself is the deliberate act.
 *
 * <p>Deliberately not a {@link grill24.fishtastic.component.CharmEffect} and deliberately not in
 * {@code fishtastic:fishing_charms} — it must not be slottable into the rod, where it would sit
 * doing nothing... except that players reach for the charm slot instinctively, so it IS accepted
 * there — {@code FishingMinigameManager#startSession} spots it, fires the storm and clears the slot
 * on the very next cast (before the fish pool and quest weather are resolved, so that cast already
 * counts as thunder). Using it from the hand does the same thing immediately.
 *
 * <p>The Luna Charm is the persistent, private, illusory upgrade (it only rewrites the
 * time-of-day this session resolves to); this is its public, temporary, real counterpart: the
 * weather genuinely changes, so {@code WeatherCondition.fromLevel} reports THUNDER on its own and
 * every weather-gated quest works with no special-casing at all. On a server, one player's storm is
 * a fishing window for everyone.
 */
public class StormCharmItem extends Item {

    /**
     * Ticks of thunder granted per use — 5 real minutes, ~12 casts. Sits inside vanilla's own
     * thunder range so a summoned storm doesn't read as anomalous, and stays short enough that
     * one use isn't a lasting imposition on anyone else sharing the world.
     */
    public static final int STORM_DURATION_TICKS = 6000;

    /**
     * Ticks the player must hold right-click before the storm breaks. Long enough to read as
     * calling something down rather than flicking a switch, short enough not to be a chore — and
     * it makes the summon interruptible, so a misclick costs nothing.
     */
    public static final int CHARGE_TICKS = 40;

    public StormCharmItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Reject up-front rather than after a wasted charge-up. These two are readable on both
        // sides, so the client doesn't start an animation the server is about to refuse; the
        // gamerule check can only run server-side and is re-tested in trySummonStorm.
        if (!level.canHaveWeather()) {
            if (!level.isClientSide()) fail(player, "no_sky", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        if (level.isThundering()) {
            if (!level.isClientSide()) fail(player, "already_storming", ChatFormatting.YELLOW);
            return InteractionResult.FAIL;
        }

        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return CHARGE_TICKS;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        // Held aloft rather than raised to the face — this is calling the weather down, not drinking it.
        return ItemUseAnimation.BOW;
    }

    /** Builds the charge visually and audibly so the hold reads as deliberate rather than laggy. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining) {
        int elapsed = CHARGE_TICKS - ticksRemaining;
        float progress = Mth.clamp(elapsed / (float) CHARGE_TICKS, 0.0f, 1.0f);

        if (level.isClientSide()) {
            // Sparks gather inward as the charge builds.
            RandomSource random = entity.getRandom();
            int sparks = 1 + (int) (progress * 4);
            double radius = 1.4 - progress * 1.0;
            for (int i = 0; i < sparks; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        entity.getX() + Math.cos(angle) * radius,
                        entity.getY() + 0.6 + random.nextDouble() * 1.2,
                        entity.getZ() + Math.sin(angle) * radius,
                        0.0, 0.02, 0.0);
            }
            return;
        }

        // A rising tick every half second — pitch climbs with the charge.
        if (elapsed > 0 && elapsed % 10 == 0) {
            level.playSound(null, entity.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT,
                    SoundSource.PLAYERS, 0.25f + progress * 0.35f, 0.6f + progress * 0.8f);
        }
    }

    /** The charge completed — break the storm and spend the charm. */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level instanceof ServerLevel serverLevel && entity instanceof Player player) {
            if (trySummonStorm(serverLevel, player)) {
                stack.consume(1, player);
            }
        }
        return stack;
    }

    /**
     * Released early — nothing happens and nothing is spent. Being interruptible is the point of
     * the charge-up: a misclick costs the player nothing.
     */
    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
        return false;
    }

    /**
     * Summons the storm, or explains why it can't. Shared by hand-use and by the cast-time
     * activation in {@code FishingMinigameManager#startSession}, so both paths enforce the same
     * guards and neither consumes the item on a refusal.
     *
     * @return true if the weather was actually changed — the caller consumes the item only then.
     */
    public static boolean trySummonStorm(ServerLevel level, Player player) {
        if (!level.canHaveWeather()) {
            fail(player, "no_sky", ChatFormatting.RED);
            return false;
        }
        if (level.isThundering()) {
            fail(player, "already_storming", ChatFormatting.YELLOW);
            return false;
        }
        // ServerLevel#advanceWeatherCycle only decrements the weather timers while ADVANCE_WEATHER
        // is on. With it off, a forced storm would never tick back down — a permanent thunderstorm
        // the player has no way to clear. Refuse rather than hand them that.
        if (!level.getGameRules().get(GameRules.ADVANCE_WEATHER)) {
            fail(player, "weather_frozen", ChatFormatting.RED);
            return false;
        }

        MinecraftServer server = level.getServer();
        // Same call the /weather thunder command makes: clear-time 0, then rain+thunder for the
        // duration. Weather data is server-wide, so this lands for every player in the overworld.
        server.setWeatherParameters(0, STORM_DURATION_TICKS, true, true);

        // setWeatherParameters only sets the *target*. Level#isThundering reads the interpolated
        // thunderLevel (itself scaled by rainLevel), and ServerLevel#advanceWeatherCycle ramps both
        // at 0.01/tick — a five-second roll-in during which WeatherCondition.fromLevel still reports
        // CLEAR. Left alone, the very cast that spent the charm would not count as thunder, which
        // reads as the item being broken. Snap both levels to full so the storm is true the instant
        // it is paid for.
        level.setRainLevel(1.0f);
        level.setThunderLevel(1.0f);
        // setRainLevel/setThunderLevel write oRainLevel == rainLevel, so advanceWeatherCycle's
        // delta checks won't fire and clients would never be told. Broadcast the same packets it
        // would have sent.
        PlayerList players = server.getPlayerList();
        players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0.0f),
                level.dimension());
        players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, 1.0f),
                level.dimension());
        players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, 1.0f),
                level.dimension());

        level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.WEATHER, 1.0f, 1.0f);
        return true;
    }

    private static void fail(Player player, String key, ChatFormatting style) {
        player.sendSystemMessage(
                Component.translatable("message.fishtastic.storm_charm." + key).withStyle(style));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                 Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        builder.accept(Component.translatable("tooltip.fishtastic.storm_charm.summons",
                STORM_DURATION_TICKS / 20 / 60).withStyle(ChatFormatting.AQUA));
        builder.accept(Component.translatable("tooltip.fishtastic.storm_charm.single_use")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
