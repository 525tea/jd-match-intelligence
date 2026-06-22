package jobflow.collector.job.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import jobflow.collector.job.ingest.JobIngestionSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileRawJobSnapshotStorageTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("raw job snapshot을 source/externalId/hash 경로에 저장하고 다시 읽는다")
    void saveAndRead() {
        LocalFileRawJobSnapshotStorage storage = new LocalFileRawJobSnapshotStorage(
                new RawJobSnapshotStorageProperties(tempDir, "LOCAL_FILE")
        );
        String rawData = """
                {"title":"Backend Engineer","company":"Example Company"}
                """;

        RawJobSnapshotMetadata metadata = storage.save(
                JobIngestionSource.JUMPIT,
                "jumpit-1001",
                rawData
        );

        assertThat(metadata.key()).startsWith("jumpit/jumpit-1001/");
        assertThat(metadata.key()).endsWith(".json");
        assertThat(metadata.hash()).hasSize(64);
        assertThat(metadata.sizeBytes()).isEqualTo(rawData.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        assertThat(metadata.storageType()).isEqualTo("LOCAL_FILE");
        assertThat(metadata.savedAt()).isNotNull();
        assertThat(Files.exists(tempDir.resolve(metadata.key()))).isTrue();
        assertThat(storage.read(metadata.key())).isEqualTo(rawData);
    }

    @Test
    @DisplayName("externalId에 경로로 쓰기 어려운 문자가 있으면 안전한 key로 치환한다")
    void sanitizeExternalId() {
        LocalFileRawJobSnapshotStorage storage = new LocalFileRawJobSnapshotStorage(
                new RawJobSnapshotStorageProperties(tempDir, "LOCAL_FILE")
        );

        RawJobSnapshotMetadata metadata = storage.save(
                JobIngestionSource.WANTED,
                "wanted/1001?source=test",
                "{\"title\":\"Backend\"}"
        );

        assertThat(metadata.key()).startsWith("wanted/wanted_1001_source_test/");
        assertThat(Files.exists(tempDir.resolve(metadata.key()))).isTrue();
    }

    @Test
    @DisplayName("raw data가 비어 있으면 snapshot 파일을 만들지 않고 빈 metadata를 반환한다")
    void skipBlankRawData() {
        LocalFileRawJobSnapshotStorage storage = new LocalFileRawJobSnapshotStorage(
                new RawJobSnapshotStorageProperties(tempDir, "LOCAL_FILE")
        );

        RawJobSnapshotMetadata metadata = storage.save(
                JobIngestionSource.JUMPIT,
                "jumpit-blank",
                " "
        );

        assertThat(metadata.key()).isNull();
        assertThat(metadata.hash()).isNull();
        assertThat(metadata.sizeBytes()).isZero();
        assertThat(metadata.storageType()).isEqualTo("LOCAL_FILE");
        assertThat(metadata.savedAt()).isNull();
    }
}
