package ckaanf.controlplane.streaming.live.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Embeddable
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@EqualsAndHashCode
public class StreamingProcess {
    private Long pid;
    private LocalDateTime startedAt;

    @Column(columnDefinition = "TEXT")
    private String ffmpegCommand;

    private StreamingProcess(Long pid, LocalDateTime startedAt, String ffmpegCommand) {
        this.pid = pid;
        this.startedAt = startedAt;
        this.ffmpegCommand = ffmpegCommand;
    }


    public static StreamingProcess init(Long pid, LocalDateTime startedAt, String ffmpegCommand) {
        return new StreamingProcess(pid, startedAt, ffmpegCommand);
    }

    public static StreamingProcess empty() {
        return new StreamingProcess(null, null, null);
    }

    public static StreamingProcess start(Long pid, String ffmpegCommand) {
        return new StreamingProcess(pid, LocalDateTime.now(), ffmpegCommand);
    }

    public StreamingProcess withPid(Long pid) {
        return new StreamingProcess(pid, this.startedAt, this.ffmpegCommand);
    }

    public StreamingProcess clearPid() {
        return new StreamingProcess(null, this.startedAt, this.ffmpegCommand);
    }

    public boolean isRunning() {
        return this.pid != null;
    }
}
