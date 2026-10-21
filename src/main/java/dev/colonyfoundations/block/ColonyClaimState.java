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

    private static final Codec<WorkOrderMetadata> WORK_ORDER_METADATA_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("priority", 0).forGetter(WorkOrderMetadata::priority),
            Codec.LONG.optionalFieldOf("created_sequence", 0L).forGetter(WorkOrderMetadata::createdSequence),
            Codec.STRING.fieldOf("status").forGetter(WorkOrderMetadata::status),
            Codec.INT.fieldOf("progress").forGetter(WorkOrderMetadata::progress),
            Codec.INT.fieldOf("total_placements").forGetter(WorkOrderMetadata::totalPlacements),
            Codec.STRING.optionalFieldOf("issue", "").forGetter(WorkOrderMetadata::issue),
            Codec.STRING.optionalFieldOf("requested_by", "").forGetter(WorkOrderMetadata::requestedBy)
    ).apply(instance, WorkOrderMetadata::new));

    private static final Codec<WorkOrderRecord> WORK_ORDER_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("order_id").forGetter(WorkOrderRecord::orderId),
            Codec.STRING.fieldOf("colony_id").forGetter(WorkOrderRecord::colonyId),
            Codec.STRING.fieldOf("dimension").forGetter(WorkOrderRecord::dimension),
            Codec.LONG.fieldOf("town_hall_position").forGetter(WorkOrderRecord::townHallPosition),
            Codec.LONG.fieldOf("site_position").forGetter(WorkOrderRecord::sitePosition),
            Codec.STRING.fieldOf("blueprint_id").forGetter(WorkOrderRecord::blueprintId),
            Codec.STRING.fieldOf("type").forGetter(WorkOrderRecord::type),
            Codec.INT.fieldOf("current_level").forGetter(WorkOrderRecord::currentLevel),
            Codec.INT.fieldOf("target_level").forGetter(WorkOrderRecord::targetLevel),
            Codec.INT.fieldOf("builder_level").forGetter(WorkOrderRecord::builderLevel),
            Codec.INT.fieldOf("rotation").forGetter(WorkOrderRecord::rotation),
            Codec.INT.fieldOf("offset_x").forGetter(WorkOrderRecord::offsetX),
            Codec.INT.fieldOf("offset_z").forGetter(WorkOrderRecord::offsetZ),
            WORK_ORDER_METADATA_CODEC.fieldOf("metadata").forGetter(WorkOrderRecord::metadata)
    ).apply(instance, WorkOrderRecord::new));


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
                    .forGetter(ColonyClaimState::buildingSnapshot),
            Codec.unboundedMap(Codec.STRING, WORK_ORDER_CODEC)
                    .optionalFieldOf("work_orders", Map.<String, WorkOrderRecord>of())
                    .forGetter(ColonyClaimState::workOrderSnapshot)
    ).apply(instance, ColonyClaimState::new));

    private static final PersistentStateType<ColonyClaimState> TYPE =
            new PersistentStateType<>(STATE_ID, ColonyClaimState::new, CODEC, null);

    private final Map<String, ClaimRecord> claims = new LinkedHashMap<>();
    private final Map<String, Map<Long, List<ClaimRecord>>> claimsByDimensionChunk = new HashMap<>();
    /** Server-authoritative citizen assignments; keyed by the persisted entity UUID. */
    private final Map<String, CitizenAssignment> citizenAssignments = new LinkedHashMap<>();
    /** Durable building directory; it remains available when building chunks are unloaded. */
    private final Map<String, BuildingRecord> buildings = new LinkedHashMap<>();
    /** Persistent build/upgrade/repair/remove history, independent of the Town Hall chunk. */
    private final Map<String, WorkOrderRecord> workOrders = new LinkedHashMap<>();

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
        this(savedClaims, savedCitizens, savedBuildings, Map.of());
    }

    ColonyClaimState(Map<String, ClaimRecord> savedClaims, Map<String, CitizenAssignment> savedCitizens,
                     Map<String, BuildingRecord> savedBuildings, Map<String, WorkOrderRecord> savedWorkOrders) {
        if (savedClaims != null) claims.putAll(savedClaims);
        if (savedCitizens != null) citizenAssignments.putAll(savedCitizens);
        if (savedBuildings != null) buildings.putAll(savedBuildings);
        if (savedWorkOrders != null) workOrders.putAll(savedWorkOrders);
        rebuildIndex();
        buildings.entrySet().removeIf(entry -> !isBuildingRecordAnchored(entry.getValue()));
        workOrders.entrySet().removeIf(entry -> !isWorkOrderRecordAnchored(entry.getValue()));
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

    private Map<String, WorkOrderRecord> workOrderSnapshot() {
        return Map.copyOf(workOrders);
    }

    private boolean isWorkOrderRecordAnchored(WorkOrderRecord order) {
        if (order == null || !order.isValid()) return false;
        UUID colonyUuid = ColonyIdentityRules.parseUuid(order.colonyId()).orElse(null);
        if (colonyUuid == null) return false;
        ClaimRecord colony = findColony(colonyUuid).orElse(null);
        return colony != null && colony.dimension().equals(order.dimension())
                && colony.center().asLong() == order.townHallPosition()
                && colony.contains(BlockPos.fromLong(order.sitePosition()));
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
                workOrders.entrySet().removeIf(entry -> removed.colonyId().equals(entry.getValue().colonyId()));
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

    /**
     * Record or update the current order lifecycle. The order remains tied to one colony and
     * Town Hall; terminal orders cannot be reactivated and progress can never move backwards.
     */
    public Optional<WorkOrderRecord> upsertWorkOrder(WorkOrderRecord requested) {
        if (requested == null || !requested.isValid() || !isWorkOrderRecordAnchored(requested)) {
            return Optional.empty();
        }
        ColonyWorkOrderType type = ColonyWorkOrderType.parse(requested.type()).orElse(null);
        if (!ColonyWorkOrderRules.validLevelTransition(type, requested.currentLevel(),
                requested.targetLevel(), requested.builderLevel())) return Optional.empty();

        String key = requested.orderId();
        WorkOrderRecord existing = workOrders.get(key);
        if (existing != null) {
            if (!existing.colonyId().equals(requested.colonyId())
                    || !existing.dimension().equals(requested.dimension())
                    || existing.townHallPosition() != requested.townHallPosition()) return Optional.empty();
            ColonyWorkOrderStatus oldStatus = ColonyWorkOrderStatus.parse(existing.status()).orElse(null);
            ColonyWorkOrderStatus newStatus = ColonyWorkOrderStatus.parse(requested.status()).orElse(null);
            if (oldStatus == null || newStatus == null) return Optional.empty();
            if (oldStatus.isTerminal() && oldStatus != newStatus) return Optional.empty();
            if (!existing.blueprintId().equals(requested.blueprintId())
                    || existing.sitePosition() != requested.sitePosition()
                    || existing.rotation() != requested.rotation()
                    || existing.offsetX() != requested.offsetX()
                    || existing.offsetZ() != requested.offsetZ()) {
                boolean replaceableQueuedOrder = oldStatus == ColonyWorkOrderStatus.QUEUED
                        && newStatus == ColonyWorkOrderStatus.QUEUED
                        && existing.progress() == 0 && requested.progress() == 0;
                if (!replaceableQueuedOrder) return Optional.empty();
            }
            WorkOrderRecord replacement = requested.withSequence(existing.createdSequence())
                    .withProgress(Math.max(existing.progress(), requested.progress()),
                            Math.max(existing.totalPlacements(), requested.totalPlacements()));
            if (!replacement.equals(existing)) {
                workOrders.put(key, replacement);
                markDirty();
            }
            return Optional.of(replacement);
        }

        for (Map.Entry<String, WorkOrderRecord> entry : workOrders.entrySet()) {
            WorkOrderRecord competing = entry.getValue();
            if (!competing.isValid() || !competing.colonyId().equals(requested.colonyId())
                    || !competing.dimension().equals(requested.dimension())
                    || competing.sitePosition() != requested.sitePosition()
                    || ColonyWorkOrderStatus.parse(competing.status()).map(ColonyWorkOrderStatus::isTerminal).orElse(true)) continue;
            ColonyWorkOrderStatus competingStatus = ColonyWorkOrderStatus.parse(competing.status()).orElse(null);
            if (competing.progress() == 0 && (competingStatus == ColonyWorkOrderStatus.QUEUED
                    || competingStatus == ColonyWorkOrderStatus.BLOCKED)) {
                workOrders.put(entry.getKey(), competing.withStatus(ColonyWorkOrderStatus.CANCELLED.id(),
                        "Replaced by a newer queued order"));
            } else {
                return Optional.empty();
            }
        }

        long sequence = workOrders.values().stream().mapToLong(WorkOrderRecord::createdSequence).max().orElse(0L) + 1L;
        WorkOrderRecord replacement = requested.withSequence(sequence);
        workOrders.put(key, replacement);
        markDirty();
        return Optional.of(replacement);
    }

    public Optional<WorkOrderRecord> workOrder(UUID orderUuid) {
        if (orderUuid == null) return Optional.empty();
        return Optional.ofNullable(workOrders.get(orderUuid.toString()))
                .filter(WorkOrderRecord::isValid)
                .filter(this::isWorkOrderRecordAnchored);
    }

    public List<WorkOrderRecord> workOrdersForColony(UUID colonyUuid) {
        if (colonyUuid == null) return List.of();
        String id = colonyUuid.toString();
        return workOrders.values().stream()
                .filter(WorkOrderRecord::isValid)
                .filter(this::isWorkOrderRecordAnchored)
                .filter(order -> id.equals(order.colonyId()))
                .sorted(java.util.Comparator.comparingInt(WorkOrderRecord::priority).reversed()
                        .thenComparingLong(WorkOrderRecord::createdSequence))
                .toList();
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

    public record WorkOrderMetadata(int priority, long createdSequence, String status,
                                    int progress, int totalPlacements, String issue, String requestedBy) {
        public WorkOrderMetadata {
            priority = Math.max(-10, Math.min(10, priority));
            createdSequence = Math.max(0L, createdSequence);
            status = status == null ? "" : status;
            progress = Math.max(0, progress);
            totalPlacements = Math.max(progress, Math.max(0, totalPlacements));
            issue = issue == null ? "" : issue;
            requestedBy = requestedBy == null ? "" : requestedBy;
            issue = issue.length() > 512 ? issue.substring(0, 512) : issue;
        }
    }

    public record WorkOrderRecord(String orderId, String colonyId, String dimension,
                                  long townHallPosition, long sitePosition, String blueprintId,
                                  String type, int currentLevel, int targetLevel, int builderLevel,
                                  int rotation, int offsetX, int offsetZ, WorkOrderMetadata metadata) {
        public WorkOrderRecord {
            orderId = orderId == null ? "" : orderId;
            colonyId = colonyId == null ? "" : colonyId;
            dimension = dimension == null ? "" : dimension;
            blueprintId = blueprintId == null ? "" : blueprintId;
            type = type == null ? "" : type;
            rotation = Math.floorMod(rotation, 4);
            offsetX = Math.max(-64, Math.min(64, offsetX));
            offsetZ = Math.max(-64, Math.min(64, offsetZ));
            builderLevel = Math.max(1, Math.min(5, builderLevel));
            metadata = metadata == null
                    ? new WorkOrderMetadata(0, 0L, ColonyWorkOrderStatus.QUEUED.id(), 0, 0, "", "")
                    : metadata;
        }

        /** Stable convenience constructor used by order producers and tests. */
        public WorkOrderRecord(String orderId, String colonyId, String dimension,
                               long townHallPosition, long sitePosition, String blueprintId,
                               String type, int currentLevel, int targetLevel, int builderLevel,
                               int rotation, int offsetX, int offsetZ, int priority,
                               long createdSequence, String status, int progress,
                               int totalPlacements, String issue, String requestedBy) {
            this(orderId, colonyId, dimension, townHallPosition, sitePosition, blueprintId,
                    type, currentLevel, targetLevel, builderLevel, rotation, offsetX, offsetZ,
                    new WorkOrderMetadata(priority, createdSequence, status, progress,
                            totalPlacements, issue, requestedBy));
        }

        public int priority() { return metadata.priority(); }
        public long createdSequence() { return metadata.createdSequence(); }
        public String status() { return metadata.status(); }
        public int progress() { return metadata.progress(); }
        public int totalPlacements() { return metadata.totalPlacements(); }
        public String issue() { return metadata.issue(); }
        public String requestedBy() { return metadata.requestedBy(); }

        public boolean isValid() {
            return validUuid(orderId) && validUuid(colonyId)
                    && Identifier.tryParse(dimension) != null
                    && Identifier.tryParse(blueprintId) != null
                    && ColonyWorkOrderType.parse(type).isPresent()
                    && ColonyWorkOrderStatus.parse(status()).isPresent()
                    && currentLevel >= 0 && currentLevel <= 5
                    && targetLevel >= 0 && targetLevel <= 5
                    && builderLevel >= 1 && builderLevel <= 5
                    && progress() <= totalPlacements()
                    && (requestedBy().isBlank() || validUuid(requestedBy()));
        }

        public boolean isTerminal() {
            return ColonyWorkOrderStatus.parse(status()).map(ColonyWorkOrderStatus::isTerminal).orElse(false);
        }

        public WorkOrderRecord withSequence(long sequence) {
            return new WorkOrderRecord(orderId, colonyId, dimension, townHallPosition, sitePosition,
                    blueprintId, type, currentLevel, targetLevel, builderLevel, rotation, offsetX, offsetZ,
                    priority(), sequence, status(), progress(), totalPlacements(), issue(), requestedBy());
        }

        public WorkOrderRecord withProgress(int newProgress, int newTotal) {
            return new WorkOrderRecord(orderId, colonyId, dimension, townHallPosition, sitePosition,
                    blueprintId, type, currentLevel, targetLevel, builderLevel, rotation, offsetX, offsetZ,
                    priority(), createdSequence(), status(), newProgress, newTotal, issue(), requestedBy());
        }

        public WorkOrderRecord withStatus(String newStatus, String newIssue) {
            return new WorkOrderRecord(orderId, colonyId, dimension, townHallPosition, sitePosition,
                    blueprintId, type, currentLevel, targetLevel, builderLevel, rotation, offsetX, offsetZ,
                    priority(), createdSequence(), newStatus, progress(), totalPlacements(), newIssue, requestedBy());
        }

        private static boolean validUuid(String value) {
            try {
                return value != null && !value.isBlank() && UUID.fromString(value) != null;
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
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
