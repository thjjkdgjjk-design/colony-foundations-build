package dev.colonyfoundations.worker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerNavigationRulesTest {
    @Test
    void idleNavigationAlwaysGetsANewPathRequest() {
        assertTrue(WorkerNavigationRules.shouldRequestPath(true, 20L, 2));
        assertTrue(WorkerNavigationRules.shouldRequestPath(true, 80L, 19));
    }

    @Test
    void activeNavigationIsNotResetEveryWorkerTick() {
        assertFalse(WorkerNavigationRules.shouldRequestPath(false, 20L, 0));
        assertFalse(WorkerNavigationRules.shouldRequestPath(false, 40L, 0));
        assertFalse(WorkerNavigationRules.shouldRequestPath(false, 60L, 0));
        assertFalse(WorkerNavigationRules.shouldRequestPath(false, 80L, 0));
        assertTrue(WorkerNavigationRules.shouldRequestPath(false, 100L, 0));
        assertFalse(WorkerNavigationRules.shouldRequestPath(false, 120L, 0));
        assertTrue(WorkerNavigationRules.shouldRequestPath(false, 200L, 0));
    }

    @Test
    void retrySlotsAreSpreadAcrossWorkers() {
        assertFalse(WorkerNavigationRules.shouldRequestPath(false, 20L, 0));
        assertTrue(WorkerNavigationRules.shouldRequestPath(false, 20L, 4));
    }
}
