package jobflow.collector.job.ingest;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Slf4j
@Service
@RequiredArgsConstructor
public class SaraminJobCollectionService {

    private final SaraminApiProperties saraminApiProperties;
    private final SaraminJobSearchClient saraminJobSearchClient;
    private final SaraminJobPostingMapper saraminJobPostingMapper;
    private final JobIngestionService jobIngestionService;

    public SaraminJobCollectionSummary collect(int collectLimit, int scanLimit) {
        int requestedCount = normalizeRequestedCount(collectLimit, scanLimit);
        int processedCount = 0;
        int collectedCount = 0;
        int createdCount = 0;
        int updatedCount = 0;
        int failedCount = 0;

        while (processedCount < scanLimit && collectedCount < collectLimit) {
            int pageCount = Math.min(
                    saraminApiProperties.defaultCountOrDefault(),
                    Math.min(scanLimit - processedCount, collectLimit - collectedCount)
            );
            List<JsonNode> jobs = saraminJobSearchClient.search(processedCount, pageCount);

            if (jobs.isEmpty()) {
                break;
            }

            for (JsonNode job : jobs) {
                if (processedCount >= scanLimit || collectedCount >= collectLimit) {
                    break;
                }

                processedCount++;

                try {
                    IngestedJobPosting posting = saraminJobPostingMapper.map(job);
                    JobIngestionResult ingestionResult = jobIngestionService.ingest(posting);

                    collectedCount++;

                    if (ingestionResult.type() == JobIngestionResultType.CREATED) {
                        createdCount++;
                    } else if (ingestionResult.type() == JobIngestionResultType.UPDATED) {
                        updatedCount++;
                    }

                    log.info(
                            "Saramin job posting collected. externalId={}, resultType={}, duplicateCandidateCount={}",
                            posting.externalId(),
                            ingestionResult.type(),
                            ingestionResult.duplicateCandidates().size()
                    );
                } catch (RuntimeException exception) {
                    failedCount++;
                    log.warn(
                            "Saramin job posting failed. processedCount={}, error={}",
                            processedCount,
                            exception.getMessage()
                    );
                }
            }

            if (jobs.size() < pageCount) {
                break;
            }
        }

        log.info(
                "Saramin API collection completed. requestedCount={}, processedCount={}, collectedCount={}, createdCount={}, updatedCount={}, failedCount={}",
                requestedCount,
                processedCount,
                collectedCount,
                createdCount,
                updatedCount,
                failedCount
        );

        return new SaraminJobCollectionSummary(
                processedCount,
                collectedCount,
                createdCount,
                updatedCount,
                failedCount
        );
    }

    private int normalizeRequestedCount(int collectLimit, int scanLimit) {
        return Math.min(collectLimit, scanLimit);
    }
}
