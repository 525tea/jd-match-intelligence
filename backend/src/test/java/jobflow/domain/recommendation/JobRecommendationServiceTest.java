package jobflow.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import jobflow.domain.analytics.JobSkillIndexQueryService;
import jobflow.domain.analytics.dto.JobSkillMatchResponse;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRepository;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.RemoteType;
import jobflow.domain.project.ProjectSkillSnapshotService;
import jobflow.domain.recommendation.dto.JobRecommendationResponse;
import jobflow.domain.user.User;
import jobflow.domain.userjob.UserJob;
import jobflow.domain.userjob.UserJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JobRecommendationServiceTest {

    @Mock
    private ProjectSkillSnapshotService projectSkillSnapshotService;

    @Mock
    private JobSkillIndexQueryService jobSkillIndexQueryService;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private UserJobRepository userJobRepository;

    private JobRecommendationService jobRecommendationService;

    @BeforeEach
    void setUp() {
        jobRecommendationService = new JobRecommendationService(
                projectSkillSnapshotService,
                jobSkillIndexQueryService,
                jobRepository,
                userJobRepository,
                new RecommendationScoreCalculator()
        );
    }

    @Test
    @DisplayName("추천 후보를 점수화하고 사용자 ignored 공고는 제외한다")
    void recommendJobs_scoresCandidatesAndFiltersIgnoredJobs() {
        Long userId = 10L;
        Long userProjectId = 20L;
        List<Long> skillIds = List.of(1L, 7L, 21L);
        LocalDateTime now = LocalDateTime.now();

        Job highMatchJob = withId(job(
                "recommended-high",
                JobRole.BACKEND,
                CareerLevel.MID,
                now.minusDays(2),
                now.plusDays(5)
        ), 100L);
        Job savedJob = withId(job(
                "recommended-saved",
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                now.minusDays(10),
                now.plusDays(20)
        ), 101L);
        Job ignoredJob = withId(job(
                "recommended-ignored",
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                now.minusDays(1),
                now.plusDays(1)
        ), 102L);

        given(projectSkillSnapshotService.findLatestSkillIds(userId, userProjectId)).willReturn(skillIds);
        given(jobSkillIndexQueryService.findTopOpenJobMatchResponses(
                skillIds,
                List.of(JobRole.BACKEND),
                50
        )).willReturn(List.of(
                matchResponse(101L, BigDecimal.valueOf(60), BigDecimal.valueOf(50), 3, 2),
                matchResponse(102L, BigDecimal.valueOf(100), BigDecimal.valueOf(100), 2, 2),
                matchResponse(100L, BigDecimal.valueOf(100), BigDecimal.valueOf(100), 2, 2)
        ));
        given(jobRepository.findByIdIn(List.of(101L, 102L, 100L)))
                .willReturn(List.of(savedJob, ignoredJob, highMatchJob));
        given(userJobRepository.findByUserIdAndJobIdIn(userId, List.of(101L, 102L, 100L)))
                .willReturn(List.of(
                        userJob(userId, savedJob, true, false),
                        userJob(userId, ignoredJob, false, true)
                ));

        List<JobRecommendationResponse> responses = jobRecommendationService.recommendJobs(
                userId,
                userProjectId,
                List.of(JobRole.BACKEND),
                10
        );

        assertThat(responses).extracting(JobRecommendationResponse::jobId)
                .containsExactly(100L, 101L);
        assertThat(responses.get(0).score().totalScore())
                .isGreaterThan(responses.get(1).score().totalScore());
        assertThat(responses.get(1).userJobStatus()).isEqualTo(jobflow.domain.userjob.UserJobStatus.SAVED);
    }

    @Test
    @DisplayName("사용자 스킬이 없는 콜드 스타트도 후보를 반환한다")
    void recommendJobs_returnsColdStartCandidates() {
        Long userId = 10L;
        Long userProjectId = 20L;
        LocalDateTime now = LocalDateTime.now();
        Job job = withId(job(
                "cold-start-recommendation",
                JobRole.BACKEND,
                CareerLevel.ANY,
                now.minusDays(3),
                now.plusDays(10)
        ), 100L);

        given(projectSkillSnapshotService.findLatestSkillIds(userId, userProjectId)).willReturn(List.of());
        given(jobSkillIndexQueryService.findTopOpenJobMatchResponses(anyCollection(), anyCollection(), org.mockito.ArgumentMatchers.eq(50)))
                .willReturn(List.of(matchResponse(100L, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0)));
        given(jobRepository.findByIdIn(List.of(100L))).willReturn(List.of(job));
        given(userJobRepository.findByUserIdAndJobIdIn(userId, List.of(100L))).willReturn(List.of());

        List<JobRecommendationResponse> responses = jobRecommendationService.recommendJobs(
                userId,
                userProjectId,
                List.of(JobRole.BACKEND),
                5
        );

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).jobId()).isEqualTo(100L);
        assertThat(responses.get(0).score().skillMatchScore()).isEqualByComparingTo("20.00");
    }

    private JobSkillMatchResponse matchResponse(
            Long jobId,
            BigDecimal requiredMatchRate,
            BigDecimal preferredMatchRate,
            long requiredSkillCount,
            long preferredSkillCount
    ) {
        return new JobSkillMatchResponse(
                jobId,
                "Sample backend engineer",
                "Example Company",
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                requiredSkillCount,
                requiredMatchRate.signum() > 0 ? Math.max(1, requiredSkillCount / 2) : 0,
                requiredSkillCount,
                requiredMatchRate,
                preferredSkillCount,
                preferredMatchRate.signum() > 0 ? Math.max(1, preferredSkillCount / 2) : 0,
                preferredSkillCount,
                preferredMatchRate,
                requiredMatchRate,
                List.of("Java"),
                List.of("Kotlin"),
                List.of("Docker"),
                List.of("Redis")
        );
    }

    private Job job(
            String externalId,
            JobRole role,
            CareerLevel careerLevel,
            LocalDateTime openedAt,
            LocalDateTime deadlineAt
    ) {
        return Job.create(
                "TEST_SOURCE",
                externalId,
                "Sample backend engineer",
                "Example Company",
                "Sample backend engineer description",
                "https://example.com/jobs/" + externalId,
                role,
                role.name(),
                careerLevel,
                null,
                null,
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
                openedAt,
                deadlineAt
        );
    }

    private Job withId(Job job, Long id) {
        ReflectionTestUtils.setField(job, "id", id);
        return job;
    }

    private UserJob userJob(Long userId, Job job, boolean saved, boolean ignored) {
        User user = org.mockito.Mockito.mock(User.class);
        UserJob userJob = UserJob.viewed(user, job, LocalDateTime.now().minusDays(1));
        if (saved) {
            userJob.save(LocalDateTime.now());
        }
        if (ignored) {
            userJob.ignore(LocalDateTime.now());
        }
        return userJob;
    }
}
