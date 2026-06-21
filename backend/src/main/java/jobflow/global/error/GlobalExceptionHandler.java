package jobflow.global.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import jobflow.global.error.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, exception.getMessage(), request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<FieldErrorResponse> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldErrorResponse)
                .toList();

        return ResponseEntity
                .status(ErrorCode.COMMON_INVALID_INPUT.getStatus())
                .body(ErrorResponse.of(ErrorCode.COMMON_INVALID_INPUT, fieldErrors, request));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(
            BindException exception,
            HttpServletRequest request
    ) {
        List<FieldErrorResponse> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldErrorResponse)
                .toList();

        return ResponseEntity
                .status(ErrorCode.COMMON_INVALID_INPUT.getStatus())
                .body(ErrorResponse.of(ErrorCode.COMMON_INVALID_INPUT, fieldErrors, request));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(ErrorCode.COMMON_INVALID_REQUEST.getStatus())
                .body(ErrorResponse.of(ErrorCode.COMMON_INVALID_REQUEST, request));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(ErrorCode.COMMON_INVALID_INPUT.getStatus())
                .body(ErrorResponse.of(
                        ErrorCode.COMMON_INVALID_INPUT,
                        "요청 파라미터 타입이 올바르지 않습니다.",
                        request
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error("Unhandled exception occurred.", exception);

        return ResponseEntity
                .status(ErrorCode.COMMON_INTERNAL_ERROR.getStatus())
                .body(ErrorResponse.of(ErrorCode.COMMON_INTERNAL_ERROR, request));
    }

    private FieldErrorResponse toFieldErrorResponse(FieldError fieldError) {
        return FieldErrorResponse.of(
                fieldError.getField(),
                fieldError.getDefaultMessage(),
                fieldError.getRejectedValue()
        );
    }
}
