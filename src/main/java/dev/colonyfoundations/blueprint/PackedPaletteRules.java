package dev.colonyfoundations.blueprint;

/**
 * Validates the unsigned 16-bit palette references stored two per packed word
 * by Structurize-v1-style schematic block arrays.
 */
public final class PackedPaletteRules {
    private PackedPaletteRules() {
    }

    public static int indexAt(int packedWord, int linearIndex, int paletteSize) {
        if (linearIndex < 0) {
            throw new IllegalArgumentException("Packed block index must be non-negative");
        }
        if (paletteSize < 1 || paletteSize > 65_535) {
            throw new IllegalArgumentException("Palette size is outside the supported range: " + paletteSize);
        }

        int paletteIndex = (linearIndex & 1) == 0
                ? (packedWord >>> 16) & 0xFFFF
                : packedWord & 0xFFFF;
        if (paletteIndex >= paletteSize) {
            throw new IllegalArgumentException("Packed block " + linearIndex
                    + " references missing palette entry " + paletteIndex
                    + " (palette size " + paletteSize + ")");
        }
        return paletteIndex;
    }
}
