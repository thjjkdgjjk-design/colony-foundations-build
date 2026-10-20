package dev.colonyschematics.blueprint;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SchematicTransformTest {
    @Test
    void everyRotationAndMirrorMapsEveryCellInsideTheTransformedBoundsWithoutCollisions() {
        for (int sizeX = 1; sizeX <= 7; sizeX++) {
            for (int sizeZ = 1; sizeZ <= 5; sizeZ++) {
                for (int turns = 0; turns < 4; turns++) {
                    for (int mirror = 0; mirror < 3; mirror++) {
                        int outputSizeX = (turns % 2 == 0) ? sizeX : sizeZ;
                        int outputSizeZ = (turns % 2 == 0) ? sizeZ : sizeX;
                        Set<BlockPos> transformed = new HashSet<>();
                        for (int x = 0; x < sizeX; x++) {
                            for (int z = 0; z < sizeZ; z++) {
                                BlockPos output = SchematicTransform.transformBlockPos(
                                        new BlockPos(x, 3, z), sizeX, sizeZ, turns, mirror);
                                assertTrue(output.getX() >= 0 && output.getX() < outputSizeX,
                                        "X out of bounds for " + sizeX + "x" + sizeZ
                                                + ", turn=" + turns + ", mirror=" + mirror + ": " + output);
                                assertTrue(output.getZ() >= 0 && output.getZ() < outputSizeZ,
                                        "Z out of bounds for " + sizeX + "x" + sizeZ
                                                + ", turn=" + turns + ", mirror=" + mirror + ": " + output);
                                assertEquals(3, output.getY());
                                assertTrue(transformed.add(output), "Two source cells mapped to " + output);
                            }
                        }
                        assertEquals(sizeX * sizeZ, transformed.size());
                    }
                }
            }
        }
    }

    @Test
    void rotationsAndMirrorsNormalizeOutOfRangeArguments() {
        for (int sizeX = 1; sizeX <= 5; sizeX++) {
            for (int sizeZ = 1; sizeZ <= 4; sizeZ++) {
                for (int x = 0; x < sizeX; x++) {
                    for (int z = 0; z < sizeZ; z++) {
                        BlockPos position = new BlockPos(x, 2, z);
                        assertEquals(
                                SchematicTransform.transformBlockPos(position, sizeX, sizeZ, 0, 0),
                                SchematicTransform.transformBlockPos(position, sizeX, sizeZ, 4, 3));
                        assertEquals(
                                SchematicTransform.transformBlockPos(position, sizeX, sizeZ, 1, 1),
                                SchematicTransform.transformBlockPos(position, sizeX, sizeZ, 5, 4));
                    }
                }
            }
        }
    }

    @Test
    void rejectsInvalidDimensionsAndSupportsExternalAnchorOffsets() {
        assertThrows(IllegalArgumentException.class,
                () -> SchematicTransform.transformBlockPos(BlockPos.ORIGIN, 0, 1, 0, 0));
        assertEquals(new BlockPos(-3, 0, -3),
                SchematicTransform.transformBlockPos(new BlockPos(-3, 0, 5), 2, 3, 1, 0));
    }

    @Test
    void entityPositionsKeepFractionalOffsetsDuringMirroringAndRotation() {
        var point = new net.minecraft.util.math.Vec3d(0.25, 7.5, 1.75);
        assertEquals(new net.minecraft.util.math.Vec3d(0.25, 7.5, 0.25),
                SchematicTransform.transformEntityPosition(point, 4, 2, 0, 1));
        assertEquals(new net.minecraft.util.math.Vec3d(1.75, 7.5, 0.25),
                SchematicTransform.transformEntityPosition(point, 4, 2, 1, 1));
    }

    @Test
    void entityPositionTransformsStayWithinContinuousRotatedBounds() {
        for (int sizeX = 1; sizeX <= 7; sizeX++) {
            for (int sizeZ = 1; sizeZ <= 5; sizeZ++) {
                for (int turns = 0; turns < 4; turns++) {
                    for (int mirror = 0; mirror < 3; mirror++) {
                        var input = new net.minecraft.util.math.Vec3d(sizeX * 0.37, 4.25, sizeZ * 0.61);
                        var output = SchematicTransform.transformEntityPosition(input, sizeX, sizeZ, turns, mirror);
                        int resultX = (turns % 2 == 0) ? sizeX : sizeZ;
                        int resultZ = (turns % 2 == 0) ? sizeZ : sizeX;
                        assertTrue(output.x >= 0.0 && output.x <= resultX);
                        assertTrue(output.z >= 0.0 && output.z <= resultZ);
                        assertEquals(input.y, output.y);
                    }
                }
            }
        }
    }

}
