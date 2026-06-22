package jobflow.domain.job.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.RemoteType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobResponseTest {

    @Test
    @DisplayName("저장된 description sections가 있으면 description parser 결과보다 우선한다")
    void preferStoredDescriptionSections() {
        Job job = Job.create(
                "WANTED",
                "wanted-100",
                "프론트엔드 개발자",
                "Example Company",
                "[주요 업무]\nCS\n• 관제",
                "https://example.com/jobs/wanted-100",
                JobRole.FRONTEND,
                "React",
                CareerLevel.JUNIOR,
                1,
                3,
                null,
                EmploymentType.FULL_TIME,
                null,
                null,
                "KR",
                "Seoul",
                "Gangnam",
                RemoteType.ONSITE,
                null,
                null,
                "KRW",
                false,
                null,
                null,
                LocalDateTime.of(2026, 7, 31, 23, 59)
        );
        job.updateDescriptionSections("""
                [
                  {
                    "type": "RESPONSIBILITIES",
                    "title": "주요 업무",
                    "body": "CS · 관제 · 고객을 잇는 운영 흐름을 어드민 안에서 구현"
                  }
                ]
                """);

        JobResponse response = JobResponse.of(job, List.of(), List.of(), job.getUrl());

        assertThat(response.descriptionSections()).hasSize(1);
        assertThat(response.descriptionSections().get(0).body())
                .isEqualTo("CS · 관제 · 고객을 잇는 운영 흐름을 어드민 안에서 구현");
    }
}
