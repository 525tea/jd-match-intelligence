package jobflow.collector.job.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import jobflow.collector.job.CareerLevel;
import jobflow.collector.job.EmploymentType;
import jobflow.collector.job.JobRole;
import jobflow.collector.job.RemoteType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ZighangJobPostingParserTest {

    private final ZighangJobPostingParser parser = new ZighangJobPostingParser(
            new JdJobRoleClassificationService()
    );

    @Test
    @DisplayName("직행 상세 HTML을 수집 공고로 변환한다")
    void parse() {
        FetchedJobPosting fetched = new FetchedJobPosting(
                JobIngestionSource.ZIGHANG,
                "00000000-0000-0000-0000-000000000100",
                "https://zighang.com/recruitment/00000000-0000-0000-0000-000000000100?utm=test",
                "https://zighang.com/recruitment/00000000-0000-0000-0000-000000000100",
                """
                        <html>
                          <head>
                            <meta property="og:title" content="Backend Engineer | JobFlow Labs | IT_개발 | 직행">
                            <title>Backend Engineer | JobFlow Labs | IT_개발 | 직행</title>
                          </head>
                          <body>
                            <main>
                              <h1>Backend Engineer</h1>
                              <a href="/company/company-example-1">JobFlow Labs</a> |2026. 6. 1. 게시|
                              <section data-testid="job-description">
                                Java, Spring Boot 기반 백엔드 개발자를 채용합니다.
                                주니어 1년 이상, 서울 성동 하이브리드 근무를 지원합니다.
                                2026. 7. 31. 마감
                              </section>
                            </main>
                          </body>
                        </html>
                        """
        );

        IngestedJobPosting posting = parser.parse(fetched);

        assertThat(posting.source()).isEqualTo(JobIngestionSource.ZIGHANG);
        assertThat(posting.externalId()).isEqualTo("00000000-0000-0000-0000-000000000100");
        assertThat(posting.title()).isEqualTo("Backend Engineer");
        assertThat(posting.companyName()).isEqualTo("JobFlow Labs");
        assertThat(posting.description()).contains("Spring Boot");
        assertThat(posting.sourceUrl())
                .isEqualTo("https://zighang.com/recruitment/00000000-0000-0000-0000-000000000100?utm=test");
        assertThat(posting.detailUrl())
                .isEqualTo("https://zighang.com/recruitment/00000000-0000-0000-0000-000000000100");
        assertThat(posting.role()).isEqualTo(JobRole.BACKEND);
        assertThat(posting.careerLevel()).isEqualTo(CareerLevel.JUNIOR);
        assertThat(posting.employmentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(posting.remoteType()).isEqualTo(RemoteType.HYBRID);
        assertThat(posting.locationCountry()).isEqualTo("KR");
        assertThat(posting.locationRegion()).isEqualTo("Seoul");
        assertThat(posting.locationCity()).isEqualTo("Seongdong");
        assertThat(posting.deadlineAt()).isEqualTo(LocalDateTime.of(2026, 7, 31, 23, 59));
        assertThat(posting.openedAt()).isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0));
        assertThat(posting.salaryCurrency()).isEqualTo("KRW");
        assertThat(posting.rawData()).contains("00000000-0000-0000-0000-000000000100");
        assertThat(posting.crawlerVersion()).isEqualTo("zighang-parser-0.1");
        assertThat(posting.collectedAt()).isNotNull();
        assertThat(posting.lastSeenAt()).isNotNull();
    }

    @Test
    @DisplayName("회사명 selector가 CTA 텍스트만 잡으면 파싱하지 않는다")
    void rejectInvalidCompanyName() {
        FetchedJobPosting fetched = new FetchedJobPosting(
                JobIngestionSource.ZIGHANG,
                "00000000-0000-0000-0000-000000000100",
                "https://zighang.com/recruitment/00000000-0000-0000-0000-000000000100",
                "https://zighang.com/recruitment/00000000-0000-0000-0000-000000000100",
                """
                        <html>
                          <body>
                            <main>
                              <h1>Backend Engineer</h1>
                              <section class="company-name">기업 정보 보기</section>
                              <section data-testid="job-description">
                                Java, Spring Boot 기반 백엔드 개발자를 채용합니다.
                              </section>
                            </main>
                          </body>
                        </html>
                        """
        );

        assertThatThrownBy(() -> parser.parse(fetched))
                .isInstanceOf(JobPostingParseException.class)
                .hasMessageContaining("companyName");
    }

    @Test
    @DisplayName("지원하지 않는 source는 파싱하지 않는다")
    void unsupportedSource() {
        FetchedJobPosting fetched = new FetchedJobPosting(
                JobIngestionSource.JUMPIT,
                "12345",
                "https://jumpit.saramin.co.kr/position/12345",
                "https://jumpit.saramin.co.kr/position/12345",
                "<html><body><h1>Frontend Engineer</h1></body></html>"
        );

        assertThatThrownBy(() -> parser.parse(fetched))
                .isInstanceOf(JobPostingParseException.class)
                .hasMessageContaining("Unsupported source");
    }

    @Test
    @DisplayName("필수 필드가 없으면 예외를 던진다")
    void missingRequiredField() {
        FetchedJobPosting fetched = new FetchedJobPosting(
                JobIngestionSource.ZIGHANG,
                "00000000-0000-0000-0000-000000000100",
                "https://zighang.com/recruitment/00000000-0000-0000-0000-000000000100",
                "https://zighang.com/recruitment/00000000-0000-0000-0000-000000000100",
                "<html><body></body></html>"
        );

        assertThatThrownBy(() -> parser.parse(fetched))
                .isInstanceOf(JobPostingParseException.class)
                .hasMessageContaining("Required field is missing");
    }
}
