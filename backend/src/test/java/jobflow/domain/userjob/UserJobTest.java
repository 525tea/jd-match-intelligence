package jobflow.domain.userjob;

import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.RemoteType;
import jobflow.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserJobTest {

    @Test
    @DisplayName("조회 상태의 UserJob을 생성한다")
    void createViewedUserJob() {
        User user = User.signup("user@example.com", "encoded-password", "사용자");
        Job job = createJob();
        LocalDateTime viewedAt = LocalDateTime.of(2026, 6, 4, 10, 0);

        UserJob userJob = UserJob.viewed(user, job, viewedAt);

        assertThat(userJob.getUser()).isEqualTo(user);
        assertThat(userJob.getJob()).isEqualTo(job);
        assertThat(userJob.getStatus()).isEqualTo(UserJobStatus.VIEWED);
        assertThat(userJob.getViewedAt()).isEqualTo(viewedAt);
        assertThat(userJob.getSavedAt()).isNull();
        assertThat(userJob.getIgnoredAt()).isNull();
    }

    @Test
    @DisplayName("공고를 저장 상태로 변경한다")
    void saveUserJob() {
        UserJob userJob = UserJob.viewed(
                User.signup("user@example.com", "encoded-password", "사용자"),
                createJob(),
                LocalDateTime.of(2026, 6, 4, 10, 0)
        );
        LocalDateTime savedAt = LocalDateTime.of(2026, 6, 4, 11, 0);

        userJob.save(savedAt);

        assertThat(userJob.getStatus()).isEqualTo(UserJobStatus.SAVED);
        assertThat(userJob.getSavedAt()).isEqualTo(savedAt);
        assertThat(userJob.isSaved()).isTrue();
        assertThat(userJob.isIgnored()).isFalse();
    }

    @Test
    @DisplayName("공고를 무시 상태로 변경한다")
    void ignoreUserJob() {
        UserJob userJob = UserJob.viewed(
                User.signup("user@example.com", "encoded-password", "사용자"),
                createJob(),
                LocalDateTime.of(2026, 6, 4, 10, 0)
        );
        LocalDateTime ignoredAt = LocalDateTime.of(2026, 6, 4, 11, 0);

        userJob.ignore(ignoredAt);

        assertThat(userJob.getStatus()).isEqualTo(UserJobStatus.IGNORED);
        assertThat(userJob.getIgnoredAt()).isEqualTo(ignoredAt);
        assertThat(userJob.isSaved()).isFalse();
        assertThat(userJob.isIgnored()).isTrue();
    }

    @Test
    @DisplayName("저장 또는 무시 상태에서 다시 조회 상태로 변경할 수 있다")
    void markViewedAgain() {
        UserJob userJob = UserJob.viewed(
                User.signup("user@example.com", "encoded-password", "사용자"),
                createJob(),
                LocalDateTime.of(2026, 6, 4, 10, 0)
        );
        userJob.save(LocalDateTime.of(2026, 6, 4, 11, 0));
        LocalDateTime viewedAt = LocalDateTime.of(2026, 6, 4, 12, 0);

        userJob.markViewed(viewedAt);

        assertThat(userJob.getStatus()).isEqualTo(UserJobStatus.VIEWED);
        assertThat(userJob.getViewedAt()).isEqualTo(viewedAt);
        assertThat(userJob.getSavedAt()).isEqualTo("2026-06-04T11:00:00");
    }

    private Job createJob() {
        return Job.create(
                "MANUAL",
                "job-1",
                "백엔드 개발자",
                "JobFlow",
                "Spring Boot 백엔드 개발자 채용",
                "https://example.com/jobs/1",
                JobRole.BACKEND,
                "Java/Spring",
                CareerLevel.JUNIOR,
                1,
                3,
                "BACHELOR",
                EmploymentType.FULL_TIME,
                "STARTUP",
                "IT",
                "KR",
                "서울",
                "강남구",
                RemoteType.HYBRID,
                40000000,
                70000000,
                "KRW",
                true,
                1,
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 6, 30, 23, 59)
        );
    }
}
