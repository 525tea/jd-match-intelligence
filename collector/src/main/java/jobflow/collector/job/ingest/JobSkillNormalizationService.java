package jobflow.collector.job.ingest;

import java.util.List;
import jobflow.collector.job.Job;
import jobflow.collector.job.JobSkill;
import jobflow.collector.job.JobSkillRepository;
import jobflow.collector.job.RequirementType;
import jobflow.collector.skill.JdSkillNormalizationService;
import jobflow.collector.skill.NormalizedSkillMatch;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobSkillNormalizationService {

    private final JdSkillNormalizationService jdSkillNormalizationService;
    private final JobSkillRepository jobSkillRepository;

    @Transactional
    public List<JobSkill> replaceNormalizedSkills(Job job, String... texts) {
        jobSkillRepository.deleteByJobId(job.getId());

        List<NormalizedSkillMatch> matches = jdSkillNormalizationService.normalize(texts);

        if (matches.isEmpty()) {
            return List.of();
        }

        List<JobSkill> jobSkills = matches.stream()
                .map(match -> JobSkill.create(
                        job,
                        match.skill(),
                        RequirementType.REQUIRED
                ))
                .toList();

        return jobSkillRepository.saveAll(jobSkills);
    }
}
