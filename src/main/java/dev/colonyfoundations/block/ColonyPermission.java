package dev.colonyfoundations.block;

import java.util.Arrays;
import java.util.Optional;

/**
 * Individually persisted colony actions. MineColonies exposes rank-based permission toggles;
 * this port stores equivalent action grants per member until shared-rank templates and UI exist.
 */
public enum ColonyPermission {
    BREAK_BLOCKS("break_blocks"),
    PLACE_BLOCKS("place_blocks"),
    USE_BLOCKS("use_blocks"),
    MANAGE_MEMBERS("manage_members"),
    BUILD_PROJECTS("build_projects"),
    ASSIGN_WORKERS("assign_workers");

    private final String id;

    ColonyPermission(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<ColonyPermission> parse(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        return Arrays.stream(values()).filter(permission -> permission.id.equals(value)).findFirst();
    }
}
