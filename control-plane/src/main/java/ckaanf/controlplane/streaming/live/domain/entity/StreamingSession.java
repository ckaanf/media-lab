package ckaanf.controlplane.streaming.live.domain.entity;

import ckaanf.controlplane.streaming.live.domain.constant.StopReason;
import ckaanf.controlplane.streaming.live.domain.constant.StreamingStatus;
import ckaanf.controlplane.streaming.live.domain.vo.StreamingProcess;
import ckaanf.controlplane.streaming.live.domain.vo.StreamingResult;
import ckaanf.controlplane.streaming.live.domain.vo.StreamingTimeline;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "streaming_sessions")
public class StreamingSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String streamKey;

    @Enumerated(EnumType.STRING)
    private StreamingStatus status;

    @Embedded
    private StreamingProcess process;

    @Embedded
    private StreamingTimeline lifecycle;

    @Embedded
    private StreamingResult result;


    @Builder
    public StreamingSession(String streamKey, Long pid, String inputUrl, String outputPath) {
        this.streamKey = streamKey;
        this.status = StreamingStatus.INIT;

        this.process = StreamingProcess.init(inputUrl, outputPath);
        this.lifecycle = StreamingTimeline.init();
        this.result = StreamingResult.empty();
    }


    public void start(Long pid) {
        this.status.canTransitionTo(StreamingStatus.STARTING);
        this.status = StreamingStatus.STARTING;
        this.process = this.process.withPid(pid);
        this.lifecycle = this.lifecycle.withStarted();
    }

    public void connect() {
        this.status.canTransitionTo(StreamingStatus.STREAMING);
        this.status = StreamingStatus.STREAMING;
        this.lifecycle = this.lifecycle.withConnected();
    }

    public void stop(StopReason reason) {
        this.status.canTransitionTo(StreamingStatus.STOPPED);
        this.status = StreamingStatus.STOPPED;
        this.lifecycle = this.lifecycle.withEnded();
        this.process = this.process.clearPid();
        this.result = StreamingResult.of(reason, null);
    }

    public void fail(StopReason reason, String message) {
        this.status.canTransitionTo(StreamingStatus.FAILED);
        this.status = StreamingStatus.FAILED;

        this.lifecycle = this.lifecycle.withEnded();
        this.process = this.process.clearPid();
        this.result = StreamingResult.of(reason, message);
    }


}
