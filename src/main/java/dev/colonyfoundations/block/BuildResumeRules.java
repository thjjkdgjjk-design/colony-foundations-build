package dev.colonyfoundations.block;

import net.minecraft.util.Identifier;

/** Pure state policy for deciding whether a Town Hall start request resumes a saved order. */
public final class BuildResumeRules {
    private BuildResumeRules() {
    }

    public static boolean shouldResume(Identifier savedBlueprint, Identifier requestedBlueprint,
                                       boolean projectActive, int progress, boolean projectComplete,
                                       String projectIssue) {
        if (savedBlueprint == null || requestedBlueprint == null
                || !savedBlueprint.equals(requestedBlueprint) || projectComplete) {
            return false;
        }
        return projectActive || progress > 0 || (projectIssue != null && !projectIssue.isBlank());
    }
}
