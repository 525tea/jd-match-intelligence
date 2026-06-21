package jobflow.domain.application;

import jobflow.domain.application.dto.ApplicationResponse;
import jobflow.domain.application.dto.ApplicationStatusHistoryResponse;
import jobflow.domain.application.dto.ApplicationSummaryResponse;
import jobflow.domain.job.JobStatus;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.GlobalExceptionHandler;
import jobflow.global.error.exception.ConflictException;
import jobflow.global.error.exception.EntityNotFoundException;
import jobflow.global.security.JwtAuthenticationFilter;
import jobflow.global.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ApplicationController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApplicationService applicationService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(testAuthentication());
    }

    @Test
    @DisplayName("지원 상태 생성 성공 시 201 ApiResponse를 반환한다")
    void createApplication() throws Exception {
        setAuthentication();

        given(applicationService.createApplication(eq(1L), any()))
                .willReturn(applicationResponse(ApplicationStatus.APPLIED));

        mockMvc.perform(post("/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobId": 10
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.jobId").value(10))
                .andExpect(jsonPath("$.data.status").value("APPLIED"));
    }

    @Test
    @DisplayName("지원 상태 생성 validation 실패 시 400 ErrorResponse를 반환한다")
    void createApplicationValidationFail() throws Exception {
        mockMvc.perform(post("/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobId": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fields", hasSize(1)));

        verifyNoInteractions(applicationService);
    }

    @Test
    @DisplayName("중복 지원 상태 생성 시 409 ErrorResponse를 반환한다")
    void createDuplicatedApplication() throws Exception {
        setAuthentication();

        willThrow(new ConflictException(ErrorCode.APPLICATION_ALREADY_EXISTS))
                .given(applicationService)
                .createApplication(eq(1L), any());

        mockMvc.perform(post("/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobId": 10
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("APPLICATION_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("마감된 공고 지원 시 409 ErrorResponse를 반환한다")
    void createApplicationForClosedJob() throws Exception {
        setAuthentication();

        willThrow(new ConflictException(ErrorCode.APPLICATION_STATUS_CONFLICT))
                .given(applicationService)
                .createApplication(eq(1L), any());

        mockMvc.perform(post("/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobId": 10
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("APPLICATION_STATUS_CONFLICT"));
    }

    @Test
    @DisplayName("내 지원 상태 단건 조회 성공 시 200 ApiResponse를 반환한다")
    void getApplication() throws Exception {
        setAuthentication();

        given(applicationService.getApplication(1L, 100L))
                .willReturn(applicationResponse(ApplicationStatus.APPLIED));

        mockMvc.perform(get("/applications/{applicationId}", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.jobId").value(10))
                .andExpect(jsonPath("$.data.status").value("APPLIED"));
    }

    @Test
    @DisplayName("존재하지 않는 지원 상태 조회 시 404 ErrorResponse를 반환한다")
    void getMissingApplication() throws Exception {
        setAuthentication();

        willThrow(new EntityNotFoundException(ErrorCode.APPLICATION_NOT_FOUND))
                .given(applicationService)
                .getApplication(1L, 999L);

        mockMvc.perform(get("/applications/{applicationId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("APPLICATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("내 지원 상태 변경 이력 조회 성공 시 200 ApiResponse를 반환한다")
    void getApplicationStatusHistories() throws Exception {
        setAuthentication();
        LocalDateTime changedAt = LocalDateTime.of(2026, 6, 21, 10, 0);

        given(applicationService.getApplicationStatusHistories(1L, 100L))
                .willReturn(List.of(new ApplicationStatusHistoryResponse(
                        1L,
                        100L,
                        null,
                        ApplicationStatus.APPLIED,
                        changedAt,
                        changedAt
                )));

        mockMvc.perform(get("/applications/{applicationId}/status-histories", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].applicationId").value(100))
                .andExpect(jsonPath("$.data[0].previousStatus").doesNotExist())
                .andExpect(jsonPath("$.data[0].nextStatus").value("APPLIED"));
    }

    @Test
    @DisplayName("내 지원 상태 목록 조회 성공 시 200 ApiResponse를 반환한다")
    void getMyApplications() throws Exception {
        setAuthentication();

        given(applicationService.getMyApplications(1L))
                .willReturn(List.of(applicationSummaryResponse()));

        mockMvc.perform(get("/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(100))
                .andExpect(jsonPath("$.data[0].jobId").value(10))
                .andExpect(jsonPath("$.data[0].status").value("APPLIED"));
    }

    @Test
    @DisplayName("지원 상태 변경 성공 시 200 ApiResponse를 반환한다")
    void updateApplicationStatus() throws Exception {
        setAuthentication();

        given(applicationService.updateApplicationStatus(eq(1L), eq(100L), any()))
                .willReturn(applicationResponse(ApplicationStatus.INTERVIEW));

        mockMvc.perform(patch("/applications/{applicationId}/status", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "INTERVIEW"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("INTERVIEW"));
    }

    @Test
    @DisplayName("지원 상태 변경 validation 실패 시 400 ErrorResponse를 반환한다")
    void updateApplicationStatusValidationFail() throws Exception {
        mockMvc.perform(patch("/applications/{applicationId}/status", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fields", hasSize(1)));

        verifyNoInteractions(applicationService);
    }

    @Test
    @DisplayName("지원 상태 변경 충돌 시 409 ErrorResponse를 반환한다")
    void updateApplicationStatusConflict() throws Exception {
        setAuthentication();

        willThrow(new ConflictException(ErrorCode.APPLICATION_STATUS_CONFLICT))
                .given(applicationService)
                .updateApplicationStatus(eq(1L), eq(100L), any());

        mockMvc.perform(patch("/applications/{applicationId}/status", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "INTERVIEW"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("APPLICATION_STATUS_CONFLICT"));
    }

    private Authentication testAuthentication() {
        UserPrincipal principal = new UserPrincipal(
                1L,
                "test@example.com",
                "테스트",
                "USER"
        );

        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.authorities()
        );
    }

    private ApplicationResponse applicationResponse(ApplicationStatus status) {
        LocalDateTime now = LocalDateTime.of(2026, 6, 2, 12, 0);

        return new ApplicationResponse(
                100L,
                10L,
                "백엔드 개발자",
                "JobFlow",
                JobStatus.OPEN,
                status,
                now,
                0L,
                now,
                now
        );
    }

    private ApplicationSummaryResponse applicationSummaryResponse() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 2, 12, 0);

        return new ApplicationSummaryResponse(
                100L,
                10L,
                "백엔드 개발자",
                "JobFlow",
                JobStatus.OPEN,
                ApplicationStatus.APPLIED,
                now,
                now
        );
    }
}
