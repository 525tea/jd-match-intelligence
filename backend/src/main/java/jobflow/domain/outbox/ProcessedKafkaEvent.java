package jobflow.domain.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "processed_kafka_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_processed_kafka_events_consumer_event",
                columnNames = {"consumer_name", "event_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedKafkaEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String consumerName;

    @Column(nullable = false)
    private Long eventId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime processedAt;

    public static ProcessedKafkaEvent create(String consumerName, Long eventId) {
        ProcessedKafkaEvent processedKafkaEvent = new ProcessedKafkaEvent();
        processedKafkaEvent.consumerName = consumerName;
        processedKafkaEvent.eventId = eventId;
        return processedKafkaEvent;
    }

    @PrePersist
    void prePersist() {
        this.processedAt = LocalDateTime.now();
    }
}
