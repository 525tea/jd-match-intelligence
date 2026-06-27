package jobflow.domain.job.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.index.AliasAction;
import org.springframework.data.elasticsearch.core.index.AliasActionParameters;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

@ExtendWith(MockitoExtension.class)
class JobSearchIndexServiceTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private IndexOperations indexOperations;

    private final JobSearchProperties jobSearchProperties = new JobSearchProperties(
            "http://localhost:9200",
            "jobflow-jobs",
            "jobflow-jobs-v1",
            false,
            true
    );

    private final JobSearchIndexDefinition jobSearchIndexDefinition = new JobSearchIndexDefinition(1, 0);

    @Test
    @DisplayName("검색 physical index가 없으면 settings와 mapping으로 생성하고 alias를 연결한다")
    void createIndexIfMissing() {
        JobSearchIndexService service = new JobSearchIndexService(
                elasticsearchOperations,
                jobSearchProperties,
                jobSearchIndexDefinition
        );

        given(elasticsearchOperations.indexOps(IndexCoordinates.of("jobflow-jobs-v1")))
                .willReturn(indexOperations);
        given(indexOperations.exists()).willReturn(false);

        boolean created = service.createIndexIfMissing();

        assertThat(created).isTrue();

        verify(indexOperations).create(jobSearchIndexDefinition.settings());
        verify(indexOperations).putMapping(Document.from(jobSearchIndexDefinition.mapping()));
        assertAliasAction();
    }

    @Test
    @DisplayName("검색 physical index가 이미 있으면 생성하지 않고 alias만 보장한다")
    void skipExistingIndex() {
        JobSearchIndexService service = new JobSearchIndexService(
                elasticsearchOperations,
                jobSearchProperties,
                jobSearchIndexDefinition
        );

        given(elasticsearchOperations.indexOps(IndexCoordinates.of("jobflow-jobs-v1")))
                .willReturn(indexOperations);
        given(indexOperations.exists()).willReturn(true);

        boolean created = service.createIndexIfMissing();

        assertThat(created).isFalse();

        verify(indexOperations, never()).create(jobSearchIndexDefinition.settings());
        verify(indexOperations, never()).putMapping(Document.from(jobSearchIndexDefinition.mapping()));
        assertAliasAction();
    }

    @Test
    @DisplayName("검색 physical index 존재 확인이 빈 응답 오류로 실패하면 index를 생성하고 alias를 연결한다")
    void createIndexWhenExistsCheckFailsWithEmptyResponse() {
        JobSearchIndexService service = new JobSearchIndexService(
                elasticsearchOperations,
                jobSearchProperties,
                jobSearchIndexDefinition
        );

        given(elasticsearchOperations.indexOps(IndexCoordinates.of("jobflow-jobs-v1")))
                .willReturn(indexOperations);
        given(indexOperations.exists())
                .willThrow(new RuntimeException(
                        "node: http://localhost:9200/, status: 400, [es/indices.exists] Expecting a response body, but none was sent"
                ));

        boolean created = service.createIndexIfMissing();

        assertThat(created).isTrue();

        verify(indexOperations).create(jobSearchIndexDefinition.settings());
        verify(indexOperations).putMapping(Document.from(jobSearchIndexDefinition.mapping()));
        assertAliasAction();
    }

    @Test
    @DisplayName("검색 physical index 존재 여부를 조회한다")
    void indexExists() {
        JobSearchIndexService service = new JobSearchIndexService(
                elasticsearchOperations,
                jobSearchProperties,
                jobSearchIndexDefinition
        );

        given(elasticsearchOperations.indexOps(IndexCoordinates.of("jobflow-jobs-v1")))
                .willReturn(indexOperations);
        given(indexOperations.exists()).willReturn(true);

        boolean exists = service.indexExists();

        assertThat(exists).isTrue();

        verify(indexOperations, never()).alias(any(AliasActions.class));
    }

    private void assertAliasAction() {
        ArgumentCaptor<AliasActions> captor = ArgumentCaptor.forClass(AliasActions.class);

        verify(indexOperations).alias(captor.capture());

        AliasActions aliasActions = captor.getValue();
        assertThat(aliasActions.getActions()).hasSize(1);

        AliasAction action = aliasActions.getActions().getFirst();
        assertThat(action).isInstanceOf(AliasAction.Add.class);

        AliasActionParameters parameters = action.getParameters();
        assertThat(parameters.getIndices()).containsExactly("jobflow-jobs-v1");
        assertThat(parameters.getAliases()).containsExactly("jobflow-jobs");
        assertThat(parameters.getWriteIndex()).isTrue();
    }
}
