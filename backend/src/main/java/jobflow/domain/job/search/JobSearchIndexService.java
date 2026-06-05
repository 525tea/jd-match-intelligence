package jobflow.domain.job.search;

import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobSearchIndexService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final JobSearchProperties jobSearchProperties;
    private final JobSearchIndexDefinition jobSearchIndexDefinition;

    public boolean createIndexIfMissing() {
        IndexOperations indexOperations = indexOperations();

        if (indexOperations.exists()) {
            return false;
        }

        indexOperations.create(jobSearchIndexDefinition.settings());
        indexOperations.putMapping(Document.from(jobSearchIndexDefinition.mapping()));

        return true;
    }

    public boolean indexExists() {
        return indexOperations().exists();
    }

    private IndexOperations indexOperations() {
        return elasticsearchOperations.indexOps(
                IndexCoordinates.of(jobSearchProperties.indexName())
        );
    }
}
