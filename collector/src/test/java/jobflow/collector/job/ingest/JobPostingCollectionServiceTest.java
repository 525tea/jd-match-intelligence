package jobflow.collector.job.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobPostingCollectionServiceTest {

    @Mock
    private JobPostingFetchService jobPostingFetchService;

    @Mock
    private JobPostingPreFilter jobPostingPreFilter;

    @Mock
    private JobPostingParser jobPostingParser;

    @Mock
    private JobIngestionService jobIngestionService;

    @Test
    @DisplayName("공고 후보를 fetch, pre-filter, parse, ingest 순서로 처리한다")
    void collect() {
        CrawlerUrlCandidate candidate = createCandidate();
        FetchedJobPosting fetched = createFetched(candidate);
        IngestedJobPosting posting = createPosting();
        Job job = createJob();
        JobPostingCollectionService service = new JobPostingCollectionService(
                jobPostingFetchService,
                List.of(jobPostingPreFilter),
                List.of(jobPostingParser),
                jobIngestionService
        );

        given(jobPostingFetchService.fetch(candidate)).willReturn(fetched);
        given(jobPostingPreFilter.supports(JobIngestionSource.ZIGHANG)).willReturn(true);
        given(jobPostingPreFilter.shouldSkip(fetched)).willReturn(false);
        given(jobPostingParser.supports(JobIngestionSource.ZIGHANG)).willReturn(true);
        given(jobPostingParser.parse(fetched)).willReturn(posting);
        given(jobIngestionService.ingest(posting))
                .willReturn(new JobIngestionResult(
                        JobIngestionResultType.CREATED,
                        job,
                        List.of(createDuplicateCandidateJob())
                ));

        JobPostingCollectionResult result = service.collect(candidate);

        assertThat(result.candidate()).isEqualTo(candidate);
        assertThat(result.success()).isTrue();
        assertThat(result.ingestionResultType()).isEqualTo(JobIngestionResultType.CREATED);
        assertThat(result.errorMessage()).isNull();
        assertThat(result.hasDuplicateCandidates()).isTrue();
        assertThat(result.duplicateCandidateCount()).isEqualTo(1);

        verify(jobPostingFetchService).fetch(candidate);
        verify(jobPostingPreFilter).shouldSkip(fetched);
        verify(jobPostingParser).parse(fetched);
        verify(jobIngestionService).ingest(posting);
    }

    @Test
    @DisplayName("pre-filter가 skip하면 parse와 ingest를 실행하지 않는다")
    void skippedByPreFilter() {
        CrawlerUrlCandidate candidate = createCandidate();
        FetchedJobPosting fetched = createFetched(candidate);
        JobPostingCollectionService service = new JobPostingCollectionService(
                jobPostingFetchService,
                List.of(jobPostingPreFilter),
                List.of(jobPostingParser),
                jobIngestionService
        );

        given(jobPostingFetchService.fetch(candidate)).willReturn(fetched);
        given(jobPostingPreFilter.supports(JobIngestionSource.ZIGHANG)).willReturn(true);
        given(jobPostingPreFilter.shouldSkip(fetched)).willReturn(true);

        JobPostingCollectionResult result = service.collect(candidate);

        assertThat(result.candidate()).isEqualTo(candidate);
        assertThat(result.success()).isTrue();
        assertThat(result.ingestionResultType()).isEqualTo(JobIngestionResultType.SKIPPED);
        assertThat(result.errorMessage()).isNull();
        assertThat(result.hasDuplicateCandidates()).isFalse();
        assertThat(result.duplicateCandidateCount()).isZero();

        verify(jobPostingFetchService).fetch(candidate);
        verify(jobPostingPreFilter).shouldSkip(fetched);
        verify(jobPostingParser, never()).parse(fetched);
        verify(jobIngestionService, never()).ingest(createPosting());
    }

    @Test
    @DisplayName("지원하는 pre-filter가 없으면 실패 결과를 반환한다")
    void preFilterNotFound() {
        CrawlerUrlCandidate candidate = createCandidate();
        FetchedJobPosting fetched = createFetched(candidate);
        JobPostingCollectionService service = new JobPostingCollectionService(
                jobPostingFetchService,
                List.of(jobPostingPreFilter),
                List.of(jobPostingParser),
                jobIngestionService
        );

        given(jobPostingFetchService.fetch(candidate)).willReturn(fetched);
        given(jobPostingPreFilter.supports(JobIngestionSource.ZIGHANG)).willReturn(false);

        JobPostingCollectionResult result = service.collect(candidate);

        assertThat(result.candidate()).isEqualTo(candidate);
        assertThat(result.success()).isFalse();
        assertThat(result.ingestionResultType()).isNull();
        assertThat(result.errorMessage()).contains("Job posting pre-filter not found");
        assertThat(result.hasDuplicateCandidates()).isFalse();
        assertThat(result.duplicateCandidateCount()).isZero();
    }

    @Test
    @DisplayName("지원하는 parser가 없으면 실패 결과를 반환한다")
    void parserNotFound() {
        CrawlerUrlCandidate candidate = createCandidate();
        FetchedJobPosting fetched = createFetched(candidate);
        JobPostingCollectionService service = new JobPostingCollectionService(
                jobPostingFetchService,
                List.of(jobPostingPreFilter),
                List.of(jobPostingParser),
                jobIngestionService
        );

        given(jobPostingFetchService.fetch(candidate)).willReturn(fetched);
        given(jobPostingPreFilter.supports(JobIngestionSource.ZIGHANG)).willReturn(true);
        given(jobPostingPreFilter.shouldSkip(fetched)).willReturn(false);
        given(jobPostingParser.supports(JobIngestionSource.ZIGHANG)).willReturn(false);

        JobPostingCollectionResult result = service.collect(candidate);

        assertThat(result.candidate()).isEqualTo(candidate);
        assertThat(result.success()).isFalse();
        assertThat(result.ingestionResultType()).isNull();
        assertThat(result.errorMessage()).contains("Job posting parser not found");
        assertThat(result.hasDuplicateCandidates()).isFalse();
        assertThat(result.duplicateCandidateCount()).isZero();
    }

    private CrawlerUrlCandidate createCandidate() {
        return new CrawlerUrlCandidate(
                JobIngestionSource.ZIGHANG,
                "https://zighang.com/recruitment/00000000-0000-0000-0000-000000000100?utm=test",
                "https://zighang.com/recruitment/00000000-0000-0000-0000-000000000100",
                "00000000-0000-0000-0000-000000000100"
        );
    }

    private FetchedJobPosting createFetched(CrawlerUrlCandidate candidate) {
        return new FetchedJobPosting(
                candidate.source(),
                candidate.externalId(),
                candidate.sourceUrl(),
                candidate.detailUrl(),
                "<html><body><h1>Backend Engineer</h1></body></html>"
        );
    }

    private IngestedJobPosting createPosting() {
        return new IngestedJobPosting(
                JobIngestionSource.ZIGHANG,
                "00000000-0000-0000-0000-000000000100",
                "Backend Engineer",
                "JobFlow Labs",
                "Spring Boot 기반 백엔드 개발자를 채용합니다.",
                "https://zighang.com/recruitment/00000000-0000-0000-0000-000000000100?utm=test",
                "https://zighang.com/recruitment/00000000-0000-0000-0000-000000000100",
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
                        {"source":"ZIGHANG","externalId":"00000000-0000-0000-0000-000000000100"}
                        """,
                "zighang-parser-0.1"
        );
    }

    private Job createJob() {
        return Job.create(
                "ZIGHANG",
                "00000000-0000-0000-0000-000000000100",
                "Backend Engineer",
                "JobFlow Labs",
                "Spring Boot 기반 백엔드 개발자를 채용합니다.",
                "https://zighang.com/recruitment/00000000-0000-0000-0000-000000000100",
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

    private Job createDuplicateCandidateJob() {
        Job job = Job.create(
                "JUMPIT",
                "jumpit-example-1",
                "Backend Engineer",
                "JobFlow Labs",
                "Spring Boot 기반 백엔드 개발자를 채용합니다.",
                "https://jumpit.saramin.co.kr/position/jumpit-example-1",
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

        job.updateCrawlingMetadata(
                "duplicate-fingerprint",
                "https://jumpit.saramin.co.kr/position/jumpit-example-1",
                LocalDateTime.of(2026, 6, 4, 10, 0),
                LocalDateTime.of(2026, 6, 4, 10, 0),
                null,
                """
                        {"source":"JUMPIT","externalId":"jumpit-example-1"}
                        """,
                "jumpit-parser-0.1"
        );

        return job;
    }
}
