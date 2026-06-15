package jobflow.domain.analytics;

import jobflow.domain.job.JobSkillRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class JobSkillIndexRebuildService {

    private final JobSkillRepository jobSkillRepository;
    private final JobSkillIndexRepository jobSkillIndexRepository;
    private final ApplicationEventPublisher eventPublisher;

    public JobSkillIndexRebuildService(
            JobSkillRepository jobSkillRepository,
            JobSkillIndexRepository jobSkillIndexRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.jobSkillRepository = jobSkillRepository;
        this.jobSkillIndexRepository = jobSkillIndexRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public JobSkillIndexRebuildResult rebuild() {
        List<JobSkillIndexSource> sources = jobSkillRepository.findOpenJobSkillIndexSources();

        jobSkillIndexRepository.deleteAllInBatch();

        List<JobSkillIndex> indexes = sources.stream()
                .map(source -> JobSkillIndex.create(
                        source.job(),
                        source.skill(),
                        source.requirementType()
                ))
                .toList();

        jobSkillIndexRepository.saveAll(indexes);

        JobSkillIndexRebuildResult result = new JobSkillIndexRebuildResult(sources.size(), indexes.size());
        eventPublisher.publishEvent(new JobSkillIndexRebuiltEvent(result.sourceCount(), result.savedCount()));
        return result;
    }
}
