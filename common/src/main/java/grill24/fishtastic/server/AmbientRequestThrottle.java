package grill24.fishtastic.server;

/**
 * Per-player token bucket for ambient context requests (Fish in World, Phase 4). Pure and
 * tick-driven so it is headless-testable: no wall clock, no Minecraft types.
 *
 * <p>A well-behaved client dispatches at most one request per second (its own bounded cadence) and
 * each request costs one token, so it can never exhaust a bucket that refills one token per second
 * and starts full. A misbehaving or malicious client burns the {@link #BURST_CAPACITY}-token burst
 * and then has requests silently dropped — the handler answers nothing, which the client already
 * treats as a supported state.
 */
public final class AmbientRequestThrottle {

    /** Requests a player may have answered back-to-back before the refill catches up. */
    public static final int BURST_CAPACITY = 4;
    /** Ticks per refilled token (one per second). */
    public static final int REFILL_INTERVAL_TICKS = 20;
    /** Tokens are discarded after this much inactivity, so a stale bucket cannot hoard. */
    public static final long IDLE_EXPIRY_TICKS = 24000L;

    private int tokens = BURST_CAPACITY;
    private long lastRefillTick = Long.MIN_VALUE;
    private long lastUseTick = Long.MIN_VALUE;
    private long accepted;
    private long dropped;

    /**
     * Takes one token if available. {@code tick} is the game time of the server thread handling the
     * request (monotonic within a level; the caller passes {@code level.getGameTime()}).
     *
     * @return whether the request may be answered
     */
    public boolean tryAcquire(long tick) {
        refill(tick);
        lastUseTick = tick;
        if (tokens > 0) {
            tokens--;
            accepted++;
            return true;
        }
        dropped++;
        return false;
    }

    private void refill(long tick) {
        if (lastRefillTick == Long.MIN_VALUE || tick < lastRefillTick) {
            // First use ever, or the game clock moved backwards (a level change swaps in another
            // level's time): adopt the tick as the clock without refilling. The sentinel test also
            // keeps the subtraction below in range — Long.MIN_VALUE cannot be subtracted from.
            lastRefillTick = tick;
            return;
        }
        if (tick == lastRefillTick) {
            return;
        }
        long gained = (tick - lastRefillTick) / REFILL_INTERVAL_TICKS;
        if (gained <= 0) {
            return;
        }
        lastRefillTick += gained * REFILL_INTERVAL_TICKS;
        tokens = (int) Math.min(BURST_CAPACITY, tokens + gained);
    }

    /** Tokens currently available (after the last refill). */
    public int tokens() {
        return tokens;
    }

    public long accepted() {
        return accepted;
    }

    public long dropped() {
        return dropped;
    }

    /** Whether this bucket has been idle long enough to forget — it holds no useful state. */
    public boolean idleExpired(long tick) {
        return lastUseTick != Long.MIN_VALUE
                && tick != Long.MIN_VALUE
                && tick - lastUseTick > IDLE_EXPIRY_TICKS;
    }

    /**
     * Game time of the last {@link #tryAcquire}, or {@link Long#MIN_VALUE} when never used. The
     * tracked-player table evicts its least-recently-used bucket by this figure.
     */
    long lastUseTick() {
        return lastUseTick;
    }
}
