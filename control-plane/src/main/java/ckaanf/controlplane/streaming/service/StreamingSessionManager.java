package ckaanf.controlplane.streaming.service;

import ckaanf.controlplane.streaming.constant.StopReason;
import ckaanf.controlplane.streaming.constant.StreamingStatus;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class StreamingSessionManager {
    private final AtomicReference<Long> sessionSequence = new AtomicReference<>(0L);
    private final AtomicReference<Long> activeSessionId = new AtomicReference<>(0L);
    private final AtomicReference<StreamingStatus> streamingStatus = new AtomicReference<>(StreamingStatus.INIT);
    private final AtomicReference<StopReason> lastStopReason = new AtomicReference<>(StopReason.NONE);
    private final AtomicReference<Boolean> stopRequested = new AtomicReference<>(false);
    private final AtomicReference<Double> currentEncodingSpeed = new AtomicReference<>(null);

    @Getter
    private final AtomicReference<StreamingSession> currentSession = new AtomicReference<>(null);

    public record StreamingSession(
            long sessionId,
            String name,
            Path workDir,
            Process process
    ) {}

    public long nextSessionId() {
        return sessionSequence.updateAndGet(seq -> seq + 1);
    }

    public void startNewSession(long sessionId, String name, Path workDir, Process process) {
        activeSessionId.set(sessionId);
        currentSession.set(new StreamingSession(sessionId, name, workDir, process));
        streamingStatus.set(StreamingStatus.STREAMING);
        stopRequested.set(false);
        lastStopReason.set(StopReason.NONE);
        currentEncodingSpeed.set(null);
    }

    public boolean isCurrentSession(long sessionId, Process process) {
        StreamingSession session = currentSession.get();
        return session != null && session.sessionId() == sessionId && session.process() == process;
    }

    public void setStatus(StreamingStatus status) {
        streamingStatus.set(status);
    }

    public StreamingStatus getStatus() {
        return streamingStatus.get();
    }

    public void setStopReason(StopReason reason) {
        lastStopReason.set(reason);
    }

    public StopReason getStopReason() {
        return lastStopReason.get();
    }

    public void setStopRequested(boolean requested) {
        stopRequested.set(requested);
    }

    public boolean isStopRequested() {
        return stopRequested.get();
    }

    public void updateEncodingSpeed(double speed) {
        currentEncodingSpeed.set(speed);
    }

    public Double getEncodingSpeed() {
        return currentEncodingSpeed.get();
    }

    public void clearEncodingSpeed() {
        currentEncodingSpeed.set(null);
    }

    public long getActiveSessionId() {
        return activeSessionId.get();
    }
}
