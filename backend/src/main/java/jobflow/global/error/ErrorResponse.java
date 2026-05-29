package jobflow.global.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        boolean success,
        ErrorBody error
) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(false, ErrorBody.of(errorCode));
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(false, ErrorBody.of(errorCode, message));
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldErrorResponse> fields) {
        return new ErrorResponse(false, ErrorBody.of(errorCode, fields));
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
