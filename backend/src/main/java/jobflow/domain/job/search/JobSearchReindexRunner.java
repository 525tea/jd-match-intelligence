package jobflow.domain.job.search;

import java.util.List;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.search.elasticsearch",
        name = "reindex-on-startup",
        havingValue = "true"
)
public class JobSearchReindexRunner implements ApplicationRunner {

    private final JobRepository jobRepository;
    private final JobSearchIndexingService jobSearchIndexingService;

    @Value("${app.search.elasticsearch.reindex-batch-size:100}")
    private int batchSize;

    @Override
    public void run(ApplicationArguments args) {
        long lastJobId = 0L;
        long indexedCount = 0L;

        while (true) {
            List<Job> jobs = jobRepository.findByIdGreaterThanOrderByIdAsc(
                    lastJobId,
                    PageRequest.of(0, batchSize)
            );

            if (jobs.isEmpty()) {
                break;
            }

            jobSearchIndexingService.indexAll(jobs);

            indexedCount += jobs.size();
            lastJobId = jobs.getLast().getId();

            log.info(
                    "Job search reindex batch completed. indexedCount={}, lastJobId={}, batchSize={}",
                    indexedCount,
                    lastJobId,
                    jobs.size()
            );
        }

        jobSearchIndexingService.refresh();

        log.info("Job search reindex completed. indexedCount={}", indexedCount);
    }
}
