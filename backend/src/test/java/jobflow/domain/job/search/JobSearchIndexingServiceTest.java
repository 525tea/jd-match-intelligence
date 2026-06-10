package jobflow.domain.job.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import jobflow.domain.job.Job;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.IndexOperations;

@ExtendWith(MockitoExtension.class)
class JobSearchIndexingServiceTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private JobSearchIndexService jobSearchIndexService;

    @Mock
    private JobSearchDocumentMapper jobSearchDocumentMapper;

    @Mock
    private IndexOperations indexOperations;

    @Mock
    private Job job;

    @Mock
    private Job anotherJob;

    private final JobSearchProperties jobSearchProperties = new JobSearchProperties(
            "http://localhost:9200",
            "jobflow-jobs",
            "jobflow-jobs-v1",
            false
    );

    @Test
    @DisplayName("Job을 검색 document로 변환해 Elasticsearch alias에 색인한다")
    void index() {
        JobSearchIndexingService service = new JobSearchIndexingService(
                elasticsearchOperations,
                jobSearchProperties,
                jobSearchIndexService,
                jobSearchDocumentMapper
        );
        JobSearchDocument document = document("1");

        given(jobSearchDocumentMapper.toDocument(job)).willReturn(document);
        given(elasticsearchOperations.save(document, IndexCoordinates.of("jobflow-jobs")))
                .willReturn(document);

        JobSearchDocument indexedDocument = service.index(job);

        assertThat(indexedDocument).isEqualTo(document);

        verify(jobSearchIndexService).createIndexIfMissing();
        verify(jobSearchDocumentMapper).toDocument(job);
        verify(elasticsearchOperations).save(document, IndexCoordinates.of("jobflow-jobs"));
    }

    @Test
    @DisplayName("Job 목록을 검색 document 목록으로 변환해 Elasticsearch alias에 bulk 색인한다")
    void indexAll() {
        JobSearchIndexingService service = new JobSearchIndexingService(
                elasticsearchOperations,
                jobSearchProperties,
                jobSearchIndexService,
                jobSearchDocumentMapper
        );
        JobSearchDocument document = document("1");
        JobSearchDocument anotherDocument = document("2");
        List<JobSearchDocument> documents = List.of(document, anotherDocument);

        given(jobSearchDocumentMapper.toDocument(job)).willReturn(document);
        given(jobSearchDocumentMapper.toDocument(anotherJob)).willReturn(anotherDocument);
        given(elasticsearchOperations.save(documents, IndexCoordinates.of("jobflow-jobs")))
                .willReturn(documents);

        List<JobSearchDocument> indexedDocuments = service.indexAll(List.of(job, anotherJob));

        assertThat(indexedDocuments).containsExactly(document, anotherDocument);

        verify(jobSearchIndexService).createIndexIfMissing();
        verify(jobSearchDocumentMapper).toDocument(job);
        verify(jobSearchDocumentMapper).toDocument(anotherJob);
        verify(elasticsearchOperations).save(documents, IndexCoordinates.of("jobflow-jobs"));
    }

    @Test
    @DisplayName("검색 alias index를 refresh한다")
    void refresh() {
        JobSearchIndexingService service = new JobSearchIndexingService(
                elasticsearchOperations,
                jobSearchProperties,
                jobSearchIndexService,
                jobSearchDocumentMapper
        );

        given(elasticsearchOperations.indexOps(IndexCoordinates.of("jobflow-jobs")))
                .willReturn(indexOperations);

        service.refresh();

        verify(elasticsearchOperations).indexOps(IndexCoordinates.of("jobflow-jobs"));
        verify(indexOperations).refresh();
    }

    private JobSearchDocument document(String id) {
        return new JobSearchDocument(
                id,
                "ZIGHANG",
                "job-" + id,
                "jobflow|backend developer|seoul",
                "백엔드 개발자",
                "JobFlow",
                "Spring Boot와 Kubernetes 기반 서비스를 개발합니다.",
                "BACKEND",
                "백엔드",
                "JUNIOR",
                "FULL_TIME",
                "IT",
                "KR",
                "서울",
                "강남",
                "HYBRID",
                LocalDateTime.of(2026, 7, 1, 23, 59),
                LocalDateTime.of(2026, 6, 5, 9, 0),
                LocalDateTime.of(2026, 6, 5, 9, 0)
        );
    }
}
