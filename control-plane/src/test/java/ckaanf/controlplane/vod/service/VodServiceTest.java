package ckaanf.controlplane.vod.service;

import ckaanf.controlplane.streaming.common.event.StreamEndedEvent;
import ckaanf.controlplane.streaming.live.service.FfmpegExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class VodServiceTest {

    private VodService vodService;

    @Mock
    private FfmpegExecutor ffmpegExecutor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        vodService = new VodService(ffmpegExecutor);
    }

    @Test
    @DisplayName("StreamEndedEvent 수신 시 m3u8 파일이 없으면 무시해야 함")
    void handleStreamEnded_NoM3u8_Ignore() throws IOException {
        // given
        Path sourceDir = tempDir.resolve("hls-source");
        Files.createDirectories(sourceDir);
        
        StreamEndedEvent event = new StreamEndedEvent(
                1L, "stream-1", "sample.mp4", sourceDir.toString());

        // when
        vodService.handleStreamEnded(event);

        // then
        // m3u8이 없어서 로직이 조기 종료되었으므로 소스 디렉토리는 finally 블록에 의해 삭제되어야 함
        assertThat(Files.exists(sourceDir)).isFalse();
    }

    @Test
    @DisplayName("작업 완료 후 소스 디렉토리가 삭제되어야 함 (Clean-up 확인)")
    void handleStreamEnded_Cleanup_AfterCompletion() throws IOException {
        // given
        Path sourceDir = tempDir.resolve("hls-source-cleanup");
        Files.createDirectories(sourceDir);
        Files.createFile(sourceDir.resolve("index.m3u8"));
        Files.createFile(sourceDir.resolve("segment0.ts"));

        StreamEndedEvent event = new StreamEndedEvent(
                1L, "stream-1", "sample.mp4", sourceDir.toString());

        // when
        // FFmpeg 변환은 실제 환경에서 실패할 가능성이 높지만 (FFmpeg 미설치 등), 
        // finally 블록의 cleanUp 동작을 확인하는 것이 주 목적임
        try {
            vodService.handleStreamEnded(event);
        } catch (Exception e) {
            // 변환 실패는 무시
        }

        // then
        assertThat(Files.exists(sourceDir)).isFalse();
    }
}
