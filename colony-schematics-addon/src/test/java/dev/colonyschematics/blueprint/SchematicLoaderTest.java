package dev.colonyschematics.blueprint;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchematicLoaderTest {
    @Test
    void catalogTitleLocalizesStyleCategoryAndBuildingTier() {
        Identifier id = Identifier.of("colony_schematics",
                "blueprints/minecolonies/ancientathens/military/guardtower5");
        assertEquals("Древние Афины — Военные постройки — Сторожевая башня, уровень 5",
                SchematicLoader.displayName(id));
    }

    @Test
    void alternativeBuildingsAreLocalized() {
        Identifier id = Identifier.of("colony_schematics",
                "blueprints/minecolonies/medievaloak/craftsmanship/altbuilder3");
        assertEquals("Деревянный стиль — Ремесло — Альтернативный вариант: Хижина строителя, уровень 3",
                SchematicLoader.displayName(id));
    }

    @Test
    void bundledPathShowsRussianStyleCategoryAndTier() {
        Identifier id = Identifier.of("colony_schematics", "minecolonies/stone/buildings/citizen/citizen1");
        assertEquals("Каменный стиль — Постройки — Дом жителя, уровень 1",
                SchematicLoader.displayName(id));
    }
}
