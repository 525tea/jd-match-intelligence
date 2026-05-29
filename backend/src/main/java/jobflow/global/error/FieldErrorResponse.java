package jobflow.global.error;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldErrorResponse(
        String field,
        String message,
        Object rejectedValue
) {

    public static FieldErrorResponse of(String field, String message, Object rejectedValue) {
        return new FieldErrorResponse(field, message, rejectedValue);
    }
}
