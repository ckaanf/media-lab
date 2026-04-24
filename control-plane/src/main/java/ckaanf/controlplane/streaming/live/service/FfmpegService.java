package ckaanf.controlplane.streaming.live.service;

import ckaanf.controlplane.streaming.live.domain.constant.StopReason;
import ckaanf.controlplane.streaming.live.domain.constant.StreamingStatus;
import ckaanf.controlplane.streaming.common.event.StreamEndedEvent;
import ckaanf.controlplane.streaming.live.dto.response.StreamingInfo;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class FfmpegService {

    private final FfmpegExecutor ffmpegExecutor;
    private final StreamingSessionManager sessionManager;
    private final HlsDirectoryManager hlsManager;
    private final FfmpegLogMonitor logMonitor;
    private final ApplicationEventPublisher eventPublisher;
    private final ThreadPoolTaskExecutor mediaTaskExecutor;

    public void startStreaming(String streamKey) {
        sessionManager.setStopRequested(false);
        sessionManager.setStatus(StreamingStatus.INIT);

        CompletableFuture.runAsync(() -> {
            if (!waitForStreamReady(streamKey)) {
                log.error("스트림 준비 시간 초과 또는 사용자에 의한 취소: {}", streamKey);
                return;
            }

            log.info("라이브 스트리밍 시작 OBS: {}", streamKey);
            executeStreamingFfmpeg(streamKey);
        }, mediaTaskExecutor);
    }

    public void startFileStreaming(String fileName) {
        CompletableFuture.runAsync(() -> {
            Path filePath = Paths.get("/app/videos/", fileName);
            if (!Files.exists(filePath)) {
                log.error("파일을 찾을 수 없습니다: {}", filePath);
                return;
            }

            log.info("파일 기반 스트리밍 시작: {}", fileName);
            executeFileFfmpeg(fileName);
        }, mediaTaskExecutor);
    }

    private boolean waitForStreamReady(String streamKey) {
        String inputUrl = "rtmp://rtmp-server:1935/live/" + streamKey;
        int maxRetries = 20;

        for (int i = 0; i < maxRetries; i++) {
            if (sessionManager.isStopRequested()) {
                log.warn("대기 중 방송 취소 요청 감지. ffprobe 폴링 중단");
                return false;
            }

            try {
                log.info("스트림 확인 중... (시도 {}/{}) URL: {}", i + 1, maxRetries, inputUrl);

                Process probe = new ProcessBuilder(
                        "ffprobe",
                        "-v", "error",
                        "-i", inputUrl,
                        "-show_streams",
                        "-select_streams", "v:0"
                ).start();

                boolean finished = probe.waitFor(5, TimeUnit.SECONDS);

                if (finished) {
                    int exitCode = probe.exitValue();
                    if (exitCode == 0) {
                        log.info("✅ 스트림 준비 완료 확인 (비디오 스트림 포함)!");
                        return true;
                    } else {
                        log.warn("❌ ffprobe 실패 (종료 코드: {})", exitCode);
                    }
                } else {
                    log.warn("⚠️ ffprobe 응답 지연 (Timeout)");
                    probe.destroyForcibly();
                }
                Thread.sleep(500);
            } catch (Exception e) {
                log.error("🚨 ffprobe 실행 중 예외 발생: {}", e.getMessage());
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
        if (sessionManager.getStatus() == StreamingStatus.STREAMING) {
            log.warn("이미 방송이 송출 중입니다.");
            return;
        }

        long sessionId = sessionManager.nextSessionId();
        Path outputDirectory = Paths.get("/tmp/hls", streamKey);
        try {
            hlsManager.createDirectory(outputDirectory);
        } catch (IOException e) {
            log.error("디렉토리 생성 실패", e);
            sessionManager.setStatus(StreamingStatus.ERROR);
            return;
        }

        List<String> command = buildStreamingCommand(streamKey, outputDirectory);

        try {
            Process process = ffmpegExecutor.startProcess(command);
            sessionManager.startNewSession(sessionId, streamKey, outputDirectory, process);

            process.onExit().thenAccept(p -> handleProcessExit(p, sessionId, streamKey, outputDirectory));
            logMonitor.monitor(process, sessionId);

        } catch (IOException e) {
            log.error("FFmpeg 실행 중 에러 발생", e);
            sessionManager.setStatus(StreamingStatus.ERROR);
        }
    }

    public synchronized void executeFileFfmpeg(String fileName) {
        if (sessionManager.getStatus() == StreamingStatus.STREAMING) {
            log.warn("이미 방송이 송출 중입니다.");
            return;
        }

        long sessionId = sessionManager.nextSessionId();
        Path workDir;
        try {
            workDir = hlsManager.createTempDirectory("hls-file-" + sessionId + "-");
        } catch (IOException e) {
            log.error("임시 디렉토리 생성 실패", e);
            sessionManager.setStatus(StreamingStatus.ERROR);
            return;
        }

        List<String> command = buildFileCommand(fileName, workDir);

        try {
            Process process = ffmpegExecutor.startProcess(command);
            sessionManager.startNewSession(sessionId, fileName, workDir, process);

            process.onExit().thenAccept(p -> handleProcessExit(p, sessionId, fileName, workDir));
            logMonitor.monitor(process, sessionId);

        } catch (IOException e) {
            log.error("FFmpeg(File) 실행 중 에러 발생", e);
            sessionManager.setStatus(StreamingStatus.ERROR);
        }
    }

    public void stopStreaming() {
        stopProcessAndCleanup("사용자 요청으로 방송 중지");
        sessionManager.setStopReason(StopReason.USER_STOP);
        sessionManager.setStatus(StreamingStatus.STOPPED);
    }



    private void handleProcessExit(Process p, long sessionId, String name, Path workDir) {
        if (!sessionManager.isCurrentSession(sessionId, p)) {
            return;
        }

        int exitCode = p.exitValue();
        boolean stopRequested = sessionManager.isStopRequested();

        if (exitCode == 0 || stopRequested) {
            if (stopRequested) {
                log.info("방송이 사용자 요청으로 정상적으로 중단되었습니다. ExitCode: {}", exitCode);
            } else {
                log.info("방송이 정상적으로 종료되었습니다.");
                sessionManager.setStopReason(StopReason.PROCESS_EXIT);
            }
            sessionManager.setStatus(StreamingStatus.STOPPED);

            String streamId = "stream-" + sessionId;
            eventPublisher.publishEvent(new StreamEndedEvent(
                    sessionId,
                    streamId,
                    name,
                    workDir.toString()
            ));
        } else {
            log.error("FFmpeg가 비정상 종료되었습니다. ExitCode: {}", exitCode);
            sessionManager.setStopReason(StopReason.PROCESS_EXIT);
            sessionManager.setStatus(StreamingStatus.ERROR);
        }

        sessionManager.clearEncodingSpeed();
    }

    private List<String> buildFileCommand(String fileName, Path workDir) {
        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-re");
        command.add("-i");
        command.add("/app/videos/" + fileName);
        command.addAll(List.of(
                "-c:v", "libx264", "-preset", "ultrafast", "-b:v", "5000k",
                "-maxrate", "5000k", "-bufsize", "10000k", "-pix_fmt", "yuv420p",
                "-g", "60", "-c:a", "aac", "-b:a", "128k", "-ac", "2", "-ar", "44100",
                "-f", "hls", "-hls_time", "4", "-hls_list_size", "6",
                "-hls_flags", "delete_segments",
                workDir.resolve("index.m3u8").toString()
        ));
        return command;
    }

    private List<String> buildStreamingCommand(String streamKey, Path workDir) {
        String inputUrl = "rtmp://rtmp-server:1935/live/" + streamKey;
        List<String> command = new ArrayList<>();

        command.add("ffmpeg");
        command.add("-rw_timeout"); command.add("5000000");
        command.add("-i"); command.add(inputUrl);

        command.addAll(List.of(
                "-c:v", "libx264", "-profile:v", "main", "-preset", "veryfast",
                "-b:v", "5000k", "-maxrate", "5000k", "-bufsize", "10000k",
                "-pix_fmt", "yuv420p", "-g", "60", "-sc_threshold", "0",
                "-c:a", "aac", "-b:a", "128k",

                // ★ 개선: 비디오(0:v)와 오디오(0:a)를 잡아와서 tee muxer로 던집니다.
                "-map", "0:v", "-map", "0:a",
                "-f", "tee",

                // 파이프 1: HLS 라이브 (지워짐) | 파이프 2: TS 녹화 (계속 쌓임)
                String.format("[f=hls:hls_time=4:hls_list_size=6:hls_flags=delete_segments]%s|[f=mpegts]%s",
                        workDir.resolve("index.m3u8").toString(),
                        workDir.resolve("record_full.ts").toString()
                )
        ));
        return command;
    }

    public StreamingStatus getStreamingStatus() {
        return sessionManager.getStatus();
    }

    public StopReason getLastStopReason() {
        return sessionManager.getStopReason();
    }

    public StreamingInfo getStreamingInfo() {
        return new StreamingInfo(sessionManager.getStatus(), sessionManager.getStopReason());
    }

    @PreDestroy
    public void destroy() {
        stopProcessAndCleanup("서버 종료로 인한 FFmpeg 강제 회수 중");
    }

    private void stopProcessAndCleanup(String logMessage) {
        log.info(logMessage);
        sessionManager.setStopRequested(true);
        StreamingSessionManager.StreamingSession session = sessionManager.getCurrentSession().get();
        if (session == null || session.process() == null || !session.process().isAlive()) {
            log.info("실행 중인 FFmpeg 프로세스가 없습니다 (대기 중이었거나 이미 죽음).");
            return;
        }

        ffmpegExecutor.stopProcess(session.process(), 5);
    }
}
