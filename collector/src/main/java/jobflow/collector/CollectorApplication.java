package jobflow.collector;

import jobflow.collector.job.backfill.RealJobNormalizationBackfillProperties;
import jobflow.collector.job.collect.CollectorRunnerProperties;
import jobflow.collector.job.ingest.CrawlerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@EnableConfigurationProperties({
		CrawlerProperties.class,
		CollectorRunnerProperties.class,
		RealJobNormalizationBackfillProperties.class
})
@SpringBootApplication
public class CollectorApplication {

	public static void main(String[] args) {
		SpringApplication.run(CollectorApplication.class, args);
	}
}
