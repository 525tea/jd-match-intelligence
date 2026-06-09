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
        prefix = "app.backfill.real-job-normalization",
        name = "enabled",
        havingValue = "true"
)
public class RealJobNormalizationBackfillRunner implements ApplicationRunner {

    private final RealJobNormalizationBackfillProperties properties;
    private final RealJobNormalizationBackfillService backfillService;

    @Override
    public void run(ApplicationArguments args) {
        List<String> sources = properties.sourcesOrDefault();

        log.info("Real job normalization backfill started. sources={}", sources);

        RealJobNormalizationBackfillSummary summary = backfillService.backfill(sources);

        log.info(
                "Real job normalization backfill completed. sources={}, processedCount={}, roleUpdatedCount={}, normalizedSkillJobCount={}, normalizedExperienceTagJobCount={}",
                sources,
                summary.processedCount(),
                summary.roleUpdatedCount(),
                summary.normalizedSkillJobCount(),
                summary.normalizedExperienceTagJobCount()
        );
    }
}
