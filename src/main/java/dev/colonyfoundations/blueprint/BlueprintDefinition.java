package dev.colonyfoundations.blueprint;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/** Validated, immutable schematic metadata used by the build menu and builder AI. */
public record BlueprintDefinition(
        Identifier id,
        String title,
        int sizeX,
        int sizeY,
        int sizeZ,
        BlockPos primaryOffset,
        List<CottageBlueprint.Placement> placements
) {
    public BlueprintDefinition {
        primaryOffset = primaryOffset == null ? BlockPos.ORIGIN : primaryOffset.toImmutable();
        placements = List.copyOf(placements);
    }
}
