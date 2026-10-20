package dev.colonyfoundations.logistics;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ColonyRequestRulesTest {
    @Test
    void requestMustBeReservedThenDispatchedBeforeDelivery() {
        UUID courier = UUID.randomUUID();
        var open = new ColonyRequestRules.Snapshot(12, 0, 5, ColonyRequestRules.Status.OPEN, "");
        assertTrue(ColonyRequestRules.beginDelivery(open, courier).isEmpty());

        var reserved = ColonyRequestRules.reserve(open, courier).orElseThrow();
        assertEquals(ColonyRequestRules.Status.RESERVED, reserved.status());
        assertTrue(ColonyRequestRules.reserve(reserved, UUID.randomUUID()).isEmpty());

        var inTransit = ColonyRequestRules.beginDelivery(reserved, courier).orElseThrow();
        assertEquals(ColonyRequestRules.Status.IN_TRANSIT, inTransit.status());
        assertTrue(ColonyRequestRules.deliver(inTransit, UUID.randomUUID(), 1).isEmpty());
        assertTrue(ColonyRequestRules.cancel(inTransit).isEmpty());
    }

    @Test
    void partialDeliveryReopensRequestAndFinalDeliveryCompletesIt() {
        UUID courier = UUID.randomUUID();
        var open = new ColonyRequestRules.Snapshot(10, 0, 5, ColonyRequestRules.Status.OPEN, "");
        var reserved = ColonyRequestRules.reserve(open, courier).orElseThrow();
        var travelling = ColonyRequestRules.beginDelivery(reserved, courier).orElseThrow();
        var partial = ColonyRequestRules.deliver(travelling, courier, 4).orElseThrow();

        assertEquals(4, partial.delivered());
        assertEquals(6, partial.remaining());
        assertEquals(ColonyRequestRules.Status.OPEN, partial.status());
        assertTrue(partial.courierUuid().isBlank());

        var nextReserved = ColonyRequestRules.reserve(partial, courier).orElseThrow();
        var nextTrip = ColonyRequestRules.beginDelivery(nextReserved, courier).orElseThrow();
        var complete = ColonyRequestRules.deliver(nextTrip, courier, 6).orElseThrow();
        assertEquals(ColonyRequestRules.Status.DELIVERED, complete.status());
        assertEquals(10, complete.delivered());
        assertTrue(ColonyRequestRules.reserve(complete, courier).isEmpty());
    }

    @Test
    void releaseAndCancelAreSafeAndIdempotent() {
        UUID courier = UUID.randomUUID();
        var open = new ColonyRequestRules.Snapshot(2, 0, 10, ColonyRequestRules.Status.OPEN, "");
        var reserved = ColonyRequestRules.reserve(open, courier).orElseThrow();
        assertTrue(ColonyRequestRules.release(reserved, UUID.randomUUID()).isEmpty());
        var released = ColonyRequestRules.release(reserved, courier).orElseThrow();
        assertEquals(ColonyRequestRules.Status.OPEN, released.status());

        var cancelled = ColonyRequestRules.cancel(released).orElseThrow();
        assertEquals(ColonyRequestRules.Status.CANCELLED, cancelled.status());
        assertTrue(ColonyRequestRules.cancel(cancelled).isEmpty());
    }

    @Test
    void priorityIsBoundedAndMergingNeverExceedsTheAmountLimit() {
        var request = new ColonyRequestRules.Snapshot(4090, 0, 7, ColonyRequestRules.Status.OPEN, "");
        var merged = ColonyRequestRules.merge(request, 50, 10, 4096);
        assertEquals(4096, merged.requested());
        assertEquals(10, merged.priority());
    }
}
