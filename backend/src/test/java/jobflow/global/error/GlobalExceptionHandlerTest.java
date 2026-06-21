package jobflow.global.error;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jobflow.global.error.exception.BusinessException;
import jobflow.global.error.exception.ConflictException;
import jobflow.global.error.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import jobflow.global.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;


@WebMvcTest(
        controllers = GlobalExceptionHandlerTest.TestController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestControllerConfig.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("BusinessException 발생 시 ErrorResponse를 반환한다")
    void handleBusinessException() throws Exception {
        mockMvc.perform(get("/test/business-exception"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value("입력값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/test/business-exception"));
    }

    @Test
    @DisplayName("EntityNotFoundException 발생 시 404 ErrorResponse를 반환한다")
    void handleEntityNotFoundException() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("사용자를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("ConflictException 발생 시 409 ErrorResponse를 반환한다")
    void handleConflictException() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_EMAIL_DUPLICATED"))
                .andExpect(jsonPath("$.error.message").value("이미 사용 중인 이메일입니다."));
    }

    @Test
    @DisplayName("Validation 실패 시 field error 목록을 반환한다")
    void handleValidationException() throws Exception {
        String requestBody = """
                {
                  "email": "invalid-email",
                  "name": ""
                }
                """;

        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fields", hasSize(2)));
    }

    @Test
    @DisplayName("잘못된 JSON 요청 시 400 ErrorResponse를 반환한다")
    void handleInvalidJson() throws Exception {
        String requestBody = """
                {
                  "email": "test@example.com",
                  "name": 
                }
                """;

        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("요청 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/test/validation"));
    }

    @TestConfiguration
    static class TestControllerConfig {

        @Bean
        TestController testController() {
            return new TestController();
        }
    }

    @Controller
    static class TestController {

        @ResponseBody
        @PostMapping("/test/validation")
        void validation(@Valid @RequestBody TestRequest request) {
        }

        @ResponseBody
        @org.springframework.web.bind.annotation.GetMapping("/test/business-exception")
        void businessException() {
            throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
        }

        @ResponseBody
        @org.springframework.web.bind.annotation.GetMapping("/test/not-found")
        void notFound() {
            throw new EntityNotFoundException(ErrorCode.USER_NOT_FOUND);
        }

        @ResponseBody
        @org.springframework.web.bind.annotation.GetMapping("/test/conflict")
        void conflict() {
            throw new ConflictException(ErrorCode.USER_EMAIL_DUPLICATED);
        }
    }

    record TestRequest(
            @Email(message = "이메일 형식이 올바르지 않습니다.")
            String email,

            @NotBlank(message = "이름은 필수입니다.")
            String name
    ) {
    }
}
