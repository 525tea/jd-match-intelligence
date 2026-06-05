package jobflow.domain.job.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobSearchIndexServiceTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private IndexOperations indexOperations;

    private final JobSearchProperties jobSearchProperties = new JobSearchProperties(
            "http://localhost:9200",
            "jobflow-jobs"
    );

    private final JobSearchIndexDefinition jobSearchIndexDefinition = new JobSearchIndexDefinition();

    @Test
    @DisplayName("검색 index가 없으면 settings와 mapping으로 생성한다")
    void createIndexIfMissing() {
        JobSearchIndexService service = new JobSearchIndexService(
                elasticsearchOperations,
                jobSearchProperties,
                jobSearchIndexDefinition
        );

        given(elasticsearchOperations.indexOps(IndexCoordinates.of("jobflow-jobs")))
                .willReturn(indexOperations);
        given(indexOperations.exists()).willReturn(false);

        boolean created = service.createIndexIfMissing();

        assertThat(created).isTrue();

        verify(indexOperations).create(jobSearchIndexDefinition.settings());
        verify(indexOperations).putMapping(Document.from(jobSearchIndexDefinition.mapping()));
    }

    @Test
    @DisplayName("검색 index가 이미 있으면 생성하지 않는다")
    void skipExistingIndex() {
        JobSearchIndexService service = new JobSearchIndexService(
                elasticsearchOperations,
                jobSearchProperties,
                jobSearchIndexDefinition
        );

        given(elasticsearchOperations.indexOps(IndexCoordinates.of("jobflow-jobs")))
                .willReturn(indexOperations);
        given(indexOperations.exists()).willReturn(true);

        boolean created = service.createIndexIfMissing();

        assertThat(created).isFalse();

        verify(indexOperations, never()).create(jobSearchIndexDefinition.settings());
        verify(indexOperations, never()).putMapping(Document.from(jobSearchIndexDefinition.mapping()));
    }

    @Test
    @DisplayName("검색 index 존재 여부를 조회한다")
    void indexExists() {
        JobSearchIndexService service = new JobSearchIndexService(
                elasticsearchOperations,
                jobSearchProperties,
                jobSearchIndexDefinition
        );

        given(elasticsearchOperations.indexOps(IndexCoordinates.of("jobflow-jobs")))
                .willReturn(indexOperations);
        given(indexOperations.exists()).willReturn(true);

        boolean exists = service.indexExists();

        assertThat(exists).isTrue();
    }
}
