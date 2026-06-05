package jobflow.domain.job.search;

import jobflow.domain.job.Job;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobSearchIndexingService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final JobSearchProperties jobSearchProperties;
    private final JobSearchIndexService jobSearchIndexService;
    private final JobSearchDocumentMapper jobSearchDocumentMapper;

    public JobSearchDocument index(Job job) {
        jobSearchIndexService.createIndexIfMissing();

        JobSearchDocument document = jobSearchDocumentMapper.toDocument(job);
        return elasticsearchOperations.save(
                document,
                IndexCoordinates.of(jobSearchProperties.indexName())
        );
    }
}
