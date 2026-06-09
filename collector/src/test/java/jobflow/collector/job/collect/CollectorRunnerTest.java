package jobflow.collector.job.collect;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import jobflow.collector.job.ingest.CrawlerUrlCandidate;
import jobflow.collector.job.ingest.JobIngestionResultType;
import jobflow.collector.job.ingest.JobIngestionSource;
import jobflow.collector.job.ingest.JobPostingCollectionResult;
import jobflow.collector.job.ingest.JobPostingCollectionService;
import jobflow.collector.job.ingest.SitemapCrawlResult;
import jobflow.collector.job.ingest.SitemapCrawlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class CollectorRunnerTest {

    @Mock
    private SitemapCrawlService sitemapCrawlService;

    @Mock
    private JobPostingCollectionService jobPostingCollectionService;

    @Test
    @DisplayName("설정된 source로 scan limit만큼 sitemap crawl을 실행하고 collect limit까지 저장한다")
    void run() {
        CollectorRunnerProperties properties = new CollectorRunnerProperties(
                true,
                JobIngestionSource.ZIGHANG,
                5,
                1,
                10
        );
        CollectorRunner runner = new CollectorRunner(
                properties,
                sitemapCrawlService,
                jobPostingCollectionService
        );
        CrawlerUrlCandidate firstCandidate = zighangCandidate("00000000-0000-0000-0000-000000000100");
        CrawlerUrlCandidate secondCandidate = zighangCandidate("00000000-0000-0000-0000-000000000101");

        given(sitemapCrawlService.crawl(JobIngestionSource.ZIGHANG, 10))
                .willReturn(new SitemapCrawlResult(
                        JobIngestionSource.ZIGHANG,
                        1,
                        List.of("https://zighang.com/seo/sitemap/sitemap-index.xml"),
                        List.of(firstCandidate, secondCandidate)
                ));
        given(jobPostingCollectionService.collect(firstCandidate))
                .willReturn(JobPostingCollectionResult.success(
                        firstCandidate,
                        JobIngestionResultType.SKIPPED
                ));
        given(jobPostingCollectionService.collect(secondCandidate))
                .willReturn(JobPostingCollectionResult.success(
                        secondCandidate,
                        JobIngestionResultType.CREATED
                ));

        runner.run(new DefaultApplicationArguments());

        verify(sitemapCrawlService).crawl(JobIngestionSource.ZIGHANG, 10);
        verify(jobPostingCollectionService).collect(firstCandidate);
        verify(jobPostingCollectionService).collect(secondCandidate);
    }

    @Test
    @DisplayName("collect limit을 채우면 남은 후보는 처리하지 않는다")
    void stopWhenCollectLimitIsReached() {
        CollectorRunnerProperties properties = new CollectorRunnerProperties(
                true,
                JobIngestionSource.JUMPIT,
                5,
                1,
                10
        );
        CollectorRunner runner = new CollectorRunner(
                properties,
                sitemapCrawlService,
                jobPostingCollectionService
        );
        CrawlerUrlCandidate firstCandidate = jumpitCandidate("54111922");
        CrawlerUrlCandidate secondCandidate = jumpitCandidate("54111931");

        given(sitemapCrawlService.crawl(JobIngestionSource.JUMPIT, 10))
                .willReturn(new SitemapCrawlResult(
                        JobIngestionSource.JUMPIT,
                        1,
                        List.of("https://jumpit.saramin.co.kr/sitemap.xml"),
                        List.of(firstCandidate, secondCandidate)
                ));
        given(jobPostingCollectionService.collect(firstCandidate))
                .willReturn(JobPostingCollectionResult.success(
                        firstCandidate,
                        JobIngestionResultType.CREATED
                ));

        runner.run(new DefaultApplicationArguments());

        verify(sitemapCrawlService).crawl(JobIngestionSource.JUMPIT, 10);
        verify(jobPostingCollectionService).collect(firstCandidate);
        verify(jobPostingCollectionService, never()).collect(secondCandidate);
    }

    @Test
    @DisplayName("source 설정이 없으면 ZIGHANG을 기본값으로 사용한다")
    void runWithDefaultSource() {
        CollectorRunnerProperties properties = new CollectorRunnerProperties(true, null, 0, 0, 0);
        CollectorRunner runner = new CollectorRunner(
                properties,
                sitemapCrawlService,
                jobPostingCollectionService
        );

        given(sitemapCrawlService.crawl(JobIngestionSource.ZIGHANG, 100))
                .willReturn(new SitemapCrawlResult(
                        JobIngestionSource.ZIGHANG,
                        0,
                        List.of(),
                        List.of()
                ));

        runner.run(new DefaultApplicationArguments());

        verify(sitemapCrawlService).crawl(JobIngestionSource.ZIGHANG, 100);
    }

    private CrawlerUrlCandidate zighangCandidate(String externalId) {
        return new CrawlerUrlCandidate(
                JobIngestionSource.ZIGHANG,
                "https://zighang.com/recruitment/" + externalId,
                "https://zighang.com/recruitment/" + externalId,
                externalId
        );
    }

    private CrawlerUrlCandidate jumpitCandidate(String externalId) {
        return new CrawlerUrlCandidate(
                JobIngestionSource.JUMPIT,
                "https://jumpit.saramin.co.kr/position/" + externalId,
                "https://jumpit.saramin.co.kr/position/" + externalId,
                externalId
        );
    }
}
