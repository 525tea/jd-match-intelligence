package jobflow.domain.notification.digest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRepository;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.JobStatus;
import jobflow.domain.job.RemoteType;
import jobflow.domain.matching.JdMatchService;
import jobflow.domain.matching.dto.JdJobMatchResponse;
import jobflow.domain.matching.dto.JdMatchScoreResponse;
import jobflow.domain.recommendation.JobRecommendationService;
import jobflow.domain.recommendation.dto.JobRecommendationResponse;
import jobflow.domain.recommendation.dto.JobRecommendationScoreResponse;
import jobflow.domain.userjob.UserJobRepository;
import jobflow.domain.userjob.UserJobStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

class DailyDigestContentServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-16T00:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    private final JobRecommendationService jobRecommendationService =
            org.mockito.Mockito.mock(JobRecommendationService.class);
    private final JdMatchService jdMatchService = org.mockito.Mockito.mock(JdMatchService.class);
    private final JobRepository jobRepository = org.mockito.Mockito.mock(JobRepository.class);
    private final UserJobRepository userJobRepository = org.mockito.Mockito.mock(UserJobRepository.class);
    private final DailyDigestSectionAssembler sectionAssembler = new DailyDigestSectionAssembler();

    private final DailyDigestContentService service = new DailyDigestContentService(
            jobRecommendationService,
            jdMatchService,
            jobRepository,
            userJobRepository,
            sectionAssembler,
            FIXED_CLOCK
    );

    @Test
    @DisplayName("추천, JD 매칭, 신규, 마감 임박 후보를 Daily Digest content로 조립한다")
    void buildDigestContent() {
        Long userId = 1L;
        Long userProjectId = 2L;
        List<JobRole> targetRoles = List.of(JobRole.BACKEND);
        CareerLevel targetCareerLevel = CareerLevel.MID;
        LocalDateTime now = LocalDateTime.now(FIXED_CLOCK);

        when(jobRecommendationService.recommendJobs(userId, userProjectId, targetRoles, 8))
                .thenReturn(List.of(recommendation(10L), recommendation(20L)));
        when(jdMatchService.findProjectJobMatches(userId, userProjectId, targetRoles, targetCareerLevel, 8))
                .thenReturn(List.of(jdMatch(20L), jdMatch(30L)));

        Job recommendationJob = job(10L, "추천 공고", now.plusDays(5));
        Job duplicatedJob = job(20L, "중복 공고", now.plusDays(7));
        Job jdJob = job(30L, "JD 매칭 공고", now.plusDays(9));
        Job newJob = job(40L, "신규 공고", now.plusDays(12));
        Job deadlineJob = job(50L, "마감 임박 공고", now.plusHours(8));

        when(jobRepository.findByIdIn(List.of(10L, 20L, 30L)))
                .thenReturn(List.of(recommendationJob, duplicatedJob, jdJob));
        when(jobRepository.findByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                eq(JobStatus.OPEN),
                eq(now.minusHours(24)),
                eq(now),
                any(Pageable.class)
        ))
                .thenReturn(List.of(newJob));
        when(userJobRepository.findSavedOpenJobsDueSoon(
                eq(userId),
                eq(UserJobStatus.SAVED),
                eq(JobStatus.OPEN),
                eq(now),
                eq(now.plusHours(24)),
                any(Pageable.class)
        ))
                .thenReturn(List.of(deadlineJob));

        DailyDigestContent content = service.buildDigest(userId, userProjectId, targetRoles, targetCareerLevel);

        assertThat(content.recommendedJobs())
                .extracting(DailyDigestJobItem::jobId)
                .containsExactly(10L, 20L);
        assertThat(content.jdMatchJobs())
                .extracting(DailyDigestJobItem::jobId)
                .containsExactly(30L);
        assertThat(content.newJobs())
                .extracting(DailyDigestJobItem::jobId)
                .containsExactly(40L);
        assertThat(content.deadlineReminderJobs())
                .extracting(DailyDigestJobItem::jobId)
                .containsExactly(50L);
        assertThat(content.totalJobCount()).isEqualTo(5);
        assertThat(content.recommendedJobs().get(0).originalUrl())
                .isEqualTo("https://example.com/jobs/10");
    }

    @Test
    @DisplayName("신규 공고 조회는 최근 24시간 OPEN 공고 기준이다")
    void queryNewJobsFromLast24Hours() {
        Long userId = 1L;
        Long userProjectId = 2L;
        LocalDateTime now = LocalDateTime.now(FIXED_CLOCK);

        when(jobRecommendationService.recommendJobs(any(), any(), anyCollection(), anyInt()))
                .thenReturn(List.of());
        when(jdMatchService.findProjectJobMatches(any(), any(), anyCollection(), any(), anyInt()))
                .thenReturn(List.of());
        when(jobRepository.findByIdIn(List.of()))
                .thenReturn(List.of());
        when(jobRepository.findByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                any(),
                any(),
                any(),
                any()
        ))
                .thenReturn(List.of());
        when(userJobRepository.findSavedOpenJobsDueSoon(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        service.buildDigest(userId, userProjectId, List.of(JobRole.BACKEND), CareerLevel.MID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        org.mockito.Mockito.verify(jobRepository)
                .findByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                        eq(JobStatus.OPEN),
                        fromCaptor.capture(),
                        toCaptor.capture(),
                        any(Pageable.class)
                );

        assertThat(fromCaptor.getValue()).isEqualTo(now.minusHours(24));
        assertThat(toCaptor.getValue()).isEqualTo(now);
    }

    private JobRecommendationResponse recommendation(Long jobId) {
        return new JobRecommendationResponse(
                jobId,
                "WANTED",
                "추천 공고 " + jobId,
                "Example Company",
                JobRole.BACKEND,
                CareerLevel.MID,
                EmploymentType.FULL_TIME,
                "Seoul",
                "Gangnam",
                RemoteType.ONSITE,
                LocalDateTime.of(2026, 6, 20, 18, 0),
                JobStatus.OPEN,
                null,
                recommendationScore(BigDecimal.valueOf(80)),
                3,
                3,
                0,
                2,
                1,
                1,
                List.of("Java", "Spring Boot"),
                List.of(),
                List.of("AWS"),
                List.of("Redis")
        );
    }

    private JdJobMatchResponse jdMatch(Long jobId) {
        return new JdJobMatchResponse(
                2L,
                3L,
                1,
                LocalDateTime.of(2026, 6, 16, 9, 0),
                jobId,
                "JD 매칭 공고 " + jobId,
                "Example Company",
                JobRole.BACKEND,
                CareerLevel.MID,
                jdScore(BigDecimal.valueOf(75)),
                3,
                2,
                1,
                2,
                1,
                1,
                2,
                1,
                1,
                List.of("Java"),
                List.of("Kotlin"),
                List.of("AWS"),
                List.of("Redis"),
                List.of(),
                List.of()
        );
    }

    private Job job(Long id, String title, LocalDateTime deadlineAt) {
        Job job = Job.create(
                "WANTED",
                "external-" + id,
                title,
                "Example Company",
                "description",
                "https://example.com/jobs/" + id,
                JobRole.BACKEND,
                null,
                CareerLevel.MID,
                3,
                7,
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
                LocalDateTime.of(2026, 6, 1, 9, 0),
                deadlineAt
        );
        org.springframework.test.util.ReflectionTestUtils.setField(job, "id", id);
        org.springframework.test.util.ReflectionTestUtils.setField(job, "originalUrl", "https://example.com/jobs/" + id);
        return job;
    }

    private JobRecommendationScoreResponse recommendationScore(BigDecimal totalScore) {
        return new JobRecommendationScoreResponse(
                totalScore,
                BigDecimal.valueOf(40),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(80),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(100)
        );
    }

    private JdMatchScoreResponse jdScore(BigDecimal totalScore) {
        return new JdMatchScoreResponse(
                totalScore,
                BigDecimal.valueOf(30),
                BigDecimal.valueOf(15),
                BigDecimal.valueOf(15),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(5),
                BigDecimal.valueOf(70),
                BigDecimal.valueOf(50),
                BigDecimal.valueOf(50),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(99)
        );
    }
}
