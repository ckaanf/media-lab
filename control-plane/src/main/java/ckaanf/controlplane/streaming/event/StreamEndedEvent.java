package ckaanf.controlplane.streaming.event;

public record StreamEndedEvent(
        Long sessionId,
        String streamId,
        String fileName,
        String sourceDirectory
) {
}
