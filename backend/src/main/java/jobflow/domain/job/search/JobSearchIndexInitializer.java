package jobflow.domain.job.search;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.search.elasticsearch",
        name = "initialize-on-startup",
        havingValue = "true"
)
public class JobSearchIndexInitializer implements ApplicationRunner {

    private final JobSearchIndexService jobSearchIndexService;

    @Override
    public void run(ApplicationArguments args) {
        jobSearchIndexService.createIndexIfMissing();
    }
}
