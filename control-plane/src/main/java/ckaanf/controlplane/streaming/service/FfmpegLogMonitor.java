package ckaanf.controlplane.streaming.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class FfmpegLogMonitor {
    private static final Pattern SPEED_PATTERN = Pattern.compile("speed=\\s*([0-9.]+)x");
    private final StreamingSessionManager sessionManager;

    public void monitor(Process process, long sessionId) {
        CompletableFuture.runAsync(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!sessionManager.isCurrentSession(sessionId, process)) {
                        return;
                    }

                    log.info("[FFmpeg Raw] {}", line);
                    parseSpeed(line, sessionId, process);
                }
            } catch (Exception e) {
                log.error("Error reading FFmpeg logs", e);
            }
        });
    }

    private void parseSpeed(String line, long sessionId, Process process) {
        Matcher matcher = SPEED_PATTERN.matcher(line);
        if (matcher.find()) {
            String speedStr = matcher.group(1);
            try {
                double speed = Double.parseDouble(speedStr);
                if (sessionManager.isCurrentSession(sessionId, process)) {
                    sessionManager.updateEncodingSpeed(speed);
                }

                if (speed < 1.0) {
                    log.warn("Encoding load detected! Current speed: {}x", speed);
                }
            } catch (NumberFormatException e) {
                log.error("Failed to parse encoding speed: {}", speedStr, e);
            }
        }
    }
}
