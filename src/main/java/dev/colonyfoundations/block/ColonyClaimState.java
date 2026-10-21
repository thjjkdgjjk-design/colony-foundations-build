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
import java.util.Set;
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
            Codec.INT.optionalFieldOf("colony_level", 1).forGetter(ClaimRecord::colonyLevel),
            Codec.unboundedMap(Codec.STRING, Codec.list(Codec.STRING))
                    .optionalFieldOf("member_permissions", Map.of())
                    .forGetter(ClaimRecord::memberPermissions)
    ).apply(instance, ClaimRecord::new));

    private static final Codec<BuildingRecord> BUILDING_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("colony_id").forGetter(BuildingRecord::colonyId),
            Codec.STRING.fieldOf("dimension").forGetter(BuildingRecord::dimension),
            Codec.LONG.fieldOf("position").forGetter(BuildingRecord::position),
            Codec.STRING.fieldOf("building_id").forGetter(BuildingRecord::buildingId),
            Codec.INT.optionalFieldOf("level", 1).forGetter(BuildingRecord::level)
    ).apply(instance, BuildingRecord::new));

    private static final Codec<CitizenAssignment> CITIZEN_ASSIGNMENT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("citizen_uuid").forGetter(CitizenAssignment::citizenUuid),
            Codec.STRING.fieldOf("colony_id").forGetter(CitizenAssignment::colonyId),
            Codec.STRING.fieldOf("dimension").forGetter(CitizenAssignment::dimension),
            Codec.LONG.fieldOf("building_position").forGetter(CitizenAssignment::buildingPosition),
            Codec.STRING.fieldOf("role_id").forGetter(CitizenAssignment::roleId),
            Codec.STRING.fieldOf("job_id").forGetter(CitizenAssignment::jobId),
            Codec.LONG.optionalFieldOf("last_known_position", 0L).forGetter(CitizenAssignment::lastKnownPosition),
            Codec.STRING.optionalFieldOf("status", "active").forGetter(CitizenAssignment::status)
    ).apply(instance, CitizenAssignment::new));

    /** Optional "citizens" keeps existing PersistentState saves readable. */
    private static final Codec<ColonyClaimState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, CLAIM_CODEC)
                    .optionalFieldOf("claims", Map.<String, ClaimRecord>of())
                    .forGetter(ColonyClaimState::claimSnapshot),
            Codec.unboundedMap(Codec.STRING, CITIZEN_ASSIGNMENT_CODEC)
                    .optionalFieldOf("citizens", Map.<String, CitizenAssignment>of())
                    .forGetter(ColonyClaimState::citizenSnapshot),
            Codec.unboundedMap(Codec.STRING, BUILDING_CODEC)
                    .optionalFieldOf("buildings", Map.<String, BuildingRecord>of())
                    .forGetter(ColonyClaimState::buildingSnapshot)
    ).apply(instance, ColonyClaimState::new));

    private static final PersistentStateType<ColonyClaimState> TYPE =
            new PersistentStateType<>(STATE_ID, ColonyClaimState::new, CODEC, null);

    private final Map<String, ClaimRecord> claims = new LinkedHashMap<>();
    private final Map<String, Map<Long, List<ClaimRecord>>> claimsByDimensionChunk = new HashMap<>();
    /** Server-authoritative citizen assignments; keyed by the persisted entity UUID. */
    private final Map<String, CitizenAssignment> citizenAssignments = new LinkedHashMap<>();
    /** Durable building directory; it remains available when building chunks are unloaded. */
    private final Map<String, BuildingRecord> buildings = new LinkedHashMap<>();

    public ColonyClaimState() {
    }

    /** Backwards-compatible constructor for older tests/data-loading callers. */
    ColonyClaimState(Map<String, ClaimRecord> savedClaims) {
        this(savedClaims, Map.of());
    }

    ColonyClaimState(Map<String, ClaimRecord> savedClaims, Map<String, CitizenAssignment> savedCitizens) {
        this(savedClaims, savedCitizens, Map.of());
    }

    ColonyClaimState(Map<String, ClaimRecord> savedClaims, Map<String, CitizenAssignment> savedCitizens,
                     Map<String, BuildingRecord> savedBuildings) {
        if (savedClaims != null) claims.putAll(savedClaims);
        if (savedCitizens != null) citizenAssignments.putAll(savedCitizens);
        if (savedBuildings != null) buildings.putAll(savedBuildings);
        rebuildIndex();
        buildings.entrySet().removeIf(entry -> !isBuildingRecordAnchored(entry.getValue()));
    }

    private Map<String, ClaimRecord> claimSnapshot() {
        return Map.copyOf(claims);
    }

    private Map<String, CitizenAssignment> citizenSnapshot() {
        return Map.copyOf(citizenAssignments);
    }

    private Map<String, BuildingRecord> buildingSnapshot() {
        return Map.copyOf(buildings);
    }

    private boolean isBuildingRecordAnchored(BuildingRecord building) {
        if (building == null || !building.isValid()) return false;
        UUID id = ColonyIdentityRules.parseUuid(building.colonyId()).orElse(null);
        if (id == null) return false;
        ClaimRecord colony = findColony(id).orElse(null);
        return colony != null && colony.dimension().equals(building.dimension())
                && colony.contains(building.center());
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
        String dimensionId = dimension.getValue().toString();
        ClaimRecord previous = claims.get(recordKey(dimensionId, position.asLong()));
        return upsert(dimension, position, owner, members, requestedColonyId,
                colonyName, ownerName, colonyLevel,
                previous == null ? Map.of() : previous.memberPermissions());
    }

    public UUID upsert(RegistryKey<World> dimension, BlockPos position, UUID owner,
                       Collection<UUID> members, UUID requestedColonyId,
                       String colonyName, String ownerName, int colonyLevel,
                       Map<String, List<String>> memberPermissions) {
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
                resolvedId.toString(), colonyName, ownerName, colonyLevel, memberPermissions);
        if (!replacement.equals(previous)) {
            claims.put(key, replacement);
            rebuildIndex();
            markDirty();
        }
        return resolvedId;
    }

    public void remove(RegistryKey<World> dimension, BlockPos position) {
        String key = recordKey(dimension.getValue().toString(), position.asLong());
        ClaimRecord removed = claims.remove(key);
        if (removed != null) {
            if (removed.hasIdentity()) {
                citizenAssignments.replaceAll((citizen, assignment) ->
                        assignment.isActive() && removed.colonyId().equals(assignment.colonyId())
                                ? assignment.withStatus("released") : assignment);
                buildings.entrySet().removeIf(entry -> removed.colonyId().equals(entry.getValue().colonyId()));
            }
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

    /**
     * Find the unique identity owning a position. Ambiguous overlaps fail closed, which prevents
     * assigning a worker to a colony chosen by iteration order.
     */
    public Optional<ClaimRecord> findColonyAt(String dimension, BlockPos target) {
        if (dimension == null || target == null) return Optional.empty();
        List<ClaimRecord> matches = claims.values().stream()
                .filter(ClaimRecord::hasIdentity)
                .filter(claim -> dimension.equals(claim.dimension()) && claim.contains(target))
                .toList();
        java.util.Set<String> ids = matches.stream().map(ClaimRecord::colonyId)
                .collect(java.util.stream.Collectors.toSet());
        if (ids.size() != 1) return Optional.empty();
        String onlyId = ids.iterator().next();
        return matches.stream().filter(claim -> onlyId.equals(claim.colonyId())).findFirst();
    }

    /**
     * Register a building only when it belongs to one unambiguous, founded colony.
     * The persistent index is also the canonical source for validating worker/job compatibility.
     */
    public boolean registerBuilding(UUID colonyUuid, RegistryKey<World> dimension, BlockPos position,
                                    String buildingId, int level) {
        if (colonyUuid == null || dimension == null || position == null
                || Identifier.tryParse(buildingId) == null) return false;
        String dimensionId = dimension.getValue().toString();
        ClaimRecord colony = findColony(colonyUuid).orElse(null);
        if (colony == null || !dimensionId.equals(colony.dimension())
                || !colony.contains(position)
                || findColonyAt(dimensionId, position)
                    .filter(record -> colonyUuid.toString().equals(record.colonyId())).isEmpty()) {
            return false;
        }

        BuildingRecord replacement = new BuildingRecord(colonyUuid.toString(), dimensionId,
                position.asLong(), buildingId, level);
        if (!replacement.isValid()) return false;
        String key = recordKey(dimensionId, position.asLong());
        BuildingRecord previous = buildings.get(key);
        if (replacement.equals(previous)) return true;
        if (previous != null && (!previous.colonyId().equals(replacement.colonyId())
                || !previous.buildingId().equals(replacement.buildingId()))) {
            releaseAssignmentsAt(dimensionId, position.asLong());
        }
        buildings.put(key, replacement);
        markDirty();
        return true;
    }

    /** Register all known hut types as typed building records, not just generic block entities. */
    public Optional<BuildingRecord> findBuilding(String dimension, BlockPos position) {
        if (dimension == null || position == null) return Optional.empty();
        BuildingRecord building = buildings.get(recordKey(dimension, position.asLong()));
        return Optional.ofNullable(building)
                .filter(BuildingRecord::isValid)
                .filter(this::isBuildingRecordAnchored);
    }

    public List<BuildingRecord> buildingsForColony(UUID colonyUuid) {
        if (colonyUuid == null) return List.of();
        String id = colonyUuid.toString();
        return buildings.values().stream()
                .filter(BuildingRecord::isValid)
                .filter(this::isBuildingRecordAnchored)
                .filter(building -> id.equals(building.colonyId()))
                .sorted(java.util.Comparator.comparing(BuildingRecord::buildingId)
                        .thenComparing(BuildingRecord::dimension)
                        .thenComparingLong(BuildingRecord::position))
                .toList();
    }

    /**
     * Remove a demolished building and release only assignments physically bound to that position.
     * If the worker is unloaded the released record remains as a tombstone for its next entity load.
     */
    public boolean unregisterBuilding(RegistryKey<World> dimension, BlockPos position) {
        if (dimension == null || position == null) return false;
        String dimensionId = dimension.getValue().toString();
        BuildingRecord removed = buildings.remove(recordKey(dimensionId, position.asLong()));
        if (removed == null) return false;
        releaseAssignmentsAt(dimensionId, position.asLong());
        markDirty();
        return true;
    }

    private void releaseAssignmentsAt(String dimension, long packedPosition) {
        boolean changed = false;
        for (Map.Entry<String, CitizenAssignment> entry : citizenAssignments.entrySet()) {
            CitizenAssignment assignment = entry.getValue();
            if (assignment.isActive() && assignment.matchesBuilding(dimension, packedPosition)) {
                entry.setValue(assignment.withStatus("released"));
                changed = true;
            }
        }
        if (changed) markDirty();
    }

    public Optional<CitizenAssignment> citizenAssignment(UUID citizenUuid) {
        if (citizenUuid == null) return Optional.empty();
        return Optional.ofNullable(citizenAssignments.get(citizenUuid.toString()))
                .filter(CitizenAssignment::isValid);
    }

    public boolean hasActiveCitizenAssignment(UUID citizenUuid) {
        return citizenAssignment(citizenUuid).filter(CitizenAssignment::isActive).isPresent();
    }

    /**
     * Register or refresh a citizen's single authoritative job assignment.
     * Idempotent updates to the same building/job are accepted; a different active
     * assignment must be explicitly released first.
     */
    public boolean registerCitizenAssignment(UUID citizenUuid, UUID colonyUuid,
                                             RegistryKey<World> dimension, BlockPos buildingPosition,
                                             String roleId, String jobId, BlockPos lastKnownPosition) {
        if (citizenUuid == null || colonyUuid == null || dimension == null
                || buildingPosition == null || lastKnownPosition == null) return false;

        String dimensionId = dimension.getValue().toString();
        Optional<ClaimRecord> colony = findColony(colonyUuid);
        if (colony.isEmpty()
                || !dimensionId.equals(colony.get().dimension())
                || !colony.get().contains(buildingPosition)
                || findColonyAt(dimensionId, buildingPosition)
                    .filter(record -> colonyUuid.toString().equals(record.colonyId())).isEmpty()) {
            return false;
        }

        BuildingRecord building = findBuilding(dimensionId, buildingPosition).orElse(null);
        if (building == null || !colonyUuid.toString().equals(building.colonyId())
                || !building.acceptsAssignment(roleId, jobId)) {
            return false;
        }

        CitizenAssignment requested = new CitizenAssignment(
                citizenUuid.toString(), colonyUuid.toString(), dimensionId, buildingPosition.asLong(),
                roleId, jobId, lastKnownPosition.asLong(), "active");
        if (!requested.isValid()) return false;

        String citizenKey = citizenUuid.toString();
        CitizenAssignment existing = citizenAssignments.get(citizenKey);
        if (!mayReplaceCitizenAssignment(existing, requested)) return false;
        if (existing != null && existing.isActive() && existing.sameAssignment(requested)) {
            if (existing.lastKnownPosition() != requested.lastKnownPosition()) {
                citizenAssignments.put(citizenKey, requested);
                markDirty();
            }
            return true;
        }

        citizenAssignments.put(citizenKey, requested);
        markDirty();
        return true;
    }

    /** Release only if the caller still owns this building assignment; keep a tombstone for audit/recovery. */
    public boolean releaseCitizenAssignment(UUID citizenUuid, RegistryKey<World> dimension, BlockPos buildingPosition) {
        if (citizenUuid == null || dimension == null || buildingPosition == null) return false;
        String key = citizenUuid.toString();
        CitizenAssignment existing = citizenAssignments.get(key);
        if (existing == null || !existing.isActive()
                || !existing.matchesBuilding(dimension.getValue().toString(), buildingPosition.asLong())) {
            return false;
        }
        citizenAssignments.put(key, existing.withStatus("released"));
        markDirty();
        return true;
    }

    static boolean mayReplaceCitizenAssignment(CitizenAssignment existing, CitizenAssignment requested) {
        if (requested == null || !requested.isValid()) return false;
        return existing == null || !existing.isValid() || !existing.isActive()
                || existing.sameAssignment(requested);
    }

    /** Remove a released tombstone only after the corresponding entity has reconciled its tags. */
    public boolean forgetReleasedCitizenAssignment(UUID citizenUuid) {
        if (citizenUuid == null) return false;
        String key = citizenUuid.toString();
        CitizenAssignment existing = citizenAssignments.get(key);
        if (existing == null || existing.isActive()) return false;
        citizenAssignments.remove(key);
        markDirty();
        return true;
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

    public record BuildingRecord(String colonyId, String dimension, long position,
                                 String buildingId, int level) {
        public BuildingRecord {
            colonyId = colonyId == null ? "" : colonyId;
            dimension = dimension == null ? "" : dimension;
            buildingId = buildingId == null ? "" : buildingId;
            level = Math.max(1, Math.min(5, level));
        }

        public boolean isValid() {
            return validUuid(colonyId) && Identifier.tryParse(dimension) != null
                    && Identifier.tryParse(buildingId) != null && level >= 1 && level <= 5;
        }

        public BlockPos center() {
            return BlockPos.fromLong(position);
        }

        public boolean acceptsAssignment(String roleId, String jobId) {
            if (roleId == null || jobId == null) return false;
            if ("colony_foundations:builder".equals(roleId)) {
                return "colony_foundations:builder".equals(jobId)
                        && "colony_foundations:town_hall".equals(buildingId);
            }
            return "colony_foundations:worker".equals(roleId) && buildingId.equals(jobId);
        }

        private static boolean validUuid(String value) {
            try {
                return value != null && !value.isBlank() && UUID.fromString(value) != null;
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
    }

    public record CitizenAssignment(String citizenUuid, String colonyId, String dimension,
                                    long buildingPosition, String roleId, String jobId,
                                    long lastKnownPosition, String status) {
        public CitizenAssignment {
            citizenUuid = citizenUuid == null ? "" : citizenUuid;
            colonyId = colonyId == null ? "" : colonyId;
            dimension = dimension == null ? "" : dimension;
            roleId = roleId == null ? "" : roleId;
            jobId = jobId == null ? "" : jobId;
            status = status == null || status.isBlank() ? "active" : status;
        }

        public boolean isValid() {
            return validUuid(citizenUuid) && validUuid(colonyId)
                    && Identifier.tryParse(dimension) != null
                    && Identifier.tryParse(roleId) != null
                    && Identifier.tryParse(jobId) != null
                    && ("active".equals(status) || "released".equals(status));
        }

        public boolean isActive() {
            return isValid() && "active".equals(status);
        }

        public boolean sameAssignment(CitizenAssignment other) {
            return other != null
                    && citizenUuid.equals(other.citizenUuid)
                    && colonyId.equals(other.colonyId)
                    && dimension.equals(other.dimension)
                    && buildingPosition == other.buildingPosition
                    && roleId.equals(other.roleId)
                    && jobId.equals(other.jobId);
        }

        public boolean matchesBuilding(String dimensionId, long position) {
            return dimension.equals(dimensionId) && buildingPosition == position;
        }

        public CitizenAssignment withStatus(String newStatus) {
            return new CitizenAssignment(citizenUuid, colonyId, dimension, buildingPosition,
                    roleId, jobId, lastKnownPosition, newStatus);
        }

        private static boolean validUuid(String value) {
            try {
                return value != null && !value.isBlank() && UUID.fromString(value) != null;
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
    }

    public record ClaimRecord(String dimension, long position, String ownerUuid, List<String> members,
                              String colonyId, String colonyName, String ownerName, int colonyLevel,
                              Map<String, List<String>> memberPermissions) {
        public ClaimRecord {
            dimension = dimension == null ? "" : dimension;
            ownerUuid = ownerUuid == null ? "" : ownerUuid;
            members = members == null ? List.of() : members.stream()
                    .filter(Objects::nonNull).distinct().sorted().toList();
            colonyId = colonyId == null ? "" : colonyId;
            colonyName = colonyName == null ? "" : colonyName;
            ownerName = ownerName == null ? "" : ownerName;
            colonyLevel = Math.max(1, colonyLevel);
            Map<String, List<String>> normalizedPermissions = new LinkedHashMap<>();
            Set<String> validMembers = Set.copyOf(members);
            if (memberPermissions != null) {
                memberPermissions.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    String member = entry.getKey();
                    if (member == null || !validMembers.contains(member)
                            || ColonyIdentityRules.parseUuid(member).isEmpty()) return;
                    List<String> grants = entry.getValue() == null ? List.of()
                            : entry.getValue().stream()
                                    .filter(permission -> ColonyPermission.parse(permission).isPresent())
                                    .distinct().sorted().toList();
                    if (!grants.isEmpty()) normalizedPermissions.put(member, grants);
                });
            }
            memberPermissions = Map.copyOf(normalizedPermissions);
        }

        /** Preserve older record signatures as permissions migrate to the explicit action model. */
        public ClaimRecord(String dimension, long position, String ownerUuid, List<String> members) {
            this(dimension, position, ownerUuid, members, "", "", "", 1, Map.of());
        }

        public ClaimRecord(String dimension, long position, String ownerUuid, List<String> members,
                           String colonyId, String colonyName, String ownerName, int colonyLevel) {
            this(dimension, position, ownerUuid, members, colonyId, colonyName, ownerName, colonyLevel, Map.of());
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

        /** Owner bypasses action grants; members only receive explicitly persisted actions. */
        public boolean canPerform(UUID actor, ColonyPermission permission) {
            if (actor == null || permission == null || !isValid()) return false;
            String actorId = actor.toString();
            if (ownerUuid.equals(actorId)) return true;
            return members.contains(actorId)
                    && memberPermissions.getOrDefault(actorId, List.of()).contains(permission.id());
        }

        /** Compatibility query: true only for an owner or a member with at least one grant. */
        public boolean canModify(UUID actor) {
            if (actor == null || !isValid()) return false;
            if (ownerUuid.equals(actor.toString())) return true;
            return java.util.Arrays.stream(ColonyPermission.values()).anyMatch(permission -> canPerform(actor, permission));
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
