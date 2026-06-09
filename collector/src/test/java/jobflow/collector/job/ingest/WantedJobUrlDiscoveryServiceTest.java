package jobflow.collector.job.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class WantedJobUrlDiscoveryServiceTest {

    @Mock
    private CrawlerHttpClient crawlerHttpClient;

    @Mock
    private CrawlerRequestThrottle crawlerRequestThrottle;

    @Test
    @DisplayName("원티드 목록 API에서 개발 직군 공고만 URL 후보로 수집한다")
    void discoverDevJobs() {
        WantedJobUrlDiscoveryService service = new WantedJobUrlDiscoveryService(
                crawlerHttpClient,
                crawlerRequestThrottle,
                new ObjectMapper()
        );
        String listUrl = "https://www.wanted.co.kr/api/v4/jobs"
                + "?country=kr&job_sort=job.latest_order&limit=100&offset=0";

        given(crawlerHttpClient.get(listUrl))
                .willReturn(new CrawlerHttpResponse(
                        200,
                        """
                                {
                                  "data": [
                                    {
                                      "id": 367044,
                                      "position": "백엔드 개발자",
                                      "category_tags": [
                                        {"id": 872, "parent_id": 518, "title": "서버/백엔드"}
                                      ]
                                    },
                                    {
                                      "id": 367045,
                                      "position": "마케터",
                                      "category_tags": [
                                        {"id": 900, "parent_id": 600, "title": "마케팅"}
                                      ]
                                    }
                                  ],
                                  "links": {"next": null}
                                }
                                """
                ));

        List<CrawlerUrlCandidate> candidates = service.discover(10);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).source()).isEqualTo(JobIngestionSource.WANTED);
        assertThat(candidates.get(0).externalId()).isEqualTo("367044");
        assertThat(candidates.get(0).sourceUrl()).isEqualTo("https://www.wanted.co.kr/wd/367044");
        assertThat(candidates.get(0).detailUrl())
                .isEqualTo("https://www.wanted.co.kr/api/v4/jobs/367044");

        verify(crawlerRequestThrottle).waitUntilAllowed(JobIngestionSource.WANTED);
    }

    @Test
    @DisplayName("최대 후보 수에 도달하면 추가 page를 조회하지 않는다")
    void stopAtMaxCandidates() {
        WantedJobUrlDiscoveryService service = new WantedJobUrlDiscoveryService(
                crawlerHttpClient,
                crawlerRequestThrottle,
                new ObjectMapper()
        );
        String listUrl = "https://www.wanted.co.kr/api/v4/jobs"
                + "?country=kr&job_sort=job.latest_order&limit=100&offset=0";

        given(crawlerHttpClient.get(listUrl))
                .willReturn(new CrawlerHttpResponse(
                        200,
                        """
                                {
                                  "data": [
                                    {"id": 367044, "category_tags": [{"parent_id": 518}]},
                                    {"id": 367045, "category_tags": [{"parent_id": 518}]}
                                  ]
                                }
                                """
                ));

        List<CrawlerUrlCandidate> candidates = service.discover(1);

        assertThat(candidates)
                .extracting(CrawlerUrlCandidate::externalId)
                .containsExactly("367044");
    }
}
