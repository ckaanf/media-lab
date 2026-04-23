package ckaanf.controlplane.streaming.common.exception;

import lombok.Getter;

@Getter
public class StreamingDomainException extends RuntimeException {
    private final StreamingErrorCode errorCode;

    public StreamingDomainException(StreamingErrorCode errorCode , Object... args) {
        super(String.format(errorCode.getMessage(), args));
        this.errorCode = errorCode;
    }
}
