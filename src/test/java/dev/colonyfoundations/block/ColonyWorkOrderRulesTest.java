package dev.colonyfoundations.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ColonyWorkOrderRulesTest {
    @Test
    void buildAndUpgradeTransitionsFollowBuilderLevelCaps() {
        assertTrue(ColonyWorkOrderRules.validLevelTransition(ColonyWorkOrderType.BUILD, 0, 1, 1));
        assertFalse(ColonyWorkOrderRules.validLevelTransition(ColonyWorkOrderType.BUILD, 1, 1, 5));
        assertTrue(ColonyWorkOrderRules.validLevelTransition(ColonyWorkOrderType.UPGRADE, 1, 2, 2));
        assertFalse(ColonyWorkOrderRules.validLevelTransition(ColonyWorkOrderType.UPGRADE, 2, 3, 2));
        assertFalse(ColonyWorkOrderRules.validLevelTransition(ColonyWorkOrderType.UPGRADE, 5, 6, 5));
    }

    @Test
    void repairAndRemoveNeedExistingBuildingsAndKeepDistinctTargetLevels() {
        assertTrue(ColonyWorkOrderRules.validLevelTransition(ColonyWorkOrderType.REPAIR, 3, 3, 3));
        assertFalse(ColonyWorkOrderRules.validLevelTransition(ColonyWorkOrderType.REPAIR, 0, 0, 5));
        assertTrue(ColonyWorkOrderRules.validLevelTransition(ColonyWorkOrderType.REMOVE, 4, 0, 4));
        assertFalse(ColonyWorkOrderRules.validLevelTransition(ColonyWorkOrderType.REMOVE, 4, 0, 3));
    }

    @Test
    void workOrderTypeAndStatusParsingRejectUnknownValues() {
        assertEquals(ColonyWorkOrderType.UPGRADE, ColonyWorkOrderType.parse("UPGRADE").orElseThrow());
        assertEquals(ColonyWorkOrderStatus.BLOCKED, ColonyWorkOrderStatus.parse("blocked").orElseThrow());
        assertTrue(ColonyWorkOrderType.parse("demolish").isEmpty());
        assertTrue(ColonyWorkOrderStatus.parse("running").isEmpty());
        assertFalse(ColonyWorkOrderStatus.QUEUED.isTerminal());
        assertTrue(ColonyWorkOrderStatus.COMPLETED.isTerminal());
    }
}
