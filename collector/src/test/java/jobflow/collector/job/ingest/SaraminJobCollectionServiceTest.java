package jobflow.collector.job.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import jobflow.collector.job.Job;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class SaraminJobCollectionServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private SaraminJobSearchClient saraminJobSearchClient;

    @Mock
    private SaraminJobPostingMapper saraminJobPostingMapper;

    @Mock
    private JobIngestionService jobIngestionService;

    @Mock
    private Job firstJob;

    @Mock
    private Job secondJob;

    @Test
    @DisplayName("사람인 API 응답을 collect limit까지 mapper와 ingestion으로 처리한다")
    void collect() throws Exception {
        SaraminJobCollectionService service = new SaraminJobCollectionService(
                new SaraminApiProperties("api", "https://example.com/job-search", "test-key", 100),
                saraminJobSearchClient,
                saraminJobPostingMapper,
                jobIngestionService
        );
        JsonNode firstNode = jobNode("54092196");
        JsonNode secondNode = jobNode("54092197");
        IngestedJobPosting firstPosting = posting("54092196");
        IngestedJobPosting secondPosting = posting("54092197");

        given(saraminJobSearchClient.search(0, 2))
                .willReturn(List.of(firstNode, secondNode));
        given(saraminJobPostingMapper.map(firstNode)).willReturn(firstPosting);
        given(saraminJobPostingMapper.map(secondNode)).willReturn(secondPosting);
        given(jobIngestionService.ingest(firstPosting))
                .willReturn(new JobIngestionResult(JobIngestionResultType.CREATED, firstJob));
        given(jobIngestionService.ingest(secondPosting))
                .willReturn(new JobIngestionResult(JobIngestionResultType.UPDATED, secondJob));

        SaraminJobCollectionSummary summary = service.collect(2, 10);

        assertThat(summary.processedCount()).isEqualTo(2);
        assertThat(summary.collectedCount()).isEqualTo(2);
        assertThat(summary.createdCount()).isEqualTo(1);
        assertThat(summary.updatedCount()).isEqualTo(1);
        assertThat(summary.failedCount()).isZero();
        verify(jobIngestionService).ingest(firstPosting);
        verify(jobIngestionService).ingest(secondPosting);
    }

    @Test
    @DisplayName("사람인 API 응답 처리 중 일부 실패해도 다음 공고를 계속 처리한다")
    void collectWithPartialFailure() throws Exception {
        SaraminJobCollectionService service = new SaraminJobCollectionService(
                new SaraminApiProperties("api", "https://example.com/job-search", "test-key", 100),
                saraminJobSearchClient,
                saraminJobPostingMapper,
                jobIngestionService
        );
        JsonNode firstNode = jobNode("54092196");
        JsonNode secondNode = jobNode("54092197");
        IngestedJobPosting secondPosting = posting("54092197");

        given(saraminJobSearchClient.search(0, 2))
                .willReturn(List.of(firstNode, secondNode));
        given(saraminJobPostingMapper.map(firstNode))
                .willThrow(new JobPostingParseException("Required field is missing. field=title"));
        given(saraminJobPostingMapper.map(secondNode)).willReturn(secondPosting);
        given(jobIngestionService.ingest(secondPosting))
                .willReturn(new JobIngestionResult(JobIngestionResultType.CREATED, secondJob));

        SaraminJobCollectionSummary summary = service.collect(2, 10);

        assertThat(summary.processedCount()).isEqualTo(2);
        assertThat(summary.collectedCount()).isEqualTo(1);
        assertThat(summary.createdCount()).isEqualTo(1);
        assertThat(summary.updatedCount()).isZero();
        assertThat(summary.failedCount()).isEqualTo(1);
        verify(jobIngestionService, never()).ingest(posting("54092196"));
        verify(jobIngestionService).ingest(secondPosting);
    }

    private JsonNode jobNode(String externalId) throws Exception {
        return objectMapper.readTree("""
                {"id": "%s"}
                """.formatted(externalId));
    }

    private IngestedJobPosting posting(String externalId) {
        return new IngestedJobPosting(
                JobIngestionSource.SARAMIN,
                externalId,
                "백엔드 개발자",
                "JobFlow Labs",
                "Spring Boot 백엔드 개발",
                "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=" + externalId,
                "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=" + externalId,
                jobflow.collector.job.JobRole.BACKEND,
                "Java Spring Boot",
                jobflow.collector.job.CareerLevel.JUNIOR,
                1,
                3,
                null,
                jobflow.collector.job.EmploymentType.FULL_TIME,
                null,
                "IT",
                "KR",
                "Seoul",
                "Gangnam",
                jobflow.collector.job.RemoteType.ONSITE,
                null,
                null,
                "KRW",
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                "{\"id\":\"%s\"}".formatted(externalId),
                "saramin-api-scaffold-0.1"
        );
    }
}
