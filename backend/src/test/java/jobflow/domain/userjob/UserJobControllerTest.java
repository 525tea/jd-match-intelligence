package jobflow.domain.userjob;

import jobflow.domain.userjob.dto.UserJobResponse;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.GlobalExceptionHandler;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = UserJobController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class UserJobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserJobService userJobService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(testAuthentication());
    }

    @Test
    @DisplayName("공고 조회 상태 기록 성공 시 200 ApiResponse를 반환한다")
    void markViewed() throws Exception {
        setAuthentication();

        given(userJobService.markViewed(1L, 10L))
                .willReturn(response(UserJobStatus.VIEWED));

        mockMvc.perform(post("/user/jobs/{jobId}/view", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.jobId").value(10))
                .andExpect(jsonPath("$.data.status").value("VIEWED"));
    }

    @Test
    @DisplayName("공고 저장 성공 시 200 ApiResponse를 반환한다")
    void saveJob() throws Exception {
        setAuthentication();

        given(userJobService.saveJob(1L, 10L))
                .willReturn(response(UserJobStatus.SAVED));

        mockMvc.perform(post("/user/jobs/{jobId}/save", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("SAVED"));
    }

    @Test
    @DisplayName("공고 무시 성공 시 200 ApiResponse를 반환한다")
    void ignoreJob() throws Exception {
        setAuthentication();

        given(userJobService.ignoreJob(1L, 10L))
                .willReturn(response(UserJobStatus.IGNORED));

        mockMvc.perform(post("/user/jobs/{jobId}/ignore", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("IGNORED"));
    }

    @Test
    @DisplayName("존재하지 않는 공고 저장 시 404 ErrorResponse를 반환한다")
    void saveMissingJob() throws Exception {
        setAuthentication();

        willThrow(new EntityNotFoundException(ErrorCode.JOB_NOT_FOUND))
                .given(userJobService)
                .saveJob(1L, 999L);

        mockMvc.perform(post("/user/jobs/{jobId}/save", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"));
    }

    private UserJobResponse response(UserJobStatus status) {
        return new UserJobResponse(
                100L,
                1L,
                10L,
                "백엔드 개발자",
                "JobFlow",
                status,
                LocalDateTime.of(2026, 6, 4, 10, 0),
                status == UserJobStatus.SAVED ? LocalDateTime.of(2026, 6, 4, 11, 0) : null,
                status == UserJobStatus.IGNORED ? LocalDateTime.of(2026, 6, 4, 11, 0) : null
        );
    }

    private Authentication testAuthentication() {
        UserPrincipal principal = new UserPrincipal(
                1L,
                "user@example.com",
                "사용자",
                "USER"
        );

        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.authorities()
        );
    }
}
