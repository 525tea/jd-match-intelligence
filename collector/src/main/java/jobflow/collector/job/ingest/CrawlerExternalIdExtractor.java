package jobflow.collector.job.ingest;

import java.net.URI;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

public class CrawlerExternalIdExtractor {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^\\d+$");

    public Optional<String> extract(JobIngestionSource source, URI uri) {
        return switch (source) {
            case SARAMIN -> queryParam(uri, "rec_idx")
                    .filter(this::isNumber)
                    .or(() -> lastPathSegment(uri).filter(this::isNumber));
            case JOBKOREA -> lastPathSegment(uri)
                    .filter(this::isNumber)
                    .or(() -> queryParam(uri, "GI_No").filter(this::isNumber))
                    .or(() -> queryParam(uri, "gi_no").filter(this::isNumber));
            case JUMPIT -> jumpitPositionId(uri);
            case ZIGHANG -> zighangRecruitmentId(uri);
        };
    }

    private Optional<String> jumpitPositionId(URI uri) {
        String[] segments = pathSegments(uri);

        if (segments.length == 2
                && "position".equals(segments[0])
                && isNumber(segments[1])) {
            return Optional.of(segments[1]);
        }

        return Optional.empty();
    }

    private Optional<String> zighangRecruitmentId(URI uri) {
        String[] segments = pathSegments(uri);

        if (segments.length == 2
                && "recruitment".equals(segments[0])
                && UUID_PATTERN.matcher(segments[1]).matches()) {
            return Optional.of(segments[1]);
        }

        return Optional.empty();
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
        String[] segments = pathSegments(uri);

        if (segments.length == 0) {
            return Optional.empty();
        }

        return Optional.of(segments[segments.length - 1]);
    }

    private String[] pathSegments(URI uri) {
        String path = uri.getPath();

        if (path == null || path.isBlank() || "/".equals(path)) {
            return new String[0];
        }

        return Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isBlank())
                .toArray(String[]::new);
    }

    private boolean isNumber(String value) {
        return NUMBER_PATTERN.matcher(value).matches();
    }
}
