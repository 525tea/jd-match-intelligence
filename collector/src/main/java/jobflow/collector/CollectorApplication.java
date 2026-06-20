package jobflow.collector;

import jobflow.collector.job.backfill.RealJobNormalizationBackfillProperties;
import jobflow.collector.job.backfill.RawJobDescriptionReplayBackfillProperties;
import jobflow.collector.job.collect.CollectorRunnerProperties;
import jobflow.collector.job.ingest.CrawlerProperties;
import jobflow.collector.job.ingest.SaraminApiProperties;
import jobflow.collector.normalization.NormalizationCandidateCollectionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@EnableConfigurationProperties({
		CrawlerProperties.class,
		CollectorRunnerProperties.class,
		SaraminApiProperties.class,
		RealJobNormalizationBackfillProperties.class,
		RawJobDescriptionReplayBackfillProperties.class,
		NormalizationCandidateCollectionProperties.class
})
@SpringBootApplication
public class CollectorApplication {

	public static void main(String[] args) {
		SpringApplication.run(CollectorApplication.class, args);
	}
}
