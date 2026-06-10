package jobflow.domain.job.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import jobflow.domain.job.Job;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

@ExtendWith(MockitoExtension.class)
class JobSearchIndexingServiceTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private JobSearchIndexService jobSearchIndexService;

    @Mock
    private JobSearchDocumentMapper jobSearchDocumentMapper;

    @Mock
    private Job job;

    private final JobSearchProperties jobSearchProperties = new JobSearchProperties(
            "http://localhost:9200",
            "jobflow-jobs",
            "jobflow-jobs-v1",
            false
    );

    @Test
    @DisplayName("Job을 검색 document로 변환해 Elasticsearch에 색인한다")
    void index() {
        JobSearchIndexingService service = new JobSearchIndexingService(
                elasticsearchOperations,
                jobSearchProperties,
                jobSearchIndexService,
                jobSearchDocumentMapper
        );
        JobSearchDocument document = document();

        given(jobSearchDocumentMapper.toDocument(job)).willReturn(document);
        given(elasticsearchOperations.save(document, IndexCoordinates.of("jobflow-jobs")))
                .willReturn(document);

        JobSearchDocument indexedDocument = service.index(job);

        assertThat(indexedDocument).isEqualTo(document);

        verify(jobSearchIndexService).createIndexIfMissing();
        verify(jobSearchDocumentMapper).toDocument(job);
        verify(elasticsearchOperations).save(document, IndexCoordinates.of("jobflow-jobs"));
    }

    private JobSearchDocument document() {
        return new JobSearchDocument(
                "1",
                "ZIGHANG",
                "job-1",
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
