package dev.colonyfoundations.worker;

/**
 * Small, deterministic retry policy for villager navigation.
 *
 * The job controller runs every 20 game ticks. When navigation remains active
 * but the citizen is still far from the target, allow a replacement path every
 * 100 ticks. Entity IDs spread retries across ticks instead of making all
 * assigned citizens recalculate paths together.
 */
public final class WorkerNavigationRules {
    private static final long CHECK_INTERVAL_TICKS = 20L;
    private static final long RETRY_INTERVAL_TICKS = 100L;
    private static final long CHECKS_PER_RETRY = RETRY_INTERVAL_TICKS / CHECK_INTERVAL_TICKS;

    private WorkerNavigationRules() {
    }

    public static boolean shouldRequestPath(boolean navigationIdle, long gameTime, int entityId) {
        if (navigationIdle) {
            return true;
        }
        long retrySlot = Math.floorMod(gameTime / CHECK_INTERVAL_TICKS + entityId, CHECKS_PER_RETRY);
        return retrySlot == 0L;
    }
}
