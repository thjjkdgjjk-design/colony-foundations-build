package dev.colonyfoundations.block;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Identity rules used by the persistent colony directory.
 *
 * New colonies receive a random UUID and persist it in the Town Hall. Existing worlds
 * whose Town Hall predates that field are assigned a deterministic UUID on first sync,
 * so repeated chunk loads do not create a new logical colony.
 */
public final class ColonyIdentityRules {
    private ColonyIdentityRules() {
    }

    public static UUID stableLegacyId(String dimension, long packedPosition, UUID owner) {
        if (dimension == null || dimension.isBlank() || owner == null) {
            throw new IllegalArgumentException("A dimension and owner are required to derive a colony identity");
        }
        String key = "colony-foundations|" + dimension + "|"
                + Long.toUnsignedString(packedPosition, 16) + "|" + owner;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    public static Optional<UUID> parseUuid(String candidate) {
        if (candidate == null || candidate.isBlank()) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(candidate));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
