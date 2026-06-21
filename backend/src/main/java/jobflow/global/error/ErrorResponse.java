package jobflow.global.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        boolean success,
        ErrorBody error,
        Instant timestamp,
        String path,
        String traceId
) {

    private static final String TRACE_ID_KEY = "traceId";

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(false, ErrorBody.of(errorCode), null, null, null);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(false, ErrorBody.of(errorCode, message), null, null, null);
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldErrorResponse> fields) {
        return new ErrorResponse(false, ErrorBody.of(errorCode, fields), null, null, null);
    }

    public static ErrorResponse of(ErrorCode errorCode, HttpServletRequest request) {
        return withMetadata(ErrorBody.of(errorCode), request);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, HttpServletRequest request) {
        return withMetadata(ErrorBody.of(errorCode, message), request);
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldErrorResponse> fields, HttpServletRequest request) {
        return withMetadata(ErrorBody.of(errorCode, fields), request);
    }

    private static ErrorResponse withMetadata(ErrorBody errorBody, HttpServletRequest request) {
        return new ErrorResponse(
                false,
                errorBody,
                Instant.now(),
                request.getRequestURI(),
                currentTraceId()
        );
    }

    private static String currentTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);

        if (!StringUtils.hasText(traceId)) {
            return null;
        }

        return traceId;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorBody(
            String code,
            String message,
            List<FieldErrorResponse> fields
    ) {

        public static ErrorBody of(ErrorCode errorCode) {
            return new ErrorBody(errorCode.getCode(), errorCode.getMessage(), null);
        }

        public static ErrorBody of(ErrorCode errorCode, String message) {
            return new ErrorBody(errorCode.getCode(), message, null);
        }

        public static ErrorBody of(ErrorCode errorCode, List<FieldErrorResponse> fields) {
            return new ErrorBody(errorCode.getCode(), errorCode.getMessage(), fields);
        }
    }
}
