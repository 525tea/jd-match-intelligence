package jobflow.domain.job.search;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.Optional;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRepository;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.RemoteType;
import jobflow.domain.outbox.OutboxKafkaMessageParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class JobSearchIndexKafkaConsumerTest {

    private final OutboxKafkaMessageParser messageParser = new OutboxKafkaMessageParser(
            JsonMapper.builder().build()
    );

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobSearchIndexingService jobSearchIndexingService;

    @Test
    @DisplayName("색인 대상 Job 이벤트를 받아 Elasticsearch 색인을 요청한다")
    void consumeIndexableJobEvent() {
        JobSearchIndexKafkaConsumer consumer = new JobSearchIndexKafkaConsumer(
                messageParser,
                jobRepository,
                jobSearchIndexingService
        );
        Job job = sampleJob();
        given(jobRepository.findById(100L)).willReturn(Optional.of(job));

        consumer.consume("""
                {
                  "eventId": 1,
                  "aggregateType": "JOB",
                  "aggregateId": 100,
                  "eventType": "JOB_CREATED",
                  "topic": "job.created",
                  "payload": {
                    "jobId": 100,
                    "smokeRunId": "sample-consumer-smoke"
                  }
                }
                """);

        verify(jobRepository).findById(100L);
        verify(jobSearchIndexingService).index(job);
    }

    @Test
    @DisplayName("색인 대상이 아닌 이벤트는 무시한다")
    void ignoreNonIndexableEvent() {
        JobSearchIndexKafkaConsumer consumer = new JobSearchIndexKafkaConsumer(
                messageParser,
                jobRepository,
                jobSearchIndexingService
        );

        consumer.consume("""
                {
                  "eventId": 2,
                  "aggregateType": "APPLICATION",
                  "aggregateId": 200,
                  "eventType": "APPLICATION_CREATED",
                  "topic": "job.created",
                  "payload": {
                    "jobId": 100
                  }
                }
                """);

        verifyNoInteractions(jobRepository);
        verify(jobSearchIndexingService, never()).index(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("색인 대상 이벤트에 jobId가 없으면 실패한다")
    void failWithoutJobId() {
        JobSearchIndexKafkaConsumer consumer = new JobSearchIndexKafkaConsumer(
                messageParser,
                jobRepository,
                jobSearchIndexingService
        );

        assertThatThrownBy(() -> consumer.consume("""
                {
                  "eventId": 3,
                  "aggregateType": "JOB",
                  "eventType": "JOB_CREATED",
                  "topic": "job.created",
                  "payload": {
                    "smokeRunId": "sample-consumer-smoke"
                  }
                }
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Kafka job event does not contain job id");
    }

    private Job sampleJob() {
        return Job.create(
                "SAMPLE_SOURCE",
                "sample-external-id",
                "Sample Backend Developer",
                "Sample Company",
                "Spring Boot 기반 서비스를 개발합니다.",
                "https://example.com/jobs/sample-backend-developer",
                JobRole.BACKEND,
                null,
                CareerLevel.JUNIOR,
                1,
                3,
                "BACHELOR",
                EmploymentType.FULL_TIME,
                "STARTUP",
                "IT",
                "KR",
                "Seoul",
                "Gangnam",
                RemoteType.HYBRID,
                null,
                null,
                "KRW",
                false,
                1,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 23, 59)
        );
    }
}
