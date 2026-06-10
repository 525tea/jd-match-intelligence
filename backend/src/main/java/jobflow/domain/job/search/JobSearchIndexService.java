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
        IndexOperations indexOperations = physicalIndexOperations();

        if (exists(indexOperations)) {
            return false;
        }

        indexOperations.create(jobSearchIndexDefinition.settings());
        indexOperations.putMapping(Document.from(jobSearchIndexDefinition.mapping()));

        return true;
    }

    public boolean indexExists() {
        return exists(physicalIndexOperations());
    }

    private boolean exists(IndexOperations indexOperations) {
        try {
            return indexOperations.exists();
        } catch (RuntimeException exception) {
            if (isEmptyExistsResponseFailure(exception)) {
                return false;
            }

            throw exception;
        }
    }

    private boolean isEmptyExistsResponseFailure(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && message.contains("[es/indices.exists]")
                    && message.contains("Expecting a response body")) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private IndexOperations physicalIndexOperations() {
        return elasticsearchOperations.indexOps(
                IndexCoordinates.of(jobSearchProperties.physicalIndexName())
        );
    }
}
