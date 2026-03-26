package ckaanf.controlplane.service;

import ckaanf.controlplane.domain.Streaming.StopReason;
import ckaanf.controlplane.domain.Streaming.StreamingStatus;
import ckaanf.controlplane.domain.Streaming.response.StreamingInfo;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class FfmpegService {
    private static final Pattern SPEED_PATTERN = Pattern.compile("speed=\\s*([0-9.]+)x");
    private final AtomicReference<Double> currentEncodingSpeed = new AtomicReference<>(1.0);

    private Process ffmpegProcess;
    private final AtomicReference<StreamingStatus> streamingStatus = new AtomicReference<>(StreamingStatus.IDLE);
    private final AtomicReference<StopReason> lastStopReason = new AtomicReference<>(StopReason.NONE);
    private final AtomicReference<Boolean> stopRequested = new AtomicReference<>(false);


    public synchronized void startStreaming(String fileName) {
        if (streamingStatus.get() == StreamingStatus.STREAMING) {
            log.warn("이미 방송이 송출 중입니다.");
            return;
        }

        stopRequested.set(false);
        lastStopReason.set(StopReason.NONE);
        List<String> command = buildAbridgedCommand(fileName);

        try {
            this.ffmpegProcess = startProcess(command);
            streamingStatus.set(StreamingStatus.STREAMING);
            this.ffmpegProcess.onExit().thenAccept(p -> {
                int exitCode = p.exitValue();

                if (stopRequested.get()) {
                    log.info("방송이 사용자 요청으로 정상적으로 중단되었습니다. ExitCode: {}", exitCode);
                    streamingStatus.set(StreamingStatus.IDLE);
                    return;
                }

                if (exitCode == 0) {
                    log.info("방송이 정상적으로 종료되었습니다.");
                    lastStopReason.set(StopReason.PROCESS_EXIT);
                    streamingStatus.set(StreamingStatus.IDLE);
                } else {
                    log.error("FFmpeg가 비정상 종료되었습니다. ExitCode: {}", exitCode);
                    lastStopReason.set(StopReason.PROCESS_EXIT);
                    streamingStatus.set(StreamingStatus.ERROR);
                    // 여기서 필요시 자동 재시작 로직 호출 가능
                }
            });

            readLogs(ffmpegProcess);

        } catch (IOException e) {
            log.error("FFmpeg 실행 중 에러 발생", e);
            streamingStatus.set(StreamingStatus.ERROR);
        }
    }

    protected Process startProcess(List<String> command) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private List<String> buildAbridgedCommand(String fileName) {
        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-re");
        command.add("-i");
        command.add("/app/videos/" + fileName); // 컨테이너 내부 경로

        command.add("-filter_complex");
        command.add("[0:v]split=3[v1][v2][v3];[v1]scale=w=1920:h=1080[v1out];[v2]scale=w=1280:h=720[v2out];[v3]scale=w=854:h=480[v3out];[0:a]asplit=3[a1][a2][a3]");

        command.addAll(List.of("-map", "[v1out]", "-c:v:0", "libx264", "-preset", "ultrafast", "-b:v:0", "5000k"));
        command.addAll(List.of("-map", "[v2out]", "-c:v:1", "libx264", "-preset", "ultrafast", "-b:v:1", "2500k"));
        command.addAll(List.of("-map", "[v3out]", "-c:v:2", "libx264", "-preset", "ultrafast", "-b:v:2", "1000k"));

        command.addAll(List.of("-map", "[a1]", "-c:a:0", "aac", "-ac", "2", "-ar", "44100", "-b:a:0", "128k"));
        command.addAll(List.of("-map", "[a2]", "-c:a:1", "aac", "-ac", "2", "-ar", "44100", "-b:a:1", "128k"));
        command.addAll(List.of("-map", "[a3]", "-c:a:2", "aac", "-ac", "2", "-ar", "44100", "-b:a:2", "128k"));

        command.addAll(List.of(
                "-f", "hls",
                "-hls_time", "2",
                "-hls_list_size", "6",
                "-hls_flags", "delete_segments",
                "-master_pl_name", "master.m3u8",
                "-var_stream_map", "v:0,a:0,name:1080 v:1,a:1,name:720 v:2,a:2,name:480",
                "/tmp/hls/%v/index.m3u8"
        ));
        return command;
    }

    public void stopStreaming() {
        log.info("이미 종료된 방송입니다");
        if (ffmpegProcess != null && ffmpegProcess.isAlive()) {
            stopRequested.set(true);
            streamingStatus.set(StreamingStatus.STOPPED);
            lastStopReason.set(StopReason.USER_STOP);
            ffmpegProcess.destroy();
            log.info("사용자 요청으로 방송 중지");
        }
    }

    public StreamingStatus getStreamingStatus() {
        return streamingStatus.get();
    }

    public StopReason getLastStopReason() {
        return lastStopReason.get();
    }

    public StreamingInfo getStreamingInfo() {
        return new StreamingInfo(streamingStatus.get(), lastStopReason.get());
    }

    @PreDestroy
    public void destroy() {
        if (ffmpegProcess != null && ffmpegProcess.isAlive()) {
            log.info("서버 종료로 인한 FFmpeg 강제 회수 중...");
            stopRequested.set(true);
            ffmpegProcess.destroy(); // 기본 종료 시도
            try {
                if (!ffmpegProcess.waitFor(5, TimeUnit.SECONDS)) {
                    ffmpegProcess.destroyForcibly(); // 안 죽으면 강제 사살
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void readLogs(Process process) {
        CompletableFuture.runAsync(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher matcher = SPEED_PATTERN.matcher(line);

                    if (matcher.find()) {
                        String speedStr = matcher.group(1);
                        double speed = Double.parseDouble(speedStr);

                        currentEncodingSpeed.set(speed);

                        // 4. 부하 감지 (1.0 미만으로 떨어지면 경고)
                        if (speed < 1.0) {
                            log.warn("인코딩 부하 감지! 현재 속도: {}x", speed);
                            // TODO: 여기서 이벤트를 발행하거나 화질 낮추기 로직을 호출할 수 있습니다.
                        }
                    }
                }
            } catch (Exception e) {
                log.error("FFmpeg 로그 읽기 중 오류 발생", e);
            }
        });
    }
}
