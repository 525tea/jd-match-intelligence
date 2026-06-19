package jobflow.collector.job.ingest;

import jobflow.collector.job.CareerLevel;
import jobflow.collector.job.EmploymentType;
import jobflow.collector.job.JobRole;
import jobflow.collector.job.RemoteType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JumpitJobPostingParserTest {

    private final JumpitJobPostingParser parser = new JumpitJobPostingParser(
            new JdJobRoleClassificationService()
    );

    @Test
    @DisplayName("점핏 상세 HTML을 수집 공고로 변환한다")
    void parse() {
        FetchedJobPosting fetched = new FetchedJobPosting(
                JobIngestionSource.JUMPIT,
                "12345",
                "https://jumpit.saramin.co.kr/position/12345?utm=test",
                "https://jumpit.saramin.co.kr/position/12345",
                """
                        <html>
                          <head>
                            <meta property="og:title" content="Backend Engineer">
                          </head>
                          <body>
                            <main>
                              <h1>Backend Engineer</h1>
                              <a href="/company/company-example-1">JobFlow Labs</a>
                              <section data-testid="position-description">
                                Java, Spring Boot 기반 백엔드 개발자를 채용합니다.
                                포지션 경력/학력/마감일/근무지역 정보
                                경력 경력 1~10년
                                학력 대학교졸업(4년) 이상
                                마감일 2026-07-31
                                근무지역 경기 과천시 과천대로7길 65
                              </section>
                            </main>
                          </body>
                        </html>
                        """
        );

        IngestedJobPosting posting = parser.parse(fetched);

        assertThat(posting.source()).isEqualTo(JobIngestionSource.JUMPIT);
        assertThat(posting.externalId()).isEqualTo("12345");
        assertThat(posting.title()).isEqualTo("Backend Engineer");
        assertThat(posting.companyName()).isEqualTo("JobFlow Labs");
        assertThat(posting.description()).contains("Spring Boot");
        assertThat(posting.sourceUrl()).isEqualTo("https://jumpit.saramin.co.kr/position/12345?utm=test");
        assertThat(posting.detailUrl()).isEqualTo("https://jumpit.saramin.co.kr/position/12345");
        assertThat(posting.role()).isEqualTo(JobRole.BACKEND);
        assertThat(posting.careerLevel()).isEqualTo(CareerLevel.JUNIOR);
        assertThat(posting.employmentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(posting.remoteType()).isEqualTo(RemoteType.ONSITE);
        assertThat(posting.locationCountry()).isEqualTo("KR");
        assertThat(posting.locationRegion()).isEqualTo("Gyeonggi");
        assertThat(posting.locationCity()).isEqualTo("Gwacheon");
        assertThat(posting.deadlineAt()).isEqualTo(LocalDateTime.of(2026, 7, 31, 23, 59));
        assertThat(posting.salaryCurrency()).isEqualTo("KRW");
        assertThat(posting.rawData()).contains("12345");
        assertThat(posting.rawData()).contains("rawBody");
        assertThat(posting.rawData()).contains("Backend Engineer");
        assertThat(posting.rawData()).contains("position-description");
        assertThat(posting.crawlerVersion()).isEqualTo("jumpit-parser-0.1");
        assertThat(posting.collectedAt()).isNotNull();
        assertThat(posting.lastSeenAt()).isNotNull();
        assertThat(posting.minExperienceYears()).isEqualTo(1);
        assertThat(posting.maxExperienceYears()).isEqualTo(10);
        assertThat(posting.educationLevel()).isEqualTo("BACHELOR");
    }

    @Test
    @DisplayName("회사명 selector가 CTA 텍스트만 잡으면 파싱하지 않는다")
    void rejectInvalidCompanyName() {
        FetchedJobPosting fetched = new FetchedJobPosting(
                JobIngestionSource.JUMPIT,
                "12345",
                "https://jumpit.saramin.co.kr/position/12345",
                "https://jumpit.saramin.co.kr/position/12345",
                """
                        <html>
                          <body>
                            <main>
                              <h1>Backend Engineer</h1>
                              <section class="company-name">기업 정보 보기</section>
                              <section data-testid="position-description">
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
    @DisplayName("점핏 description 섹션과 bullet 줄바꿈을 보존한다")
    void preserveDescriptionSectionLineBreaks() {
        FetchedJobPosting fetched = new FetchedJobPosting(
                JobIngestionSource.JUMPIT,
                "jumpit-100",
                "https://jumpit.saramin.co.kr/position/jumpit-100",
                "https://jumpit.saramin.co.kr/position/jumpit-100",
                """
                        <html>
                          <body>
                            <main>
                              <h1>Backend Engineer</h1>
                              <a href="/company/example-labs">Example Labs</a>
                              <section data-testid="position-description">
                                포지션 상세 정보 기술스택 Java Spring Boot Docker 주요업무
                                • Java 기반 API 개발
                                • Redis 캐시 운영
                                [백엔드 팀이 사용하는 기술]
                                Java, Spring Boot, Redis
                                자격요건
                                • 백엔드 개발 경험 3년 이상
                                우대사항
                                • 대용량 트래픽 경험
                                채용절차 및 기타 지원 유의사항
                                • 서류 전형 - 기술 면접 - 최종 합격
                                마감일 2026-07-31
                              </section>
                            </main>
                          </body>
                        </html>
                        """
        );

        IngestedJobPosting posting = parser.parse(fetched);

        assertThat(posting.description())
                .contains("기술스택\nJava Spring Boot Docker")
                .doesNotContain("JavaSpring")
                .contains("주요업무\n")
                .contains("\n• Java 기반 API 개발")
                .contains("\n[백엔드 팀이 사용하는 기술]\nJava, Spring Boot, Redis")
                .contains("자격요건\n")
                .contains("우대사항\n")
                .contains("채용절차 및 기타 지원 유의사항\n");
    }

    @Test
    @DisplayName("직무 탐색 페이지 제목은 공고로 파싱하지 않는다")
    void rejectExplorePageTitle() {
        FetchedJobPosting fetched = new FetchedJobPosting(
                JobIngestionSource.JUMPIT,
                "12345",
                "https://jumpit.saramin.co.kr/position/12345",
                "https://jumpit.saramin.co.kr/position/12345",
                """
                        <html>
                          <body>
                            <main>
                              <h1>점핏 | 개발 직무 탐색</h1>
                              <section class="company-name">JobFlow Labs</section>
                              <section data-testid="position-description">
                                Java, Spring Boot 기반 백엔드 개발자를 채용합니다.
                              </section>
                            </main>
                          </body>
                        </html>
                        """
        );

        assertThatThrownBy(() -> parser.parse(fetched))
                .isInstanceOf(JobPostingParseException.class)
                .hasMessageContaining("title");
    }

    @Test
    @DisplayName("지원하지 않는 source는 파싱하지 않는다")
    void unsupportedSource() {
        FetchedJobPosting fetched = new FetchedJobPosting(
                JobIngestionSource.ZIGHANG,
                "zighang-example-1",
                "https://zighang.com/recruitment/00000000-0000-0000-0000-000000000100",
                "https://zighang.com/recruitment/00000000-0000-0000-0000-000000000100",
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
                "12345",
                "https://jumpit.saramin.co.kr/position/12345",
                "https://jumpit.saramin.co.kr/position/12345",
                "<html><body></body></html>"
        );

        assertThatThrownBy(() -> parser.parse(fetched))
                .isInstanceOf(JobPostingParseException.class)
                .hasMessageContaining("Required field is missing");
    }
}
