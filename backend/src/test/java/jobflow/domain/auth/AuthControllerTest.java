package jobflow.domain.auth;

import jobflow.domain.auth.dto.DemoLoginResponse;
import jobflow.domain.auth.dto.LoginResponse;
import jobflow.domain.auth.dto.SignupResponse;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.GlobalExceptionHandler;
import jobflow.global.error.exception.BusinessException;
import jobflow.global.error.exception.ConflictException;
import jobflow.global.security.JwtAuthenticationFilter;
import jobflow.global.security.JwtCookieService;
import jobflow.global.security.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        GlobalExceptionHandler.class,
        JwtCookieService.class
})
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
                .willReturn(LoginResponse.bearer("access-token", 3600000L, 2L));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600000))
                .andExpect(jsonPath("$.data.userProjectId").value(2))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("jobflow_access_token=access-token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));
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

    @Test
    @DisplayName("데모 로그인 성공 시 200 ApiResponse와 access token, 프로젝트 id를 반환한다")
    void demoLogin() throws Exception {
        given(authService.demoLogin())
                .willReturn(DemoLoginResponse.bearer("demo-access-token", 3600000L, 2L));

        mockMvc.perform(post("/auth/demo-login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value("demo-access-token"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600000))
                .andExpect(jsonPath("$.data.userProjectId").value(2))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("jobflow_access_token=demo-access-token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));
    }

    @Test
    @DisplayName("OAuth2 인증 코드 교환 성공 시 200 ApiResponse와 access token을 반환한다")
    void exchangeOAuth2Code() throws Exception {
        String requestBody = """
            {
              "code": "oauth2-code"
            }
            """;

        given(authService.exchangeOAuth2Code(any()))
                .willReturn(LoginResponse.bearer("oauth-access-token", 3600000L, 2L));

        mockMvc.perform(post("/auth/oauth2/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value("oauth-access-token"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600000))
                .andExpect(jsonPath("$.data.userProjectId").value(2))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("jobflow_access_token=oauth-access-token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));
    }

    @Test
    @DisplayName("로그아웃 성공 시 access token 쿠키를 삭제한다")
    void logout() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("jobflow_access_token=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")));
    }

    @Test
    @DisplayName("내 정보 조회 시 사용자 정보와 최신 분석 프로젝트 id를 반환한다")
    void me() {
        UserPrincipal principal = new UserPrincipal(
                4L,
                "test@example.com",
                "Test User",
                "USER"
        );

        given(authService.findLatestProjectId(principal.id())).willReturn(2L);

        AuthController controller = new AuthController(authService, mock(JwtCookieService.class));

        ResponseEntity<?> response = controller.me(principal);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).extracting("success").isEqualTo(true);
        assertThat(response.getBody()).extracting("data.id").isEqualTo(4L);
        assertThat(response.getBody()).extracting("data.email").isEqualTo("test@example.com");
        assertThat(response.getBody()).extracting("data.name").isEqualTo("Test User");
        assertThat(response.getBody()).extracting("data.role").isEqualTo("USER");
        assertThat(response.getBody()).extracting("data.userProjectId").isEqualTo(2L);
    }

    @Test
    @DisplayName("OAuth2 인증 코드 교환 요청 validation 실패 시 400 ErrorResponse를 반환한다")
    void exchangeOAuth2CodeValidationFail() throws Exception {
        String requestBody = """
            {
              "code": ""
            }
            """;

        mockMvc.perform(post("/auth/oauth2/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fields", hasSize(1)));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("OAuth2 인증 코드가 유효하지 않으면 401 ErrorResponse를 반환한다")
    void exchangeOAuth2CodeInvalidCode() throws Exception {
        String requestBody = """
            {
              "code": "invalid-code"
            }
            """;

        willThrow(new BusinessException(ErrorCode.AUTH_OAUTH2_CODE_INVALID))
                .given(authService)
                .exchangeOAuth2Code(any());

        mockMvc.perform(post("/auth/oauth2/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_OAUTH2_CODE_INVALID"))
                .andExpect(jsonPath("$.error.message").value("유효하지 않은 OAuth2 인증 코드입니다."));
    }
}
