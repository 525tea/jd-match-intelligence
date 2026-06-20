package jobflow.domain.job.search;

import java.util.List;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;

public record JobSearchIntent(
        List<JobRole> roles,
        List<CareerLevel> careerLevels,
        List<String> locationRegions,
        List<String> requiredSkillKeywords
) {

    public boolean hasAnySignal() {
        return !roles.isEmpty()
                || !careerLevels.isEmpty()
                || !locationRegions.isEmpty()
                || !requiredSkillKeywords.isEmpty();
    }
}
