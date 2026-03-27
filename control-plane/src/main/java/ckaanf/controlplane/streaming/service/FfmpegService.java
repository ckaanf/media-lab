package ckaanf.controlplane.streaming.service;

import ckaanf.controlplane.streaming.constant.StopReason;
import ckaanf.controlplane.streaming.constant.StreamingStatus;
import ckaanf.controlplane.streaming.event.StreamEndedEvent;
import ckaanf.controlplane.streaming.response.StreamingInfo;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class FfmpegService {
    private static final Pattern SPEED_PATTERN = Pattern.compile("speed=\\s*([0-9.]+)x");
    private final AtomicReference<Double> currentEncodingSpeed = new AtomicReference<>(null);
    private final AtomicReference<Long> activeSessionId = new AtomicReference<>(0L);
    private final AtomicReference<Long> sessionSequence = new AtomicReference<>(0L);

    private record VideoContext(
            long sessionId,
            String fileName,
            java.nio.file.Path workDir
    ) {
    }

    private record StreamingContext(
            long sessionId,
            String streamKey,
            java.nio.file.Path workDir
    ) {
    }

    private final AtomicReference<StreamingContext> currentContext = new AtomicReference<>(null);
    private Process ffmpegProcess;
    private final AtomicReference<StreamingStatus> streamingStatus = new AtomicReference<>(StreamingStatus.IDLE);
    private final AtomicReference<StopReason> lastStopReason = new AtomicReference<>(StopReason.NONE);
    private final AtomicReference<Boolean> stopRequested = new AtomicReference<>(false);

    private final ApplicationEventPublisher eventPublisher;

    public void startStreaming(String streamKey) {
        CompletableFuture.runAsync(() -> {
            if (!waitForStreamReady(streamKey)) {
                log.error("스트림 준비 시간 초과: {}", streamKey);
                return;
            }

            log.info("라이브 스트리밍 시작 OBS: {}", streamKey);
            executeStreamingFfmpeg(streamKey);
        });
    }

    public void startFileStreaming(String fileName) {
        CompletableFuture.runAsync(() -> {
            java.nio.file.Path filePath = java.nio.file.Paths.get("/app/videos/", fileName);
            if (!java.nio.file.Files.exists(filePath)) {
                log.error("파일을 찾을 수 없습니다: {}", filePath);
                return;
            }

            log.info("파일 기반 스트리밍 시작: {}", fileName);
            executeFileFfmpeg(fileName);
        });
    }

    private boolean waitForStreamReady(String streamKey) {
        String inputUrl = "rtmp://rtmp-server:1935/live/" + streamKey; // 도커 컴포즈 서비스명 확인 필수
        int maxRetries = 20;

        for (int i = 0; i < maxRetries; i++) {
            try {
                log.info("스트림 확인 중... (시도 {}/{}) URL: {}", i + 1, maxRetries, inputUrl);

                Process probe = new ProcessBuilder(
                        "ffprobe", 
                        "-v", "error", 
                        "-i", inputUrl,
                        "-show_streams",
                        "-select_streams", "v:0" // 비디오 스트림이 있는지 명시적으로 확인
                ).start();

                // ffprobe가 무한 대기하는 것을 막기 위해 최대 5초까지 기다림
                boolean finished = probe.waitFor(5, TimeUnit.SECONDS);

                if (finished) {
                    int exitCode = probe.exitValue();
                    if (exitCode == 0) {
                        log.info("✅ 스트림 준비 완료 확인 (비디오 스트림 포함)!");
                        return true;
                    } else {
                        log.warn("❌ ffprobe 실패 (종료 코드: {}) - 스트림이 아직 안 열렸거나 비디오가 없을 수 있음", exitCode);
                    }
                } else {
                    log.warn("⚠️ ffprobe 응답 지연 (Timeout) - 프로세스 강제 종료");
                    probe.destroyForcibly();
                }

                Thread.sleep(500); // 대기 시간을 조금 줄여서 더 자주 확인

            } catch (Exception e) {
                log.error("🚨 ffprobe 실행 중 치명적 예외 발생: {}", e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return false;
    }


    public synchronized void executeStreamingFfmpeg(String streamKey) {
        if (streamingStatus.get() == StreamingStatus.STREAMING) {
            log.warn("이미 방송이 송출 중입니다.");
            return;
        }

        long sessionId = sessionSequence.updateAndGet(seq -> seq + 1);
        activeSessionId.set(sessionId);
        currentEncodingSpeed.set(null);

        Path outputDirectory = Paths.get("/tmp/hls", streamKey);
        try {
            Files.createDirectories(outputDirectory);
            log.info("HLS 출력 디렉토리 생성 완료: {}", outputDirectory);
        } catch (Exception e) {
            log.error("디렉토리 생성 실패", e);
        }

        StreamingContext context = new StreamingContext(sessionId, streamKey, outputDirectory);
        currentContext.set(context);

        stopRequested.set(false);
        lastStopReason.set(StopReason.NONE);
        List<String> command = buildAbridgedCommand(context);

        try {
            this.ffmpegProcess = startProcess(command);
            streamingStatus.set(StreamingStatus.STREAMING);
            Process process = this.ffmpegProcess;
            process.onExit().thenAccept(p -> {
                if (!isCurrentSession(sessionId, p)) {
                    return;
                }

                int exitCode = p.exitValue();

                if (exitCode == 0 || stopRequested.get()) {
                    if (stopRequested.get()) {
                        log.info("방송이 사용자 요청으로 정상적으로 중단되었습니다. ExitCode: {}", exitCode);
                    } else {
                        log.info("방송이 정상적으로 종료되었습니다.");
                        lastStopReason.set(StopReason.PROCESS_EXIT);
                    }
                    streamingStatus.set(StreamingStatus.IDLE);

                    String streamId = "stream-" + context.sessionId();
                    eventPublisher.publishEvent(new StreamEndedEvent(
                            context.sessionId(),
                            streamId,
                            context.streamKey(),
                            context.workDir().toString()
                    ));
                } else {
                    log.error("FFmpeg가 비정상 종료되었습니다. ExitCode: {}", exitCode);
                    lastStopReason.set(StopReason.PROCESS_EXIT);
                    streamingStatus.set(StreamingStatus.ERROR);
                }

                currentEncodingSpeed.set(null);
            });

            readLogs(process, sessionId);

        } catch (IOException e) {
            log.error("FFmpeg 실행 중 에러 발생", e);
            streamingStatus.set(StreamingStatus.ERROR);
            currentEncodingSpeed.set(null);
        }
    }

    public synchronized void executeFileFfmpeg(String fileName) {
        if (streamingStatus.get() == StreamingStatus.STREAMING) {
            log.warn("이미 방송이 송출 중입니다.");
            return;
        }

        long sessionId = sessionSequence.updateAndGet(seq -> seq + 1);
        activeSessionId.set(sessionId);
        currentEncodingSpeed.set(null);

        java.nio.file.Path workDir;
        try {
            workDir = java.nio.file.Files.createTempDirectory("hls-file-" + sessionId + "-");
        } catch (IOException e) {
            log.error("임시 디렉토리 생성 실패", e);
            streamingStatus.set(StreamingStatus.ERROR);
            return;
        }

        VideoContext context = new VideoContext(sessionId, fileName, workDir);
        // currentContext는 StreamingContext를 저장하도록 되어 있으므로, 파일 기반 컨텍스트를 지원하도록 확장이 필요할 수도 있지만,
        // 현재는 stopStreaming 등에서 세션 체크를 위해 사용하므로 일단 유지합니다.
        // 다만 StreamingContext와 VideoContext가 서로 다른 레코드이므로 currentContext에 저장할 수 없습니다.
        // 여기서는 stop 기능을 위해 currentContext에 가상의 StreamingContext를 넣어둡니다 (세션 ID만 동일하면 됨).
        currentContext.set(new StreamingContext(sessionId, "file-streaming", workDir));

        stopRequested.set(false);
        lastStopReason.set(StopReason.NONE);
        List<String> command = buildAbridgedCommand(context);

        try {
            this.ffmpegProcess = startProcess(command);
            streamingStatus.set(StreamingStatus.STREAMING);
            Process process = this.ffmpegProcess;
            process.onExit().thenAccept(p -> {
                if (!isCurrentSession(sessionId, p)) {
                    return;
                }

                int exitCode = p.exitValue();

                if (exitCode == 0 || stopRequested.get()) {
                    if (stopRequested.get()) {
                        log.info("파일 스트리밍이 사용자 요청으로 정상적으로 중단되었습니다. ExitCode: {}", exitCode);
                    } else {
                        log.info("파일 스트리밍이 정상적으로 종료되었습니다.");
                        lastStopReason.set(StopReason.PROCESS_EXIT);
                    }
                    streamingStatus.set(StreamingStatus.IDLE);

                    String streamId = "stream-" + context.sessionId();
                    eventPublisher.publishEvent(new StreamEndedEvent(
                            context.sessionId(),
                            streamId,
                            context.fileName(),
                            context.workDir().toString()
                    ));
                } else {
                    log.error("FFmpeg(File)가 비정상 종료되었습니다. ExitCode: {}", exitCode);
                    lastStopReason.set(StopReason.PROCESS_EXIT);
                    streamingStatus.set(StreamingStatus.ERROR);
                }

                currentEncodingSpeed.set(null);
            });

            readLogs(process, sessionId);

        } catch (IOException e) {
            log.error("FFmpeg(File) 실행 중 에러 발생", e);
            streamingStatus.set(StreamingStatus.ERROR);
            currentEncodingSpeed.set(null);
        }
    }

    protected Process startProcess(List<String> command) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private List<String> buildAbridgedCommand(VideoContext context) {
        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-re");
        command.add("-i");
        command.add("/app/videos/" + context.fileName()); // 컨테이너 내부 경로

        // 단일 화질 출력으로 단순화 (1080p 기본)
        command.addAll(List.of(
                "-c:v", "libx264",
                "-preset", "ultrafast",
                "-b:v", "5000k",
                "-maxrate", "5000k",
                "-bufsize", "10000k",
                "-pix_fmt", "yuv420p",
                "-g", "60",
                "-c:a", "aac",
                "-b:a", "128k",
                "-ac", "2",
                "-ar", "44100",
                "-f", "hls",
                "-hls_time", "4",
                "-hls_list_size", "6",
                "-hls_flags", "delete_segments",
                context.workDir().resolve("index.m3u8").toString()
        ));
        return command;
    }

    private List<String> buildAbridgedCommand(StreamingContext context) {
        String inputUrl = "rtmp://rtmp-server:1935/live/" + context.streamKey();

        List<String> command = new ArrayList<>();
        command.add("ffmpeg");

        command.add("-rw_timeout");
        command.add("5000000"); // 5초 대기

        // ★ 주의: 라이브 소스(RTMP)를 받을 때 -re 옵션은 절대 사용하면 안 됩니다.
        // (프레임 드랍 및 비디오 블랙아웃의 주범)
        // -fflags nobuffer+genpts 도 실시간 인제스트에서는 오히려 불안정성을 유발할 수 있어 제거했습니다.

        command.add("-i");
        command.add(inputUrl);

        command.add("-map"); command.add("0:v");
        command.add("-map"); command.add("0:a");

        // 2. 단일 화질 출력으로 단순화 (브라우저 호환성 및 안정성 극대화)
        command.addAll(List.of(
                "-c:v", "libx264",
                "-profile:v", "main",      // ★ 브라우저(Safari, Chrome) 호환성을 위한 프로필 지정
                "-preset", "veryfast",
                "-b:v", "5000k",
                "-maxrate", "5000k",
                "-bufsize", "10000k",
                "-pix_fmt", "yuv420p",     // ★ 웹 표준 픽셀 포맷
                "-g", "60",                // ★ 2초마다 완벽한 키프레임 강제 생성 (30fps 기준)
                "-sc_threshold", "0",      // ★ 화면이 갑자기 바뀔 때 키프레임 주기가 꼬이는 것 방지
                "-c:a", "aac",
                "-b:a", "128k",
                "-f", "hls",
                "-hls_time", "4",
                "-hls_list_size", "6",
                "-hls_flags", "delete_segments",
                context.workDir().resolve("index.m3u8").toString()
        ));

        return command;
    }

    public void stopStreaming() {
        if (ffmpegProcess != null && ffmpegProcess.isAlive()) {
            stopRequested.set(true);
            streamingStatus.set(StreamingStatus.STOPPED);
            lastStopReason.set(StopReason.USER_STOP);
            ffmpegProcess.destroy();
            log.info("사용자 요청으로 방송 중지");
        } else {
            log.info("이미 종료된 방송입니다");
        }
    }

    public StreamingStatus getStreamingStatus() {
        return streamingStatus.get();
    }

    public double getCurrentEncodingSpeed() {
        Double speed = currentEncodingSpeed.get();
        return speed != null ? speed : 0.0;
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
            ffmpegProcess.destroy();
            try {
                if (!ffmpegProcess.waitFor(5, TimeUnit.SECONDS)) {
                    ffmpegProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void readLogs(Process process, long sessionId) {
        CompletableFuture.runAsync(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!isCurrentSession(sessionId, process)) {
                        return;
                    }

                    log.info("[FFmpeg Raw] {}", line);

                    Matcher matcher = SPEED_PATTERN.matcher(line);

                    if (matcher.find()) {
                        String speedStr = matcher.group(1);

                        try {
                            double speed = Double.parseDouble(speedStr);

                            if (isCurrentSession(sessionId, process)) {
                                currentEncodingSpeed.set(speed);
                            }

                            if (speed < 1.0) {
                                log.warn("인코딩 부하 감지! 현재 속도: {}x", speed);
                                // TODO: 여기서 이벤트를 발행하거나 화질 낮추기 로직을 호출할 수 있습니다.
                            }
                        } catch (NumberFormatException e) {
                            log.error("인코딩 속도 파싱 실패: {}", speedStr, e);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("FFmpeg 로그 읽기 중 오류 발생", e);
            }
        });
    }

    private boolean isCurrentSession(long sessionId, Process process) {
        return activeSessionId.get() == sessionId && ffmpegProcess == process;
    }
}
