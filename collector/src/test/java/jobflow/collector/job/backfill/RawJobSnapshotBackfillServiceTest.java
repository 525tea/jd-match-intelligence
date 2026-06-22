package jobflow.collector.job.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import jobflow.collector.job.CareerLevel;
import jobflow.collector.job.EmploymentType;
import jobflow.collector.job.Job;
import jobflow.collector.job.JobRepository;
import jobflow.collector.job.JobRole;
import jobflow.collector.job.RemoteType;
import jobflow.collector.job.ingest.JobIngestionSource;
import jobflow.collector.job.snapshot.RawJobSnapshotMetadata;
import jobflow.collector.job.snapshot.RawJobSnapshotStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RawJobSnapshotBackfillServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private RawJobSnapshotStorage rawJobSnapshotStorage;

    @Test
    @DisplayName("raw_data가 있고 snapshot metadata가 없으면 raw snapshot을 저장하고 metadata를 갱신한다")
    void backfillRawSnapshot() {
        Job job = createJob("JUMPIT", "jumpit-1001", """
                {"rawBody":"<html>Backend Engineer</html>"}
                """);
        RawJobSnapshotBackfillService service = new RawJobSnapshotBackfillService(
                jobRepository,
                rawJobSnapshotStorage,
                new RawJobSnapshotBackfillProperties(false, List.of("JUMPIT"), false)
        );

        given(jobRepository.findBySourceInOrderByIdAsc(List.of("JUMPIT")))
                .willReturn(List.of(job));
        given(rawJobSnapshotStorage.save(JobIngestionSource.JUMPIT, "jumpit-1001", job.getRawData()))
                .willReturn(new RawJobSnapshotMetadata(
                        "jumpit/jumpit-1001/raw.json",
                        "a".repeat(64),
                        128L,
                        "LOCAL_FILE",
                        LocalDateTime.of(2026, 6, 4, 11, 0)
                ));

        RawJobSnapshotBackfillSummary summary = service.backfill(List.of("JUMPIT"));

        assertThat(summary.processedCount()).isEqualTo(1);
        assertThat(summary.snapshottedCount()).isEqualTo(1);
        assertThat(summary.purgedRawDataCount()).isZero();
        assertThat(summary.skippedMissingRawDataCount()).isZero();
        assertThat(summary.skippedAlreadySnapshottedCount()).isZero();
        assertThat(summary.failedCount()).isZero();
        assertThat(job.getRawSnapshotKey()).isEqualTo("jumpit/jumpit-1001/raw.json");
        assertThat(job.getRawSnapshotHash()).hasSize(64);
        assertThat(job.getRawSnapshotSizeBytes()).isEqualTo(128L);
        assertThat(job.getRawSnapshotStorageType()).isEqualTo("LOCAL_FILE");
        assertThat(job.getRawSnapshotSavedAt()).isEqualTo(LocalDateTime.of(2026, 6, 4, 11, 0));
        assertThat(job.getRawData()).contains("Backend Engineer");
    }

    @Test
    @DisplayName("purge 옵션이 켜져 있으면 snapshot 저장 후 raw_data를 비운다")
    void purgeRawDataAfterSnapshot() {
        Job job = createJob("JUMPIT", "jumpit-1002", """
                {"rawBody":"<html>Frontend Engineer</html>"}
                """);
        RawJobSnapshotBackfillService service = new RawJobSnapshotBackfillService(
                jobRepository,
                rawJobSnapshotStorage,
                new RawJobSnapshotBackfillProperties(false, List.of("JUMPIT"), true)
        );

        given(jobRepository.findBySourceInOrderByIdAsc(List.of("JUMPIT")))
                .willReturn(List.of(job));
        given(rawJobSnapshotStorage.save(JobIngestionSource.JUMPIT, "jumpit-1002", job.getRawData()))
                .willReturn(new RawJobSnapshotMetadata(
                        "jumpit/jumpit-1002/raw.json",
                        "c".repeat(64),
                        256L,
                        "LOCAL_FILE",
                        LocalDateTime.of(2026, 6, 4, 11, 30)
                ));

        RawJobSnapshotBackfillSummary summary = service.backfill(List.of("JUMPIT"));

        assertThat(summary.processedCount()).isEqualTo(1);
        assertThat(summary.snapshottedCount()).isEqualTo(1);
        assertThat(summary.purgedRawDataCount()).isEqualTo(1);
        assertThat(summary.failedCount()).isZero();
        assertThat(job.getRawSnapshotKey()).isEqualTo("jumpit/jumpit-1002/raw.json");
        assertThat(job.getRawData()).isNull();
    }

    @Test
    @DisplayName("이미 snapshot metadata가 있으면 다시 저장하지 않는다")
    void skipAlreadySnapshottedJob() {
        Job job = createJob("WANTED", "wanted-1001", """
                {"job":{"position":"Backend Engineer"}}
                """);
        job.updateRawSnapshotMetadata(
                "wanted/wanted-1001/raw.json",
                "b".repeat(64),
                256L,
                "LOCAL_FILE",
                LocalDateTime.of(2026, 6, 4, 12, 0)
        );
        RawJobSnapshotBackfillService service = new RawJobSnapshotBackfillService(
                jobRepository,
                rawJobSnapshotStorage,
                new RawJobSnapshotBackfillProperties(false, List.of("WANTED"), false)
        );

        given(jobRepository.findBySourceInOrderByIdAsc(List.of("WANTED")))
                .willReturn(List.of(job));

        RawJobSnapshotBackfillSummary summary = service.backfill(List.of("WANTED"));

        assertThat(summary.processedCount()).isEqualTo(1);
        assertThat(summary.snapshottedCount()).isZero();
        assertThat(summary.skippedAlreadySnapshottedCount()).isEqualTo(1);
        verify(rawJobSnapshotStorage, never()).save(
                JobIngestionSource.WANTED,
                "wanted-1001",
                job.getRawData()
        );
    }

    @Test
    @DisplayName("raw_data가 없으면 snapshot 저장을 건너뛴다")
    void skipMissingRawData() {
        Job job = createJob("JUMPIT", "jumpit-blank", null);
        RawJobSnapshotBackfillService service = new RawJobSnapshotBackfillService(
                jobRepository,
                rawJobSnapshotStorage,
                new RawJobSnapshotBackfillProperties(false, List.of("JUMPIT"), false)
        );

        given(jobRepository.findBySourceInOrderByIdAsc(List.of("JUMPIT")))
                .willReturn(List.of(job));

        RawJobSnapshotBackfillSummary summary = service.backfill(List.of("JUMPIT"));

        assertThat(summary.processedCount()).isEqualTo(1);
        assertThat(summary.snapshottedCount()).isZero();
        assertThat(summary.skippedMissingRawDataCount()).isEqualTo(1);
        verify(rawJobSnapshotStorage, never()).save(
                JobIngestionSource.JUMPIT,
                "jumpit-blank",
                job.getRawData()
        );
    }

    private Job createJob(String source, String externalId, String rawData) {
        Job job = Job.create(
                source,
                externalId,
                "Backend Engineer",
                "Example Company",
                "Backend engineer position",
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
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 23, 59)
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
}
