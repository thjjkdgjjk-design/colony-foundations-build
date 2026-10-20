package dev.colonyschematics.blueprint;

import net.minecraft.util.math.BlockPos;

/**
 * Deterministic coordinate transforms shared by schematic block and anchor placement.
 * Coordinates are local to the untransformed schematic's minimum corner.
 */
public final class SchematicTransform {
    private SchematicTransform() { }

    /**
     * Mirrors the local X/Z grid first, then rotates it around the schematic's local origin.
     *
     * @param position source block position inside a schematic
     * @param sizeX source X dimension
     * @param sizeZ source Z dimension
     * @param quarterTurns clockwise quarter turns in the horizontal plane
     * @param mirrorMode 0=none, 1=front/back, 2=left/right
     */
    public static BlockPos transformBlockPos(BlockPos position, int sizeX, int sizeZ,
                                              int quarterTurns, int mirrorMode) {
        if (sizeX < 1 || sizeZ < 1) {
            throw new IllegalArgumentException("Schematic X/Z dimensions must be positive");
        }
        int x = position.getX();
        int y = position.getY();
        int z = position.getZ();
        if (x < 0 || x >= sizeX || z < 0 || z >= sizeZ) {
            throw new IllegalArgumentException("Position lies outside schematic X/Z dimensions: " + position);
        }

        int mirror = Math.floorMod(mirrorMode, 3);
        if (mirror == 1) z = sizeZ - 1 - z;
        if (mirror == 2) x = sizeX - 1 - x;
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 1 -> new BlockPos(sizeZ - 1 - z, y, x);
            case 2 -> new BlockPos(sizeX - 1 - x, y, sizeZ - 1 - z);
            case 3 -> new BlockPos(z, y, sizeX - 1 - x);
            default -> new BlockPos(x, y, z);
        };
    }
}
