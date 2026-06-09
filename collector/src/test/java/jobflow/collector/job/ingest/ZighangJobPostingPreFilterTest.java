package jobflow.collector.job.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ZighangJobPostingPreFilterTest {

    private final ZighangJobPostingPreFilter preFilter = new ZighangJobPostingPreFilter();

    @Test
    @DisplayName("직행 IT 개발 카테고리 공고는 skip하지 않는다")
    void allowTargetCategory() {
        FetchedJobPosting fetched = new FetchedJobPosting(
                JobIngestionSource.ZIGHANG,
                "00000000-0000-0000-0000-000000000100",
                "https://zighang.com/recruitment/00000000-0000-0000-0000-000000000100",
                "https://zighang.com/recruitment/00000000-0000-0000-0000-000000000100",
                """
                        <html>
                          <head>
                            <meta property="og:title" content="[JobFlow Labs] 백엔드 개발자 채용 | IT_개발">
                            <meta name="keywords" content="직행, 채용, 공고">
                            <title>[JobFlow Labs] 백엔드 개발자 채용 | IT_개발</title>
                          </head>
                          <body>
                            <h1>백엔드 개발자</h1>
                          </body>
                        </html>
                        """
        );

        assertThat(preFilter.shouldSkip(fetched)).isFalse();
    }

    @Test
    @DisplayName("직행 비개발 카테고리 공고는 skip한다")
    void skipNonTargetCategory() {
        FetchedJobPosting fetched = new FetchedJobPosting(
                JobIngestionSource.ZIGHANG,
                "24c110eb-f4f5-4040-ba1e-6fb5121411d4",
                "https://zighang.com/recruitment/24c110eb-f4f5-4040-ba1e-6fb5121411d4",
                "https://zighang.com/recruitment/24c110eb-f4f5-4040-ba1e-6fb5121411d4",
                """
                        <html>
                          <head>
                            <meta property="og:title" content="[넷마블] 인사전략담당자모집 채용 | 인사_노무_HRD_총무">
                            <meta name="keywords" content="직행, 채용, 공고, IT_개발">
                            <title>[넷마블] 인사전략담당자모집 채용 | 인사_노무_HRD_총무</title>
                          </head>
                          <body>
                            <h1>인사전략담당자모집</h1>
                          </body>
                        </html>
                        """
        );

        assertThat(preFilter.shouldSkip(fetched)).isTrue();
    }

    @Test
    @DisplayName("직행 해외영업 카테고리 공고는 meta keywords에 IT가 있어도 skip한다")
    void skipSalesCategoryEvenIfMetaKeywordsContainIt() {
        FetchedJobPosting fetched = new FetchedJobPosting(
                JobIngestionSource.ZIGHANG,
                "21c5a888-0933-455a-b2c5-9d2a389e7475",
                "https://zighang.com/recruitment/21c5a888-0933-455a-b2c5-9d2a389e7475",
                "https://zighang.com/recruitment/21c5a888-0933-455a-b2c5-9d2a389e7475",
                """
                        <html>
                          <head>
                            <meta property="og:title" content="[에스알티상사] 해외 화학제품/원료 해외영업직 채용 | 영업_해외영업">
                            <meta name="keywords" content="직행, 채용, 공고, IT_개발">
                            <title>[에스알티상사] 해외 화학제품/원료 해외영업직 채용 | 영업_해외영업</title>
                          </head>
                          <body>
                            <h1>해외 화학제품/원료 해외영업직 채용</h1>
                          </body>
                        </html>
                        """
        );

        assertThat(preFilter.shouldSkip(fetched)).isTrue();
    }

    @Test
    @DisplayName("직행 카테고리 신호가 없으면 안전하게 skip한다")
    void skipWhenCategorySignalIsMissing() {
        FetchedJobPosting fetched = new FetchedJobPosting(
                JobIngestionSource.ZIGHANG,
                "00000000-0000-0000-0000-000000000100",
                "https://zighang.com/recruitment/00000000-0000-0000-0000-000000000100",
                "https://zighang.com/recruitment/00000000-0000-0000-0000-000000000100",
                """
                        <html>
                          <body>
                            <h1>백엔드 개발자</h1>
                          </body>
                        </html>
                        """
        );

        assertThat(preFilter.shouldSkip(fetched)).isTrue();
    }

    @Test
    @DisplayName("직행 source만 지원한다")
    void supports() {
        assertThat(preFilter.supports(JobIngestionSource.ZIGHANG)).isTrue();
        assertThat(preFilter.supports(JobIngestionSource.JUMPIT)).isFalse();
    }
}
