package ckaanf.controlplane.streaming.common.event;

public record StreamEndedEvent(
        Long sessionId,
        String streamId,
        String fileName,
        String sourceDirectory
) {
}
