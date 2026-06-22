package jobflow.collector.job.backfill;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.backfill.raw-job-snapshot",
        name = "enabled",
        havingValue = "true"
)
public class RawJobSnapshotBackfillRunner implements ApplicationRunner {

    private final RawJobSnapshotBackfillProperties properties;
    private final RawJobSnapshotBackfillService backfillService;

    @Override
    public void run(ApplicationArguments args) {
        List<String> sources = properties.sourcesOrDefault();

        log.info("Raw job snapshot backfill started. sources={}", sources);
        if (properties.purgeRawDataAfterSnapshot()) {
            log.warn("Raw job snapshot backfill will purge jobs.raw_data after snapshot metadata is saved.");
        }

        RawJobSnapshotBackfillSummary summary = backfillService.backfill(sources);

        log.info(
                "Raw job snapshot backfill completed. sources={}, processedCount={}, snapshottedCount={}, purgedRawDataCount={}, skippedMissingRawDataCount={}, skippedAlreadySnapshottedCount={}, failedCount={}",
                sources,
                summary.processedCount(),
                summary.snapshottedCount(),
                summary.purgedRawDataCount(),
                summary.skippedMissingRawDataCount(),
                summary.skippedAlreadySnapshottedCount(),
                summary.failedCount()
        );
    }
}
