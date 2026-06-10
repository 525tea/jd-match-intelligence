package jobflow.domain.job.search;

import java.util.ArrayList;
import java.util.List;
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
                indexCoordinates()
        );
    }

    public List<JobSearchDocument> indexAll(List<Job> jobs) {
        jobSearchIndexService.createIndexIfMissing();

        List<JobSearchDocument> documents = jobs.stream()
                .map(jobSearchDocumentMapper::toDocument)
                .toList();

        Iterable<JobSearchDocument> savedDocuments = elasticsearchOperations.save(
                documents,
                indexCoordinates()
        );

        List<JobSearchDocument> result = new ArrayList<>();
        savedDocuments.forEach(result::add);
        return result;
    }

    private IndexCoordinates indexCoordinates() {
        return IndexCoordinates.of(jobSearchProperties.indexName());
    }
}
