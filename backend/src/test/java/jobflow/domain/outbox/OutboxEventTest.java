package jobflow.domain.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

    @Test
    @DisplayName("이벤트 처리 성공 시 PUBLISHED 상태로 변경하고 publishedAt을 기록한다")
    void markPublished() {
        OutboxEvent event = createEvent();

        event.markFailed("previous error", 3);

        event.markPublished();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getLastError()).isNull();
        assertThat(event.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("새 outbox event는 v1 envelope schemaVersion을 가진다")
    void createWithSchemaVersion() {
        OutboxEvent event = createEvent();

        assertThat(event.getSchemaVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("이벤트 처리 실패 시 retryCount를 증가시키고 lastError를 기록한다")
    void markFailed() {
        OutboxEvent event = createEvent();

        event.markFailed("relay failed", 3);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("relay failed");
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("최대 재시도 횟수에 도달하면 FAILED 상태로 변경한다")
    void markFailedWithMaxRetryCount() {
        OutboxEvent event = createEvent();

        event.markFailed("first failure", 3);
        event.markFailed("second failure", 3);
        event.markFailed("third failure", 3);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(3);
        assertThat(event.getLastError()).isEqualTo("third failure");
    }

    private OutboxEvent createEvent() {
        return OutboxEvent.create(
                "JOB",
                1L,
                OutboxEventTypes.JOB_CREATED,
                """
                        {"jobId":1}
                        """,
                OutboxEvent.TOPIC_JOB_EVENTS
        );
    }
}
