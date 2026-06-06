package jobflow.domain.job;

import java.util.List;
import jobflow.domain.skill.JdExperienceTagNormalizationService;
import jobflow.domain.skill.NormalizedExperienceTagMatch;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobExperienceTagNormalizationService {

    private final JdExperienceTagNormalizationService jdExperienceTagNormalizationService;
    private final JobExperienceTagRepository jobExperienceTagRepository;

    @Transactional
    public List<JobExperienceTag> saveNormalizedExperienceTags(Job job, String... texts) {
        List<NormalizedExperienceTagMatch> matches = jdExperienceTagNormalizationService.normalize(texts);

        if (matches.isEmpty()) {
            return List.of();
        }

        List<JobExperienceTag> jobExperienceTags = matches.stream()
                .map(match -> JobExperienceTag.create(
                        job,
                        match.tagCode(),
                        match.sourcePhrase()
                ))
                .toList();

        return jobExperienceTagRepository.saveAll(jobExperienceTags);
    }
}
