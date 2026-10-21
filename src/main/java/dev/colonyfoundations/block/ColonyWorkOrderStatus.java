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

    public static Optional<ColonyWorkOrderStatus> parse(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        return Arrays.stream(values()).filter(status -> status.id.equalsIgnoreCase(value)).findFirst();
    }
}
