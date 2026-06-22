package jobflow.collector.job.snapshot;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.raw-snapshot.storage")
public record RawJobSnapshotStorageProperties(
        Path rootPath,
        String storageType
) {

    public RawJobSnapshotStorageProperties {
        if (rootPath == null) {
            rootPath = Path.of("build/raw-snapshots");
        }

        if (storageType == null || storageType.isBlank()) {
            storageType = "LOCAL_FILE";
        }
    }
}
