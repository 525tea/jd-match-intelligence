package jobflow.domain.job;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobValidationTest {

    @Test
    @DisplayName("유효한 경력, 연봉, 일정 값이면 공고를 생성한다")
    void createValidJob() {
        assertThatCode(() -> createJob(
                1,
                3,
                3000,
                5000,
                2,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 23, 59)
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("최소 경력 연차가 최대 경력 연차보다 크면 예외가 발생한다")
    void rejectInvalidExperienceRange() {
        assertInvalidJobValue(() -> createJob(
                5,
                3,
                3000,
                5000,
                2,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 23, 59)
        ), "최소 경력 연차");
    }

    @Test
    @DisplayName("경력 연차가 음수이면 예외가 발생한다")
    void rejectNegativeExperienceYears() {
        assertInvalidJobValue(() -> createJob(
                -1,
                3,
                3000,
                5000,
                2,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 23, 59)
        ), "경력 연차");
    }

    @Test
    @DisplayName("최소 연봉이 최대 연봉보다 크면 예외가 발생한다")
    void rejectInvalidSalaryRange() {
        assertInvalidJobValue(() -> createJob(
                1,
                3,
                7000,
                5000,
                2,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 23, 59)
        ), "최소 연봉");
    }

    @Test
    @DisplayName("연봉이 음수이면 예외가 발생한다")
    void rejectNegativeSalary() {
        assertInvalidJobValue(() -> createJob(
                1,
                3,
                -3000,
                5000,
                2,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 23, 59)
        ), "연봉");
    }

    @Test
    @DisplayName("채용 인원이 음수이면 예외가 발생한다")
    void rejectNegativeHiringCount() {
        assertInvalidJobValue(() -> createJob(
                1,
                3,
                3000,
                5000,
                -1,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 23, 59)
        ), "채용 인원");
    }

    @Test
    @DisplayName("공고 시작일이 마감일보다 늦으면 예외가 발생한다")
    void rejectInvalidDateRange() {
        assertInvalidJobValue(() -> createJob(
                1,
                3,
                3000,
                5000,
                2,
                LocalDateTime.of(2026, 7, 2, 9, 0),
                LocalDateTime.of(2026, 7, 1, 23, 59)
        ), "공고 시작일");
    }

    @Test
    @DisplayName("공고 수정 시에도 경력, 연봉, 일정 값을 검증한다")
    void rejectInvalidUpdateValues() {
        Job job = createJob(
                1,
                3,
                3000,
                5000,
                2,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 23, 59)
        );

        assertInvalidJobValue(() -> job.update(
                "백엔드 개발자",
                "Example Company",
                "Spring Boot 기반 백엔드 개발",
                "https://example.com/jobs/backend",
                JobRole.BACKEND,
                "Java/Spring",
                CareerLevel.JUNIOR,
                5,
                3,
                "BACHELOR",
                EmploymentType.FULL_TIME,
                "STARTUP",
                "IT",
                "KR",
                "Seoul",
                "Gangnam",
                RemoteType.HYBRID,
                3000,
                5000,
                "KRW",
                true,
                2,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 23, 59)
        ), "최소 경력 연차");
    }

    private static void assertInvalidJobValue(Runnable runnable, String expectedMessagePart) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(expectedMessagePart)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
    }

    private static Job createJob(
            Integer minExperienceYears,
            Integer maxExperienceYears,
            Integer salaryMin,
            Integer salaryMax,
            Integer hiringCount,
            LocalDateTime openedAt,
            LocalDateTime deadlineAt
    ) {
        return Job.create(
                "WANTED",
                "sample-job-1",
                "백엔드 개발자",
                "Example Company",
                "Spring Boot 기반 백엔드 개발",
                "https://example.com/jobs/backend",
                JobRole.BACKEND,
                "Java/Spring",
                CareerLevel.JUNIOR,
                minExperienceYears,
                maxExperienceYears,
                "BACHELOR",
                EmploymentType.FULL_TIME,
                "STARTUP",
                "IT",
                "KR",
                "Seoul",
                "Gangnam",
                RemoteType.HYBRID,
                salaryMin,
                salaryMax,
                "KRW",
                true,
                hiringCount,
                openedAt,
                deadlineAt
        );
    }
}
