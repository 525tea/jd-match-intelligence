package jobflow.domain.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jobflow.domain.analytics.JobSkillIndexQueryService;
import jobflow.domain.analytics.JobSkillMatchDetail;
import jobflow.domain.analytics.JobSkillMatchSummary;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobExperienceTag;
import jobflow.domain.job.JobExperienceTagRepository;
import jobflow.domain.job.JobRole;
import jobflow.domain.matching.dto.JdJobMatchResponse;
import jobflow.domain.project.UserProjectAnalysis;
import jobflow.domain.project.UserProjectAnalysisRepository;
import jobflow.domain.project.UserProjectExperienceTag;
import jobflow.domain.project.UserProjectExperienceTagRepository;
import jobflow.domain.project.UserProjectRepository;
import jobflow.domain.project.UserProjectSkillRepository;
import jobflow.domain.skill.ExperienceTagCode;
import jobflow.global.error.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JdMatchServiceTest {

    private final UserProjectRepository userProjectRepository = mock(UserProjectRepository.class);
    private final UserProjectAnalysisRepository userProjectAnalysisRepository =
            mock(UserProjectAnalysisRepository.class);
    private final UserProjectSkillRepository userProjectSkillRepository = mock(UserProjectSkillRepository.class);
    private final UserProjectExperienceTagRepository userProjectExperienceTagRepository =
            mock(UserProjectExperienceTagRepository.class);
    private final JobSkillIndexQueryService jobSkillIndexQueryService = mock(JobSkillIndexQueryService.class);
    private final JobExperienceTagRepository jobExperienceTagRepository = mock(JobExperienceTagRepository.class);
    private final JdMatchScoreCalculator jdMatchScoreCalculator = new JdMatchScoreCalculator();

    private final JdMatchService jdMatchService = new JdMatchService(
            userProjectRepository,
            userProjectAnalysisRepository,
            userProjectSkillRepository,
            userProjectExperienceTagRepository,
            jobSkillIndexQueryService,
            jobExperienceTagRepository,
            jdMatchScoreCalculator
    );

    @Test
    @DisplayName("프로젝트 최신 분석 결과와 공고 skill/tag를 비교해 JD 매칭 점수를 반환한다")
    void findProjectJobMatches() {
        UserProjectAnalysis analysis = analysis();
        JobSkillMatchDetail strongExperienceMatch = jobSkillMatchDetail(
                200L,
                "백엔드 플랫폼 개발자",
                CareerLevel.JUNIOR,
                2,
                2,
                0,
                0,
                List.of("Java", "Spring Boot"),
                List.of(),
                List.of(),
                List.of()
        );
        JobSkillMatchDetail strongRequiredMatch = jobSkillMatchDetail(
                100L,
                "백엔드 API 개발자",
                CareerLevel.MID,
                4,
                3,
                2,
                1,
                List.of("Java", "Spring Boot", "MySQL"),
                List.of("Redis"),
                List.of("Docker"),
                List.of("Kubernetes")
        );
        List<UserProjectExperienceTag> projectExperienceTags = List.of(
                projectExperienceTag("CI_CD", "CI/CD"),
                projectExperienceTag("TESTING", "테스트")
        );
        List<JobExperienceTag> jobExperienceTags = List.of(
                jobExperienceTag(100L, "CI_CD", "CI/CD"),
                jobExperienceTag(100L, "CLOUD_INFRA", "클라우드/인프라"),
                jobExperienceTag(200L, "TESTING", "테스트")
        );

        given(userProjectRepository.existsByIdAndUserId(10L, 1L)).willReturn(true);
        given(userProjectAnalysisRepository.findFirstByUserProjectIdAndUserProjectUserIdOrderByAnalyzedAtDescIdDesc(
                10L,
                1L
        )).willReturn(Optional.of(analysis));
        given(userProjectSkillRepository.findDistinctSkillIdsByAnalysisId(1000L)).willReturn(List.of(1L, 7L, 16L));
        given(userProjectExperienceTagRepository.findByAnalysisIdWithTagCode(1000L)).willReturn(projectExperienceTags);
        given(jobSkillIndexQueryService.findTopOpenJobMatchDetails(
                List.of(1L, 7L, 16L),
                List.of(JobRole.BACKEND),
                50
        )).willReturn(List.of(strongRequiredMatch, strongExperienceMatch));
        given(jobExperienceTagRepository.findByJobIdInWithTagCode(List.of(100L, 200L))).willReturn(jobExperienceTags);

        List<JdJobMatchResponse> responses = jdMatchService.findProjectJobMatches(
                1L,
                10L,
                List.of(JobRole.BACKEND),
                CareerLevel.MID,
                10
        );

        assertThat(responses).hasSize(2);

        JdJobMatchResponse topMatch = responses.get(0);
        assertThat(topMatch.jobId()).isEqualTo(200L);
        assertThat(topMatch.title()).isEqualTo("백엔드 플랫폼 개발자");
        assertThat(topMatch.score().totalScore()).isEqualByComparingTo("76.50");
        assertThat(topMatch.requiredSkillCount()).isEqualTo(2);
        assertThat(topMatch.matchedRequiredSkillCount()).isEqualTo(2);
        assertThat(topMatch.experienceTagCount()).isEqualTo(1);
        assertThat(topMatch.matchedExperienceTagCount()).isEqualTo(1);
        assertThat(topMatch.matchedExperienceTags())
                .extracting("code")
                .containsExactly("TESTING");
        assertThat(topMatch.missingExperienceTags()).isEmpty();

        JdJobMatchResponse secondMatch = responses.get(1);
        assertThat(secondMatch.jobId()).isEqualTo(100L);
        assertThat(secondMatch.score().totalScore()).isEqualByComparingTo("67.75");
        assertThat(secondMatch.matchedRequiredSkills()).containsExactly("Java", "Spring Boot", "MySQL");
        assertThat(secondMatch.missingRequiredSkills()).containsExactly("Redis");
        assertThat(secondMatch.matchedPreferredSkills()).containsExactly("Docker");
        assertThat(secondMatch.missingPreferredSkills()).containsExactly("Kubernetes");
        assertThat(secondMatch.matchedExperienceTags())
                .extracting("code")
                .containsExactly("CI_CD");
        assertThat(secondMatch.missingExperienceTags())
                .extracting("code")
                .containsExactly("CLOUD_INFRA");
    }

    @Test
    @DisplayName("프로젝트 분석 결과가 없으면 USER_PROJECT_NOT_FOUND 예외를 던진다")
    void findProjectJobMatchesWithoutAnalysis() {
        given(userProjectRepository.existsByIdAndUserId(10L, 1L)).willReturn(true);
        given(userProjectAnalysisRepository.findFirstByUserProjectIdAndUserProjectUserIdOrderByAnalyzedAtDescIdDesc(
                10L,
                1L
        )).willReturn(Optional.empty());

        assertThatThrownBy(() -> jdMatchService.findProjectJobMatches(
                1L,
                10L,
                List.of(JobRole.BACKEND),
                CareerLevel.MID,
                10
        )).isInstanceOf(EntityNotFoundException.class);

        verifyNoInteractions(
                userProjectSkillRepository,
                userProjectExperienceTagRepository,
                jobSkillIndexQueryService,
                jobExperienceTagRepository
        );
    }

    @Test
    @DisplayName("프로젝트 skill/tag가 모두 비어 있으면 빈 매칭 결과를 반환한다")
    void findProjectJobMatchesWithoutProjectInventory() {
        UserProjectAnalysis analysis = analysis();

        given(userProjectRepository.existsByIdAndUserId(10L, 1L)).willReturn(true);
        given(userProjectAnalysisRepository.findFirstByUserProjectIdAndUserProjectUserIdOrderByAnalyzedAtDescIdDesc(
                10L,
                1L
        )).willReturn(Optional.of(analysis));
        given(userProjectSkillRepository.findDistinctSkillIdsByAnalysisId(1000L)).willReturn(List.of());
        given(userProjectExperienceTagRepository.findByAnalysisIdWithTagCode(1000L)).willReturn(List.of());

        List<JdJobMatchResponse> responses = jdMatchService.findProjectJobMatches(
                1L,
                10L,
                List.of(JobRole.BACKEND),
                CareerLevel.MID,
                10
        );

        assertThat(responses).isEmpty();
        verifyNoInteractions(jobSkillIndexQueryService, jobExperienceTagRepository);
    }

    @Test
    @DisplayName("소유하지 않은 프로젝트면 USER_PROJECT_NOT_FOUND 예외를 던진다")
    void findProjectJobMatchesWithMissingProject() {
        given(userProjectRepository.existsByIdAndUserId(999L, 1L)).willReturn(false);

        assertThatThrownBy(() -> jdMatchService.findProjectJobMatches(
                1L,
                999L,
                List.of(JobRole.BACKEND),
                CareerLevel.MID,
                10
        )).isInstanceOf(EntityNotFoundException.class);

        verifyNoInteractions(
                userProjectAnalysisRepository,
                userProjectSkillRepository,
                userProjectExperienceTagRepository,
                jobSkillIndexQueryService,
                jobExperienceTagRepository
        );
    }

    private UserProjectAnalysis analysis() {
        UserProjectAnalysis analysis = mock(UserProjectAnalysis.class);
        given(analysis.getId()).willReturn(1000L);
        given(analysis.getAnalysisVersion()).willReturn(2);
        given(analysis.getAnalyzedAt()).willReturn(LocalDateTime.of(2026, 6, 15, 10, 0));
        given(analysis.getConfidenceScore()).willReturn(new BigDecimal("0.8000"));
        return analysis;
    }

    private JobSkillMatchDetail jobSkillMatchDetail(
            Long jobId,
            String title,
            CareerLevel careerLevel,
            long requiredSkillCount,
            long matchedRequiredSkillCount,
            long preferredSkillCount,
            long matchedPreferredSkillCount,
            List<String> matchedRequiredSkills,
            List<String> missingRequiredSkills,
            List<String> matchedPreferredSkills,
            List<String> missingPreferredSkills
    ) {
        return new JobSkillMatchDetail(
                new JobSkillMatchSummary(
                        jobId,
                        title,
                        "Example Company",
                        JobRole.BACKEND,
                        careerLevel,
                        requiredSkillCount,
                        matchedRequiredSkillCount,
                        preferredSkillCount,
                        matchedPreferredSkillCount
                ),
                matchedRequiredSkills,
                missingRequiredSkills,
                matchedPreferredSkills,
                missingPreferredSkills
        );
    }

    private UserProjectExperienceTag projectExperienceTag(String code, String name) {
        ExperienceTagCode tagCode = experienceTagCode(code, name);
        UserProjectExperienceTag projectExperienceTag = mock(UserProjectExperienceTag.class);
        given(projectExperienceTag.getTagCode()).willReturn(tagCode);
        return projectExperienceTag;
    }

    private JobExperienceTag jobExperienceTag(Long jobId, String code, String name) {
        ExperienceTagCode tagCode = experienceTagCode(code, name);
        Job job = mock(Job.class);
        given(job.getId()).willReturn(jobId);

        JobExperienceTag jobExperienceTag = mock(JobExperienceTag.class);
        given(jobExperienceTag.getJob()).willReturn(job);
        given(jobExperienceTag.getTagCode()).willReturn(tagCode);
        return jobExperienceTag;
    }

    private ExperienceTagCode experienceTagCode(String code, String name) {
        try {
            Constructor<ExperienceTagCode> constructor = ExperienceTagCode.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            ExperienceTagCode tagCode = constructor.newInstance();
            ReflectionTestUtils.setField(tagCode, "code", code);
            ReflectionTestUtils.setField(tagCode, "name", name);
            ReflectionTestUtils.setField(tagCode, "description", name + " 설명");
            ReflectionTestUtils.setField(tagCode, "createdAt", LocalDateTime.now());
            return tagCode;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create experience tag code for test", exception);
        }
    }
}
