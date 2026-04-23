package ckaanf.controlplane.vod.service;

import ckaanf.controlplane.streaming.common.event.StreamEndedEvent;
import ckaanf.controlplane.streaming.live.service.FfmpegExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VodService {

    private static final String VOD_STORAGE_BASE_PATH = "/app/videos/";
    private final FfmpegExecutor ffmpegExecutor;

    @Async
    @EventListener
    public void handleStreamEnded(StreamEndedEvent event) {
        log.info("[VOD 자산화 시작] Stream ID: {}, Session ID: {}", event.streamId(), event.sessionId());

        Path sourceBaseDir = Paths.get(event.sourceDirectory());
        String streamName = event.fileName().contains(".") 
                ? event.fileName().substring(0, event.fileName().lastIndexOf('.')) 
                : event.fileName();

        try {
            Path recordFile = sourceBaseDir.resolve("record_full.ts");
            
            if (!Files.exists(recordFile)) {
                log.warn("녹화 파일을 찾을 수 없습니다: {}", recordFile);
                return;
            }

            // sessionId/streamingName/
            Path targetDir = Paths.get(VOD_STORAGE_BASE_PATH, String.valueOf(event.sessionId()), streamName);
            Path targetFile = targetDir.resolve(streamName + ".mp4");

            try {
                Files.createDirectories(targetDir);
                convertToMp4(recordFile, targetFile);
                log.info("[VOD 자산화 완료] 경로: {}", targetFile);
            } catch (IOException | InterruptedException e) {
                log.error("[VOD 자산화 실패] Stream ID: {}", event.streamId(), e);
            }
        } finally {
            // Clean-up: 모든 작업 완료 후 소스 디렉토리 삭제
            cleanUp(sourceBaseDir);
        }
    }

    private void cleanUp(Path path) {
        try {
            if (Files.exists(path)) {
                try (java.util.stream.Stream<Path> walk = Files.walk(path)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(java.io.File::delete);
                }
                log.info("[Clean-up 완료] 소스 디렉토리 삭제됨: {}", path);
            }
        } catch (IOException e) {
            log.warn("[Clean-up 실패] 소스 디렉토리 삭제 중 오류 발생: {}", path, e);
        }
    }

    private void convertToMp4(Path sourceM3u8, Path targetFile) throws IOException, InterruptedException {
        List<String> command = List.of(
                "ffmpeg",
                "-protocol_whitelist", "file,crypto,tcp,udp",
                "-i", sourceM3u8.toString(),
                "-c", "copy",
                "-movflags", "faststart",
                "-y",
                targetFile.toString()
        );

        Process process = ffmpegExecutor.startProcess(command);

        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 필요시 로그 출력
                // log.debug("[FFmpeg VOD] {}", line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("FFmpeg 변환 실패 (exit code: " + exitCode + ")");
        }
    }
}
