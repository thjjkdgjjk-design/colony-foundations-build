package dev.colonyfoundations.blueprint;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BlueprintTransformTest {
    @Test
    void allRotationsPreserveBoundsAndUniqueCellMapping() {
        for (int sizeX = 1; sizeX <= 7; sizeX++) {
            for (int sizeZ = 1; sizeZ <= 5; sizeZ++) {
                for (int turns = 0; turns < 4; turns++) {
                    int rotatedX = BlueprintTransform.rotatedSizeX(sizeX, sizeZ, turns);
                    int rotatedZ = BlueprintTransform.rotatedSizeZ(sizeX, sizeZ, turns);
                    Set<BlockPos> mapped = new HashSet<>();
                    for (int x = 0; x < sizeX; x++) {
                        for (int z = 0; z < sizeZ; z++) {
                            BlockPos result = BlueprintTransform.rotate(new BlockPos(x, 2, z), sizeX, sizeZ, turns);
                            assertTrue(result.getX() >= 0 && result.getX() < rotatedX);
                            assertTrue(result.getZ() >= 0 && result.getZ() < rotatedZ);
                            assertEquals(2, result.getY());
                            mapped.add(result);
                        }
                    }
                    assertEquals(sizeX * sizeZ, mapped.size());
                }
            }
        }
    }

    @Test
    void rotatingFourTimesWithSwappedDimensionsReturnsOriginalCell() {
        for (int sizeX = 1; sizeX <= 7; sizeX++) {
            for (int sizeZ = 1; sizeZ <= 5; sizeZ++) {
                for (int x = 0; x < sizeX; x++) for (int z = 0; z < sizeZ; z++) {
                    BlockPos original = new BlockPos(x, 3, z);
                    BlockPos position = original;
                    int currentX = sizeX, currentZ = sizeZ;
                    for (int turn = 0; turn < 4; turn++) {
                        position = BlueprintTransform.rotate(position, currentX, currentZ, 1);
                        int temp = currentX;
                        currentX = currentZ;
                        currentZ = temp;
                    }
                    assertEquals(original, position);
                }
            }
        }
    }

    @Test
    void primaryAnchorMapsToWorldOriginForEveryRotation() {
        BlockPos anchor = new BlockPos(1, 0, 3);
        for (int turns = 0; turns < 4; turns++) {
            assertEquals(BlockPos.ORIGIN,
                    BlueprintTransform.worldOffset(anchor, anchor, 8, 6, turns));
        }
    }
}
