package dev.colonyfoundations.block;

import java.util.Arrays;
import java.util.Optional;

/** Work-order operation identifiers aligned with MineColonies WorkOrderType. */
public enum ColonyWorkOrderType {
    BUILD("build"),
    UPGRADE("upgrade"),
    REPAIR("repair"),
    REMOVE("remove");

    private final String id;

    ColonyWorkOrderType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<ColonyWorkOrderType> parse(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        return Arrays.stream(values()).filter(type -> type.id.equalsIgnoreCase(value)).findFirst();
    }
}
