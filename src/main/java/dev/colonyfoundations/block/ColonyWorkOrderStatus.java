package dev.colonyfoundations.block;

import java.util.Arrays;
import java.util.Optional;

public enum ColonyWorkOrderStatus {
    QUEUED("queued"),
    ACTIVE("active"),
    PAUSED("paused"),
    BLOCKED("blocked"),
    COMPLETED("completed"),
    CANCELLED("cancelled");

    private final String id;

    ColonyWorkOrderStatus(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    /**
     * Allowed lifecycle edges for a single work order. Repeating the same state is idempotent;
     * terminal states cannot be reopened, and a queued/progressed order cannot be moved backwards.
     */
    public boolean canTransitionTo(ColonyWorkOrderStatus next) {
        if (next == null) return false;
        if (this == next) return true;
        return switch (this) {
            case QUEUED -> next == ACTIVE || next == BLOCKED || next == CANCELLED;
            case ACTIVE -> next == PAUSED || next == BLOCKED || next == COMPLETED;
            case PAUSED -> next == ACTIVE || next == BLOCKED;
            case BLOCKED -> next == ACTIVE || next == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    public static Optional<ColonyWorkOrderStatus> parse(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        return Arrays.stream(values()).filter(status -> status.id.equalsIgnoreCase(value)).findFirst();
    }
}
