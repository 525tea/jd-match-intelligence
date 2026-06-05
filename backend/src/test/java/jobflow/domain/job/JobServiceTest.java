package jobflow.domain.job;

import jobflow.domain.job.dto.*;
import jobflow.domain.outbox.OutboxEventService;
import jobflow.domain.skill.*;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.ConflictException;
import jobflow.global.error.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import jobflow.domain.job.dto.JobSearchResponse;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobSkillRepository jobSkillRepository;

    @Mock
    private JobExperienceTagRepository jobExperienceTagRepository;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private ExperienceTagCodeRepository experienceTagCodeRepository;

    @InjectMocks
    private JobService jobService;

    @Mock
    private OutboxEventService outboxEventService;

    @Test
    @DisplayName("공고를 생성하고 스킬과 경험 태그를 함께 저장한다")
    void createJob() {
        JobCreateRequest request = createRequest();
        Job savedJob = createJobEntity(1L);
        Skill skill = createSkill(1L);
        ExperienceTagCode tagCode = createExperienceTagCode("HIGH_TRAFFIC");

        JobSkill jobSkill = JobSkill.create(savedJob, skill, RequirementType.REQUIRED);
        JobExperienceTag jobExperienceTag = JobExperienceTag.create(
                savedJob,
                tagCode,
                "대용량 트래픽 처리 경험"
        );

        given(jobRepository.save(any(Job.class))).willReturn(savedJob);
        given(skillRepository.findById(1L)).willReturn(Optional.of(skill));
        given(experienceTagCodeRepository.findById("HIGH_TRAFFIC")).willReturn(Optional.of(tagCode));
        given(jobSkillRepository.saveAll(any())).willReturn(List.of(jobSkill));
        given(jobExperienceTagRepository.saveAll(any())).willReturn(List.of(jobExperienceTag));

        JobResponse response = jobService.createJob(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo("백엔드 개발자");
        assertThat(response.companyName()).isEqualTo("JobFlow");
        assertThat(response.status()).isEqualTo(JobStatus.OPEN);
        assertThat(response.skills()).hasSize(1);
        assertThat(response.skills().getFirst().name()).isEqualTo("Spring Boot");
        assertThat(response.skills().getFirst().requirementType()).isEqualTo(RequirementType.REQUIRED);
        assertThat(response.experienceTags()).hasSize(1);
        assertThat(response.experienceTags().getFirst().code()).isEqualTo("HIGH_TRAFFIC");

        verify(jobRepository).save(any(Job.class));
        verify(jobSkillRepository).saveAll(any());
        verify(jobExperienceTagRepository).saveAll(any());
        verify(outboxEventService).save(
                eq("JOB"),
                eq(1L),
                eq("JOB_CREATED"),
                any(),
                eq("job.events")
        );
    }

    @Test
    @DisplayName("존재하지 않는 스킬로 공고를 생성하면 예외가 발생한다")
    void createJobWithMissingSkill() {
        JobCreateRequest request = createRequest();
        Job savedJob = createJobEntity(1L);

        given(jobRepository.save(any(Job.class))).willReturn(savedJob);
        given(skillRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.createJob(request))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SKILL_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 경험 태그로 공고를 생성하면 예외가 발생한다")
    void createJobWithMissingExperienceTag() {
        JobCreateRequest request = createRequest();
        Job savedJob = createJobEntity(1L);
        Skill skill = createSkill(1L);
        JobSkill jobSkill = JobSkill.create(savedJob, skill, RequirementType.REQUIRED);

        given(jobRepository.save(any(Job.class))).willReturn(savedJob);
        given(skillRepository.findById(1L)).willReturn(Optional.of(skill));
        given(jobSkillRepository.saveAll(any())).willReturn(List.of(jobSkill));
        given(experienceTagCodeRepository.findById("HIGH_TRAFFIC")).willReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.createJob(request))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXPERIENCE_TAG_NOT_FOUND);
    }

    @Test
    @DisplayName("공고 상세를 조회한다")
    void getJob() {
        Long jobId = 1L;
        Job job = createJobEntity(jobId);
        Skill skill = createSkill(1L);
        ExperienceTagCode tagCode = createExperienceTagCode("HIGH_TRAFFIC");
        JobSkill jobSkill = JobSkill.create(job, skill, RequirementType.REQUIRED);
        JobExperienceTag jobExperienceTag = JobExperienceTag.create(
                job,
                tagCode,
                "대용량 트래픽 처리 경험"
        );

        given(jobRepository.findById(jobId)).willReturn(Optional.of(job));
        given(jobSkillRepository.findByJobId(jobId)).willReturn(List.of(jobSkill));
        given(jobExperienceTagRepository.findByJobId(jobId)).willReturn(List.of(jobExperienceTag));

        JobResponse response = jobService.getJob(jobId);

        assertThat(response.id()).isEqualTo(jobId);
        assertThat(response.title()).isEqualTo("백엔드 개발자");
        assertThat(response.skills()).hasSize(1);
        assertThat(response.experienceTags()).hasSize(1);
    }

    @Test
    @DisplayName("존재하지 않는 공고를 조회하면 예외가 발생한다")
    void getMissingJob() {
        Long jobId = 999L;

        given(jobRepository.findById(jobId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.getJob(jobId))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.JOB_NOT_FOUND);
    }

    @Test
    @DisplayName("공고 목록을 조회한다")
    void getJobs() {
        Job job = createJobEntity(1L);

        given(jobRepository.findAllByOrderByCreatedAtDesc()).willReturn(List.of(job));

        List<JobSummaryResponse> responses = jobService.getJobs();

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().id()).isEqualTo(1L);
        assertThat(responses.getFirst().title()).isEqualTo("백엔드 개발자");
        assertThat(responses.getFirst().status()).isEqualTo(JobStatus.OPEN);
    }

    @Test
    @DisplayName("FULLTEXT 검색 결과와 score를 요약 응답으로 변환한다")
    void searchJobs() {
        JobSearchProjection projection = jobSearchProjection();

        given(jobRepository.searchOpenJobsByFullText("백엔드", 20))
                .willReturn(List.of(projection));

        List<JobSearchResponse> responses = jobService.searchJobs(" 백엔드 ", 20);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().id()).isEqualTo(1L);
        assertThat(responses.getFirst().title()).isEqualTo("백엔드 개발자");
        assertThat(responses.getFirst().score()).isEqualTo(0.42);

        verify(jobRepository).searchOpenJobsByFullText("백엔드", 20);
    }

    @Test
    @DisplayName("검색어가 비어 있으면 빈 목록을 반환한다")
    void searchJobsWithBlankKeyword() {
        List<JobSearchResponse> responses = jobService.searchJobs(" ", 20);

        assertThat(responses).isEmpty();
    }

    @Test
    @DisplayName("검색 limit은 1 이상 100 이하로 보정한다")
    void searchJobsClampLimit() {
        JobSearchProjection projection = jobSearchProjection();

        given(jobRepository.searchOpenJobsByFullText("백엔드", 100))
                .willReturn(List.of(projection));

        List<JobSearchResponse> responses = jobService.searchJobs("백엔드", 999);

        assertThat(responses).hasSize(1);

        verify(jobRepository).searchOpenJobsByFullText("백엔드", 100);
    }

    @Test
    @DisplayName("공고 기본 정보를 수정한다")
    void updateJob() {
        Long jobId = 1L;
        Job job = createJobEntity(jobId);
        JobUpdateRequest request = updateRequest();

        given(jobRepository.findById(jobId)).willReturn(Optional.of(job));
        given(jobSkillRepository.findByJobId(jobId)).willReturn(List.of());
        given(jobExperienceTagRepository.findByJobId(jobId)).willReturn(List.of());

        JobResponse response = jobService.updateJob(jobId, request);

        assertThat(response.id()).isEqualTo(jobId);
        assertThat(response.title()).isEqualTo("수정된 백엔드 개발자");
        assertThat(response.companyName()).isEqualTo("Updated JobFlow");
        assertThat(response.role()).isEqualTo(JobRole.BACKEND);

        verify(outboxEventService).save(
                eq("JOB"),
                eq(jobId),
                eq("JOB_UPDATED"),
                any(),
                eq("job.events")
        );
    }

    @Test
    @DisplayName("공고를 종료 상태로 변경한다")
    void closeJob() {
        Long jobId = 1L;
        Job job = createJobEntity(jobId);

        given(jobRepository.findById(jobId)).willReturn(Optional.of(job));
        given(jobSkillRepository.findByJobId(jobId)).willReturn(List.of());
        given(jobExperienceTagRepository.findByJobId(jobId)).willReturn(List.of());

        JobResponse response = jobService.closeJob(jobId);

        assertThat(response.status()).isEqualTo(JobStatus.CLOSED);

        verify(outboxEventService).save(
                eq("JOB"),
                eq(jobId),
                eq("JOB_CLOSED"),
                any(),
                eq("job.events")
        );
    }

    @Test
    @DisplayName("공고를 만료 상태로 변경한다")
    void expireJob() {
        Long jobId = 1L;
        Job job = createJobEntity(jobId);

        given(jobRepository.findById(jobId)).willReturn(Optional.of(job));
        given(jobSkillRepository.findByJobId(jobId)).willReturn(List.of());
        given(jobExperienceTagRepository.findByJobId(jobId)).willReturn(List.of());

        JobResponse response = jobService.expireJob(jobId);

        assertThat(response.status()).isEqualTo(JobStatus.EXPIRED);

        verify(outboxEventService).save(
                eq("JOB"),
                eq(jobId),
                eq("JOB_EXPIRED"),
                any(),
                eq("job.events")
        );
    }

    @Test
    @DisplayName("이미 종료된 공고를 다시 상태 변경하면 예외가 발생한다")
    void closeAlreadyClosedJob() {
        Long jobId = 1L;
        Job job = createJobEntity(jobId);
        job.close();

        given(jobRepository.findById(jobId)).willReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.closeJob(jobId))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.JOB_STATUS_CONFLICT);
    }

    private JobCreateRequest createRequest() {
        return new JobCreateRequest(
                "SARAMIN",
                "external-1",
                "백엔드 개발자",
                "JobFlow",
                "Spring Boot 기반 백엔드 개발자 채용",
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
                "Seoul",
                "Gangnam",
                RemoteType.HYBRID,
                3000,
                5000,
                "KRW",
                true,
                2,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 23, 59),
                List.of(new JobSkillRequest(1L, RequirementType.REQUIRED)),
                List.of(new JobExperienceTagRequest("HIGH_TRAFFIC", "대용량 트래픽 처리 경험"))
        );
    }

    private JobUpdateRequest updateRequest() {
        return new JobUpdateRequest(
                "수정된 백엔드 개발자",
                "Updated JobFlow",
                "수정된 공고 설명",
                "https://example.com/jobs/1-updated",
                JobRole.BACKEND,
                "Java/Spring/JPA",
                CareerLevel.MID,
                3,
                5,
                "BACHELOR",
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
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 23, 59)
        );
    }

    private Job createJobEntity(Long id) {
        Job job = Job.create(
                "SARAMIN",
                "external-1",
                "백엔드 개발자",
                "JobFlow",
                "Spring Boot 기반 백엔드 개발자 채용",
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
        );
        ReflectionTestUtils.setField(job, "id", id);
        return job;
    }

    private JobSearchProjection jobSearchProjection() {
        return new JobSearchProjection() {
            @Override
            public Long getId() {
                return 1L;
            }

            @Override
            public String getTitle() {
                return "백엔드 개발자";
            }

            @Override
            public String getCompanyName() {
                return "JobFlow";
            }

            @Override
            public String getRole() {
                return "BACKEND";
            }

            @Override
            public String getCareerLevel() {
                return "JUNIOR";
            }

            @Override
            public String getEmploymentType() {
                return "FULL_TIME";
            }

            @Override
            public String getLocationRegion() {
                return "Seoul";
            }

            @Override
            public String getLocationCity() {
                return "Gangnam";
            }

            @Override
            public String getRemoteType() {
                return "HYBRID";
            }

            @Override
            public LocalDateTime getDeadlineAt() {
                return LocalDateTime.of(2026, 7, 1, 23, 59);
            }

            @Override
            public String getStatus() {
                return "OPEN";
            }

            @Override
            public Double getScore() {
                return 0.42;
            }
        };
    }

    private Skill createSkill(Long id) {
        Skill skill = Skill.create(
                "Spring Boot",
                "spring boot",
                SkillCategory.FRAMEWORK
        );
        ReflectionTestUtils.setField(skill, "id", id);
        return skill;
    }

    private ExperienceTagCode createExperienceTagCode(String code) {
        try {
            Constructor<ExperienceTagCode> constructor = ExperienceTagCode.class.getDeclaredConstructor();
            constructor.setAccessible(true);

            ExperienceTagCode tagCode = constructor.newInstance();
            ReflectionTestUtils.setField(tagCode, "code", code);
            ReflectionTestUtils.setField(tagCode, "name", "대용량 트래픽");
            ReflectionTestUtils.setField(tagCode, "description", "대용량 트래픽 처리 경험");

            return tagCode;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create ExperienceTagCode for test", exception);
        }
    }
}
