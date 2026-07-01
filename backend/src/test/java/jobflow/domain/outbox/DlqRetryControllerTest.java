package jobflow.domain.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jobflow.global.error.ErrorCode;
import jobflow.global.error.GlobalExceptionHandler;
import jobflow.global.error.exception.BusinessException;
import jobflow.global.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = DlqRetryController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class DlqRetryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KafkaDlqRetryService kafkaDlqRetryService;

    @MockitoBean
    private DlqMessageService dlqMessageService;

    @Test
    @DisplayName("DLQ 재처리 성공 시 target topic/key를 반환한다")
    void retry() throws Exception {
        given(kafkaDlqRetryService.retry(any(DlqRetryRequest.class)))
                .willReturn(new DlqRetryResponse(1, "job.created", "JOB:1"));

        mockMvc.perform(post("/admin/dlq/retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dlqEnvelope()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.schemaVersion").value(1))
                .andExpect(jsonPath("$.data.targetTopic").value("job.created"))
                .andExpect(jsonPath("$.data.targetKey").value("JOB:1"));
    }

    @Test
    @DisplayName("잘못된 DLQ envelope는 400을 반환한다")
    void retryWithInvalidEnvelope() throws Exception {
        willThrow(new BusinessException(ErrorCode.KAFKA_DLQ_INVALID_ENVELOPE, "DLQ envelope sourceTopic이 필요합니다."))
                .given(kafkaDlqRetryService)
                .retry(any(DlqRetryRequest.class));

        mockMvc.perform(post("/admin/dlq/retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("KAFKA_DLQ_INVALID_ENVELOPE"));
    }

    private String dlqEnvelope() {
        return """
                {
                  "schemaVersion": 1,
                  "sourceTopic": "job.created",
                  "sourceKey": "JOB:1",
                  "original": {
                    "payload": {
                      "jobId": 1
                    }
                  }
                }
                """;
    }
}
