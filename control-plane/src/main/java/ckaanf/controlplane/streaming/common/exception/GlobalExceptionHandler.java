package ckaanf.controlplane.streaming.common.exception;

import ckaanf.controlplane.streaming.common.exception.StreamingDomainException;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(StreamingDomainException.class)
    public ResponseEntity<ErrorResponse> handleStreamingDomainException(StreamingDomainException e) {
        // 정책: S(Session) 에러는 400(Bad Request), P(Process) 에러는 500(Internal Server Error)
        HttpStatus status = e.getErrorCode().getCode().startsWith("S")
                ? HttpStatus.BAD_REQUEST
                : HttpStatus.INTERNAL_SERVER_ERROR;

        return ResponseEntity.status(status)
                .body(ErrorResponse.builder()
                        .code(e.getErrorCode().getCode())
                        .message(e.getMessage())
                        .build());
    }

    @Getter
    @Builder
    public static class ErrorResponse {
        private final String code;
        private final String message;
    }
}
