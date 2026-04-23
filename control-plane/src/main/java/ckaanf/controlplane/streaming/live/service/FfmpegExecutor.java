package ckaanf.controlplane.streaming.live.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class FfmpegExecutor {

    public Process startProcess(List<String> command) throws IOException {
        log.info("Starting FFmpeg with command: {}", String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        return pb.start();
    }

    public void stopProcess(Process process, long timeoutSeconds) {
        if (process == null || !process.isAlive()) {
            return;
        }

        log.info("Stopping FFmpeg process (pid: {})", process.pid());
        process.destroy();
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                log.warn("FFmpeg process did not stop within {} seconds, forcing destruction", timeoutSeconds);
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
