package dev.colonyfoundations.block;

import java.util.Collection;
import java.util.UUID;

/** Deterministic initial Town Hall claim geometry and owner/member authorization. */
public final class ColonyTerritoryRules {
    /** Initial claim extends four chunk coordinates in every horizontal direction from the Town Hall chunk. */
    public static final int INITIAL_RADIUS_CHUNKS = 4;

    private ColonyTerritoryRules() {
    }

    public static boolean containsInitialClaim(int hallBlockX, int hallBlockZ, int targetBlockX, int targetBlockZ) {
        int hallChunkX = Math.floorDiv(hallBlockX, 16);
        int hallChunkZ = Math.floorDiv(hallBlockZ, 16);
        int targetChunkX = Math.floorDiv(targetBlockX, 16);
        int targetChunkZ = Math.floorDiv(targetBlockZ, 16);
        return Math.abs(targetChunkX - hallChunkX) <= INITIAL_RADIUS_CHUNKS
                && Math.abs(targetChunkZ - hallChunkZ) <= INITIAL_RADIUS_CHUNKS;
    }

    public static boolean canModify(UUID owner, Collection<UUID> members, UUID actor) {
        if (owner == null || actor == null) return false;
        return owner.equals(actor) || (members != null && members.contains(actor));
    }
}
