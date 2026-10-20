package dev.colonyschematics.blueprint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PackedPaletteRulesTest {
    @Test
    void decodesUpperAndLowerUnsignedPaletteIndices() {
        int packedWord = 0x0002_0001;
        assertEquals(2, PackedPaletteRules.indexAt(packedWord, 0, 3));
        assertEquals(1, PackedPaletteRules.indexAt(packedWord, 1, 3));
    }

    @Test
    void rejectsUpperAndLowerIndicesOutsideThePaletteInsteadOfDroppingCells() {
        assertThrows(IllegalArgumentException.class,
                () -> PackedPaletteRules.indexAt(0x0003_0001, 0, 3));
        assertThrows(IllegalArgumentException.class,
                () -> PackedPaletteRules.indexAt(0x0002_0003, 1, 3));
        assertThrows(IllegalArgumentException.class,
                () -> PackedPaletteRules.indexAt(0xFFFF_0000, 0, 16));
    }

    @Test
    void rejectsInvalidDecoderArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> PackedPaletteRules.indexAt(0, -1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> PackedPaletteRules.indexAt(0, 0, 0));
    }
}
