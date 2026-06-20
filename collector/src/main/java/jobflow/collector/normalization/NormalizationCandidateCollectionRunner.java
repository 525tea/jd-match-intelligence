package jobflow.collector.normalization;

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
        prefix = "app.normalization-candidate-collection",
        name = "enabled",
        havingValue = "true"
)
public class NormalizationCandidateCollectionRunner implements ApplicationRunner {

    private final NormalizationCandidateCollectionProperties properties;
    private final NormalizationCandidateCollectionService collectionService;

    @Override
    public void run(ApplicationArguments args) {
        List<String> sources = properties.sourcesOrDefault();

        log.info("Normalization candidate collection started. sources={}", sources);

        NormalizationCandidateCollectionSummary summary = collectionService.collect(sources);

        log.info(
                "Normalization candidate collection completed. sources={}, processedJobCount={}, skillAliasCandidateCount={}, sectionLabelCandidateCount={}",
                sources,
                summary.processedJobCount(),
                summary.skillAliasCandidateCount(),
                summary.sectionLabelCandidateCount()
        );
    }
}
