package jobflow.collector.job.ingest;

import java.util.List;
import jobflow.collector.job.Job;
import jobflow.collector.job.JobExperienceTag;
import jobflow.collector.job.JobExperienceTagRepository;
import jobflow.collector.skill.JdExperienceTagNormalizationService;
import jobflow.collector.skill.NormalizedExperienceTagMatch;
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
    public List<JobExperienceTag> replaceNormalizedExperienceTags(Job job, String... texts) {
        jobExperienceTagRepository.deleteByJobId(job.getId());
        jobExperienceTagRepository.flush();

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
