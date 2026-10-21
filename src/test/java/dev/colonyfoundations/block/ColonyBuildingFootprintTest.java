package dev.colonyfoundations.block;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ColonyBuildingFootprintTest {
    @Test
    void computesWorldBoundsFromNegativeAndPositivePositions() {
        ColonyBuildingFootprint footprint = ColonyBuildingFootprint.fromWorldPositions(List.of(
                new BlockPos(5, 70, -8), new BlockPos(-2, 63, 4), new BlockPos(1, 66, -1)))
                .orElseThrow();

        assertEquals(new BlockPos(-2, 63, -8), footprint.min());
        assertEquals(new BlockPos(5, 70, 4), footprint.max());
        assertTrue(footprint.contains(new BlockPos(0, 65, 0)));
        assertFalse(footprint.contains(new BlockPos(6, 65, 0)));
    }

    @Test
    void detectsInclusiveThreeDimensionalOverlapsButAllowsAdjacentSites() {
        ColonyBuildingFootprint first = new ColonyBuildingFootprint(
                new BlockPos(-4, 60, -4), new BlockPos(4, 70, 4));
        ColonyBuildingFootprint adjacentX = new ColonyBuildingFootprint(
                new BlockPos(5, 60, -4), new BlockPos(10, 70, 4));
        ColonyBuildingFootprint overlappingCorner = new ColonyBuildingFootprint(
                new BlockPos(4, 70, 4), new BlockPos(8, 75, 8));
        ColonyBuildingFootprint separateY = new ColonyBuildingFootprint(
                new BlockPos(-4, 71, -4), new BlockPos(4, 80, 4));

        assertFalse(first.overlaps(adjacentX));
        assertTrue(first.overlaps(overlappingCorner));
        assertFalse(first.overlaps(separateY));
    }

    @Test
    void rejectsCorruptedInvertedPackedBounds() {
        assertFalse(ColonyBuildingFootprint.validPackedBounds(
                new BlockPos(4, 10, 4).asLong(), new BlockPos(3, 9, 8).asLong()));
        assertTrue(ColonyBuildingFootprint.validPackedBounds(
                new BlockPos(-4, 60, -4).asLong(), new BlockPos(4, 70, 4).asLong()));
    }
}
