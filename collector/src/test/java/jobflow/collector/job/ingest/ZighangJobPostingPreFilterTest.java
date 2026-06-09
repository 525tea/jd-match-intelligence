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
                            <meta property="og:title" content="백엔드 개발자 | JobFlow Labs | IT_개발 | 직행">
                            <meta name="keywords" content="백엔드 개발자, IT_개발, 직행">
                            <title>백엔드 개발자 | JobFlow Labs | IT_개발 | 직행</title>
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
                            <meta property="og:title" content="인사전략담당자모집 | 넷마블 | 인사_노무_HRD_총무 | 직행">
                            <meta name="keywords" content="인사전략담당자모집, 인사_노무_HRD_총무, 직행">
                            <title>인사전략담당자모집 | 넷마블 | 인사_노무_HRD_총무 | 직행</title>
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
