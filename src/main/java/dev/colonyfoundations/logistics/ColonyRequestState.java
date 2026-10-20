package dev.colonyfoundations.logistics;

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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable, colony-scoped item request queue saved in the world's persistent state.
 * It owns request state only; courier movement and physical item transfer are integrated
 * by the next logistics stage.
 */
public final class ColonyRequestState extends PersistentState {
    private static final String STATE_ID = "colony_foundations_requests";
    public static final int MAX_ACTIVE_REQUESTS = 4_096;
    public static final int MAX_REQUEST_AMOUNT = 4_096;

    private static final Codec<RequestRecord> REQUEST_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("request_id").forGetter(RequestRecord::requestId),
            Codec.STRING.fieldOf("colony_owner").forGetter(RequestRecord::colonyOwnerUuid),
            Codec.STRING.fieldOf("dimension").forGetter(RequestRecord::dimension),
            Codec.LONG.fieldOf("target_position").forGetter(RequestRecord::targetPosition),
            Codec.STRING.fieldOf("item_id").forGetter(RequestRecord::itemId),
            Codec.INT.fieldOf("requested").forGetter(RequestRecord::requestedCount),
            Codec.INT.fieldOf("delivered").forGetter(RequestRecord::deliveredCount),
            Codec.INT.fieldOf("priority").forGetter(RequestRecord::priority),
            Codec.STRING.fieldOf("status").forGetter(RequestRecord::status),
            Codec.STRING.optionalFieldOf("courier_uuid", "").forGetter(RequestRecord::courierUuid),
            Codec.LONG.fieldOf("created_at").forGetter(RequestRecord::createdAt),
            Codec.LONG.fieldOf("updated_at").forGetter(RequestRecord::updatedAt),
            Codec.STRING.optionalFieldOf("transfer_id", "").forGetter(RequestRecord::transferId),
            Codec.STRING.optionalFieldOf("last_transfer_id", "").forGetter(RequestRecord::lastTransferId),
            Codec.LONG.optionalFieldOf("courier_warehouse_position", 0L)
                    .forGetter(RequestRecord::courierWarehousePosition)
    ).apply(instance, RequestRecord::new));

    private static final Codec<ColonyRequestState> CODEC = Codec.unboundedMap(Codec.STRING, REQUEST_CODEC)
            .fieldOf("requests").codec()
            .xmap(ColonyRequestState::new, ColonyRequestState::snapshot);
    private static final PersistentStateType<ColonyRequestState> TYPE =
            new PersistentStateType<>(STATE_ID, ColonyRequestState::new, CODEC, null);

    private final Map<String, RequestRecord> requests = new LinkedHashMap<>();

    public ColonyRequestState() { }

    ColonyRequestState(Map<String, RequestRecord> savedRequests) {
        boolean repaired = false;
        if (savedRequests != null) {
            for (Map.Entry<String, RequestRecord> entry : savedRequests.entrySet()) {
                RequestRecord record = entry.getValue();
                if (record == null || !entry.getKey().equals(record.requestId()) || !record.isValid()) {
                    repaired = true;
                    continue;
                }
                RequestRecord recovered = record.reconcileAfterLoad();
                requests.put(entry.getKey(), recovered);
                repaired |= !recovered.equals(record);
            }
        }
        // Persist repairs so impossible states are not rediscovered on every restart.
        if (repaired) markDirty();
    }

    public static ColonyRequestState get(ServerWorld world) {
        return world.getServer().getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    public Optional<RequestRecord> getRequest(String requestId) {
        return Optional.ofNullable(requests.get(requestId));
    }

    public List<RequestRecord> openRequests(UUID colonyOwner, String dimension) {
        if (colonyOwner == null || dimension == null) return List.of();
        return requests.values().stream()
                .filter(request -> request.colonyOwnerUuid().equals(colonyOwner.toString()))
                .filter(request -> request.dimension().equals(dimension))
                .filter(request -> request.status().equals(ColonyRequestRules.Status.OPEN.id()))
                .sorted(Comparator.comparingInt(RequestRecord::priority).reversed()
                        .thenComparingLong(RequestRecord::createdAt)
                        .thenComparing(RequestRecord::requestId))
                .toList();
    }

    public List<RequestRecord> requestsFor(UUID colonyOwner) {
        if (colonyOwner == null) return List.of();
        return requests.values().stream()
                .filter(request -> request.colonyOwnerUuid().equals(colonyOwner.toString()))
                .sorted(Comparator.comparingLong(RequestRecord::createdAt)
                        .thenComparing(RequestRecord::requestId))
                .toList();
    }

    public List<RequestRecord> requestsForCourier(UUID colonyOwner, String dimension, UUID courier) {
        if (colonyOwner == null || dimension == null || courier == null) return List.of();
        String ownerId = colonyOwner.toString();
        String courierId = courier.toString();
        return requests.values().stream()
                .filter(request -> request.colonyOwnerUuid().equals(ownerId))
                .filter(request -> request.dimension().equals(dimension))
                .filter(request -> request.courierUuid().equals(courierId))
                .filter(request -> request.status().equals(ColonyRequestRules.Status.RESERVED.id())
                        || request.status().equals(ColonyRequestRules.Status.IN_TRANSIT.id()))
                .sorted(Comparator.comparingInt(RequestRecord::priority).reversed()
                        .thenComparingLong(RequestRecord::updatedAt)
                        .thenComparing(RequestRecord::requestId))
                .toList();
    }

    /**
     * Coalesces requests for the same item/target/colony while it is active, bounded to
     * MAX_REQUEST_AMOUNT. Returns the durable request ID or empty when input/capacity fails.
     */
    public Optional<String> createOrMerge(UUID colonyOwner, RegistryKey<World> dimension,
                                          BlockPos target, Identifier itemId,
                                          int amount, int priority, long now) {
        if (colonyOwner == null || dimension == null || target == null || itemId == null
                || amount < 1 || amount > MAX_REQUEST_AMOUNT || priority < 0 || priority > 10) {
            return Optional.empty();
        }

        String dimensionId = dimension.getValue().toString();
        String ownerId = colonyOwner.toString();
        for (RequestRecord old : new ArrayList<>(requests.values())) {
            if (!old.colonyOwnerUuid().equals(ownerId) || !old.dimension().equals(dimensionId)
                    || old.targetPosition() != target.asLong() || !old.itemId().equals(itemId.toString())
                    || old.status().equals(ColonyRequestRules.Status.DELIVERED.id())
                    || old.status().equals(ColonyRequestRules.Status.CANCELLED.id())) continue;
            ColonyRequestRules.Snapshot merged = ColonyRequestRules.merge(
                    old.snapshot(), amount, priority, MAX_REQUEST_AMOUNT);
            RequestRecord replacement = old.withSnapshot(merged, now);
            requests.put(old.requestId(), replacement);
            markDirty();
            return Optional.of(old.requestId());
        }

        long activeCount = requests.values().stream().filter(RequestRecord::isActive).count();
        if (activeCount >= MAX_ACTIVE_REQUESTS) return Optional.empty();
        String id = UUID.randomUUID().toString();
        RequestRecord request = new RequestRecord(id, ownerId, dimensionId, target.asLong(), itemId.toString(),
                amount, 0, priority, ColonyRequestRules.Status.OPEN.id(), "", now, now, "", "", 0L);
        requests.put(id, request);
        markDirty();
        return Optional.of(id);
    }

    public Optional<RequestRecord> reserve(String requestId, UUID courier, long now) {
        return reserve(requestId, courier, "", null, now);
    }

    public Optional<RequestRecord> reserve(String requestId, UUID courier, BlockPos warehouse, long now) {
        return reserve(requestId, courier, "", warehouse, now);
    }

    public Optional<RequestRecord> reserve(String requestId, UUID courier, String preferredTransferId,
                                           BlockPos warehouse, long now) {
        RequestRecord current = requests.get(requestId);
        if (current == null || courier == null) return Optional.empty();
        Optional<ColonyRequestRules.Snapshot> result = ColonyRequestRules.reserve(current.snapshot(), courier);
        if (result.isEmpty()) return Optional.empty();
        RequestRecord updated = current.withSnapshot(result.get(), now);
        String token = isUuid(preferredTransferId) ? preferredTransferId : updated.transferId();
        if (!isUuid(token)) token = UUID.randomUUID().toString();
        long warehousePosition = warehouse == null ? updated.courierWarehousePosition() : warehouse.asLong();
        updated = updated.withCourierTrip(token, updated.lastTransferId(), warehousePosition, now);
        requests.put(requestId, updated);
        markDirty();
        return Optional.of(updated);
    }

    public Optional<RequestRecord> bindCourierTrip(String requestId, UUID courier, String preferredTransferId,
                                                   BlockPos warehouse, long now) {
        RequestRecord current = requests.get(requestId);
        if (current == null || courier == null
                || !current.courierUuid().equals(courier.toString())
                || (!current.status().equals(ColonyRequestRules.Status.RESERVED.id())
                    && !current.status().equals(ColonyRequestRules.Status.IN_TRANSIT.id()))) return Optional.empty();
        String token = isUuid(current.transferId()) ? current.transferId() : "";
        if (isUuid(preferredTransferId)) {
            if (!token.isBlank() && !token.equals(preferredTransferId)) return Optional.empty();
            token = preferredTransferId;
        }
        if (token.isBlank()) token = UUID.randomUUID().toString();
        long warehousePosition = current.courierWarehousePosition();
        if (warehouse != null) {
            if (warehousePosition != 0L && warehousePosition != warehouse.asLong()) return Optional.empty();
            warehousePosition = warehouse.asLong();
        }
        RequestRecord updated = current.withCourierTrip(token, current.lastTransferId(), warehousePosition, now);
        if (!updated.equals(current)) {
            requests.put(requestId, updated);
            markDirty();
        }
        return Optional.of(updated);
    }

    public Optional<RequestRecord> beginDelivery(String requestId, UUID courier, long now) {
        return transition(requestId, now, current -> ColonyRequestRules.beginDelivery(current, courier));
    }
    public Optional<RequestRecord> beginDelivery(String requestId, UUID courier, String transferId, long now) {
        if (!ownsTransfer(requestId, courier, transferId)) return Optional.empty();
        return transition(requestId, now, current -> ColonyRequestRules.beginDelivery(current, courier));
    }
    public Optional<RequestRecord> release(String requestId, UUID courier, long now) {
        return transition(requestId, now, current -> ColonyRequestRules.release(current, courier));
    }
    public Optional<RequestRecord> release(String requestId, UUID courier, String transferId, long now) {
        if (!ownsTransfer(requestId, courier, transferId)) return Optional.empty();
        return transition(requestId, now, current -> ColonyRequestRules.release(current, courier));
    }
    public Optional<RequestRecord> recordDelivery(String requestId, UUID courier, int amount, long now) {
        return transition(requestId, now, current -> ColonyRequestRules.deliver(current, courier, amount));
    }
    public Optional<RequestRecord> recordDelivery(String requestId, UUID courier, String transferId,
                                                  int amount, long now) {
        if (!ownsTransfer(requestId, courier, transferId)) return Optional.empty();
        return transition(requestId, now, current -> ColonyRequestRules.deliver(current, courier, amount));
    }

    public Optional<RequestRecord> beginDelivery(String requestId, UUID courier, long now) {
        return transition(requestId, now, current -> ColonyRequestRules.beginDelivery(current, courier));
    }

    public Optional<RequestRecord> release(String requestId, UUID courier, long now) {
        return transition(requestId, now, current -> ColonyRequestRules.release(current, courier));
    }

    public Optional<RequestRecord> recordDelivery(String requestId, UUID courier, int amount, long now) {
        return transition(requestId, now, current -> ColonyRequestRules.deliver(current, courier, amount));
    }

    public Optional<RequestRecord> cancel(String requestId, UUID colonyOwner, long now) {
        RequestRecord current = requests.get(requestId);
        if (current == null || colonyOwner == null
                || !current.colonyOwnerUuid().equals(colonyOwner.toString())) return Optional.empty();
        Optional<ColonyRequestRules.Snapshot> result = ColonyRequestRules.cancel(current.snapshot());
        if (result.isEmpty()) return Optional.empty();
        RequestRecord updated = current.withSnapshot(result.get(), now);
        requests.put(requestId, updated);
        markDirty();
        return Optional.of(updated);
    }

    public int activeRequestCount(UUID colonyOwner) {
        if (colonyOwner == null) return 0;
        return (int) requests.values().stream()
                .filter(RequestRecord::isActive)
                .filter(request -> request.colonyOwnerUuid().equals(colonyOwner.toString()))
                .count();
    }

    public boolean hasActiveRequest(UUID colonyOwner, String dimension, BlockPos target, Identifier itemId) {
        if (colonyOwner == null || dimension == null || target == null || itemId == null) return false;
        return requests.values().stream().anyMatch(request -> request.isActive()
                && request.colonyOwnerUuid().equals(colonyOwner.toString())
                && request.dimension().equals(dimension)
                && request.targetPosition() == target.asLong()
                && request.itemId().equals(itemId.toString()));
    }

    private boolean ownsTransfer(String requestId, UUID courier, String transferId) {
        RequestRecord current = requests.get(requestId);
        return current != null && courier != null && isUuid(transferId)
                && current.courierUuid().equals(courier.toString())
                && current.transferId().equals(transferId);
    }
    private static boolean isUuid(String value) {
        try { return value != null && !value.isBlank() && UUID.fromString(value) != null; }
        catch (IllegalArgumentException exception) { return false; }
    }

    private Optional<RequestRecord> transition(
            String requestId, long now,
            java.util.function.Function<ColonyRequestRules.Snapshot, Optional<ColonyRequestRules.Snapshot>> operation) {
        RequestRecord current = requests.get(requestId);
        if (current == null) return Optional.empty();
        Optional<ColonyRequestRules.Snapshot> result = operation.apply(current.snapshot());
        if (result.isEmpty()) return Optional.empty();
        RequestRecord updated = current.withSnapshot(result.get(), now);
        requests.put(requestId, updated);
        markDirty();
        return Optional.of(updated);
    }

    private Map<String, RequestRecord> snapshot() {
        return Map.copyOf(requests);
    }

    public record RequestRecord(String requestId, String colonyOwnerUuid, String dimension, long targetPosition,
                                String itemId, int requestedCount, int deliveredCount, int priority,
                                String status, String courierUuid, long createdAt, long updatedAt,
                                String transferId, String lastTransferId, long courierWarehousePosition) {
        public RequestRecord {
            requestId = requestId == null ? "" : requestId;
            colonyOwnerUuid = colonyOwnerUuid == null ? "" : colonyOwnerUuid;
            dimension = dimension == null ? "" : dimension;
            itemId = itemId == null ? "" : itemId;
            status = status == null ? "" : status;
            courierUuid = courierUuid == null ? "" : courierUuid;
            transferId = transferId == null ? "" : transferId;
            lastTransferId = lastTransferId == null ? "" : lastTransferId;
        }

        public BlockPos target() { return BlockPos.fromLong(targetPosition); }

        public int remainingCount() { return Math.max(0, requestedCount - deliveredCount); }

        public boolean isActive() {
            return ColonyRequestRules.Status.parse(status).map(s -> !s.terminal()).orElse(false);
        }

        public ColonyRequestRules.Snapshot snapshot() {
            ColonyRequestRules.Status parsed = ColonyRequestRules.Status.parse(status)
                    .orElseThrow(() -> new IllegalStateException("Unknown request status: " + status));
            return new ColonyRequestRules.Snapshot(requestedCount, deliveredCount, priority, parsed, courierUuid);
        }

        public RequestRecord withSnapshot(ColonyRequestRules.Snapshot value, long now) {
            String nextTransferId = transferId;
            String nextLastTransferId = lastTransferId;
            long nextWarehousePosition = courierWarehousePosition;
            if (status.equals(ColonyRequestRules.Status.IN_TRANSIT.id())
                    && value.delivered() > deliveredCount && isUuid(transferId)) nextLastTransferId = transferId;
            if (value.status() == ColonyRequestRules.Status.RESERVED
                    && !status.equals(ColonyRequestRules.Status.RESERVED.id())) {
                nextTransferId = UUID.randomUUID().toString();
                nextWarehousePosition = 0L;
            } else if (value.status() == ColonyRequestRules.Status.OPEN || value.status().terminal()) {
                nextTransferId = "";
                nextWarehousePosition = 0L;
            }
            return new RequestRecord(requestId, colonyOwnerUuid, dimension, targetPosition, itemId,
                    value.requested(), value.delivered(), value.priority(), value.status().id(),
                    value.courierUuid(), createdAt, now, nextTransferId, nextLastTransferId, nextWarehousePosition);
        }
        public RequestRecord withCourierTrip(String nextTransferId, String nextLastTransferId,
                                             long warehousePosition, long now) {
            return new RequestRecord(requestId, colonyOwnerUuid, dimension, targetPosition, itemId,
                    requestedCount, deliveredCount, priority, status, courierUuid, createdAt, now,
                    nextTransferId, nextLastTransferId, warehousePosition);
        }

        /** Leave courier metadata for repair so an orphaned claim can be safely released. */
        private boolean isValid() {
            return isValidUuid(requestId) && isValidUuid(colonyOwnerUuid)
                    && Identifier.tryParse(dimension) != null && Identifier.tryParse(itemId) != null
                    && requestedCount > 0 && requestedCount <= MAX_REQUEST_AMOUNT
                    && deliveredCount >= 0 && deliveredCount <= requestedCount
                    && priority >= 0 && priority <= 10
                    && ColonyRequestRules.Status.parse(status).isPresent();
        }

        private RequestRecord reconcileAfterLoad() {
            ColonyRequestRules.Status loaded = ColonyRequestRules.Status.parse(status).orElseThrow();
            ColonyRequestRules.Status repairedStatus = loaded;
            String repairedCourier = courierUuid;
            String repairedTransferId = transferId;
            String repairedLastTransferId = lastTransferId;
            long repairedWarehousePosition = courierWarehousePosition;
            if (loaded == ColonyRequestRules.Status.DELIVERED && deliveredCount < requestedCount) {
                repairedStatus = ColonyRequestRules.Status.OPEN;
            } else if ((loaded == ColonyRequestRules.Status.OPEN
                    || loaded == ColonyRequestRules.Status.RESERVED || loaded == ColonyRequestRules.Status.IN_TRANSIT)
                    && deliveredCount == requestedCount) {
                repairedStatus = ColonyRequestRules.Status.DELIVERED;
            }
            if ((repairedStatus == ColonyRequestRules.Status.RESERVED || repairedStatus == ColonyRequestRules.Status.IN_TRANSIT)
                    && !isValidUuid(repairedCourier)) repairedStatus = ColonyRequestRules.Status.OPEN;
            if (!isValidUuid(repairedLastTransferId)) repairedLastTransferId = "";
            if (repairedStatus != ColonyRequestRules.Status.RESERVED && repairedStatus != ColonyRequestRules.Status.IN_TRANSIT) {
                repairedCourier = "";
                repairedTransferId = "";
                repairedWarehousePosition = 0L;
            } else if (!isValidUuid(repairedTransferId)) repairedTransferId = "";
            return new RequestRecord(requestId, colonyOwnerUuid, dimension, targetPosition, itemId,
                    requestedCount, deliveredCount, priority, repairedStatus.id(), repairedCourier,
                    createdAt, updatedAt, repairedTransferId, repairedLastTransferId, repairedWarehousePosition);
        }

        private static boolean isValidUuid(String raw) {
            try {
                return raw != null && !raw.isBlank() && UUID.fromString(raw) != null;
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
    }
}
