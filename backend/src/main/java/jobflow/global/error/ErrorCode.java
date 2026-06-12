package jobflow.global.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    COMMON_INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_INVALID_INPUT", "입력값이 올바르지 않습니다."),
    COMMON_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_INVALID_REQUEST", "요청 형식이 올바르지 않습니다."),
    COMMON_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_UNAUTHORIZED", "인증이 필요합니다."),
    COMMON_FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_FORBIDDEN", "접근 권한이 없습니다."),
    COMMON_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
    COMMON_CONFLICT(HttpStatus.CONFLICT, "COMMON_CONFLICT", "요청이 현재 리소스 상태와 충돌합니다."),
    COMMON_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."),

    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."),
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_TOKEN", "유효하지 않은 인증 토큰입니다."),
    AUTH_EMAIL_ALREADY_USED(HttpStatus.CONFLICT, "AUTH_EMAIL_ALREADY_USED", "이미 다른 로그인 방식으로 가입된 이메일입니다."),
    AUTH_OAUTH2_PROVIDER_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "AUTH_OAUTH2_PROVIDER_NOT_SUPPORTED", "지원하지 않는 소셜 로그인 제공자입니다."),
    AUTH_OAUTH2_EMAIL_NOT_FOUND(HttpStatus.BAD_REQUEST, "AUTH_OAUTH2_EMAIL_NOT_FOUND", "소셜 계정에서 이메일 정보를 찾을 수 없습니다."),
    AUTH_OAUTH2_CODE_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_OAUTH2_CODE_INVALID", "유효하지 않은 OAuth2 인증 코드입니다."),
    AUTH_OAUTH2_PROVIDER_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_OAUTH2_PROVIDER_TOKEN_INVALID", "유효하지 않은 OAuth2 provider token입니다."),
    AUTH_OAUTH2_PROVIDER_TOKEN_KEY_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH_OAUTH2_PROVIDER_TOKEN_KEY_MISSING", "OAuth2 provider token 암호화 키가 설정되지 않았습니다."),
    AUTH_OAUTH2_PROVIDER_TOKEN_CRYPTO_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH_OAUTH2_PROVIDER_TOKEN_CRYPTO_FAILED", "OAuth2 provider token 암호화 처리에 실패했습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    USER_PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_PROJECT_NOT_FOUND", "사용자 프로젝트를 찾을 수 없습니다."),
    USER_JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_JOB_NOT_FOUND", "사용자 공고 상태를 찾을 수 없습니다."),
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "공고를 찾을 수 없습니다."),
    JOB_STATUS_CONFLICT(HttpStatus.CONFLICT, "JOB_STATUS_CONFLICT", "공고 상태를 변경할 수 없습니다."),
    SKILL_NOT_FOUND(HttpStatus.NOT_FOUND, "SKILL_NOT_FOUND", "스킬을 찾을 수 없습니다."),
    EXPERIENCE_TAG_NOT_FOUND(HttpStatus.NOT_FOUND, "EXPERIENCE_TAG_NOT_FOUND", "경험 태그를 찾을 수 없습니다."),
    SKILL_ALREADY_EXISTS(HttpStatus.CONFLICT, "SKILL_ALREADY_EXISTS", "이미 등록된 스킬입니다."),

    USER_EMAIL_DUPLICATED(HttpStatus.CONFLICT, "USER_EMAIL_DUPLICATED", "이미 사용 중인 이메일입니다."),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "지원 상태를 찾을 수 없습니다."),
    APPLICATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "APPLICATION_ALREADY_EXISTS", "이미 지원한 공고입니다."),
    APPLICATION_STATUS_CONFLICT(HttpStatus.CONFLICT, "APPLICATION_STATUS_CONFLICT", "지원 상태를 변경할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
