package jobflow.collector.job.ingest;

import jobflow.collector.job.RequirementType;

public record SkillRequirementSection(
        RequirementType requirementType,
        String text
) {
}
