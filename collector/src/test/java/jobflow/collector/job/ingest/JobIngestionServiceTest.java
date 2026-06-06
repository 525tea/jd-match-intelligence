package jobflow.collector.job.ingest;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Optional;
import jobflow.collector.job.CareerLevel;
import jobflow.collector.job.EmploymentType;
import jobflow.collector.job.Job;
import jobflow.collector.job.JobRepository;
import jobflow.collector.job.JobRole;
import jobflow.collector.job.RemoteType;
import jobflow.collector.outbox.OutboxEvent;
import jobflow.collector.outbox.OutboxEventService;
import jobflow.collector.outbox.OutboxEventTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JobIngestionServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private OutboxEventService outboxEventService;

    @Mock
    private JobSkillNormalizationService jobSkillNormalizationService;

    private final IngestedJobMapper mapper = new IngestedJobMapper(new CanonicalFingerprintGenerator());

    @Test
    @DisplayName("신규 수집 공고를 저장하고 JOB_CREATED outbox event를 기록한다")
    void ingestNewJob() {
        IngestedJobPosting posting = createPosting("zighang-123", "백엔드 개발자");
        JobIngestionService service = new JobIngestionService(
                jobRepository,
                mapper,
                outboxEventService,
                jobSkillNormalizationService
        );

        given(jobRepository.findBySourceAndExternalId("ZIGHANG", "zighang-123"))
                .willReturn(Optional.empty());
        given(jobRepository.save(any(Job.class)))
                .willAnswer(invocation -> {
                    Job savedJob = invocation.getArgument(0);
                    ReflectionTestUtils.setField(savedJob, "id", 1L);
                    return savedJob;
                });

        JobIngestionResult result = service.ingest(posting);

        assertThat(result.type()).isEqualTo(JobIngestionResultType.CREATED);
        assertThat(result.job().getId()).isEqualTo(1L);
        assertThat(result.job().getSource()).isEqualTo("ZIGHANG");
        assertThat(result.job().getExternalId()).isEqualTo("zighang-123");
        assertThat(result.job().getTitle()).isEqualTo("백엔드 개발자");
        assertThat(result.job().getOriginalUrl()).isEqualTo("https://zighang.com/jobs/zighang-123?utm=test");
        assertThat(result.job().getCanonicalFingerprint()).hasSize(64);

        verify(jobRepository).save(any(Job.class));
        verify(outboxEventService).save(
                eq("JOB"),
                eq(1L),
                eq(OutboxEventTypes.JOB_CREATED),
                any(),
                eq(OutboxEvent.TOPIC_JOB_EVENTS)
        );
        verify(jobSkillNormalizationService).replaceNormalizedSkills(
                result.job(),
                result.job().getTitle(),
                result.job().getDescription(),
                result.job().getRoleDetail()
        );
    }

    @Test
    @DisplayName("기존 수집 공고를 갱신하고 JOB_UPDATED outbox event를 기록한다")
    void ingestExistingJob() {
        IngestedJobPosting posting = createPosting("zighang-123", "백엔드 개발자 수정");
        Job existingJob = createJob("ZIGHANG", "zighang-123", "백엔드 개발자");
        ReflectionTestUtils.setField(existingJob, "id", 1L);

        LocalDateTime originalCollectedAt = LocalDateTime.of(2026, 6, 4, 9, 0);
        existingJob.updateCrawlingMetadata(
                null,
                "https://zighang.com/jobs/zighang-123",
                originalCollectedAt,
                LocalDateTime.of(2026, 6, 4, 9, 5),
                null,
                """
                        {"titleText":"백엔드 개발자"}
                        """,
                "zighang-parser-0.1"
        );

        JobIngestionService service = new JobIngestionService(
                jobRepository,
                mapper,
                outboxEventService,
                jobSkillNormalizationService
        );

        given(jobRepository.findBySourceAndExternalId("ZIGHANG", "zighang-123"))
                .willReturn(Optional.of(existingJob));

        JobIngestionResult result = service.ingest(posting);

        assertThat(result.type()).isEqualTo(JobIngestionResultType.UPDATED);
        assertThat(result.job()).isEqualTo(existingJob);
        assertThat(existingJob.getTitle()).isEqualTo("백엔드 개발자 수정");
        assertThat(existingJob.getCollectedAt()).isEqualTo(originalCollectedAt);
        assertThat(existingJob.getLastSeenAt()).isEqualTo(posting.lastSeenAt());
        assertThat(existingJob.getRawData()).contains("백엔드 개발자 수정");
        assertThat(existingJob.getCanonicalFingerprint()).hasSize(64);

        verify(outboxEventService).save(
                eq("JOB"),
                eq(1L),
                eq(OutboxEventTypes.JOB_UPDATED),
                any(),
                eq(OutboxEvent.TOPIC_JOB_EVENTS)
        );
        verify(jobSkillNormalizationService).replaceNormalizedSkills(
                existingJob,
                existingJob.getTitle(),
                existingJob.getDescription(),
                existingJob.getRoleDetail()
        );
    }

    @Test
    @DisplayName("같은 canonical fingerprint를 가진 다른 source 공고 후보를 탐지한다")
    void detectDuplicateCandidatesAcrossSources() {
        IngestedJobPosting posting = createPosting("zighang-123", "백엔드 개발자");
        Job duplicateCandidate = createJob("JUMPIT", "jumpit-999", "백엔드 개발자");
        ReflectionTestUtils.setField(duplicateCandidate, "id", 2L);

        JobIngestionService service = new JobIngestionService(
                jobRepository,
                mapper,
                outboxEventService,
                jobSkillNormalizationService
        );

        given(jobRepository.findBySourceAndExternalId("ZIGHANG", "zighang-123"))
                .willReturn(Optional.empty());
        given(jobRepository.save(any(Job.class)))
                .willAnswer(invocation -> {
                    Job savedJob = invocation.getArgument(0);
                    ReflectionTestUtils.setField(savedJob, "id", 1L);
                    return savedJob;
                });
        given(jobRepository.findByCanonicalFingerprintAndSourceNot(any(), eq("ZIGHANG")))
                .willReturn(List.of(duplicateCandidate));

        JobIngestionResult result = service.ingest(posting);

        assertThat(result.type()).isEqualTo(JobIngestionResultType.CREATED);
        assertThat(result.hasDuplicateCandidates()).isTrue();
        assertThat(result.duplicateCandidates()).containsExactly(duplicateCandidate);
    }

    private Job createJob(String source, String externalId, String title) {
        return Job.create(
                source,
                externalId,
                title,
                "JobFlow",
                "Spring Boot 기반 백엔드 개발자를 채용합니다.",
                "https://zighang.com/jobs/" + externalId,
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
                LocalDateTime.of(2026, 6, 4, 9, 0),
                LocalDateTime.of(2026, 7, 1, 23, 59)
        );
    }

    private IngestedJobPosting createPosting(String externalId, String title) {
        return new IngestedJobPosting(
                JobIngestionSource.ZIGHANG,
                externalId,
                title,
                "JobFlow",
                "Spring Boot 기반 백엔드 개발자를 채용합니다.",
                "https://zighang.com/jobs/" + externalId + "?utm=test",
                "https://zighang.com/jobs/" + externalId,
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
                LocalDateTime.of(2026, 6, 4, 9, 0),
                LocalDateTime.of(2026, 7, 1, 23, 59),
                LocalDateTime.of(2026, 6, 4, 10, 0),
                LocalDateTime.of(2026, 6, 4, 10, 5),
                LocalDateTime.of(2026, 6, 4, 9, 30),
                """
                        {"titleText":"%s","companyText":"JobFlow"}
                        """.formatted(title),
                "zighang-parser-0.1"
        );
    }
}
