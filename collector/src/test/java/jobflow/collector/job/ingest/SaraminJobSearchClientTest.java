package jobflow.collector.job.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SaraminJobSearchClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("사람인 API key가 없으면 요청하지 않고 예외를 던진다")
    void missingAccessKey() {
        RecordingCrawlerHttpClient httpClient = new RecordingCrawlerHttpClient(
                new CrawlerHttpResponse(200, "{}")
        );
        SaraminJobSearchClient client = new SaraminJobSearchClient(
                new SaraminApiProperties("api", "https://oapi.saramin.co.kr/job-search", "", 100),
                httpClient,
                objectMapper
        );

        assertThatThrownBy(() -> client.search(0, 10))
                .isInstanceOf(SaraminApiAccessKeyMissingException.class)
                .hasMessageContaining("SARAMIN_ACCESS_KEY");
        assertThat(httpClient.requestedUrl()).isNull();
    }

    @Test
    @DisplayName("사람인 API 검색 URL을 만들고 job 배열을 반환한다")
    void search() {
        RecordingCrawlerHttpClient httpClient = new RecordingCrawlerHttpClient(
                new CrawlerHttpResponse(
                        200,
                        """
                                {
                                  "jobs": {
                                    "job": [
                                      {"id": "1"},
                                      {"id": "2"}
                                    ]
                                  }
                                }
                                """
                )
        );
        SaraminJobSearchClient client = new SaraminJobSearchClient(
                new SaraminApiProperties("api", "https://oapi.saramin.co.kr/job-search", "test-key", 100),
                httpClient,
                objectMapper
        );

        List<JsonNode> jobs = client.search(20, 10);

        assertThat(jobs).hasSize(2);
        assertThat(jobs.get(0).path("id").asText()).isEqualTo("1");
        assertThat(httpClient.requestedUrl())
                .startsWith("https://oapi.saramin.co.kr/job-search?")
                .contains("access-key=test-key")
                .contains("start=20")
                .contains("count=10")
                .contains("keywords=IT%EA%B0%9C%EB%B0%9C")
                .contains("fields=posting-date%2Cexpiration-date%2Ckeyword-code%2Ccount");
    }

    @Test
    @DisplayName("사람인 API가 단일 job object를 반환해도 목록으로 감싼다")
    void searchSingleJobObject() {
        RecordingCrawlerHttpClient httpClient = new RecordingCrawlerHttpClient(
                new CrawlerHttpResponse(200, """
                        {"jobs": {"job": {"id": "1"}}}
                        """)
        );
        SaraminJobSearchClient client = new SaraminJobSearchClient(
                new SaraminApiProperties("api", "https://oapi.saramin.co.kr/job-search", "test-key", 100),
                httpClient,
                objectMapper
        );

        List<JsonNode> jobs = client.search(0, 0);

        assertThat(jobs).hasSize(1);
        assertThat(httpClient.requestedUrl()).contains("count=100");
    }

    @Test
    @DisplayName("사람인 API 실패 응답은 예외로 변환한다")
    void failedResponse() {
        RecordingCrawlerHttpClient httpClient = new RecordingCrawlerHttpClient(
                new CrawlerHttpResponse(401, "unauthorized")
        );
        SaraminJobSearchClient client = new SaraminJobSearchClient(
                new SaraminApiProperties("api", "https://oapi.saramin.co.kr/job-search", "test-key", 100),
                httpClient,
                objectMapper
        );

        assertThatThrownBy(() -> client.search(0, 10))
                .isInstanceOf(SaraminApiClientException.class)
                .hasMessageContaining("401");
    }

    private static class RecordingCrawlerHttpClient implements CrawlerHttpClient {

        private final CrawlerHttpResponse response;
        private String requestedUrl;

        private RecordingCrawlerHttpClient(CrawlerHttpResponse response) {
            this.response = response;
        }

        @Override
        public CrawlerHttpResponse get(String url) {
            requestedUrl = url;
            return response;
        }

        private String requestedUrl() {
            return requestedUrl;
        }
    }
}
