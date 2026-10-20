package dev.colonyfoundations.block;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ColonyIdentityRulesTest {
    private static final UUID OWNER = UUID.fromString("a06f5a60-2a4f-4fbd-a3f4-28d9d2f78ce1");
    private static final long HALL_POS = 0x0000000100000002L;

    @Test
    void legacyIdentityIsStableAcrossRepeatedLoads() {
        UUID first = ColonyIdentityRules.stableLegacyId("minecraft:overworld", HALL_POS, OWNER);
        UUID repeated = ColonyIdentityRules.stableLegacyId("minecraft:overworld", HALL_POS, OWNER);

        assertEquals(first, repeated);
    }

    @Test
    void legacyIdentitySeparatesDimensionsPositionsAndOwners() {
        UUID expected = ColonyIdentityRules.stableLegacyId("minecraft:overworld", HALL_POS, OWNER);

        assertNotEquals(expected, ColonyIdentityRules.stableLegacyId("minecraft:the_nether", HALL_POS, OWNER));
        assertNotEquals(expected, ColonyIdentityRules.stableLegacyId("minecraft:overworld", HALL_POS + 1, OWNER));
        assertNotEquals(expected, ColonyIdentityRules.stableLegacyId(
                "minecraft:overworld", HALL_POS, UUID.fromString("bd8d2dbf-0a5d-4aaf-b9b5-a6f85f7c2a1e")));
    }

    @Test
    void malformedOrMissingIdentityNeverParses() {
        assertTrue(ColonyIdentityRules.parseUuid(null).isEmpty());
        assertTrue(ColonyIdentityRules.parseUuid("").isEmpty());
        assertTrue(ColonyIdentityRules.parseUuid("not-a-uuid").isEmpty());
        assertEquals(OWNER, ColonyIdentityRules.parseUuid(OWNER.toString()).orElseThrow());
    }

    @Test
    void persistentColonyRecordExposesIdentityAndNormalizesMetadata() {
        ColonyClaimState.ClaimRecord record = new ColonyClaimState.ClaimRecord(
                "minecraft:overworld", HALL_POS, OWNER.toString(), java.util.List.of(),
                OWNER.toString(), "  Test colony  ", "Owner", 0);

        assertTrue(record.hasIdentity());
        assertEquals(1, record.colonyLevel());
        assertEquals("  Test colony  ", record.colonyName());
    }
}
