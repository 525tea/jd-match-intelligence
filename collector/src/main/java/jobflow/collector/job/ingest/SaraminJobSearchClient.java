package jobflow.collector.job.ingest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class SaraminJobSearchClient {

    private static final String DEV_KEYWORD = "IT개발";

    private final SaraminApiProperties saraminApiProperties;
    private final CrawlerHttpClient crawlerHttpClient;
    private final ObjectMapper objectMapper;

    public List<JsonNode> search(int start, int count) {
        String url = buildSearchUrl(start, count);
        CrawlerHttpResponse response = crawlerHttpClient.get(url);

        if (!response.isSuccessful()) {
            throw new SaraminApiClientException(
                    "Saramin API request failed. statusCode=" + response.statusCode()
            );
        }

        return parseJobs(response.body());
    }

    private String buildSearchUrl(int start, int count) {
        int normalizedStart = Math.max(start, 0);
        int normalizedCount = count <= 0 ? saraminApiProperties.defaultCountOrDefault() : count;

        return saraminApiProperties.apiBaseUrlOrDefault()
                + "?access-key="
                + encode(saraminApiProperties.accessKeyOrThrow())
                + "&start="
                + normalizedStart
                + "&count="
                + normalizedCount
                + "&keywords="
                + encode(DEV_KEYWORD)
                + "&fields="
                + encode("posting-date,expiration-date,keyword-code,count");
    }

    private List<JsonNode> parseJobs(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode jobs = root.path("jobs").path("job");

            if (jobs.isArray()) {
                List<JsonNode> result = new ArrayList<>();
                jobs.forEach(result::add);
                return result;
            }

            if (jobs.isObject()) {
                return List.of(jobs);
            }

            return List.of();
        } catch (JacksonException exception) {
            throw new SaraminApiClientException(
                    "Failed to parse Saramin API response. error=" + exception.getMessage(),
                    exception
            );
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
