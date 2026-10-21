package dev.colonyfoundations.block;

import net.minecraft.util.math.BlockPos;

import java.util.Collection;
import java.util.Optional;

/** Immutable inclusive world-space bounds for a built schematic or hut anchor. */
public record ColonyBuildingFootprint(BlockPos min, BlockPos max) {
    public ColonyBuildingFootprint {
        if (min == null || max == null) throw new IllegalArgumentException("Footprint bounds are required");
        BlockPos a = min.toImmutable();
        BlockPos b = max.toImmutable();
        min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    public static Optional<ColonyBuildingFootprint> fromWorldPositions(Collection<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) return Optional.empty();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : positions) {
            if (pos == null) continue;
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        if (minX == Integer.MAX_VALUE) return Optional.empty();
        return Optional.of(new ColonyBuildingFootprint(
                new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ)));
    }

    /** Bounds are inclusive: sharing even one block is a site conflict. */
    public boolean overlaps(ColonyBuildingFootprint other) {
        return other != null
                && min.getX() <= other.max.getX() && max.getX() >= other.min.getX()
                && min.getY() <= other.max.getY() && max.getY() >= other.min.getY()
                && min.getZ() <= other.max.getZ() && max.getZ() >= other.min.getZ();
    }

    public boolean contains(BlockPos position) {
        return position != null
                && position.getX() >= min.getX() && position.getX() <= max.getX()
                && position.getY() >= min.getY() && position.getY() <= max.getY()
                && position.getZ() >= min.getZ() && position.getZ() <= max.getZ();
    }

    public static boolean validPackedBounds(long minPacked, long maxPacked) {
        BlockPos min = BlockPos.fromLong(minPacked);
        BlockPos max = BlockPos.fromLong(maxPacked);
        return min.getX() <= max.getX() && min.getY() <= max.getY() && min.getZ() <= max.getZ();
    }
}
