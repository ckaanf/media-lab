package ckaanf.controlplane.streaming.live.domain.vo;

import ckaanf.controlplane.streaming.live.domain.constant.StopReason;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;

@Embeddable
@Getter
@lombok.NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class StreamingResult {

    @Enumerated(EnumType.STRING)
    private StopReason stopReason;
    private String errorMessage;

    private StreamingResult(StopReason stopReason, String errorMessage) {
        this.stopReason = stopReason;
        this.errorMessage = errorMessage;
    }

    public static StreamingResult empty() {
        return new StreamingResult(null, null);
    }

    public static StreamingResult of(StopReason stopReason, String errorMessage) {
        return new StreamingResult(stopReason, errorMessage);
    }

    public boolean hasError() {
        return this.errorMessage != null && !this.errorMessage.isBlank();
    }
}
