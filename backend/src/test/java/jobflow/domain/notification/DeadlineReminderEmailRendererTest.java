package jobflow.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeadlineReminderEmailRendererTest {

    private final DeadlineReminderEmailRenderer renderer = new DeadlineReminderEmailRenderer();

    @Test
    @DisplayName("마감 알림 email 제목과 text/html 본문을 만든다")
    void render() {
        DeadlineReminderTarget target = new DeadlineReminderTarget(
                1L,
                "user@example.com",
                "사용자",
                10L,
                "백엔드 <개발자>",
                "Example Company",
                LocalDateTime.of(2026, 6, 17, 9, 30),
                "https://example.com/jobs/10"
        );

        EmailSendRequest request = renderer.render(target);

        assertThat(request.to()).isEqualTo("user@example.com");
        assertThat(request.subject()).isEqualTo("[JobFlow] 저장한 공고 마감이 다가오고 있어요");
        assertThat(request.text()).contains("백엔드 <개발자>");
        assertThat(request.text()).contains("2026-06-17 09:30");
        assertThat(request.html()).contains("백엔드 &lt;개발자&gt;");
        assertThat(request.html()).contains("https://example.com/jobs/10");
    }
}
