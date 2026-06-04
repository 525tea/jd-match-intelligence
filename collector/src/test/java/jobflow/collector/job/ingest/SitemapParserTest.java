package jobflow.collector.job.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SitemapParserTest {

    private final SitemapParser parser = new SitemapParser();

    @Test
    @DisplayName("urlset sitemap을 파싱한다")
    void parseUrlSet() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                    <url>
                        <loc>https://zighang.com/jobs/123</loc>
                        <lastmod>2026-06-04</lastmod>
                    </url>
                    <url>
                        <loc>https://zighang.com/jobs/456</loc>
                        <lastmod>2026-06-04T10:15:30+09:00</lastmod>
                    </url>
                </urlset>
                """;

        ParsedSitemap sitemap = parser.parse(xml);

        assertThat(sitemap.type()).isEqualTo(SitemapType.URL_SET);
        assertThat(sitemap.isUrlSet()).isTrue();
        assertThat(sitemap.entries()).hasSize(2);
        assertThat(sitemap.entries().get(0).loc()).isEqualTo("https://zighang.com/jobs/123");
        assertThat(sitemap.entries().get(0).lastModified()).isEqualTo(LocalDateTime.of(2026, 6, 4, 0, 0));
        assertThat(sitemap.entries().get(1).lastModified()).isEqualTo(LocalDateTime.of(2026, 6, 4, 10, 15, 30));
    }

    @Test
    @DisplayName("sitemapindex sitemap을 파싱한다")
    void parseSitemapIndex() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                    <sitemap>
                        <loc>https://zighang.com/seo/sitemap/jobs-1.xml</loc>
                        <lastmod>2026-06-04T01:20:00Z</lastmod>
                    </sitemap>
                    <sitemap>
                        <loc>https://zighang.com/seo/sitemap/jobs-2.xml</loc>
                    </sitemap>
                </sitemapindex>
                """;

        ParsedSitemap sitemap = parser.parse(xml);

        assertThat(sitemap.type()).isEqualTo(SitemapType.SITEMAP_INDEX);
        assertThat(sitemap.isSitemapIndex()).isTrue();
        assertThat(sitemap.entries()).hasSize(2);
        assertThat(sitemap.entries().get(0).loc()).isEqualTo("https://zighang.com/seo/sitemap/jobs-1.xml");
        assertThat(sitemap.entries().get(0).lastModified()).isEqualTo(LocalDateTime.of(2026, 6, 4, 1, 20));
        assertThat(sitemap.entries().get(1).lastModified()).isNull();
    }

    @Test
    @DisplayName("loc가 없는 entry는 제외한다")
    void skipEntryWithoutLoc() {
        String xml = """
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                    <url>
                        <lastmod>2026-06-04</lastmod>
                    </url>
                    <url>
                        <loc>https://jumpit.saramin.co.kr/position/123</loc>
                    </url>
                </urlset>
                """;

        ParsedSitemap sitemap = parser.parse(xml);

        assertThat(sitemap.entries()).hasSize(1);
        assertThat(sitemap.entries().getFirst().loc()).isEqualTo("https://jumpit.saramin.co.kr/position/123");
    }

    @Test
    @DisplayName("지원하지 않는 root면 예외가 발생한다")
    void throwExceptionWithUnsupportedRoot() {
        String xml = """
                <rss>
                    <channel></channel>
                </rss>
                """;

        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported sitemap root");
    }

    @Test
    @DisplayName("빈 XML이면 예외가 발생한다")
    void throwExceptionWithBlankXml() {
        assertThatThrownBy(() -> parser.parse(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }
}
