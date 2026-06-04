package jobflow.collector.job.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class JobPostingFetchServiceTest {

    @Mock
    private RobotsPolicyService robotsPolicyService;

    @Mock
    private CrawlerHttpClient crawlerHttpClient;

    @Test
    @DisplayName("robots.txt 허용 확인 후 상세 공고 HTML을 조회한다")
    void fetch() {
        CrawlerUrlCandidate candidate = new CrawlerUrlCandidate(
                JobIngestionSource.ZIGHANG,
                "https://zighang.com/jobs/example-1?utm=test",
                "https://zighang.com/jobs/example-1",
                "example-1"
        );
        JobPostingFetchService service = new JobPostingFetchService(
                robotsPolicyService,
                crawlerHttpClient
        );

        given(crawlerHttpClient.get("https://zighang.com/jobs/example-1"))
                .willReturn(new CrawlerHttpResponse(
                        200,
                        """
                                <html>
                                  <body>
                                    <h1>Backend Engineer</h1>
                                  </body>
                                </html>
                                """
                ));

        FetchedJobPosting fetched = service.fetch(candidate);

        assertThat(fetched.source()).isEqualTo(JobIngestionSource.ZIGHANG);
        assertThat(fetched.externalId()).isEqualTo("example-1");
        assertThat(fetched.sourceUrl()).isEqualTo("https://zighang.com/jobs/example-1?utm=test");
        assertThat(fetched.detailUrl()).isEqualTo("https://zighang.com/jobs/example-1");
        assertThat(fetched.body()).contains("Backend Engineer");

        verify(robotsPolicyService).assertAllowed(
                JobIngestionSource.ZIGHANG,
                "https://zighang.com/jobs/example-1"
        );
    }

    @Test
    @DisplayName("상세 공고 조회 실패 시 예외를 던진다")
    void fetchFailed() {
        CrawlerUrlCandidate candidate = new CrawlerUrlCandidate(
                JobIngestionSource.ZIGHANG,
                "https://zighang.com/jobs/example-1",
                "https://zighang.com/jobs/example-1",
                "example-1"
        );
        JobPostingFetchService service = new JobPostingFetchService(
                robotsPolicyService,
                crawlerHttpClient
        );

        given(crawlerHttpClient.get("https://zighang.com/jobs/example-1"))
                .willReturn(new CrawlerHttpResponse(500, "server error"));

        assertThatThrownBy(() -> service.fetch(candidate))
                .isInstanceOf(JobPostingFetchException.class)
                .hasMessageContaining("Failed to fetch job posting");

        verify(robotsPolicyService).assertAllowed(
                JobIngestionSource.ZIGHANG,
                "https://zighang.com/jobs/example-1"
        );
    }
}
