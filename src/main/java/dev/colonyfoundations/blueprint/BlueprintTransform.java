package dev.colonyfoundations.blueprint;

import net.minecraft.util.math.BlockPos;

/** Shared coordinate transforms for the server builder and client preview. */
public final class BlueprintTransform {
    private BlueprintTransform() {
    }

    public static BlockPos rotate(BlockPos source, int sizeX, int sizeZ, int quarterTurns) {
        int x = source.getX(), y = source.getY(), z = source.getZ();
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 1 -> new BlockPos(sizeZ - 1 - z, y, x);
            case 2 -> new BlockPos(sizeX - 1 - x, y, sizeZ - 1 - z);
            case 3 -> new BlockPos(z, y, sizeX - 1 - x);
            default -> source.toImmutable();
        };
    }

    public static BlockPos worldOffset(BlockPos source, BlockPos primaryOffset,
                                       int sizeX, int sizeZ, int quarterTurns) {
        return rotate(source, sizeX, sizeZ, quarterTurns)
                .subtract(rotate(primaryOffset, sizeX, sizeZ, quarterTurns));
    }

    public static int rotatedSizeX(int sizeX, int sizeZ, int quarterTurns) {
        return (Math.floorMod(quarterTurns, 2) == 0) ? sizeX : sizeZ;
    }

    public static int rotatedSizeZ(int sizeX, int sizeZ, int quarterTurns) {
        return (Math.floorMod(quarterTurns, 2) == 0) ? sizeZ : sizeX;
    }
}
