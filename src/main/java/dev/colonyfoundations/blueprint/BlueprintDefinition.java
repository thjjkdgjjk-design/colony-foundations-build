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
        List<CottageBlueprint.Placement> placements,
        List<String> compatibilityWarnings
) {
    public BlueprintDefinition {
        primaryOffset = primaryOffset == null ? BlockPos.ORIGIN : primaryOffset.toImmutable();
        placements = List.copyOf(placements);
        compatibilityWarnings = compatibilityWarnings == null ? List.of() : List.copyOf(compatibilityWarnings);
    }

    /** Retain the old constructor for built-in blueprints and third-party callers. */
    public BlueprintDefinition(Identifier id, String title, int sizeX, int sizeY, int sizeZ,
                               BlockPos primaryOffset, List<CottageBlueprint.Placement> placements) {
        this(id, title, sizeX, sizeY, sizeZ, primaryOffset, placements, List.of());
    }
}
