package ckaanf.controlplane.streaming.live.domain.vo;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StreamingTimeline {

    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime connectedAt;
    private LocalDateTime endedAt;

    private StreamingTimeline(LocalDateTime createdAt, LocalDateTime startedAt,
                              LocalDateTime connectedAt, LocalDateTime endedAt) {
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.connectedAt = connectedAt;
        this.endedAt = endedAt;
    }

    public static StreamingTimeline init() {
        return new StreamingTimeline(LocalDateTime.now(), null, null, null);
    }

    public StreamingTimeline withStarted() {
        return new StreamingTimeline(this.createdAt, LocalDateTime.now(), this.connectedAt, this.endedAt);
    }

    public StreamingTimeline withConnected() {
        return new StreamingTimeline(this.createdAt, this.startedAt, LocalDateTime.now(), this.endedAt);
    }

    public StreamingTimeline withEnded() {
        return new StreamingTimeline(this.createdAt, this.startedAt, this.connectedAt, LocalDateTime.now());
    }

    public Long getStartupLatencyMillis() {
        if (startedAt == null || connectedAt == null) return null;
        return Duration.between(startedAt, connectedAt).toMillis();
    }
}