package com.cai.trading.api;

import com.cai.trading.api.dto.ErrorResponse;
import com.cai.trading.domain.DomainException;
import com.cai.trading.domain.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(DomainException exception, HttpServletRequest request) {
        HttpStatus status = switch (exception.code()) {
            case ORDER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DUPLICATE_ORDER, DUPLICATE_REQUEST_CONFLICT, ORDER_NOT_CANCELABLE -> HttpStatus.CONFLICT;
            case INSUFFICIENT_BALANCE, INSUFFICIENT_POSITION -> HttpStatus.UNPROCESSABLE_ENTITY;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(new ErrorResponse(
                exception.code(), exception.getMessage(), correlationId(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception,
                                                           HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst().orElse("Invalid request");
        return ResponseEntity.badRequest().body(new ErrorResponse(
                ErrorCode.INVALID_ORDER, message, correlationId(request)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException exception,
                                                           HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                ErrorCode.INVALID_ORDER, exception.getMessage(), correlationId(request)));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception exception,
                                                                 HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                ErrorCode.INVALID_ORDER, "Request contains an invalid value or identifier",
                correlationId(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        // 生产环境应在这里记录完整异常；响应中不泄露堆栈、SQL 或内部类名。
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(ErrorCode.INTERNAL_ERROR, "Unexpected server error",
                        correlationId(request)));
    }

    private static String correlationId(HttpServletRequest request) {
        String provided = request.getHeader("X-Correlation-Id");
        return provided == null || provided.isBlank()
                ? java.util.UUID.randomUUID().toString()
                : provided;
    }
}

