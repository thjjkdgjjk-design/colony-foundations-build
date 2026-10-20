package dev.colonyfoundations.block;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Initial port of MineColonies' OWNER/OFFICER/FRIEND/NEUTRAL/HOSTILE ranks.
 * Profiles intentionally cover only actions currently enforced by this Fabric port.
 * Other upstream actions, hostile behavior, alliances and the permissions UI remain open work.
 */
public enum ColonyRank {
    OWNER("owner"),
    OFFICER("officer"),
    FRIEND("friend"),
    NEUTRAL("neutral"),
    HOSTILE("hostile");

    private final String id;

    ColonyRank(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<ColonyRank> parse(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        String normalized = value.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(rank -> rank.id.equals(normalized)).findFirst();
    }

    /** Returns new mutable profiles for each colony; editing one colony won't mutate defaults. */
    public static Map<ColonyRank, Set<ColonyPermission>> defaultProfiles() {
        EnumMap<ColonyRank, Set<ColonyPermission>> profiles = new EnumMap<>(ColonyRank.class);
        profiles.put(OWNER, EnumSet.allOf(ColonyPermission.class));
        profiles.put(OFFICER, EnumSet.allOf(ColonyPermission.class));
        profiles.put(FRIEND, EnumSet.of(ColonyPermission.USE_BLOCKS));
        profiles.put(NEUTRAL, EnumSet.noneOf(ColonyPermission.class));
        profiles.put(HOSTILE, EnumSet.noneOf(ColonyPermission.class));
        return profiles;
    }
}
