package ckaanf.controlplane.streaming.live.domain.entity;

import ckaanf.controlplane.streaming.common.exception.StreamingDomainException;
import ckaanf.controlplane.streaming.common.exception.StreamingErrorCode;
import ckaanf.controlplane.streaming.live.domain.constant.StopReason;
import ckaanf.controlplane.streaming.live.domain.constant.StreamingStatus;
import ckaanf.controlplane.streaming.live.domain.service.ProcessChecker;
import ckaanf.controlplane.streaming.live.domain.vo.StreamingProcess;
import ckaanf.controlplane.streaming.live.domain.vo.StreamingResult;
import ckaanf.controlplane.streaming.live.domain.vo.StreamingTask;
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
    private StreamingTask task;

    @Embedded
    private StreamingProcess process;

    @Embedded
    private StreamingTimeline lifecycle;

    @Embedded
    private StreamingResult result;


    @Builder
    public StreamingSession(String streamKey, Long pid, String inputUrl, String outputPath, String resolution,
                            Integer bitrate) {
        this.streamKey = streamKey;
        this.status = StreamingStatus.INIT;

        this.task = StreamingTask.builder()
                .inputUrl(inputUrl)
                .outputPath(outputPath)
                .resolution(resolution)
                .bitrate(bitrate)
                .build();

        this.process = StreamingProcess.empty();
        this.lifecycle = StreamingTimeline.init();
        this.result = StreamingResult.empty();
    }


    public void start(Long pid, String ffmpegCommand) {
        this.status.canTransitionTo(StreamingStatus.STARTING);
        this.status = StreamingStatus.STARTING;

        this.process = StreamingProcess.start(pid, ffmpegCommand);
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

    public void validateProcessState(ProcessChecker checker) {
        if (this.status == StreamingStatus.STREAMING) {
            if (this.process == null || !checker.isAlive(this.process.getPid())) {
                this.status = StreamingStatus.FAILED;
                throw new StreamingDomainException(
                        StreamingErrorCode.PROCESS_ORPHANED,
                        this.process != null ? this.process.getPid() : "N/A"
                );
            }
        }
    }

}
