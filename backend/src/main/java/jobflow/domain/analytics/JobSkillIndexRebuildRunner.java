package jobflow.domain.analytics;

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
        prefix = "jobflow.analytics.job-skill-index.runner",
        name = "enabled",
        havingValue = "true"
)
public class JobSkillIndexRebuildRunner implements ApplicationRunner {

    private final JobSkillIndexRebuildService jobSkillIndexRebuildService;

    @Override
    public void run(ApplicationArguments args) {
        JobSkillIndexRebuildResult result = jobSkillIndexRebuildService.rebuild();

        log.info(
                "Job skill index rebuild completed. sourceCount={}, savedCount={}",
                result.sourceCount(),
                result.savedCount()
        );
    }
}
