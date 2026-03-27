package ckaanf.controlplane.streaming.response;

import ckaanf.controlplane.streaming.constant.StopReason;
import ckaanf.controlplane.streaming.constant.StreamingStatus;

public record StreamingInfo(
        StreamingStatus status,
        StopReason stopReason
) {
}
