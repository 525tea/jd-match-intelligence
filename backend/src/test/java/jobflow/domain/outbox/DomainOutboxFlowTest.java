package jobflow.domain.outbox;

import jobflow.domain.application.ApplicationRepository;
import jobflow.domain.application.ApplicationService;
import jobflow.domain.application.ApplicationStatus;
import jobflow.domain.application.dto.ApplicationCreateRequest;
import jobflow.domain.application.dto.ApplicationResponse;
import jobflow.domain.application.dto.ApplicationStatusUpdateRequest;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.JobRepository;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.JobService;
import jobflow.domain.job.RemoteType;
import jobflow.domain.job.dto.JobCreateRequest;
import jobflow.domain.job.dto.JobResponse;
import jobflow.domain.user.User;
import jobflow.domain.user.UserRepository;
import jobflow.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import jobflow.domain.job.search.JobSearchService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({
        JpaAuditingConfig.class,
        JobService.class,
        ApplicationService.class,
        OutboxEventService.class,
        OutboxRelayService.class,
        DomainOutboxFlowTest.JsonMapperTestConfig.class,
        DomainOutboxFlowTest.TestHandlerConfig.class
})
class DomainOutboxFlowTest {

    @Autowired
    private JobService jobService;

    @MockitoBean
    private JobSearchService jobSearchService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxRelayService outboxRelayService;

    @Autowired
    private RecordingOutboxEventHandler outboxEventHandler;

    @BeforeEach
    void setUp() {
        outboxEventHandler.clear();
    }

    @Test
    @DisplayName("공고 생성 시 JOB_CREATED outbox event를 저장한다")
    void createJobStoresOutboxEvent() {
        JobResponse response = jobService.createJob(createJobRequest("job-1"));

        List<OutboxEvent> events = outboxEventRepository.findAll();

        assertThat(jobRepository.findById(response.id())).isPresent();
        assertThat(events).hasSize(1);

        OutboxEvent event = events.getFirst();

        assertThat(event.getAggregateType()).isEqualTo("JOB");
        assertThat(event.getAggregateId()).isEqualTo(response.id());
        assertThat(event.getEventType()).isEqualTo(OutboxEventTypes.JOB_CREATED);
        assertThat(event.getTopic()).isEqualTo(OutboxEvent.TOPIC_JOB_EVENTS);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getPayload()).contains("\"jobId\":" + response.id());
        assertThat(event.getPayload()).contains("\"source\":\"MANUAL\"");
        assertThat(event.getPayload()).contains("\"externalId\":\"job-1\"");
    }

    @Test
    @DisplayName("지원 생성과 상태 변경 시 APPLICATION outbox event를 저장한다")
    void createAndUpdateApplicationStoresOutboxEvents() {
        User user = userRepository.save(User.signup("user@example.com", "encoded-password", "사용자"));
        JobResponse job = jobService.createJob(createJobRequest("job-2"));
        outboxEventRepository.deleteAll();

        ApplicationResponse created = applicationService.createApplication(
                user.getId(),
                new ApplicationCreateRequest(job.id())
        );
        ApplicationResponse updated = applicationService.updateApplicationStatus(
                user.getId(),
                created.id(),
                new ApplicationStatusUpdateRequest(ApplicationStatus.INTERVIEW)
        );

        List<OutboxEvent> events = outboxEventRepository.findAll();

        assertThat(applicationRepository.findById(created.id())).isPresent();
        assertThat(updated.status()).isEqualTo(ApplicationStatus.INTERVIEW);
        assertThat(events)
                .extracting(OutboxEvent::getEventType)
                .containsExactly(
                        OutboxEventTypes.APPLICATION_CREATED,
                        OutboxEventTypes.APPLICATION_STATUS_CHANGED
                );
        assertThat(events)
                .extracting(OutboxEvent::getAggregateType)
                .containsExactly("APPLICATION", "APPLICATION");
        assertThat(events)
                .extracting(OutboxEvent::getAggregateId)
                .containsExactly(created.id(), created.id());
        assertThat(events)
                .extracting(OutboxEvent::getTopic)
                .containsExactly(
                        OutboxEvent.TOPIC_APPLICATION_EVENTS,
                        OutboxEvent.TOPIC_APPLICATION_EVENTS
                );
        assertThat(events)
                .extracting(OutboxEvent::getStatus)
                .containsExactly(OutboxStatus.PENDING, OutboxStatus.PENDING);
        assertThat(events.getLast().getPayload()).contains("\"status\":\"INTERVIEW\"");
    }

    private JobCreateRequest createJobRequest(String externalId) {
        return new JobCreateRequest(
                "MANUAL",
                externalId,
                "백엔드 개발자",
                "JobFlow",
                "Spring Boot 백엔드 개발자 채용",
                "https://example.com/jobs/" + externalId,
                JobRole.BACKEND,
                "Java/Spring",
                CareerLevel.JUNIOR,
                1,
                3,
                "BACHELOR",
                EmploymentType.FULL_TIME,
                "STARTUP",
                "IT",
                "KR",
                "서울",
                "강남구",
                RemoteType.HYBRID,
                40000000,
                70000000,
                "KRW",
                true,
                1,
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 6, 30, 23, 59),
                List.of(),
                List.of()
        );
    }

    @Test
    @DisplayName("도메인 서비스가 저장한 outbox event를 relay로 처리한다")
    void relayDomainOutboxEvents() {
        User user = userRepository.save(User.signup("relay-user@example.com", "encoded-password", "사용자"));
        JobResponse job = jobService.createJob(createJobRequest("job-relay"));
        ApplicationResponse application = applicationService.createApplication(
                user.getId(),
                new ApplicationCreateRequest(job.id())
        );
        applicationService.updateApplicationStatus(
                user.getId(),
                application.id(),
                new ApplicationStatusUpdateRequest(ApplicationStatus.INTERVIEW)
        );

        List<Long> pendingEventIds = outboxEventRepository.findAll()
                .stream()
                .map(OutboxEvent::getId)
                .toList();

        int relayedCount = outboxRelayService.relayPendingEvents();

        List<OutboxEvent> events = outboxEventRepository.findAll();

        assertThat(pendingEventIds).hasSize(3);
        assertThat(relayedCount).isEqualTo(3);
        assertThat(outboxEventHandler.handledEventIds()).containsExactlyElementsOf(pendingEventIds);
        assertThat(events)
                .extracting(OutboxEvent::getStatus)
                .containsOnly(OutboxStatus.PUBLISHED);
        assertThat(events)
                .allSatisfy(event -> assertThat(event.getPublishedAt()).isNotNull());
    }

    @TestConfiguration
    static class JsonMapperTestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }
    }

    @TestConfiguration
    static class TestHandlerConfig {

        @Bean
        RecordingOutboxEventHandler recordingOutboxEventHandler() {
            return new RecordingOutboxEventHandler();
        }
    }

    static class RecordingOutboxEventHandler implements OutboxEventHandler {

        private final List<Long> handledEventIds = new ArrayList<>();

        @Override
        public boolean supports(OutboxEvent event) {
            return true;
        }

        @Override
        public void handle(OutboxEvent event) {
            handledEventIds.add(event.getId());
        }

        List<Long> handledEventIds() {
            return handledEventIds;
        }

        void clear() {
            handledEventIds.clear();
        }
    }
}
