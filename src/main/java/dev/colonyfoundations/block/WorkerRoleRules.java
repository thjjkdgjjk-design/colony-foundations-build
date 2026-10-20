package dev.colonyfoundations.block;

import java.util.Collection;

/**
 * Shared rules for mutually exclusive citizen job roles. Command tags are retained
 * for backwards compatibility with existing worlds, but assignment decisions are
 * centralized here so a villager cannot be hired into conflicting jobs.
 */
public final class WorkerRoleRules {
    public static final String BUILDER_TAG = "colony_foundations_builder";
    public static final String TOWN_HALL_ASSIGNMENT_PREFIX = "colony_foundations_assigned_";
    public static final String HUT_WORKER_TAG = "colony_foundations_hut_worker";
    public static final String HUT_ASSIGNMENT_PREFIX = "colony_foundations_hut_assigned_";
    public static final String JOB_TAG_PREFIX = "colony_foundations_job_";

    private WorkerRoleRules() { }

    public static boolean canRecruitAsBuilder(Collection<String> tags) {
        return isUnassigned(tags);
    }

    public static boolean canAssignToHut(Collection<String> tags) {
        return isUnassigned(tags);
    }

    public static boolean isUnassigned(Collection<String> tags) {
        for (String tag : tags) {
            if (tag.equals(BUILDER_TAG)
                    || tag.equals(HUT_WORKER_TAG)
                    || tag.startsWith(TOWN_HALL_ASSIGNMENT_PREFIX)
                    || tag.startsWith(HUT_ASSIGNMENT_PREFIX)
                    || tag.startsWith(JOB_TAG_PREFIX)) {
                return false;
            }
        }
        return true;
    }

    /** Returns true when the villager is not working for another hall or a hut. */
    public static boolean hasForeignAssignment(Collection<String> tags, String ownHallAssignmentTag) {
        for (String tag : tags) {
            if (tag.equals(HUT_WORKER_TAG)
                    || tag.startsWith(HUT_ASSIGNMENT_PREFIX)
                    || tag.startsWith(JOB_TAG_PREFIX)
                    || (tag.startsWith(TOWN_HALL_ASSIGNMENT_PREFIX) && !tag.equals(ownHallAssignmentTag))) {
                return true;
            }
        }
        return false;
    }

    public static boolean canContinueBuilderAssignment(Collection<String> tags, String ownHallAssignmentTag) {
        return tags.contains(BUILDER_TAG) && !hasForeignAssignment(tags, ownHallAssignmentTag);
    }
}
