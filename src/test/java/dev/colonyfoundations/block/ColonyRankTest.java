package dev.colonyfoundations.block;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ColonyRankTest {
    @Test
    void rankIdsParseCaseInsensitivelyAndRejectUnknownValues() {
        assertEquals(ColonyRank.OFFICER, ColonyRank.parse("OFFICER").orElseThrow());
        assertEquals(ColonyRank.FRIEND, ColonyRank.parse("friend").orElseThrow());
        assertTrue(ColonyRank.parse("not-a-rank").isEmpty());
        assertTrue(ColonyRank.parse(null).isEmpty());
    }

    @Test
    void defaultProfilesFailClosedForNeutralAndHostile() {
        var profiles = ColonyRank.defaultProfiles();
        assertEquals(Set.of(), profiles.get(ColonyRank.NEUTRAL));
        assertEquals(Set.of(), profiles.get(ColonyRank.HOSTILE));
        assertTrue(profiles.get(ColonyRank.FRIEND).contains(ColonyPermission.USE_BLOCKS));
        assertFalse(profiles.get(ColonyRank.FRIEND).contains(ColonyPermission.BREAK_BLOCKS));
        assertFalse(profiles.get(ColonyRank.FRIEND).contains(ColonyPermission.BUILD_PROJECTS));
        assertTrue(profiles.get(ColonyRank.OFFICER).containsAll(Set.of(ColonyPermission.values())));
    }

    @Test
    void defaultProfilesAreIndependentAcrossColonies() {
        var first = ColonyRank.defaultProfiles();
        var second = ColonyRank.defaultProfiles();
        first.get(ColonyRank.OFFICER).remove(ColonyPermission.BREAK_BLOCKS);
        assertTrue(second.get(ColonyRank.OFFICER).contains(ColonyPermission.BREAK_BLOCKS));
    }
}
