package jobflow.collector.job.backfill;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.backfill.raw-job-snapshot")
public record RawJobSnapshotBackfillProperties(
        boolean enabled,
        List<String> sources,
        boolean purgeRawDataAfterSnapshot
) {

    private static final List<String> DEFAULT_SOURCES = List.of("JUMPIT", "WANTED");

    public List<String> sourcesOrDefault() {
        if (sources == null || sources.isEmpty()) {
            return DEFAULT_SOURCES;
        }

        return sources.stream()
                .filter(source -> source != null && !source.isBlank())
                .map(String::trim)
                .map(String::toUpperCase)
                .distinct()
                .toList();
    }
}
