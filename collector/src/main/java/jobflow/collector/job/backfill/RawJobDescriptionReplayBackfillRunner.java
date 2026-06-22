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
        prefix = "app.backfill.raw-job-description-replay",
        name = "enabled",
        havingValue = "true"
)
public class RawJobDescriptionReplayBackfillRunner implements ApplicationRunner {

    private final RawJobDescriptionReplayBackfillProperties properties;
    private final RawJobDescriptionReplayBackfillService backfillService;

    @Override
    public void run(ApplicationArguments args) {
        List<String> sources = properties.sourcesOrDefault();

        log.info("Raw job description replay backfill started. sources={}", sources);

        RawJobDescriptionReplayBackfillSummary summary = backfillService.backfill(sources);

        log.info(
                "Raw job description replay backfill completed. sources={}, processedCount={}, updatedDescriptionCount={}, unchangedDescriptionCount={}, updatedDescriptionSectionsCount={}, updatedRoleCount={}, skippedCount={}, failedCount={}, normalizedSkillJobCount={}, normalizedExperienceTagJobCount={}",
                sources,
                summary.processedCount(),
                summary.updatedDescriptionCount(),
                summary.unchangedDescriptionCount(),
                summary.updatedDescriptionSectionsCount(),
                summary.updatedRoleCount(),
                summary.skippedCount(),
                summary.failedCount(),
                summary.normalizedSkillJobCount(),
                summary.normalizedExperienceTagJobCount()
        );
    }
}
