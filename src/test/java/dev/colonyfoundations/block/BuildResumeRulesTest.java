package dev.colonyfoundations.block;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuildResumeRulesTest {
    private static final Identifier COTTAGE = Identifier.of("colony_foundations", "cottage");
    private static final Identifier BARN = Identifier.of("colony_foundations", "barn");

    @Test
    void interruptedBeforeFirstBlockResumesWithoutDiscardingSavedPlacement() {
        assertTrue(BuildResumeRules.shouldResume(COTTAGE, COTTAGE, false, 0, false,
                "Позиция занята блоком minecraft:stone"));
    }

    @Test
    void alreadyActiveOrderWithZeroProgressIsNotReinitialized() {
        assertTrue(BuildResumeRules.shouldResume(COTTAGE, COTTAGE, true, 0, false, ""));
    }

    @Test
    void progressedOrderResumesEvenIfIssueTextWasLost() {
        assertTrue(BuildResumeRules.shouldResume(COTTAGE, COTTAGE, false, 7, false, ""));
    }

    @Test
    void aNewUnstartedSelectionIsNotMistakenForResume() {
        assertFalse(BuildResumeRules.shouldResume(COTTAGE, COTTAGE, false, 0, false, ""));
    }

    @Test
    void changingBlueprintAndRestartingCompletedWorkAreNewOrders() {
        assertFalse(BuildResumeRules.shouldResume(COTTAGE, BARN, false, 7, false, "blocked"));
        assertFalse(BuildResumeRules.shouldResume(COTTAGE, COTTAGE, false, 20, true, ""));
    }
}
