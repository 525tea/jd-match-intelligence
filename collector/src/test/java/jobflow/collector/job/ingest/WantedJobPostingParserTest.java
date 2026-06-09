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
import tools.jackson.databind.ObjectMapper;

class WantedJobPostingParserTest {

    private final WantedJobPostingParser parser = new WantedJobPostingParser(
            new ObjectMapper(),
            new JdJobRoleClassificationService()
    );

    @Test
    @DisplayName("원티드 상세 JSON을 수집 공고로 변환한다")
    void parse() {
        FetchedJobPosting fetched = new FetchedJobPosting(
                JobIngestionSource.WANTED,
                "367044",
                "https://www.wanted.co.kr/wd/367044",
                "https://www.wanted.co.kr/api/v4/jobs/367044",
                """
                        {
                          "job": {
                            "id": 367044,
                            "position": "백엔드 개발자",
                            "annual_from": 3,
                            "annual_to": 7,
                            "due_time": "2026-06-22",
                            "skill_tags": [
                              {"title": "Java"},
                              {"title": "Spring Boot"}
                            ],
                            "company_tags": [
                              {"title": "원격근무", "kind_title": "COMPANY_MANAGEMENT"}
                            ],
                            "company": {
                              "id": 57702,
                              "name": "JobFlow Labs",
                              "industry_name": "IT, 콘텐츠"
                            },
                            "address": {
                              "country_code": "kr",
                              "location": "서울",
                              "full_location": "서울 강남구 테헤란로"
                            },
                            "detail": {
                              "intro": "채용 플랫폼을 개발합니다.",
                              "main_tasks": "Spring Boot API 개발",
                              "requirements": "경력 3~7년 Java 개발 경험",
                              "preferred_points": "대용량 트래픽 경험",
                              "benefits": "자율 근무"
                            }
                          }
                        }
                        """
        );

        IngestedJobPosting posting = parser.parse(fetched);

        assertThat(posting.source()).isEqualTo(JobIngestionSource.WANTED);
        assertThat(posting.externalId()).isEqualTo("367044");
        assertThat(posting.title()).isEqualTo("백엔드 개발자");
        assertThat(posting.companyName()).isEqualTo("JobFlow Labs");
        assertThat(posting.description()).contains("Spring Boot API 개발");
        assertThat(posting.sourceUrl()).isEqualTo("https://www.wanted.co.kr/wd/367044");
        assertThat(posting.detailUrl()).isEqualTo("https://www.wanted.co.kr/api/v4/jobs/367044");
        assertThat(posting.role()).isEqualTo(JobRole.BACKEND);
        assertThat(posting.roleDetail()).contains("Java", "Spring Boot");
        assertThat(posting.careerLevel()).isEqualTo(CareerLevel.JUNIOR);
        assertThat(posting.minExperienceYears()).isEqualTo(3);
        assertThat(posting.maxExperienceYears()).isEqualTo(7);
        assertThat(posting.employmentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(posting.industry()).isEqualTo("IT, 콘텐츠");
        assertThat(posting.locationCountry()).isEqualTo("KR");
        assertThat(posting.locationRegion()).isEqualTo("Seoul");
        assertThat(posting.locationCity()).isEqualTo("Gangnam");
        assertThat(posting.remoteType()).isEqualTo(RemoteType.REMOTE);
        assertThat(posting.salaryMin()).isEqualTo(30_000_000);
        assertThat(posting.salaryMax()).isEqualTo(70_000_000);
        assertThat(posting.salaryVisible()).isTrue();
        assertThat(posting.deadlineAt()).isEqualTo(LocalDateTime.of(2026, 6, 22, 23, 59));
        assertThat(posting.rawData()).contains("367044");
        assertThat(posting.crawlerVersion()).isEqualTo("wanted-parser-0.1");
        assertThat(posting.collectedAt()).isNotNull();
        assertThat(posting.lastSeenAt()).isNotNull();
    }

    @Test
    @DisplayName("필수 필드가 없으면 예외를 던진다")
    void missingRequiredField() {
        FetchedJobPosting fetched = new FetchedJobPosting(
                JobIngestionSource.WANTED,
                "367044",
                "https://www.wanted.co.kr/wd/367044",
                "https://www.wanted.co.kr/api/v4/jobs/367044",
                """
                        {
                          "job": {
                            "position": "",
                            "company": {"name": "JobFlow Labs"},
                            "detail": {"requirements": "Java 개발 경험"}
                          }
                        }
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
                JobIngestionSource.JUMPIT,
                "367044",
                "https://jumpit.saramin.co.kr/position/367044",
                "https://jumpit.saramin.co.kr/position/367044",
                "{}"
        );

        assertThatThrownBy(() -> parser.parse(fetched))
                .isInstanceOf(JobPostingParseException.class)
                .hasMessageContaining("Unsupported source");
    }
}
