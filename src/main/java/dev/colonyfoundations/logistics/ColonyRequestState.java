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
            Codec.LONG.fieldOf("updated_at").forGetter(RequestRecord::updatedAt)
    ).apply(instance, RequestRecord::new));

    private static final Codec<ColonyRequestState> CODEC = Codec.unboundedMap(Codec.STRING, REQUEST_CODEC)
            .fieldOf("requests").codec()
            .xmap(ColonyRequestState::new, ColonyRequestState::snapshot);
    private static final PersistentStateType<ColonyRequestState> TYPE =
            new PersistentStateType<>(STATE_ID, ColonyRequestState::new, CODEC, null);

    private final Map<String, RequestRecord> requests = new LinkedHashMap<>();

    public ColonyRequestState() { }

    ColonyRequestState(Map<String, RequestRecord> savedRequests) {
        if (savedRequests != null) requests.putAll(savedRequests);
        requests.entrySet().removeIf(entry -> !entry.getValue().isValid()
                || !entry.getKey().equals(entry.getValue().requestId()));
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
                amount, 0, priority, ColonyRequestRules.Status.OPEN.id(), "", now, now);
        requests.put(id, request);
        markDirty();
        return Optional.of(id);
    }

    public Optional<RequestRecord> reserve(String requestId, UUID courier, long now) {
        return transition(requestId, now, current -> ColonyRequestRules.reserve(current, courier));
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
                                String status, String courierUuid, long createdAt, long updatedAt) {
        public RequestRecord {
            requestId = requestId == null ? "" : requestId;
            colonyOwnerUuid = colonyOwnerUuid == null ? "" : colonyOwnerUuid;
            dimension = dimension == null ? "" : dimension;
            itemId = itemId == null ? "" : itemId;
            status = status == null ? "" : status;
            courierUuid = courierUuid == null ? "" : courierUuid;
            priority = Math.max(0, Math.min(10, priority));
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
            return new RequestRecord(requestId, colonyOwnerUuid, dimension, targetPosition, itemId,
                    value.requested(), value.delivered(), value.priority(), value.status().id(),
                    value.courierUuid(), createdAt, now);
        }

        private boolean isValid() {
            return isValidUuid(requestId) && isValidUuid(colonyOwnerUuid)
                    && Identifier.tryParse(dimension) != null && Identifier.tryParse(itemId) != null
                    && requestedCount > 0 && requestedCount <= MAX_REQUEST_AMOUNT
                    && deliveredCount >= 0 && deliveredCount <= requestedCount
                    && priority >= 0 && priority <= 10
                    && ColonyRequestRules.Status.parse(status).isPresent()
                    && (courierUuid.isBlank() || isValidUuid(courierUuid));
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
