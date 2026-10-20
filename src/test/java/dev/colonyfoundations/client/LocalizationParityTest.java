package dev.colonyfoundations.client;

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
                "Every player-facing key must have both Russian and English text");
    }

    @Test
    void buildingNamesDoNotFallBackToTechnicalIds() throws IOException {
        JsonObject russian = readLocale("ru_ru");
        String prefix = "block.colony_foundations.";
        for (String key : russian.keySet()) {
            if (!key.startsWith(prefix)) continue;
            String rawFallback = key.substring(prefix.length()).replace('_', ' ');
            assertNotEquals(rawFallback, russian.get(key).getAsString(),
                    "Missing Russian building name for " + key);
        }
    }

    private static JsonObject readLocale(String locale) throws IOException {
        String resource = "/assets/colony_foundations/lang/" + locale + ".json";
        InputStream stream = LocalizationParityTest.class.getResourceAsStream(resource);
        assertNotNull(stream, "Missing locale resource " + resource);
        try (InputStream input = stream;
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
