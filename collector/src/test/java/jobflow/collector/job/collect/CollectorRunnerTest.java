package jobflow.collector.job.collect;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import jobflow.collector.job.ingest.CrawlerUrlCandidate;
import jobflow.collector.job.ingest.JobIngestionSource;
import jobflow.collector.job.ingest.JobIngestionResultType;
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
    @DisplayName("설정된 source로 sitemap crawl과 제한된 공고 수집을 실행한다")
    void run() {
        CollectorRunnerProperties properties = new CollectorRunnerProperties(
                true,
                JobIngestionSource.ZIGHANG,
                5,
                10
        );
        CollectorRunner runner = new CollectorRunner(
                properties,
                sitemapCrawlService,
                jobPostingCollectionService
        );
        CrawlerUrlCandidate candidate = new CrawlerUrlCandidate(
                JobIngestionSource.ZIGHANG,
                "https://zighang.com/jobs/example-1",
                "https://zighang.com/jobs/example-1",
                "example-1"
        );

        given(sitemapCrawlService.crawl(JobIngestionSource.ZIGHANG))
                .willReturn(new SitemapCrawlResult(
                        JobIngestionSource.ZIGHANG,
                        1,
                        List.of("https://zighang.com/seo/sitemap/sitemap-index.xml"),
                        List.of(candidate)
                ));
        given(jobPostingCollectionService.collect(candidate))
                .willReturn(new JobPostingCollectionResult(
                        candidate,
                        JobIngestionResultType.CREATED
                ));

        runner.run(new DefaultApplicationArguments());

        verify(sitemapCrawlService).crawl(JobIngestionSource.ZIGHANG);
        verify(jobPostingCollectionService).collect(candidate);
    }

    @Test
    @DisplayName("source 설정이 없으면 ZIGHANG을 기본값으로 사용한다")
    void runWithDefaultSource() {
        CollectorRunnerProperties properties = new CollectorRunnerProperties(true, null, 0, 0);
        CollectorRunner runner = new CollectorRunner(
                properties,
                sitemapCrawlService,
                jobPostingCollectionService
        );

        given(sitemapCrawlService.crawl(JobIngestionSource.ZIGHANG))
                .willReturn(new SitemapCrawlResult(
                        JobIngestionSource.ZIGHANG,
                        0,
                        List.of(),
                        List.of()
                ));

        runner.run(new DefaultApplicationArguments());

        verify(sitemapCrawlService).crawl(JobIngestionSource.ZIGHANG);
    }
}
