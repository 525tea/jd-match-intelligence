package jobflow.collector.job.snapshot;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import jobflow.collector.job.ingest.JobIngestionSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalFileRawJobSnapshotStorage implements RawJobSnapshotStorage {

    private final RawJobSnapshotStorageProperties properties;

    @Override
    public RawJobSnapshotMetadata save(
            JobIngestionSource source,
            String externalId,
            String rawData
    ) {
        if (rawData == null || rawData.isBlank()) {
            return new RawJobSnapshotMetadata(
                    null,
                    null,
                    0L,
                    properties.storageType(),
                    null
            );
        }

        byte[] bytes = rawData.getBytes(StandardCharsets.UTF_8);
        String hash = sha256(bytes);
        LocalDateTime savedAt = LocalDateTime.now(ZoneOffset.UTC);
        String key = key(source, externalId, hash);

        Path path = properties.rootPath().resolve(key);

        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (java.io.IOException exception) {
            throw new UncheckedIOException("Failed to save raw job snapshot. key=" + key, exception);
        }

        return new RawJobSnapshotMetadata(
                key,
                hash,
                bytes.length,
                properties.storageType(),
                savedAt
        );
    }

    @Override
    public String read(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        Path path = properties.rootPath().resolve(key);

        try {
            if (!Files.exists(path)) {
                return null;
            }

            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) {
            throw new UncheckedIOException("Failed to read raw job snapshot. key=" + key, exception);
        }
    }

    private String key(JobIngestionSource source, String externalId, String hash) {
        return "%s/%s/%s.json".formatted(
                source.name().toLowerCase(),
                sanitize(externalId),
                hash
        );
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }

        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", exception);
        }
    }
}
