package ckaanf.controlplane.streaming.vo;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class StreamingProcess {
    private Long pid;

    private String inputUrl;
    private String outputPath;

    private StreamingProcess(Long pid, String inputUrl, String outputPath) {
        this.pid = pid;
        this.inputUrl = inputUrl;
        this.outputPath = outputPath;
    }

    // 초기 생성 시 PID 없음
    public static StreamingProcess init(String inputUrl, String outputPath) {
        return new StreamingProcess(null, inputUrl, outputPath);
    }

    public StreamingProcess withPid(Long pid) {
        return new StreamingProcess(pid, this.inputUrl, this.outputPath);
    }

    public StreamingProcess clearPid() {
        return new StreamingProcess(null, this.inputUrl, this.outputPath);
    }

    public boolean isRunning() {
        return this.pid != null;
    }
}
