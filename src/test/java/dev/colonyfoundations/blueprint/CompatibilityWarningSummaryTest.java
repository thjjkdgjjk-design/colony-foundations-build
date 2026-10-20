package dev.colonyfoundations.blueprint;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompatibilityWarningSummaryTest {
    @Test
    void emptyWarningsDoNotProduceNoise() {
        assertEquals("", CompatibilityWarningSummary.summarize(List.of(), 3));
    }

    @Test
    void summaryShowsOnlyTheRequestedNumberAndCountsTheRemainder() {
        assertEquals("A; B; и ещё 2 предупреждений",
                CompatibilityWarningSummary.summarize(List.of("A", "B", "C", "D"), 2));
    }

    @Test
    void nonPositiveDisplayLimitSuppressesSummary() {
        assertEquals("", CompatibilityWarningSummary.summarize(List.of("A"), 0));
    }
}
