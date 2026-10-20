package dev.colonyschematics.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class LocalizationParityTest {
    @Test
    void RussianAndEnglishLocaleKeysMatch() throws IOException {
        JsonObject russian = readLocale("ru_ru");
        JsonObject english = readLocale("en_us");

        assertEquals(english.keySet(), russian.keySet(),
                "Every schematic catalog message must have both Russian and English text");
    }

    @Test
    void userFacingTranslationsAreNotBlank() throws IOException {
        JsonObject russian = readLocale("ru_ru");
        for (String key : russian.keySet()) {
            assertFalse(russian.get(key).getAsString().isBlank(),
                    "Blank Russian translation for " + key);
        }
    }

    private static JsonObject readLocale(String locale) throws IOException {
        String resource = "/assets/colony_schematics/lang/" + locale + ".json";
        InputStream stream = LocalizationParityTest.class.getResourceAsStream(resource);
        assertNotNull(stream, "Missing locale resource " + resource);
        try (InputStream input = stream;
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
