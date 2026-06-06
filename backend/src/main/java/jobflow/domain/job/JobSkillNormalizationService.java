package jobflow.domain.job;

import java.util.List;
import jobflow.domain.skill.JdSkillNormalizationService;
import jobflow.domain.skill.NormalizedSkillMatch;
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
    public List<JobSkill> saveNormalizedSkills(Job job, String... texts) {
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
