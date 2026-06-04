package jobflow.collector.job.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RobotsTxtParserTest {

    private final RobotsTxtParser parser = new RobotsTxtParser();

    @Test
    @DisplayName("현재 crawler user-agent에 맞는 일반 규칙을 선택한다")
    void parseGeneralRules() {
        String robotsTxt = """
                User-agent: GPTBot
                Disallow: /

                User-agent: *
                Disallow: /login/
                Disallow: /Search/?stext=
                Allow: /Recruit/GI_Read
                Sitemap: https://www.jobkorea.co.kr/sitemap.xml
                """;

        RobotsTxt parsed = parser.parse(robotsTxt, "JobFlowCrawler/0.1");

        assertThat(parsed.isAllowed("/Recruit/GI_Read/46789123")).isTrue();
        assertThat(parsed.isAllowed("/login/form")).isFalse();
        assertThat(parsed.isAllowed("/Search/?stext=java")).isFalse();
        assertThat(parsed.sitemapUrls()).containsExactly("https://www.jobkorea.co.kr/sitemap.xml");
    }

    @Test
    @DisplayName("더 구체적인 user-agent 그룹이 있으면 해당 그룹을 선택한다")
    void selectSpecificUserAgentGroup() {
        String robotsTxt = """
                User-agent: *
                Disallow: /private/

                User-agent: JobFlowCrawler
                Allow: /
                Disallow: /blocked/
                """;

        RobotsTxt parsed = parser.parse(robotsTxt, "JobFlowCrawler/0.1");

        assertThat(parsed.isAllowed("/private/page")).isTrue();
        assertThat(parsed.isAllowed("/blocked/page")).isFalse();
    }

    @Test
    @DisplayName("동일 prefix에서는 더 긴 rule이 우선되고 allow가 적용된다")
    void longestRuleWins() {
        String robotsTxt = """
                User-agent: *
                Disallow: /private/
                Allow: /private/public/
                """;

        RobotsTxt parsed = parser.parse(robotsTxt, "JobFlowCrawler/0.1");

        assertThat(parsed.isAllowed("/private/page")).isFalse();
        assertThat(parsed.isAllowed("/private/public/page")).isTrue();
    }

    @Test
    @DisplayName("빈 Disallow는 차단 rule로 추가하지 않는다")
    void ignoreEmptyDisallow() {
        String robotsTxt = """
                User-agent: *
                Disallow:
                """;

        RobotsTxt parsed = parser.parse(robotsTxt, "JobFlowCrawler/0.1");

        assertThat(parsed.isAllowed("/anything")).isTrue();
    }
}
