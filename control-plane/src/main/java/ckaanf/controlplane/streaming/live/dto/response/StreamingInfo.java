package ckaanf.controlplane.streaming.live.dto.response;

import ckaanf.controlplane.streaming.live.domain.constant.StopReason;
import ckaanf.controlplane.streaming.live.domain.constant.StreamingStatus;

public record StreamingInfo(
        StreamingStatus status,
        StopReason stopReason
) {
}
