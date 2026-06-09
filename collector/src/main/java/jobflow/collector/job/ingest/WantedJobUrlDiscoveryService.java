package jobflow.collector.job.ingest;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class WantedJobUrlDiscoveryService {

    private static final int DEV_CATEGORY_PARENT_ID = 518;
    private static final int PAGE_SIZE = 100;
    private static final String LIST_API_BASE =
            "https://www.wanted.co.kr/api/v4/jobs?country=kr&job_sort=job.latest_order&limit=" + PAGE_SIZE;

    private final CrawlerHttpClient crawlerHttpClient;
    private final CrawlerRequestThrottle crawlerRequestThrottle;
    private final ObjectMapper objectMapper;

    public List<CrawlerUrlCandidate> discover(int maxCandidates) {
        List<CrawlerUrlCandidate> candidates = new ArrayList<>();
        int offset = 0;
        int pagesFetched = 0;

        while (maxCandidates <= 0 || candidates.size() < maxCandidates) {
            crawlerRequestThrottle.waitUntilAllowed(JobIngestionSource.WANTED);

            String url = LIST_API_BASE + "&offset=" + offset;
            CrawlerHttpResponse response = crawlerHttpClient.get(url);

            if (!response.isSuccessful()) {
                log.warn(
                        "Wanted list API failed. url={}, statusCode={}",
                        url,
                        response.statusCode()
                );
                break;
            }

            pagesFetched++;
            WantedListPage page = parseListPage(response.body());

            if (page.candidates().isEmpty()) {
                log.info("Wanted list API returned empty page. offset={}", offset);
                break;
            }

            for (CrawlerUrlCandidate candidate : page.candidates()) {
                if (maxCandidates > 0 && candidates.size() >= maxCandidates) {
                    break;
                }

                candidates.add(candidate);
            }

            log.info(
                    "Wanted page fetched. offset={}, pageDevJobCount={}, totalCandidates={}",
                    offset,
                    page.candidates().size(),
                    candidates.size()
            );

            if (!page.hasNext()) {
                break;
            }

            offset += PAGE_SIZE;
        }

        log.info(
                "Wanted discovery completed. pagesFetched={}, totalCandidates={}",
                pagesFetched,
                candidates.size()
        );

        return candidates;
    }

    private WantedListPage parseListPage(String body) {
        List<CrawlerUrlCandidate> result = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");

            if (!data.isArray()) {
                return new WantedListPage(result, false);
            }

            for (JsonNode job : data) {
                if (!isDevJob(job)) {
                    continue;
                }

                long id = job.path("id").asLong(0);

                if (id <= 0) {
                    continue;
                }

                String externalId = String.valueOf(id);

                result.add(new CrawlerUrlCandidate(
                        JobIngestionSource.WANTED,
                        "https://www.wanted.co.kr/wd/" + externalId,
                        "https://www.wanted.co.kr/api/v4/jobs/" + externalId,
                        externalId
                ));
            }
            return new WantedListPage(result, hasNext(root));
        } catch (JacksonException exception) {
            log.warn("Wanted list page parse failed. error={}", exception.getMessage());
        }

        return new WantedListPage(result, false);
    }

    private boolean hasNext(JsonNode root) {
        JsonNode next = root.path("links").path("next");

        return !next.isMissingNode()
                && !next.isNull()
                && !next.asText("").isBlank();
    }

    private boolean isDevJob(JsonNode job) {
        JsonNode categoryTags = job.path("category_tags");

        if (!categoryTags.isArray()) {
            return false;
        }

        for (JsonNode tag : categoryTags) {
            if (tag.path("parent_id").asInt(0) == DEV_CATEGORY_PARENT_ID) {
                return true;
            }
        }

        return false;
    }

    private record WantedListPage(
            List<CrawlerUrlCandidate> candidates,
            boolean hasNext
    ) {
    }
}
