package jobflow.domain.analytics;

import jobflow.domain.job.JobSkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class JobSkillIndexRebuildService {

    private final JobSkillRepository jobSkillRepository;
    private final JobSkillIndexRepository jobSkillIndexRepository;

    public JobSkillIndexRebuildService(
            JobSkillRepository jobSkillRepository,
            JobSkillIndexRepository jobSkillIndexRepository
    ) {
        this.jobSkillRepository = jobSkillRepository;
        this.jobSkillIndexRepository = jobSkillIndexRepository;
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

        return new JobSkillIndexRebuildResult(sources.size(), indexes.size());
    }
}
