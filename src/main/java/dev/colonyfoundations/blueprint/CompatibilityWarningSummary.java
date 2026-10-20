package dev.colonyfoundations.blueprint;

import java.util.List;

/** Formats a bounded, user-visible summary of schematic compatibility warnings. */
public final class CompatibilityWarningSummary {
    private CompatibilityWarningSummary() {
    }

    public static String summarize(List<String> warnings, int maximumShown) {
        if (warnings == null || warnings.isEmpty() || maximumShown < 1) return "";
        int shown = Math.min(warnings.size(), maximumShown);
        String summary = String.join("; ", warnings.subList(0, shown));
        int remaining = warnings.size() - shown;
        if (remaining > 0) summary += "; и ещё " + remaining + " предупреждений";
        return summary;
    }
}
