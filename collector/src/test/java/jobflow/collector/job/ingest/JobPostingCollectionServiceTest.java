package jobflow.collector.job.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import jobflow.collector.job.CareerLevel;
import jobflow.collector.job.EmploymentType;
import jobflow.collector.job.Job;
import jobflow.collector.job.JobRole;
import jobflow.collector.job.RemoteType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class JobPostingCollectionServiceTest {

    @Mock
    private JobPostingFetchService jobPostingFetchService;

    @Mock
    private JobPostingParser jobPostingParser;

    @Mock
    private JobIngestionService jobIngestionService;

    @Test
    @DisplayName("공고 후보를 fetch, parse, ingest 순서로 처리한다")
    void collect() {
        CrawlerUrlCandidate candidate = createCandidate();
        FetchedJobPosting fetched = new FetchedJobPosting(
                JobIngestionSource.ZIGHANG,
                "zighang-example-1",
                candidate.sourceUrl(),
                candidate.detailUrl(),
                "<html><body><h1>Backend Engineer</h1></body></html>"
        );
        IngestedJobPosting posting = createPosting();
        Job job = createJob();
        JobPostingCollectionService service = new JobPostingCollectionService(
                jobPostingFetchService,
                List.of(jobPostingParser),
                jobIngestionService
        );

        given(jobPostingFetchService.fetch(candidate)).willReturn(fetched);
        given(jobPostingParser.supports(JobIngestionSource.ZIGHANG)).willReturn(true);
        given(jobPostingParser.parse(fetched)).willReturn(posting);
        given(jobIngestionService.ingest(posting))
                .willReturn(new JobIngestionResult(JobIngestionResultType.CREATED, job));

        JobPostingCollectionResult result = service.collect(candidate);

        assertThat(result.candidate()).isEqualTo(candidate);
        assertThat(result.ingestionResultType()).isEqualTo(JobIngestionResultType.CREATED);

        verify(jobPostingFetchService).fetch(candidate);
        verify(jobPostingParser).parse(fetched);
        verify(jobIngestionService).ingest(posting);
    }

    @Test
    @DisplayName("지원하는 parser가 없으면 예외를 던진다")
    void parserNotFound() {
        CrawlerUrlCandidate candidate = createCandidate();
        FetchedJobPosting fetched = new FetchedJobPosting(
                JobIngestionSource.ZIGHANG,
                "zighang-example-1",
                candidate.sourceUrl(),
                candidate.detailUrl(),
                "<html><body><h1>Backend Engineer</h1></body></html>"
        );
        JobPostingCollectionService service = new JobPostingCollectionService(
                jobPostingFetchService,
                List.of(jobPostingParser),
                jobIngestionService
        );

        given(jobPostingFetchService.fetch(candidate)).willReturn(fetched);
        given(jobPostingParser.supports(JobIngestionSource.ZIGHANG)).willReturn(false);

        assertThatThrownBy(() -> service.collect(candidate))
                .isInstanceOf(JobPostingParserNotFoundException.class)
                .hasMessageContaining("Job posting parser not found");
    }

    private CrawlerUrlCandidate createCandidate() {
        return new CrawlerUrlCandidate(
                JobIngestionSource.ZIGHANG,
                "https://zighang.com/jobs/zighang-example-1?utm=test",
                "https://zighang.com/jobs/zighang-example-1",
                "zighang-example-1"
        );
    }

    private IngestedJobPosting createPosting() {
        return new IngestedJobPosting(
                JobIngestionSource.ZIGHANG,
                "zighang-example-1",
                "Backend Engineer",
                "JobFlow Labs",
                "Spring Boot 기반 백엔드 개발자를 채용합니다.",
                "https://zighang.com/jobs/zighang-example-1?utm=test",
                "https://zighang.com/jobs/zighang-example-1",
                JobRole.BACKEND,
                "Java/Spring",
                CareerLevel.JUNIOR,
                0,
                3,
                "학력무관",
                EmploymentType.FULL_TIME,
                "STARTUP",
                "IT",
                "KR",
                "Seoul",
                "Gangnam",
                RemoteType.HYBRID,
                null,
                null,
                "KRW",
                false,
                null,
                null,
                null,
                LocalDateTime.of(2026, 6, 4, 10, 0),
                LocalDateTime.of(2026, 6, 4, 10, 0),
                null,
                """
                        {"source":"ZIGHANG","externalId":"zighang-example-1"}
                        """,
                "zighang-parser-0.1"
        );
    }

    private Job createJob() {
        return Job.create(
                "ZIGHANG",
                "zighang-example-1",
                "Backend Engineer",
                "JobFlow Labs",
                "Spring Boot 기반 백엔드 개발자를 채용합니다.",
                "https://zighang.com/jobs/zighang-example-1",
                JobRole.BACKEND,
                "Java/Spring",
                CareerLevel.JUNIOR,
                0,
                3,
                "학력무관",
                EmploymentType.FULL_TIME,
                "STARTUP",
                "IT",
                "KR",
                "Seoul",
                "Gangnam",
                RemoteType.HYBRID,
                null,
                null,
                "KRW",
                false,
                null,
                null,
                null
        );
    }
}
