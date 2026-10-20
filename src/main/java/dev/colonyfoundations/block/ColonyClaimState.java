package dev.colonyfoundations.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * World-persistent claim records. The index is rebuilt from saved records so protection
 * does not disappear when a Town Hall's chunk is unloaded.
 */
public final class ColonyClaimState extends PersistentState {
    private static final String STATE_ID = "colony_foundations_claims";

    private static final Codec<ClaimRecord> CLAIM_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("dimension").forGetter(ClaimRecord::dimension),
            Codec.LONG.fieldOf("position").forGetter(ClaimRecord::position),
            Codec.STRING.fieldOf("owner").forGetter(ClaimRecord::ownerUuid),
            Codec.list(Codec.STRING).optionalFieldOf("members", List.of()).forGetter(ClaimRecord::members),
            Codec.STRING.optionalFieldOf("colony_id", "").forGetter(ClaimRecord::colonyId),
            Codec.STRING.optionalFieldOf("colony_name", "").forGetter(ClaimRecord::colonyName),
            Codec.STRING.optionalFieldOf("owner_name", "").forGetter(ClaimRecord::ownerName),
            Codec.INT.optionalFieldOf("colony_level", 1).forGetter(ClaimRecord::colonyLevel)
    ).apply(instance, ClaimRecord::new));

    private static final Codec<ColonyClaimState> CODEC = Codec.unboundedMap(Codec.STRING, CLAIM_CODEC)
            .fieldOf("claims").codec()
            .xmap(ColonyClaimState::new, ColonyClaimState::snapshot);

    private static final PersistentStateType<ColonyClaimState> TYPE =
            new PersistentStateType<>(STATE_ID, ColonyClaimState::new, CODEC, null);

    private final Map<String, ClaimRecord> claims = new LinkedHashMap<>();
    private final Map<String, Map<Long, List<ClaimRecord>>> claimsByDimensionChunk = new HashMap<>();

    public ColonyClaimState() {
    }

    ColonyClaimState(Map<String, ClaimRecord> savedClaims) {
        if (savedClaims != null) claims.putAll(savedClaims);
        rebuildIndex();
    }

    public static ColonyClaimState get(ServerWorld world) {
        ServerWorld storageWorld = world.getServer().getOverworld();
        return storageWorld.getPersistentStateManager().getOrCreate(TYPE);
    }

    /** Backwards-compatible entry point for callers without colony metadata. */
    public void upsert(RegistryKey<World> dimension, BlockPos position, UUID owner, Collection<UUID> members) {
        upsert(dimension, position, owner, members, null, "", "", 1);
    }

    /**
     * Persist colony identity and display metadata alongside the claim. The record is stored
     * in Overworld PersistentState, so these details remain queryable while the Town Hall chunk
     * is unloaded. A legacy record keeps its identity; otherwise an older save receives a
     * deterministic identity based on dimension, position and owner.
     *
     * @return the durable colony UUID, or null when this position is not a founded colony
     */
    public UUID upsert(RegistryKey<World> dimension, BlockPos position, UUID owner,
                       Collection<UUID> members, UUID requestedColonyId,
                       String colonyName, String ownerName, int colonyLevel) {
        if (owner == null) {
            remove(dimension, position);
            return null;
        }
        String dimensionId = dimension.getValue().toString();
        String key = recordKey(dimensionId, position.asLong());
        ClaimRecord previous = claims.get(key);
        List<String> memberIds = members == null ? List.of() : members.stream()
                .filter(Objects::nonNull)
                .map(UUID::toString)
                .distinct()
                .sorted()
                .toList();

        UUID resolvedId = requestedColonyId;
        if (resolvedId == null && previous != null && owner.toString().equals(previous.ownerUuid())) {
            resolvedId = ColonyIdentityRules.parseUuid(previous.colonyId()).orElse(null);
        }
        if (resolvedId == null) {
            resolvedId = ColonyIdentityRules.stableLegacyId(dimensionId, position.asLong(), owner);
        }

        ClaimRecord replacement = new ClaimRecord(
                dimensionId, position.asLong(), owner.toString(), memberIds,
                resolvedId.toString(), colonyName, ownerName, colonyLevel);
        if (!replacement.equals(previous)) {
            claims.put(key, replacement);
            rebuildIndex();
            markDirty();
        }
        return resolvedId;
    }

    public void remove(RegistryKey<World> dimension, BlockPos position) {
        String key = recordKey(dimension.getValue().toString(), position.asLong());
        if (claims.remove(key) != null) {
            rebuildIndex();
            markDirty();
        }
    }

    /** Find a saved colony by its durable identity without loading its Town Hall chunk. */
    public Optional<ClaimRecord> findColony(UUID colonyId) {
        if (colonyId == null) return Optional.empty();
        String id = colonyId.toString();
        return claims.values().stream()
                .filter(ClaimRecord::hasIdentity)
                .filter(claim -> id.equals(claim.colonyId()))
                .findFirst();
    }

    /** Snapshot of registered colony records; uninitialized legacy claims are intentionally omitted. */
    public List<ClaimRecord> colonies() {
        return claims.values().stream()
                .filter(ClaimRecord::hasIdentity)
                .sorted(java.util.Comparator.comparing(ClaimRecord::colonyId))
                .toList();
    }

    public List<ClaimRecord> nearbyClaims(String dimension, BlockPos target) {
        Map<Long, List<ClaimRecord>> byChunk = claimsByDimensionChunk.get(dimension);
        if (byChunk == null) return List.of();

        int chunkX = target.getX() >> 4;
        int chunkZ = target.getZ() >> 4;
        List<ClaimRecord> nearby = new ArrayList<>();
        for (int dx = -ColonyTerritoryRules.INITIAL_RADIUS_CHUNKS;
             dx <= ColonyTerritoryRules.INITIAL_RADIUS_CHUNKS; dx++) {
            for (int dz = -ColonyTerritoryRules.INITIAL_RADIUS_CHUNKS;
                 dz <= ColonyTerritoryRules.INITIAL_RADIUS_CHUNKS; dz++) {
                List<ClaimRecord> bucket = byChunk.get(chunkKey(chunkX + dx, chunkZ + dz));
                if (bucket != null) nearby.addAll(bucket);
            }
        }
        return List.copyOf(nearby);
    }

    /** Ambiguous overlapping claims fail closed: logistics never crosses colony ownership boundaries. */
    public Optional<UUID> ownerAt(String dimension, BlockPos target) {
        if (dimension == null || target == null) return Optional.empty();
        java.util.Set<String> owners = new java.util.HashSet<>();
        for (ClaimRecord claim : nearbyClaims(dimension, target)) {
            if (claim.contains(target) && claim.isValid()) owners.add(claim.ownerUuid());
        }
        if (owners.size() != 1) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(owners.iterator().next()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private Map<String, ClaimRecord> snapshot() {
        return Map.copyOf(claims);
    }

    private void rebuildIndex() {
        claimsByDimensionChunk.clear();
        claims.entrySet().removeIf(entry -> !entry.getValue().isValid());
        for (ClaimRecord claim : claims.values()) {
            BlockPos center = claim.center();
            long chunk = chunkKey(center.getX() >> 4, center.getZ() >> 4);
            claimsByDimensionChunk.computeIfAbsent(claim.dimension(), ignored -> new HashMap<>())
                    .computeIfAbsent(chunk, ignored -> new ArrayList<>())
                    .add(claim);
        }
    }

    private static String recordKey(String dimension, long position) {
        return dimension + "#" + Long.toUnsignedString(position, 16);
    }

    private static long chunkKey(int x, int z) {
        return ((long) x & 0xFFFF_FFFFL) << 32 | ((long) z & 0xFFFF_FFFFL);
    }

    public record ClaimRecord(String dimension, long position, String ownerUuid, List<String> members,
                              String colonyId, String colonyName, String ownerName, int colonyLevel) {
        public ClaimRecord {
            dimension = dimension == null ? "" : dimension;
            ownerUuid = ownerUuid == null ? "" : ownerUuid;
            members = members == null ? List.of() : members.stream()
                    .filter(Objects::nonNull).distinct().sorted().toList();
            colonyId = colonyId == null ? "" : colonyId;
            colonyName = colonyName == null ? "" : colonyName;
            ownerName = ownerName == null ? "" : ownerName;
            colonyLevel = Math.max(1, colonyLevel);
        }

        public boolean hasIdentity() {
            return ColonyIdentityRules.parseUuid(colonyId).isPresent() && isValid();
        }

        public BlockPos center() {
            return BlockPos.fromLong(position);
        }

        public boolean contains(BlockPos target) {
            BlockPos center = center();
            return ColonyTerritoryRules.containsInitialClaim(
                    center.getX(), center.getZ(), target.getX(), target.getZ());
        }

        public boolean canModify(UUID actor) {
            if (actor == null || !isValid()) return false;
            String actorId = actor.toString();
            return ownerUuid.equals(actorId) || members.contains(actorId);
        }

        private boolean isValid() {
            return Identifier.tryParse(dimension) != null && parseUuid(ownerUuid) != null
                    && (colonyId.isBlank() || parseUuid(colonyId) != null)
                    && members.stream().allMatch(member -> parseUuid(member) != null);
        }

        private static UUID parseUuid(String raw) {
            try {
                return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }
}
