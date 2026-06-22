package jobflow.collector.job.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import jobflow.collector.job.CareerLevel;
import jobflow.collector.job.EmploymentType;
import jobflow.collector.job.Job;
import jobflow.collector.job.JobExperienceTag;
import jobflow.collector.job.JobRepository;
import jobflow.collector.job.JobRole;
import jobflow.collector.job.JobSkill;
import jobflow.collector.job.RemoteType;
import jobflow.collector.job.ingest.JdJobRoleClassificationService;
import jobflow.collector.job.ingest.JobExperienceTagNormalizationService;
import jobflow.collector.job.ingest.JobIngestionSource;
import jobflow.collector.job.ingest.JobPostingParser;
import jobflow.collector.job.ingest.JobSkillNormalizationService;
import jobflow.collector.job.ingest.JumpitJobPostingParser;
import jobflow.collector.job.ingest.WantedJobPostingParser;
import jobflow.collector.job.snapshot.RawJobSnapshotStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RawJobDescriptionReplayBackfillServiceTest {

    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 6, 1, 9, 0);
    private static final LocalDateTime DEADLINE_AT = LocalDateTime.of(2026, 7, 1, 23, 59);

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobSkillNormalizationService jobSkillNormalizationService;

    @Mock
    private JobExperienceTagNormalizationService jobExperienceTagNormalizationService;

    @Mock
    private RawJobSnapshotStorage rawJobSnapshotStorage;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Wanted raw_data를 replay해 description 섹션 줄바꿈을 복구하고 정규화 relation을 교체한다")
    void replayWantedRawData() {
        Job job = createJob(
                "WANTED",
                "wanted-100",
                "Old one-line description",
                wantedRawData()
        );
        RawJobDescriptionReplayBackfillService service = createService();

        given(jobRepository.findBySourceInOrderByIdAsc(List.of("WANTED")))
                .willReturn(List.of(job));
        given(jobSkillNormalizationService.replaceNormalizedSkills(any(), anyString(), anyString(), any()))
                .willReturn(List.of(mock(JobSkill.class)));
        given(jobExperienceTagNormalizationService.replaceNormalizedExperienceTags(any(), anyString(), anyString(), any()))
                .willReturn(List.of(mock(JobExperienceTag.class)));

        RawJobDescriptionReplayBackfillSummary summary = service.backfill(List.of("WANTED"));

        assertThat(job.getDescription())
                .contains("[회사 소개]\nExample Labs builds hiring tools.")
                .contains("[주요 업무]\nBuild backend APIs.")
                .contains("[자격 요건]\nJava and Spring Boot experience.")
                .contains("[채용절차 및 기타 지원 유의사항]\nDocument review and technical interview.");
        assertThat(summary.processedCount()).isEqualTo(1);
        assertThat(summary.updatedDescriptionCount()).isEqualTo(1);
        assertThat(summary.unchangedDescriptionCount()).isZero();
        assertThat(summary.updatedRoleCount()).isZero();
        assertThat(summary.skippedCount()).isZero();
        assertThat(summary.failedCount()).isZero();
        assertThat(summary.normalizedSkillJobCount()).isEqualTo(1);
        assertThat(summary.normalizedExperienceTagJobCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Jumpit raw_data.rawBody를 replay해 HTML 원문에서 description을 재생성한다")
    void replayJumpitRawBody() {
        Job job = createJob(
                "JUMPIT",
                "jumpit-100",
                "Old Jumpit description",
                jumpitRawData()
        );
        RawJobDescriptionReplayBackfillService service = createService();

        given(jobRepository.findBySourceInOrderByIdAsc(List.of("JUMPIT")))
                .willReturn(List.of(job));
        given(jobSkillNormalizationService.replaceNormalizedSkills(any(), anyString(), anyString(), any()))
                .willReturn(List.of());
        given(jobExperienceTagNormalizationService.replaceNormalizedExperienceTags(any(), anyString(), anyString(), any()))
                .willReturn(List.of());

        RawJobDescriptionReplayBackfillSummary summary = service.backfill(List.of("JUMPIT"));

        assertThat(job.getDescription())
                .contains("Build payment APIs")
                .contains("Java Spring Boot")
                .contains("Docker");
        assertThat(summary.processedCount()).isEqualTo(1);
        assertThat(summary.updatedDescriptionCount()).isEqualTo(1);
        assertThat(summary.updatedRoleCount()).isZero();
        assertThat(summary.normalizedSkillJobCount()).isZero();
        assertThat(summary.normalizedExperienceTagJobCount()).isZero();
    }

    @Test
    @DisplayName("raw_data가 없으면 description replay와 정규화 교체를 건너뛴다")
    void skipMissingRawData() {
        Job job = createJob(
                "WANTED",
                "wanted-101",
                "Existing description",
                null
        );
        RawJobDescriptionReplayBackfillService service = createService();

        given(jobRepository.findBySourceInOrderByIdAsc(List.of("WANTED")))
                .willReturn(List.of(job));

        RawJobDescriptionReplayBackfillSummary summary = service.backfill(List.of("WANTED"));

        assertThat(job.getDescription()).isEqualTo("Existing description");
        assertThat(summary.processedCount()).isEqualTo(1);
        assertThat(summary.updatedDescriptionCount()).isZero();
        assertThat(summary.updatedRoleCount()).isZero();
        assertThat(summary.skippedCount()).isEqualTo(1);
        verify(jobSkillNormalizationService, never()).replaceNormalizedSkills(any(), anyString(), anyString(), any());
        verify(jobExperienceTagNormalizationService, never())
                .replaceNormalizedExperienceTags(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("내용은 같아도 줄바꿈 포맷이 다르면 description을 갱신한다")
    void updateWhenOnlyLineBreaksDiffer() {
        String rawData = wantedRawData();
        String collapsedDescription = "[회사 소개] Example Labs builds hiring tools. "
                + "[주요 업무] Build backend APIs. "
                + "[자격 요건] Java and Spring Boot experience. "
                + "[채용절차 및 기타 지원 유의사항] Document review and technical interview.";
        Job job = createJob("WANTED", "wanted-102", collapsedDescription, rawData);
        RawJobDescriptionReplayBackfillService service = createService();

        given(jobRepository.findBySourceInOrderByIdAsc(List.of("WANTED")))
                .willReturn(List.of(job));
        given(jobSkillNormalizationService.replaceNormalizedSkills(any(), anyString(), anyString(), any()))
                .willReturn(List.of());
        given(jobExperienceTagNormalizationService.replaceNormalizedExperienceTags(any(), anyString(), anyString(), any()))
                .willReturn(List.of());

        RawJobDescriptionReplayBackfillSummary summary = service.backfill(List.of("WANTED"));

        assertThat(summary.updatedDescriptionCount()).isEqualTo(1);
        assertThat(summary.updatedRoleCount()).isZero();
        assertThat(job.getDescription()).contains("[주요 업무]\nBuild backend APIs.");
    }

    @Test
    @DisplayName("raw replay 결과의 role이 기존 role과 다르면 role을 갱신한다")
    void updateRoleFromReplayedPosting() {
        Job job = createJob(
                "WANTED",
                "wanted-data-100",
                "Old frontend description",
                wantedDataEngineerRawData()
        );
        job.updateRole(JobRole.FRONTEND);
        RawJobDescriptionReplayBackfillService service = createService();

        given(jobRepository.findBySourceInOrderByIdAsc(List.of("WANTED")))
                .willReturn(List.of(job));
        given(jobSkillNormalizationService.replaceNormalizedSkills(any(), anyString(), anyString(), any()))
                .willReturn(List.of());
        given(jobExperienceTagNormalizationService.replaceNormalizedExperienceTags(any(), anyString(), anyString(), any()))
                .willReturn(List.of());

        RawJobDescriptionReplayBackfillSummary summary = service.backfill(List.of("WANTED"));

        assertThat(job.getRole()).isEqualTo(JobRole.DATA_ENGINEER);
        assertThat(job.getDescription()).contains("Python ETL 파이프라인 개발");
        assertThat(summary.updatedDescriptionCount()).isEqualTo(1);
        assertThat(summary.updatedRoleCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("DB raw_data가 없으면 raw snapshot storage에서 원문을 읽어 description을 재생성한다")
    void replayFromRawSnapshotWhenRawDataMissing() {
        Job job = createJob(
                "WANTED",
                "wanted-snapshot-100",
                "Old snapshot description",
                null
        );
        job.updateRawSnapshotMetadata(
                "wanted/wanted-snapshot-100/raw.json",
                "a".repeat(64),
                512L,
                "LOCAL_FILE",
                LocalDateTime.of(2026, 6, 4, 11, 0)
        );
        RawJobDescriptionReplayBackfillService service = createService();

        given(jobRepository.findBySourceInOrderByIdAsc(List.of("WANTED")))
                .willReturn(List.of(job));
        given(rawJobSnapshotStorage.read("wanted/wanted-snapshot-100/raw.json"))
                .willReturn(wantedRawData());
        given(jobSkillNormalizationService.replaceNormalizedSkills(any(), anyString(), anyString(), any()))
                .willReturn(List.of());
        given(jobExperienceTagNormalizationService.replaceNormalizedExperienceTags(any(), anyString(), anyString(), any()))
                .willReturn(List.of());

        RawJobDescriptionReplayBackfillSummary summary = service.backfill(List.of("WANTED"));

        assertThat(job.getDescription())
                .contains("[회사 소개]\nExample Labs builds hiring tools.")
                .contains("[주요 업무]\nBuild backend APIs.")
                .contains("[자격 요건]\nJava and Spring Boot experience.")
                .contains("[채용절차 및 기타 지원 유의사항]\nDocument review and technical interview.");
        assertThat(summary.processedCount()).isEqualTo(1);
        assertThat(summary.updatedDescriptionCount()).isEqualTo(1);
        assertThat(summary.updatedRoleCount()).isZero();
        assertThat(summary.skippedCount()).isZero();
        assertThat(summary.failedCount()).isZero();
    }

    private RawJobDescriptionReplayBackfillService createService() {
        JdJobRoleClassificationService roleClassificationService = new JdJobRoleClassificationService();
        List<JobPostingParser> parsers = List.of(
                new WantedJobPostingParser(objectMapper, roleClassificationService),
                new JumpitJobPostingParser(roleClassificationService)
        );

        return new RawJobDescriptionReplayBackfillService(
                jobRepository,
                objectMapper,
                parsers,
                jobSkillNormalizationService,
                jobExperienceTagNormalizationService,
                rawJobSnapshotStorage
        );
    }

    private Job createJob(
            String source,
            String externalId,
            String description,
            String rawData
    ) {
        Job job = Job.create(
                source,
                externalId,
                "Backend Engineer",
                "Example Labs",
                description,
                "https://example.com/jobs/" + externalId,
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
        ReflectionTestUtils.setField(job, "id", 1L);
        job.updateCrawlingMetadata(
                "sample-fingerprint",
                "https://example.com/jobs/" + externalId,
                LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDateTime.of(2026, 6, 1, 10, 5),
                null,
                rawData,
                source.toLowerCase() + "-parser-test"
        );
        return job;
    }

    private String wantedRawData() {
        return """
                {
                  "job": {
                    "position": "Backend Engineer",
                    "company": {
                      "name": "Example Labs"
                    },
                    "detail": {
                      "intro": "Example Labs builds hiring tools.",
                      "main_tasks": "Build backend APIs.",
                      "requirements": "Java and Spring Boot experience.",
                      "hiring_process": "Document review and technical interview."
                    },
                    "skill_tags": [
                      {
                        "title": "Java"
                      },
                      {
                        "title": "Spring Boot"
                      }
                    ],
                    "address": {
                      "location": "Seoul Gangnam"
                    }
                  }
                }
                """;
    }

    private String wantedDataEngineerRawData() {
        return """
                {
                  "job": {
                    "position": "Data Engineer 4~7년",
                    "company": {
                      "name": "Example Company"
                    },
                    "detail": {
                      "main_tasks": "React 기반 admin 화면 협업과 데이터 파이프라인 운영",
                      "requirements": "Python ETL 파이프라인 개발 경험"
                    },
                    "skill_tags": [
                      {
                        "title": "Python"
                      },
                      {
                        "title": "Airflow"
                      }
                    ],
                    "address": {
                      "location": "Seoul"
                    }
                  }
                }
                """;
    }

    private String jumpitRawData() {
        return """
                {
                  "rawBody": "<html><body><main><h1>Backend Engineer</h1><a href='/company/example'>Example Labs</a><section data-testid='position-description'>Build payment APIs with Java Spring Boot and Docker. 경력 1~3년</section></main></body></html>"
                }
                """;
    }
}
