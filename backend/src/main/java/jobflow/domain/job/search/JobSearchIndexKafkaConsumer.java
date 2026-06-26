package jobflow.domain.job.search;

import java.util.Set;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRepository;
import jobflow.domain.outbox.OutboxEventTypes;
import jobflow.domain.outbox.OutboxKafkaEnvelope;
import jobflow.domain.outbox.OutboxKafkaMessageParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jobflow.kafka.consumer.enabled", havingValue = "true")
public class JobSearchIndexKafkaConsumer {

    private static final Set<String> INDEXABLE_EVENT_TYPES = Set.of(
            OutboxEventTypes.JOB_CREATED,
            OutboxEventTypes.JOB_UPDATED,
            OutboxEventTypes.JOB_CLOSED,
            OutboxEventTypes.JOB_EXPIRED
    );

    private final OutboxKafkaMessageParser messageParser;
    private final JobRepository jobRepository;
    private final JobSearchIndexingService jobSearchIndexingService;

    @Value("${jobflow.kafka.consumer.search.refresh-after-index:false}")
    private boolean refreshAfterIndex;

    @Transactional(readOnly = true)
    @KafkaListener(
            topics = "${jobflow.kafka.consumer.topics.job-events:job.created}",
            groupId = "${jobflow.kafka.consumer.group-id:jobflow-backend}"
    )
    public void consume(String message) {
        OutboxKafkaEnvelope envelope = messageParser.parseEnvelope(message);

        if (!INDEXABLE_EVENT_TYPES.contains(envelope.eventType())) {
            log.debug("Kafka job event ignored. eventId={}, eventType={}", envelope.eventId(), envelope.eventType());
            return;
        }

        Long jobId = resolveJobId(envelope);
        if (jobId == null) {
            throw new IllegalArgumentException("Kafka job event does not contain job id");
        }

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Kafka job event target was not found. jobId=" + jobId));

        jobSearchIndexingService.index(job);
        if (refreshAfterIndex) {
            jobSearchIndexingService.refresh();
        }

        log.info(
                "Kafka job search index event handled. eventId={}, eventType={}, jobId={}, kafka_consumer_smoke_run_id={}",
                envelope.eventId(),
                envelope.eventType(),
                jobId,
                textOrNull(envelope.payload(), "smokeRunId")
        );
    }

    private Long resolveJobId(OutboxKafkaEnvelope envelope) {
        JsonNode jobId = envelope.payload().path("jobId");
        if (!jobId.isMissingNode() && !jobId.isNull()) {
            if (!jobId.canConvertToLong()) {
                throw new IllegalArgumentException("Kafka job event contains invalid job id");
            }
            return jobId.longValue();
        }
        return envelope.aggregateId();
    }

    private String textOrNull(JsonNode payload, String fieldName) {
        JsonNode value = payload.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asText();
    }
}
