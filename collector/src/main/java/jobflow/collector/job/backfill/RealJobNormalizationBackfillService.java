package jobflow.collector.job.backfill;

import java.util.List;
import jobflow.collector.job.Job;
import jobflow.collector.job.JobRepository;
import jobflow.collector.job.JobRole;
import jobflow.collector.job.ingest.JdJobRoleClassificationService;
import jobflow.collector.job.ingest.JobExperienceTagNormalizationService;
import jobflow.collector.job.ingest.JobSkillNormalizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RealJobNormalizationBackfillService {

    private final JobRepository jobRepository;
    private final JdJobRoleClassificationService jobRoleClassificationService;
    private final JobSkillNormalizationService jobSkillNormalizationService;
    private final JobExperienceTagNormalizationService jobExperienceTagNormalizationService;

    @Transactional
    public RealJobNormalizationBackfillSummary backfill(List<String> sources) {
        List<Job> jobs = jobRepository.findBySourceInOrderByIdAsc(sources);

        int roleUpdatedCount = 0;
        int normalizedSkillJobCount = 0;
        int normalizedExperienceTagJobCount = 0;

        for (Job job : jobs) {
            JobRole beforeRole = job.getRole();
            JobRole afterRole = jobRoleClassificationService.classify(
                    job.getTitle(),
                    job.getDescription(),
                    job.getRoleDetail()
            );

            if (afterRole != beforeRole) {
                job.updateRole(afterRole);
                roleUpdatedCount++;
            }

            int skillCount = jobSkillNormalizationService.replaceNormalizedSkills(
                    job,
                    job.getTitle(),
                    job.getDescription(),
                    job.getRoleDetail()
            ).size();

            if (skillCount > 0) {
                normalizedSkillJobCount++;
            }

            int experienceTagCount = jobExperienceTagNormalizationService.replaceNormalizedExperienceTags(
                    job,
                    job.getTitle(),
                    job.getDescription(),
                    job.getRoleDetail()
            ).size();

            if (experienceTagCount > 0) {
                normalizedExperienceTagJobCount++;
            }
        }

        return new RealJobNormalizationBackfillSummary(
                jobs.size(),
                roleUpdatedCount,
                normalizedSkillJobCount,
                normalizedExperienceTagJobCount
        );
    }
}
