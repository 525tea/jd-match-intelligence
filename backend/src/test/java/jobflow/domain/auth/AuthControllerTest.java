package jobflow.domain.auth;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jobflow.domain.auth.dto.LoginResponse;
import jobflow.domain.auth.dto.SignupResponse;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.GlobalExceptionHandler;
import jobflow.global.error.exception.BusinessException;
import jobflow.global.error.exception.ConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("회원가입 성공 시 201 ApiResponse를 반환한다")
    void signup() throws Exception {
        String requestBody = """
                {
                  "email": "test@example.com",
                  "password": "password123",
                  "name": "테스트"
                }
                """;

        given(authService.signup(any()))
                .willReturn(new SignupResponse(1L, "test@example.com", "테스트"));

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.name").value("테스트"));
    }

    @Test
    @DisplayName("회원가입 요청 validation 실패 시 400 ErrorResponse를 반환한다")
    void signupValidationFail() throws Exception {
        String requestBody = """
                {
                  "email": "invalid-email",
                  "password": "123",
                  "name": ""
                }
                """;

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fields", hasSize(3)));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("중복 이메일 회원가입 시 409 ErrorResponse를 반환한다")
    void signupDuplicatedEmail() throws Exception {
        String requestBody = """
                {
                  "email": "test@example.com",
                  "password": "password123",
                  "name": "테스트"
                }
                """;

        willThrow(new ConflictException(ErrorCode.USER_EMAIL_DUPLICATED))
                .given(authService)
                .signup(any());

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_EMAIL_DUPLICATED"))
                .andExpect(jsonPath("$.error.message").value("이미 사용 중인 이메일입니다."));
    }

    @Test
    @DisplayName("로그인 성공 시 200 ApiResponse와 access token을 반환한다")
    void login() throws Exception {
        String requestBody = """
                {
                  "email": "test@example.com",
                  "password": "password123"
                }
                """;

        given(authService.login(any()))
                .willReturn(LoginResponse.bearer("access-token", 3600000L));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600000));
    }

    @Test
    @DisplayName("로그인 요청 validation 실패 시 400 ErrorResponse를 반환한다")
    void loginValidationFail() throws Exception {
        String requestBody = """
                {
                  "email": "invalid-email",
                  "password": ""
                }
                """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fields", hasSize(2)));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("로그인 실패 시 401 ErrorResponse를 반환한다")
    void loginFail() throws Exception {
        String requestBody = """
                {
                  "email": "test@example.com",
                  "password": "wrong-password"
                }
                """;

        willThrow(new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS))
                .given(authService)
                .login(any());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.error.message").value("이메일 또는 비밀번호가 올바르지 않습니다."));
    }
}
