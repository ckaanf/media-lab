package ckaanf.controlplane.streaming.common.exception;

import lombok.Getter;

@Getter
public class StreamingDomainException extends RuntimeException {
    private final StreamingErrorCode errorCode;
    private final Object[] args;

    public StreamingDomainException(StreamingErrorCode errorCode , Object... args) {
        super(errorCode.getCode());
        this.errorCode = errorCode;
        this.args = args;
    }

    @Override
    public String getMessage() {
        if (args == null || args.length == 0) {
            return errorCode.getMessage();
        }
        return String.format(errorCode.getMessage(), args);
    }
}
