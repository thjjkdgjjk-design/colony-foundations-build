package dev.colonyfoundations.block;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorkerRoleRulesTest {
    @Test
    void villagersWithoutJobTagsCanBeRecruitedOrAssigned() {
        assertTrue(WorkerRoleRules.canRecruitAsBuilder(Set.of("minecraft:vanilla_tag")));
        assertTrue(WorkerRoleRules.canAssignToHut(Set.of()));
    }

    @Test
    void builderCannotBeAssignedToWorkHutAndHutWorkerCannotBeRecruitedAsBuilder() {
        assertFalse(WorkerRoleRules.canAssignToHut(Set.of(WorkerRoleRules.BUILDER_TAG)));
        assertFalse(WorkerRoleRules.canRecruitAsBuilder(Set.of(WorkerRoleRules.HUT_WORKER_TAG)));
    }

    @Test
    void staleAssignmentAndJobTagsPreventDuplicateHiring() {
        assertFalse(WorkerRoleRules.canRecruitAsBuilder(Set.of(
                WorkerRoleRules.TOWN_HALL_ASSIGNMENT_PREFIX + "hall",
                WorkerRoleRules.BUILDER_TAG)));
        assertFalse(WorkerRoleRules.canAssignToHut(Set.of(
                WorkerRoleRules.JOB_TAG_PREFIX + "farmer")));
    }

    @Test
    void aBuilderCanContinueOnlyItsOwnHallsAssignment() {
        String own = WorkerRoleRules.TOWN_HALL_ASSIGNMENT_PREFIX + "hall-a";
        assertTrue(WorkerRoleRules.canContinueBuilderAssignment(Set.of(WorkerRoleRules.BUILDER_TAG, own), own));
        assertFalse(WorkerRoleRules.canContinueBuilderAssignment(Set.of(
                WorkerRoleRules.BUILDER_TAG, own, WorkerRoleRules.HUT_WORKER_TAG), own));
        assertFalse(WorkerRoleRules.canContinueBuilderAssignment(Set.of(
                WorkerRoleRules.BUILDER_TAG,
                WorkerRoleRules.TOWN_HALL_ASSIGNMENT_PREFIX + "hall-b"), own));
    }
}
