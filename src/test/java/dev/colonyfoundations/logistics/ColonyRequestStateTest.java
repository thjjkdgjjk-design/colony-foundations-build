package dev.colonyfoundations.logistics;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ColonyRequestStateTest {
    private static final RegistryKey<World> OVERWORLD =
            RegistryKey.of(RegistryKeys.WORLD, Identifier.of("minecraft", "overworld"));

    @Test
    void orphanedCourierReservationIsReopenedInsteadOfStuckForever() {
        UUID owner = UUID.randomUUID();
        String id = UUID.randomUUID().toString();
        ColonyRequestState.RequestRecord saved = new ColonyRequestState.RequestRecord(
                id, owner.toString(), "minecraft:overworld", new BlockPos(12, 64, -9).asLong(),
                "minecraft:wheat_seeds", 24, 0, 4, "reserved", "", 100, 120, "", "", 0L);

        ColonyRequestState restored = new ColonyRequestState(Map.of(id, saved));
        ColonyRequestState.RequestRecord recovered = restored.getRequest(id).orElseThrow();

        assertEquals("open", recovered.status());
        assertEquals("", recovered.courierUuid());
        assertEquals(24, recovered.remainingCount());
        assertEquals(1, restored.openRequests(owner, "minecraft:overworld").size());
    }

    @Test
    void incompleteDeliveredRecordReturnsToQueueAndFullySatisfiedOpenRecordCompletes() {
        UUID owner = UUID.randomUUID();
        String partialId = UUID.randomUUID().toString();
        String completeId = UUID.randomUUID().toString();
        ColonyRequestState.RequestRecord partial = new ColonyRequestState.RequestRecord(
                partialId, owner.toString(), "minecraft:overworld", new BlockPos(1, 64, 1).asLong(),
                "minecraft:carrot", 10, 4, 2, "delivered", "", 100, 200, "", "", 0L);
        ColonyRequestState.RequestRecord complete = new ColonyRequestState.RequestRecord(
                completeId, owner.toString(), "minecraft:overworld", new BlockPos(2, 64, 2).asLong(),
                "minecraft:potato", 5, 5, 2, "open", "", 100, 200, "", "", 0L);

        ColonyRequestState restored = new ColonyRequestState(Map.of(partialId, partial, completeId, complete));

        assertEquals("open", restored.getRequest(partialId).orElseThrow().status());
        assertEquals(6, restored.getRequest(partialId).orElseThrow().remainingCount());
        assertEquals("delivered", restored.getRequest(completeId).orElseThrow().status());
        assertTrue(restored.openRequests(owner, "minecraft:overworld").stream()
                .noneMatch(request -> request.requestId().equals(completeId)));
    }

    @Test
    void creatingTheSameTargetItemRequestMergesInsteadOfDuplicating() {
        UUID owner = UUID.randomUUID();
        BlockPos target = new BlockPos(0, 70, 0);
        Identifier item = Identifier.of("minecraft", "wheat_seeds");
        ColonyRequestState requests = new ColonyRequestState();

        String first = requests.createOrMerge(owner, OVERWORLD, target, item, 3, 1, 100).orElseThrow();
        String merged = requests.createOrMerge(owner, OVERWORLD, target, item, 4, 8, 200).orElseThrow();

        assertEquals(first, merged);
        assertEquals(7, requests.getRequest(first).orElseThrow().requestedCount());
        assertEquals(8, requests.getRequest(first).orElseThrow().priority());
        assertEquals(1, requests.activeRequestCount(owner));
    }

    @Test
    void reservationPersistsTransferTokenAndRemembersCompletedPartialTrip() {
        UUID owner = UUID.randomUUID();
        UUID courier = UUID.randomUUID();
        BlockPos target = new BlockPos(8, 65, 8);
        BlockPos warehouse = new BlockPos(3, 64, 3);
        ColonyRequestState requests = new ColonyRequestState();
        String id = requests.createOrMerge(owner, OVERWORLD, target,
                Identifier.of("minecraft", "wheat_seeds"), 16, 5, 100).orElseThrow();
        ColonyRequestState.RequestRecord reserved = requests.reserve(id, courier, warehouse, 120).orElseThrow();
        assertFalse(reserved.transferId().isBlank());
        assertEquals(warehouse.asLong(), reserved.courierWarehousePosition());

        ColonyRequestState restored = new ColonyRequestState(Map.of(id, reserved));
        ColonyRequestState.RequestRecord recovered = restored.getRequest(id).orElseThrow();
        assertEquals(reserved.transferId(), recovered.transferId());
        assertEquals(warehouse.asLong(), recovered.courierWarehousePosition());
        String firstTransfer = recovered.transferId();
        restored.beginDelivery(id, courier, firstTransfer, 130).orElseThrow();
        ColonyRequestState.RequestRecord partial = restored.recordDelivery(id, courier, firstTransfer, 4, 140).orElseThrow();
        assertEquals("open", partial.status());
        assertEquals("", partial.transferId());
        assertEquals(firstTransfer, partial.lastTransferId());
        assertEquals(12, partial.remainingCount());
        assertTrue(restored.recordDelivery(id, courier, firstTransfer, 4, 150).isEmpty());
        ColonyRequestState.RequestRecord next = restored.reserve(id, courier, warehouse, 160).orElseThrow();
        assertNotEquals(firstTransfer, next.transferId());
        assertEquals(4, next.deliveredCount());
    }

    @Test
    void replacementCourierCanAdoptPersistedTripWithoutChangingTransferIdentity() {
        UUID owner = UUID.randomUUID();
        UUID previousCourier = UUID.randomUUID();
        UUID replacementCourier = UUID.randomUUID();
        BlockPos target = new BlockPos(8, 65, 8);
        BlockPos warehouse = new BlockPos(3, 64, 3);
        ColonyRequestState requests = new ColonyRequestState();
        String id = requests.createOrMerge(owner, OVERWORLD, target,
                Identifier.of("minecraft", "wheat_seeds"), 16, 5, 100).orElseThrow();
        ColonyRequestState.RequestRecord reserved = requests.reserve(id, previousCourier, warehouse, 120).orElseThrow();
        String transferId = reserved.transferId();
        requests.beginDelivery(id, previousCourier, transferId, 130).orElseThrow();

        ColonyRequestState.RequestRecord adopted = requests.reassignCourier(
                id, previousCourier, replacementCourier, transferId, 140).orElseThrow();
        assertEquals(replacementCourier.toString(), adopted.courierUuid());
        assertEquals(transferId, adopted.transferId());
        assertEquals(warehouse.asLong(), adopted.courierWarehousePosition());
        assertEquals("in_transit", adopted.status());
        assertTrue(requests.recordDelivery(id, previousCourier, transferId, 4, 150).isEmpty());
        assertTrue(requests.recordDelivery(id, replacementCourier, transferId, 4, 160).isPresent());
    }

    @Test
    void replacementCourierCannotAdoptAnotherTripsTokenOrCompletedTrip() {
        UUID owner = UUID.randomUUID();
        UUID previousCourier = UUID.randomUUID();
        UUID replacementCourier = UUID.randomUUID();
        ColonyRequestState requests = new ColonyRequestState();
        String id = requests.createOrMerge(owner, OVERWORLD, new BlockPos(8, 65, 8),
                Identifier.of("minecraft", "wheat_seeds"), 8, 5, 100).orElseThrow();
        ColonyRequestState.RequestRecord reserved = requests.reserve(id, previousCourier,
                new BlockPos(3, 64, 3), 120).orElseThrow();
        assertTrue(requests.reassignCourier(id, previousCourier, replacementCourier,
                UUID.randomUUID().toString(), 130).isEmpty());
        assertTrue(requests.reassignCourier(id, previousCourier, replacementCourier,
                reserved.transferId(), 130).isPresent());
        assertTrue(requests.reassignCourier(id, previousCourier, replacementCourier,
                reserved.transferId(), 140).isEmpty());
    }

    @Test
    void malformedCountsAndUnknownStatusesAreDiscardedOnRestore() {
        UUID owner = UUID.randomUUID();
        String invalidCountId = UUID.randomUUID().toString();
        String invalidStatusId = UUID.randomUUID().toString();
        ColonyRequestState.RequestRecord invalidCount = new ColonyRequestState.RequestRecord(
                invalidCountId, owner.toString(), "minecraft:overworld", 0L,
                "minecraft:stone", 2, 3, 0, "open", "", 1, 1, "", "", 0L);
        ColonyRequestState.RequestRecord invalidStatus = new ColonyRequestState.RequestRecord(
                invalidStatusId, owner.toString(), "minecraft:overworld", 0L,
                "minecraft:stone", 2, 0, 0, "future-status", "", 1, 1, "", "", 0L);

        ColonyRequestState restored = new ColonyRequestState(Map.of(
                invalidCountId, invalidCount, invalidStatusId, invalidStatus));

        assertTrue(restored.requestsFor(owner).isEmpty());
    }
}
