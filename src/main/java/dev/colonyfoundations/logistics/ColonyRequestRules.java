package dev.colonyfoundations.logistics;

import java.util.Optional;
import java.util.UUID;

/** Pure, testable lifecycle rules for durable colony item requests. */
public final class ColonyRequestRules {
    public enum Status {
        OPEN("open"),
        RESERVED("reserved"),
        IN_TRANSIT("in_transit"),
        DELIVERED("delivered"),
        CANCELLED("cancelled");

        private final String id;
        Status(String id) { this.id = id; }
        public String id() { return id; }

        public static Optional<Status> parse(String id) {
            for (Status status : values()) if (status.id.equals(id)) return Optional.of(status);
            return Optional.empty();
        }

        public boolean terminal() {
            return this == DELIVERED || this == CANCELLED;
        }
    }

    public record Snapshot(int requested, int delivered, int priority, Status status, String courierUuid) {
        public Snapshot {
            if (requested < 1 || delivered < 0 || delivered > requested) {
                throw new IllegalArgumentException("Invalid requested/delivered amount");
            }
            priority = Math.max(0, Math.min(10, priority));
            if (status == null) throw new IllegalArgumentException("Request status is required");
            courierUuid = courierUuid == null ? "" : courierUuid;
            if (status == Status.DELIVERED && delivered != requested) {
                throw new IllegalArgumentException("A delivered request must be fully satisfied");
            }
            if (status == Status.OPEN || status == Status.DELIVERED || status == Status.CANCELLED) {
                courierUuid = "";
            }
        }

        public int remaining() {
            return requested - delivered;
        }
    }

    private ColonyRequestRules() { }

    public static Optional<Snapshot> reserve(Snapshot current, UUID courier) {
        if (current == null || courier == null || current.status() != Status.OPEN || current.remaining() == 0) {
            return Optional.empty();
        }
        return Optional.of(new Snapshot(current.requested(), current.delivered(), current.priority(),
                Status.RESERVED, courier.toString()));
    }

    public static Optional<Snapshot> beginDelivery(Snapshot current, UUID courier) {
        if (!ownedBy(current, courier) || current.status() != Status.RESERVED) return Optional.empty();
        return Optional.of(new Snapshot(current.requested(), current.delivered(), current.priority(),
                Status.IN_TRANSIT, courier.toString()));
    }

    public static Optional<Snapshot> release(Snapshot current, UUID courier) {
        if (!ownedBy(current, courier)
                || (current.status() != Status.RESERVED && current.status() != Status.IN_TRANSIT)) {
            return Optional.empty();
        }
        return Optional.of(new Snapshot(current.requested(), current.delivered(), current.priority(),
                Status.OPEN, ""));
    }

    public static Optional<Snapshot> deliver(Snapshot current, UUID courier, int deliveredNow) {
        if (!ownedBy(current, courier) || current.status() != Status.IN_TRANSIT
                || deliveredNow < 1 || deliveredNow > current.remaining()) {
            return Optional.empty();
        }
        int newDelivered = current.delivered() + deliveredNow;
        Status next = newDelivered == current.requested() ? Status.DELIVERED : Status.OPEN;
        return Optional.of(new Snapshot(current.requested(), newDelivered, current.priority(), next, ""));
    }

    public static Optional<Snapshot> cancel(Snapshot current) {
        if (current == null || current.status().terminal()) return Optional.empty();
        return Optional.of(new Snapshot(current.requested(), current.delivered(), current.priority(),
                Status.CANCELLED, ""));
    }

    public static Snapshot merge(Snapshot current, int additionalAmount, int requestedPriority, int maximumAmount) {
        if (current == null || current.status().terminal() || additionalAmount < 1 || maximumAmount < 1) {
            throw new IllegalArgumentException("Cannot merge an invalid request");
        }
        int mergedRequested = (int) Math.min(maximumAmount, (long) current.requested() + additionalAmount);
        return new Snapshot(mergedRequested, current.delivered(),
                Math.max(current.priority(), requestedPriority), current.status(), current.courierUuid());
    }

    private static boolean ownedBy(Snapshot current, UUID courier) {
        return current != null && courier != null && courier.toString().equals(current.courierUuid());
    }
}
