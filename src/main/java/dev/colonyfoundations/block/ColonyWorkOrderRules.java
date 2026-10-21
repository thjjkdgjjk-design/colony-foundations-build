package dev.colonyfoundations.block;

/** Level and builder-capability policy for upstream build-order types. */
public final class ColonyWorkOrderRules {
    private ColonyWorkOrderRules() { }

    public static boolean validLevelTransition(ColonyWorkOrderType type,
                                               int currentLevel,
                                               int targetLevel,
                                               int builderLevel) {
        if (type == null || builderLevel < 1 || builderLevel > 5
                || currentLevel < 0 || currentLevel > 5
                || targetLevel < 0 || targetLevel > 5) return false;
        return switch (type) {
            case BUILD -> currentLevel == 0 && targetLevel == 1;
            case UPGRADE -> currentLevel >= 1 && currentLevel < 5
                    && targetLevel == currentLevel + 1 && targetLevel <= builderLevel;
            case REPAIR -> currentLevel >= 1 && currentLevel <= 5
                    && targetLevel == currentLevel && currentLevel <= builderLevel;
            case REMOVE -> currentLevel >= 1 && currentLevel <= 5
                    && targetLevel == 0 && currentLevel <= builderLevel;
        };
    }
}
