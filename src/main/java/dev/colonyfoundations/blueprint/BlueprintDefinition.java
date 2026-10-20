package dev.colonyfoundations.blueprint;

import net.minecraft.util.Identifier;

import java.util.List;

/** Validated, immutable schematic metadata used by the build menu and builder AI. */
public record BlueprintDefinition(
        Identifier id,
        String title,
        int sizeX,
        int sizeY,
        int sizeZ,
        List<CottageBlueprint.Placement> placements
) {
    public BlueprintDefinition {
        placements = List.copyOf(placements);
    }
}
