package jobflow.domain.notification.digest;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;
import jobflow.domain.notification.EmailSendRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DailyDigestEmailRendererTest {

    private final DailyDigestEmailRenderer renderer = new DailyDigestEmailRenderer();

    @Test
    @DisplayName("Daily Digest email 제목과 text/html 본문을 만든다")
    void render() {
        DailyDigestContent content = new DailyDigestContent(
                List.of(item(1L, "추천 <백엔드> 공고", BigDecimal.valueOf(82.67))),
                List.of(item(2L, "JD 매칭 공고", BigDecimal.valueOf(75))),
                List.of(item(3L, "신규 공고", null)),
                List.of(item(4L, "마감 임박 공고", null))
        );

        EmailSendRequest request = renderer.render(
                "user@example.com",
                "Example User",
                content
        );

        assertThat(request.to()).isEqualTo("user@example.com");
        assertThat(request.subject()).isEqualTo("[JobFlow] 오늘의 맞춤 공고 Digest");

        assertThat(request.text()).contains("Example User님");
        assertThat(request.text()).contains("## 추천 공고");
        assertThat(request.text()).contains("추천 <백엔드> 공고");
        assertThat(request.text()).contains("82.67점");
        assertThat(request.text()).contains("2026-06-18 09:30");
        assertThat(request.text()).contains("https://example.com/jobs/1");

        assertThat(request.html()).contains("Example User님");
        assertThat(request.html()).contains("<h2>추천 공고</h2>");
        assertThat(request.html()).contains("추천 &lt;백엔드&gt; 공고");
        assertThat(request.html()).contains("82.67점");
        assertThat(request.html()).contains("<a href=\"https://example.com/jobs/1\">https://example.com/jobs/1</a>");
    }

    @Test
    @DisplayName("빈 Digest content는 빈 상태 안내 email을 만든다")
    void renderEmptyContent() {
        DailyDigestContent content = new DailyDigestContent(
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        EmailSendRequest request = renderer.render(
                "user@example.com",
                null,
                content
        );

        assertThat(request.text()).contains("사용자님");
        assertThat(request.text()).contains("오늘은 새로 추천할 공고가 없습니다");
        assertThat(request.html()).contains("사용자님");
        assertThat(request.html()).contains("오늘은 새로 추천할 공고가 없습니다");
    }

    @Test
    @DisplayName("공고 URL이 없으면 링크 없음으로 표시한다")
    void renderMissingUrl() {
        DailyDigestContent content = new DailyDigestContent(
                List.of(new DailyDigestJobItem(
                        1L,
                        "추천 공고",
                        "Example Company",
                        JobRole.BACKEND,
                        CareerLevel.MID,
                        BigDecimal.valueOf(80),
                        LocalDateTime.of(2026, 6, 18, 9, 30),
                        null,
                        "추천 점수 기반"
                )),
                List.of(),
                List.of(),
                List.of()
        );

        EmailSendRequest request = renderer.render(
                "user@example.com",
                "Example User",
                content
        );

        assertThat(request.text()).contains("링크: 링크 없음");
        assertThat(request.html()).contains("링크: 링크 없음");
        assertThat(request.html()).doesNotContain("<a href=");
    }

    private DailyDigestJobItem item(Long jobId, String title, BigDecimal score) {
        return new DailyDigestJobItem(
                jobId,
                title,
                "Example Company",
                JobRole.BACKEND,
                CareerLevel.MID,
                score,
                LocalDateTime.of(2026, 6, 18, 9, 30),
                "https://example.com/jobs/" + jobId,
                "추천 점수 기반"
        );
    }
}
