package dev.colonyfoundations.blueprint;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintRegistryAvailabilityTest {
    @Test
    void missingNonCottageBlueprintDoesNotFallBackToStarterCottage() {
        Identifier removedBlueprint = Identifier.of(
                "colony_foundations", "minecolonies/removed_style/buildings/citizen/citizen1");
        assertTrue(BlueprintRegistry.getBuildDefinition(removedBlueprint).isEmpty());
    }
}
