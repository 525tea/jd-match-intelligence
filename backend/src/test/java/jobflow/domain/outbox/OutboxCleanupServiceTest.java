package jobflow.domain.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class OutboxCleanupServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-07-03T00:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    @Test
    @DisplayName("retention이 지난 cleanup 후보를 batch 단위로 삭제한다")
    void cleanupProcessedEvents() {
        OutboxCleanupService service = new OutboxCleanupService(
                outboxEventRepository,
                clock,
                Duration.ofMinutes(30),
                100
        );
        given(outboxEventRepository.findCleanupCandidateIds(
                LocalDateTime.of(2026, 7, 3, 8, 30),
                PageRequest.of(0, 100)
        )).willReturn(List.of(1L, 2L, 3L));
        given(outboxEventRepository.deleteByIdIn(List.of(1L, 2L, 3L))).willReturn(3);

        int deletedCount = service.cleanupProcessedEvents();

        assertThat(deletedCount).isEqualTo(3);
        verify(outboxEventRepository).deleteByIdIn(List.of(1L, 2L, 3L));
    }

    @Test
    @DisplayName("cleanup 후보가 없으면 delete를 실행하지 않는다")
    void skipDeleteWhenNoCleanupCandidateExists() {
        OutboxCleanupService service = new OutboxCleanupService(
                outboxEventRepository,
                clock,
                Duration.ofMinutes(30),
                100
        );
        given(outboxEventRepository.findCleanupCandidateIds(
                LocalDateTime.of(2026, 7, 3, 8, 30),
                PageRequest.of(0, 100)
        )).willReturn(List.of());

        int deletedCount = service.cleanupProcessedEvents();

        assertThat(deletedCount).isZero();
    }

    @Test
    @DisplayName("잘못된 batch size는 최소 1로 보정한다")
    void normalizeInvalidBatchSize() {
        OutboxCleanupService service = new OutboxCleanupService(
                outboxEventRepository,
                clock,
                Duration.ofMinutes(30),
                0
        );
        given(outboxEventRepository.findCleanupCandidateIds(
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.any(PageRequest.class)
        )).willReturn(List.of());

        service.cleanupProcessedEvents();

        ArgumentCaptor<PageRequest> pageRequestCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(outboxEventRepository).findCleanupCandidateIds(
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                pageRequestCaptor.capture()
        );
        assertThat(pageRequestCaptor.getValue().getPageSize()).isEqualTo(1);
    }
}
