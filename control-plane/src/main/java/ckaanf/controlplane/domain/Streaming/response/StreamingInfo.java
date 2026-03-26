package ckaanf.controlplane.domain.Streaming.response;

import ckaanf.controlplane.domain.Streaming.StopReason;
import ckaanf.controlplane.domain.Streaming.StreamingStatus;

public record StreamingInfo(
        StreamingStatus status,
        StopReason stopReason
) {
}
