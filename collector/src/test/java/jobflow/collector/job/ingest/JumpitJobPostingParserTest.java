package jobflow.collector.job.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jobflow.collector.job.CareerLevel;
import jobflow.collector.job.EmploymentType;
import jobflow.collector.job.JobRole;
import jobflow.collector.job.RemoteType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JumpitJobPostingParserTest {

    private final JumpitJobPostingParser parser = new JumpitJobPostingParser(
            new JdJobRoleClassificationService()
    );

    @Test
    @DisplayName("점핏 상세 HTML을 수집 공고로 변환한다")
    void parse() {
        FetchedJobPosting fetched = new FetchedJobPosting(
                JobIngestionSource.JUMPIT,
                "jumpit-example-1",
                "https://jumpit.saramin.co.kr/position/jumpit-example-1?utm=test",
                "https://jumpit.saramin.co.kr/position/jumpit-example-1",
                """
                        <html>
                          <head>
                            <meta property="og:title" content="Backend Engineer">
                          </head>
                          <body>
                            <main>
                              <h1>Backend Engineer</h1>
                              <section class="company-name">JobFlow Labs</section>
                              <section data-testid="position-description">
                                Java, Spring Boot 기반 백엔드 개발자를 채용합니다.
                                주니어 1년 이상, 서울 하이브리드 근무를 지원합니다.
                              </section>
                            </main>
                          </body>
                        </html>
                        """
        );

        IngestedJobPosting posting = parser.parse(fetched);

        assertThat(posting.source()).isEqualTo(JobIngestionSource.JUMPIT);
        assertThat(posting.externalId()).isEqualTo("jumpit-example-1");
        assertThat(posting.title()).isEqualTo("Backend Engineer");
        assertThat(posting.companyName()).isEqualTo("JobFlow Labs");
        assertThat(posting.description()).contains("Spring Boot");
        assertThat(posting.sourceUrl()).isEqualTo("https://jumpit.saramin.co.kr/position/jumpit-example-1?utm=test");
        assertThat(posting.detailUrl()).isEqualTo("https://jumpit.saramin.co.kr/position/jumpit-example-1");
        assertThat(posting.role()).isEqualTo(JobRole.BACKEND);
        assertThat(posting.careerLevel()).isEqualTo(CareerLevel.JUNIOR);
        assertThat(posting.employmentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(posting.remoteType()).isEqualTo(RemoteType.HYBRID);
        assertThat(posting.locationCountry()).isEqualTo("KR");
        assertThat(posting.locationRegion()).isEqualTo("Seoul");
        assertThat(posting.salaryCurrency()).isEqualTo("KRW");
        assertThat(posting.rawData()).contains("jumpit-example-1");
        assertThat(posting.crawlerVersion()).isEqualTo("jumpit-parser-0.1");
        assertThat(posting.collectedAt()).isNotNull();
        assertThat(posting.lastSeenAt()).isNotNull();
    }

    @Test
    @DisplayName("지원하지 않는 source는 파싱하지 않는다")
    void unsupportedSource() {
        FetchedJobPosting fetched = new FetchedJobPosting(
                JobIngestionSource.ZIGHANG,
                "zighang-example-1",
                "https://zighang.com/jobs/zighang-example-1",
                "https://zighang.com/jobs/zighang-example-1",
                "<html><body><h1>Backend Engineer</h1></body></html>"
        );

        assertThatThrownBy(() -> parser.parse(fetched))
                .isInstanceOf(JobPostingParseException.class)
                .hasMessageContaining("Unsupported source");
    }

    @Test
    @DisplayName("필수 필드가 없으면 예외를 던진다")
    void missingRequiredField() {
        FetchedJobPosting fetched = new FetchedJobPosting(
                JobIngestionSource.JUMPIT,
                "jumpit-example-1",
                "https://jumpit.saramin.co.kr/position/jumpit-example-1",
                "https://jumpit.saramin.co.kr/position/jumpit-example-1",
                "<html><body></body></html>"
        );

        assertThatThrownBy(() -> parser.parse(fetched))
                .isInstanceOf(JobPostingParseException.class)
                .hasMessageContaining("Required field is missing");
    }
}
