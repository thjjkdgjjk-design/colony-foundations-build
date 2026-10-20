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
            Codec.list(Codec.STRING).optionalFieldOf("members", List.of()).forGetter(ClaimRecord::members)
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

    public void upsert(RegistryKey<World> dimension, BlockPos position, UUID owner, Collection<UUID> members) {
        if (owner == null) {
            remove(dimension, position);
            return;
        }
        List<String> memberIds = members == null ? List.of() : members.stream()
                .filter(Objects::nonNull)
                .map(UUID::toString)
                .distinct()
                .sorted()
                .toList();
        ClaimRecord replacement = new ClaimRecord(
                dimension.getValue().toString(), position.asLong(), owner.toString(), memberIds);
        String key = recordKey(replacement.dimension(), replacement.position());
        ClaimRecord previous = claims.put(key, replacement);
        if (!replacement.equals(previous)) {
            rebuildIndex();
            markDirty();
        }
    }

    public void remove(RegistryKey<World> dimension, BlockPos position) {
        String key = recordKey(dimension.getValue().toString(), position.asLong());
        if (claims.remove(key) != null) {
            rebuildIndex();
            markDirty();
        }
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

    public record ClaimRecord(String dimension, long position, String ownerUuid, List<String> members) {
        public ClaimRecord {
            dimension = dimension == null ? "" : dimension;
            ownerUuid = ownerUuid == null ? "" : ownerUuid;
            members = members == null ? List.of() : members.stream()
                    .filter(Objects::nonNull).distinct().sorted().toList();
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
