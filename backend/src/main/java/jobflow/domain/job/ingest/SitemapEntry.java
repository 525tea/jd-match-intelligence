package jobflow.domain.job.ingest;

import java.time.LocalDateTime;

public record SitemapEntry(
        String loc,
        LocalDateTime lastModified
) {
}
