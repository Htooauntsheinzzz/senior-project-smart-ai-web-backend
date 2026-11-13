package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

/**
 * Step 15B (spec file 54, section 9): options of a Student search. topK 1 to 10, ownProgramQuota 0 to 10 (places kept
 * for passages of the Student's own program), maxNeighborPassages 0 to 4 (continuations of a hit's section). Other
 * values throw IllegalArgumentException.
 */
public record StudentSearchOptions(int topK, int ownProgramQuota, int maxNeighborPassages) {
    public StudentSearchOptions {
        if (topK < 1 || topK > 10) throw new IllegalArgumentException("topK must be between 1 and 10");
        if (ownProgramQuota < 0 || ownProgramQuota > 10) throw new IllegalArgumentException("ownProgramQuota must be between 0 and 10");
        if (maxNeighborPassages < 0 || maxNeighborPassages > 4) throw new IllegalArgumentException("maxNeighborPassages must be between 0 and 4");
    }

    /** Spec file 52 behaviour: no reserve, no neighbours. */
    public static StudentSearchOptions plain(int topK) { return new StudentSearchOptions(topK, 0, 0); }
}
