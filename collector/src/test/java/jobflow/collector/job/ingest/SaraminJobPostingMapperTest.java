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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SaraminJobPostingMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SaraminJobPostingMapper mapper = new SaraminJobPostingMapper(
            objectMapper,
            new JdJobRoleClassificationService()
    );

    @Test
    @DisplayName("사람인 API 응답 후보 JSON을 수집 공고로 변환한다")
    void map() throws Exception {
        JsonNode job = objectMapper.readTree("""
                {
                  "id": "54092196",
                  "url": "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=54092196",
                  "posting-date": "2026-06-10T09:00:00+0900",
                  "expiration-date": "2026-07-07T23:59:59+0900",
                  "keyword": "Kafka, Linux, Python, Redis",
                  "company": {
                    "detail": {
                      "name": "피아스페이스(주)",
                      "industry": "소프트웨어개발"
                    }
                  },
                  "position": {
                    "title": "필드 엔지니어 채용",
                    "job-code": {"name": "IT개발·데이터"},
                    "job-mid-code": {"name": "SE(시스템엔지니어)"},
                    "experience-level": {"name": "경력 1~10년"},
                    "required-education-level": {"name": "고등학교졸업이상"},
                    "location": {"name": "서울 서초구"}
                  }
                }
                """);

        IngestedJobPosting posting = mapper.map(job);

        assertThat(posting.source()).isEqualTo(JobIngestionSource.SARAMIN);
        assertThat(posting.externalId()).isEqualTo("54092196");
        assertThat(posting.title()).isEqualTo("필드 엔지니어 채용");
        assertThat(posting.companyName()).isEqualTo("피아스페이스(주)");
        assertThat(posting.description()).contains("Kafka", "SE(시스템엔지니어)");
        assertThat(posting.sourceUrl()).isEqualTo("https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=54092196");
        assertThat(posting.detailUrl()).isEqualTo("https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=54092196");
        assertThat(posting.role()).isEqualTo(JobRole.DEVOPS);
        assertThat(posting.roleDetail()).contains("IT개발·데이터", "SE(시스템엔지니어)", "Kafka");
        assertThat(posting.careerLevel()).isEqualTo(CareerLevel.JUNIOR);
        assertThat(posting.minExperienceYears()).isEqualTo(1);
        assertThat(posting.maxExperienceYears()).isEqualTo(10);
        assertThat(posting.educationLevel()).isEqualTo("고등학교졸업이상");
        assertThat(posting.employmentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(posting.industry()).isEqualTo("소프트웨어개발");
        assertThat(posting.locationCountry()).isEqualTo("KR");
        assertThat(posting.locationRegion()).isEqualTo("Seoul");
        assertThat(posting.locationCity()).isEqualTo("Seocho");
        assertThat(posting.remoteType()).isEqualTo(RemoteType.ONSITE);
        assertThat(posting.openedAt()).isEqualTo(LocalDateTime.of(2026, 6, 10, 9, 0));
        assertThat(posting.deadlineAt()).isEqualTo(LocalDateTime.of(2026, 7, 7, 23, 59, 59));
        assertThat(posting.rawData()).contains("54092196");
        assertThat(posting.crawlerVersion()).isEqualTo("saramin-api-scaffold-0.1");
        assertThat(posting.collectedAt()).isNotNull();
        assertThat(posting.lastSeenAt()).isNotNull();
    }

    @Test
    @DisplayName("사람인 API 응답에서 title 직군이 명확하면 title 직군을 우선한다")
    void preferTitleRole() throws Exception {
        JsonNode job = objectMapper.readTree("""
                {
                  "id": "54000001",
                  "url": "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=54000001",
                  "keyword": "Android, Kotlin, 서버 연동",
                  "company": {"detail": {"name": "JobFlow Labs"}},
                  "position": {
                    "title": "안드로이드 개발자",
                    "experience-level": {"name": "경력 5년 이상"},
                    "location": {"name": "서울 강남구"}
                  }
                }
                """);

        IngestedJobPosting posting = mapper.map(job);

        assertThat(posting.role()).isEqualTo(JobRole.ANDROID);
        assertThat(posting.careerLevel()).isEqualTo(CareerLevel.MID);
        assertThat(posting.minExperienceYears()).isEqualTo(5);
        assertThat(posting.maxExperienceYears()).isNull();
    }

    @Test
    @DisplayName("사람인 API timestamp 날짜도 수집 날짜로 변환한다")
    void parseTimestampDate() throws Exception {
        JsonNode job = objectMapper.readTree("""
                {
                  "id": "54000002",
                  "url": "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=54000002",
                  "posting-timestamp": "1780963200",
                  "expiration-timestamp": "1783555199",
                  "company": {"detail": {"name": "JobFlow Labs"}},
                  "position": {
                    "title": "백엔드 개발자",
                    "job-code": {"name": "IT개발·데이터"},
                    "location": {"name": "경기 성남시 분당구"}
                  }
                }
                """);

        IngestedJobPosting posting = mapper.map(job);

        assertThat(posting.openedAt()).isEqualTo(LocalDateTime.of(2026, 6, 9, 9, 0));
        assertThat(posting.deadlineAt()).isEqualTo(LocalDateTime.of(2026, 7, 9, 8, 59, 59));
        assertThat(posting.locationRegion()).isEqualTo("Gyeonggi");
        assertThat(posting.locationCity()).isEqualTo("Bundang");
    }

    @Test
    @DisplayName("사람인 필수 필드가 없으면 예외를 던진다")
    void missingRequiredField() throws Exception {
        JsonNode job = objectMapper.readTree("""
                {
                  "id": "54000003",
                  "url": "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=54000003",
                  "company": {"detail": {"name": "JobFlow Labs"}},
                  "position": {"title": ""}
                }
                """);

        assertThatThrownBy(() -> mapper.map(job))
                .isInstanceOf(JobPostingParseException.class)
                .hasMessageContaining("title");
    }
}
