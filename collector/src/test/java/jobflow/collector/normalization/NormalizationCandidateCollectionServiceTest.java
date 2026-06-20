package jobflow.collector.normalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jobflow.collector.job.CareerLevel;
import jobflow.collector.job.EmploymentType;
import jobflow.collector.job.Job;
import jobflow.collector.job.JobRepository;
import jobflow.collector.job.JobRole;
import jobflow.collector.job.RemoteType;
import jobflow.collector.skill.Skill;
import jobflow.collector.skill.SkillAlias;
import jobflow.collector.skill.SkillAliasRepository;
import jobflow.collector.skill.SkillCategory;
import jobflow.collector.skill.SkillRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NormalizationCandidateCollectionServiceTest {

    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 6, 1, 9, 0);
    private static final LocalDateTime DEADLINE_AT = LocalDateTime.of(2026, 7, 1, 23, 59);

    @Mock
    private JobRepository jobRepository;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private SkillAliasRepository skillAliasRepository;

    @Mock
    private NormalizationCandidateRepository candidateRepository;

    @Test
    @DisplayName("기존 skill/alias/section label을 제외하고 리뷰 후보만 저장한다")
    void collectOnlyUnknownCandidates() {
        Job job = createJob(
                """
                포지션 상세 정보

                기술스택
                Java SpringBoot FastAPI FastMCP LangGraph OpenTelemetry

                주요업무
                AI backend API 개발

                우대사항
                • 복지 지원
                - 식대(E-식권)지원

                [채용 전형]
                서류 검토 후 기술 인터뷰를 진행합니다.

                [사용기술]
                Java SpringBoot FastMCP
                """
        );
        Skill javaSkill = Skill.create("Java", "java", SkillCategory.LANGUAGE);
        Skill springBoot = Skill.create("Spring Boot", "spring boot", SkillCategory.FRAMEWORK);
        Skill fastApi = Skill.create("FastAPI", "fastapi", SkillCategory.FRAMEWORK);
        SkillAlias springBootAlias = SkillAlias.create(springBoot, "SpringBoot", "springboot", java.math.BigDecimal.ONE);

        given(jobRepository.findBySourceInOrderByIdAsc(List.of("JUMPIT"))).willReturn(List.of(job));
        given(skillRepository.findAllByOrderByNameAsc()).willReturn(List.of(fastApi, javaSkill, springBoot));
        given(skillAliasRepository.findByEnabledTrueOrderByNormalizedAliasAsc()).willReturn(List.of(springBootAlias));
        given(candidateRepository.findByTypeAndSourceAndNormalizedValue(any(), any(), any()))
                .willReturn(Optional.empty());

        NormalizationCandidateCollectionService service = new NormalizationCandidateCollectionService(
                jobRepository,
                skillRepository,
                skillAliasRepository,
                candidateRepository
        );

        NormalizationCandidateCollectionSummary summary = service.collect(List.of("JUMPIT"));

        ArgumentCaptor<NormalizationCandidate> candidateCaptor =
                ArgumentCaptor.forClass(NormalizationCandidate.class);
        org.mockito.Mockito.verify(candidateRepository, org.mockito.Mockito.times(3))
                .save(candidateCaptor.capture());

        assertThat(summary.processedJobCount()).isEqualTo(1);
        assertThat(summary.skillAliasCandidateCount()).isEqualTo(3);
        assertThat(summary.sectionLabelCandidateCount()).isEqualTo(0);
        assertThat(candidateCaptor.getAllValues())
                .extracting(
                        NormalizationCandidate::getType,
                        NormalizationCandidate::getSource,
                        NormalizationCandidate::getValue,
                        NormalizationCandidate::getNormalizedValue,
                        NormalizationCandidate::getOccurrenceCount,
                        NormalizationCandidate::getStatus
                )
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                                NormalizationCandidateType.SKILL_ALIAS,
                                "JUMPIT",
                                "FastMCP",
                                "fastmcp",
                                1,
                                NormalizationCandidateStatus.PENDING
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                NormalizationCandidateType.SKILL_ALIAS,
                                "JUMPIT",
                                "LangGraph",
                                "langgraph",
                                1,
                                NormalizationCandidateStatus.PENDING
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                NormalizationCandidateType.SKILL_ALIAS,
                                "JUMPIT",
                                "OpenTelemetry",
                                "opentelemetry",
                                1,
                                NormalizationCandidateStatus.PENDING
                        )
                );
    }

    @Test
    @DisplayName("같은 공고를 다시 수집해도 occurrence count를 중복 증가시키지 않는다")
    void collectDoesNotDoubleCountSameJob() {
        Job job = createJob(
                """
                기술스택
                FastMCP
                """
        );
        NormalizationCandidate existingCandidate = NormalizationCandidate.firstSeen(
                NormalizationCandidateType.SKILL_ALIAS,
                "JUMPIT",
                "FastMCP",
                "fastmcp",
                100L,
                "Backend Engineer",
                "FastMCP"
        );
        ReflectionTestUtils.setField(existingCandidate, "id", 1L);

        given(jobRepository.findBySourceInOrderByIdAsc(List.of("JUMPIT"))).willReturn(List.of(job));
        given(skillRepository.findAllByOrderByNameAsc()).willReturn(List.of());
        given(skillAliasRepository.findByEnabledTrueOrderByNormalizedAliasAsc()).willReturn(List.of());
        given(candidateRepository.findByTypeAndSourceAndNormalizedValue(
                NormalizationCandidateType.SKILL_ALIAS,
                "JUMPIT",
                "fastmcp"
        )).willReturn(Optional.of(existingCandidate));

        NormalizationCandidateCollectionService service = new NormalizationCandidateCollectionService(
                jobRepository,
                skillRepository,
                skillAliasRepository,
                candidateRepository
        );

        service.collect(List.of("JUMPIT"));

        ArgumentCaptor<NormalizationCandidate> candidateCaptor =
                ArgumentCaptor.forClass(NormalizationCandidate.class);
        org.mockito.Mockito.verify(candidateRepository).save(candidateCaptor.capture());

        assertThat(candidateCaptor.getValue().getOccurrenceCount()).isEqualTo(1);
        assertThat(candidateCaptor.getValue().getLastSeenJobId()).isEqualTo(100L);
    }

    private Job createJob(String description) {
        Job job = Job.create(
                "JUMPIT",
                "candidate-100",
                "Backend Engineer",
                "Example Labs",
                description,
                "https://example.com/jobs/candidate-100",
                JobRole.BACKEND,
                "Java/Spring",
                CareerLevel.JUNIOR,
                0,
                3,
                "학력무관",
                EmploymentType.FULL_TIME,
                "STARTUP",
                "IT",
                "KR",
                "Seoul",
                "Gangnam",
                RemoteType.HYBRID,
                4000,
                7000,
                "KRW",
                true,
                1,
                OPENED_AT,
                DEADLINE_AT
        );
        ReflectionTestUtils.setField(job, "id", 100L);
        return job;
    }
}
