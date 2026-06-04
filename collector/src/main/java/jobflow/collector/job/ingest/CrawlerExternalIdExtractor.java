package jobflow.collector.job.ingest;

import java.net.URI;
import java.util.Arrays;
import java.util.Optional;

public class CrawlerExternalIdExtractor {

    public Optional<String> extract(JobIngestionSource source, URI uri) {
        return switch (source) {
            case SARAMIN -> queryParam(uri, "rec_idx")
                    .or(() -> lastPathSegment(uri));
            case JOBKOREA -> lastPathSegment(uri)
                    .or(() -> queryParam(uri, "GI_No"))
                    .or(() -> queryParam(uri, "gi_no"));
            case JUMPIT, ZIGHANG -> lastPathSegment(uri);
        };
    }

    private Optional<String> queryParam(URI uri, String name) {
        String query = uri.getRawQuery();

        if (query == null || query.isBlank()) {
            return Optional.empty();
        }

        return Arrays.stream(query.split("&"))
                .map(parameter -> parameter.split("=", 2))
                .filter(parts -> parts.length == 2)
                .filter(parts -> parts[0].equals(name))
                .map(parts -> parts[1])
                .filter(value -> !value.isBlank())
                .findFirst();
    }

    private Optional<String> lastPathSegment(URI uri) {
        String path = uri.getPath();

        if (path == null || path.isBlank() || "/".equals(path)) {
            return Optional.empty();
        }

        String[] segments = path.split("/");

        for (int index = segments.length - 1; index >= 0; index--) {
            String segment = segments[index];

            if (!segment.isBlank()) {
                return Optional.of(segment);
            }
        }

        return Optional.empty();
    }
}
