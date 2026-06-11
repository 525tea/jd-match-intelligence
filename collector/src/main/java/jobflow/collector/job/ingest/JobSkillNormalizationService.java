package jobflow.collector.job.ingest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final JdRequirementSectionExtractor requirementSectionExtractor;
    private final JobSkillRepository jobSkillRepository;

    @Transactional
    public List<JobSkill> replaceNormalizedSkills(Job job, String... texts) {
        jobSkillRepository.deleteByJobId(job.getId());
        jobSkillRepository.flush();

        Map<String, NormalizedSkillRequirement> skillRequirements = new LinkedHashMap<>();

        for (SkillRequirementSection section : requirementSectionExtractor.extract(texts)) {
            List<NormalizedSkillMatch> matches = jdSkillNormalizationService.normalize(section.text());

            for (NormalizedSkillMatch match : matches) {
                String key = match.skill().getNormalizedName();
                NormalizedSkillRequirement current = skillRequirements.get(key);
                NormalizedSkillRequirement candidate = new NormalizedSkillRequirement(
                        match,
                        section.requirementType()
                );

                if (current == null || shouldReplace(current, candidate)) {
                    skillRequirements.put(key, candidate);
                }
            }
        }

        if (skillRequirements.isEmpty()) {
            return List.of();
        }

        List<JobSkill> jobSkills = skillRequirements.values()
                .stream()
                .map(skillRequirement -> JobSkill.create(
                        job,
                        skillRequirement.match().skill(),
                        skillRequirement.requirementType()
                ))
                .toList();

        return jobSkillRepository.saveAll(jobSkills);
    }

    private boolean shouldReplace(
            NormalizedSkillRequirement current,
            NormalizedSkillRequirement candidate
    ) {
        return current.requirementType() == RequirementType.PREFERRED
                && candidate.requirementType() == RequirementType.REQUIRED;
    }

    private record NormalizedSkillRequirement(
            NormalizedSkillMatch match,
            RequirementType requirementType
    ) {
    }
}
