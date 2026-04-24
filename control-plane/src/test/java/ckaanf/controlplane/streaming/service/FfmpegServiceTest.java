package ckaanf.controlplane.streaming.service;

import ckaanf.controlplane.streaming.live.domain.constant.StopReason;
import ckaanf.controlplane.streaming.live.domain.constant.StreamingStatus;
import ckaanf.controlplane.streaming.live.dto.response.StreamingInfo;
import ckaanf.controlplane.streaming.live.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FfmpegServiceTest {

    @InjectMocks
    private FfmpegService ffmpegService;

    @Mock
    private FfmpegExecutor ffmpegExecutor;

    @Spy
    private StreamingSessionManager sessionManager = new StreamingSessionManager();

    @Mock
    private HlsDirectoryManager hlsManager;

    @Mock
    private FfmpegLogMonitor logMonitor;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ThreadPoolTaskExecutor mediaTaskExecutor;

    private Process mockProcess;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        mockProcess = mock(Process.class);

        // CompletableFuture.runAsync(..., executor) 내부에서 executor.execute()를 호출하므로 모킹 필요
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(mediaTaskExecutor).execute(any(Runnable.class));

        when(mockProcess.getInputStream()).thenReturn(new ByteArrayInputStream("test log".getBytes()));
        when(mockProcess.onExit()).thenReturn(new CompletableFuture<>());

        when(hlsManager.createDirectory(any())).thenAnswer(inv -> inv.getArgument(0));
        when(hlsManager.createTempDirectory(anyString())).thenReturn(Path.of("/tmp/hls-temp"));
    }

    @Test
    @DisplayName("RTMP 스트리밍 시작 시 상태가 STREAMING으로 변경되어야 함")
    void executeStreamingFfmpeg_Success() throws IOException {
        // given
        when(ffmpegExecutor.startProcess(any())).thenReturn(mockProcess);

        // when
        ffmpegService.executeStreamingFfmpeg("test-stream");

        // then
        assertThat(ffmpegService.getStreamingStatus()).isEqualTo(StreamingStatus.STREAMING);
        assertThat(ffmpegService.getLastStopReason()).isEqualTo(StopReason.NONE);
        verify(ffmpegExecutor, times(1)).startProcess(any());
    }

    @Test
    @DisplayName("파일 기반 스트리밍 시작 시 상태가 STREAMING으로 변경되어야 함")
    void executeFileFfmpeg_Success() throws IOException {
        // given
        when(ffmpegExecutor.startProcess(any())).thenReturn(mockProcess);

        // when
        ffmpegService.executeFileFfmpeg("sample.mp4");

        // then
        assertThat(ffmpegService.getStreamingStatus()).isEqualTo(StreamingStatus.STREAMING);
        assertThat(ffmpegService.getLastStopReason()).isEqualTo(StopReason.NONE);
        verify(ffmpegExecutor, times(1)).startProcess(any());
    }

    @Test
    @DisplayName("이미 스트리밍 중일 때 다시 시작하면 추가 프로세스가 실행되지 않아야 함")
    void executeFfmpeg_AlreadyStreaming() throws IOException {
        // given
        when(ffmpegExecutor.startProcess(any())).thenReturn(mockProcess);
        ffmpegService.executeStreamingFfmpeg("test-stream");

        // when
        ffmpegService.executeStreamingFfmpeg("test-stream");

        // then
        assertThat(ffmpegService.getStreamingStatus()).isEqualTo(StreamingStatus.STREAMING);
        verify(ffmpegExecutor, times(1)).startProcess(any());
    }

    @Test
    @DisplayName("스트리밍 중지 시 프로세스가 종료되어야 함")
    void stopStreaming_Success() throws IOException {
        // given
        when(ffmpegExecutor.startProcess(any())).thenReturn(mockProcess);
        when(mockProcess.isAlive()).thenReturn(true);
        ffmpegService.executeStreamingFfmpeg("test.mp4");

        // when
        ffmpegService.stopStreaming();

        // then
        assertThat(ffmpegService.getStreamingStatus()).isEqualTo(StreamingStatus.STOPPED);
        assertThat(ffmpegService.getLastStopReason()).isEqualTo(StopReason.USER_STOP);
        verify(ffmpegExecutor, times(1)).stopProcess(eq(mockProcess), anyLong());
    }

    @Test
    @DisplayName("이미 종료된 방송에 stop이 다시 들어오면 destroy가 추가 호출되지 않아야 함")
    void stopStreaming_When_AlreadyStopped() throws IOException {
        // given
        when(ffmpegExecutor.startProcess(any())).thenReturn(mockProcess);
        when(mockProcess.isAlive()).thenReturn(true, false);

        ffmpegService.executeStreamingFfmpeg("test.mp4");

        // when
        ffmpegService.stopStreaming(); // 최초 stop
        ffmpegService.stopStreaming(); // 이미 종료된 상태에서 재호출

        // then
        assertThat(ffmpegService.getStreamingStatus()).isEqualTo(StreamingStatus.STOPPED);
        assertThat(ffmpegService.getLastStopReason()).isEqualTo(StopReason.USER_STOP);
        verify(ffmpegExecutor, times(1)).stopProcess(eq(mockProcess), anyLong());
    }

    @Test
    @DisplayName("프로세스가 정상 종료(0)되면 상태가 STOPPED 변경되어야 함")
    void onExit_Success() throws IOException {
        // given
        CompletableFuture<Process> exitFuture = new CompletableFuture<>();
        when(mockProcess.onExit()).thenReturn(exitFuture);
        when(mockProcess.exitValue()).thenReturn(0);
        when(ffmpegExecutor.startProcess(any())).thenReturn(mockProcess);

        ffmpegService.executeStreamingFfmpeg("test.mp4");
        assertThat(ffmpegService.getStreamingStatus()).isEqualTo(StreamingStatus.STREAMING);

        // when
        exitFuture.complete(mockProcess);

        // then
        assertThat(ffmpegService.getStreamingStatus()).isEqualTo(StreamingStatus.STOPPED);
    }

    @Test
    @DisplayName("프로세스가 비정상 종료(1)되면 상태가 ERROR로 변경되어야 함")
    void onExit_Failure() throws IOException {
        // given
        CompletableFuture<Process> exitFuture = new CompletableFuture<>();
        when(mockProcess.onExit()).thenReturn(exitFuture);
        when(mockProcess.exitValue()).thenReturn(1);
        when(ffmpegExecutor.startProcess(any())).thenReturn(mockProcess);

        ffmpegService.executeStreamingFfmpeg("test.mp4");

        // when
        exitFuture.complete(mockProcess);

        // then
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(ffmpegService.getStreamingStatus()).isEqualTo(StreamingStatus.ERROR);
            assertThat(ffmpegService.getLastStopReason()).isEqualTo(StopReason.PROCESS_EXIT);
        });
    }

    @Test
    @DisplayName("로그 모니터가 정상적으로 호출되어야 함")
    void logMonitor_ShouldBeCalled() throws IOException {
        // given
        when(ffmpegExecutor.startProcess(any())).thenReturn(mockProcess);

        // when
        ffmpegService.executeStreamingFfmpeg("test.mp4");

        // then
        verify(logMonitor, times(1)).monitor(eq(mockProcess), anyLong());
    }

    @Test
    @DisplayName("getStreamingInfo는 현재 상태와 마지막 중지 사유를 포함해야 함")
    void getStreamingInfo_ShouldReturnCorrectInfo() throws IOException {
        // given
        when(ffmpegExecutor.startProcess(any())).thenReturn(mockProcess);
        ffmpegService.executeStreamingFfmpeg("test.mp4");

        // when
        StreamingInfo info = ffmpegService.getStreamingInfo();

        // then
        assertThat(info.status()).isEqualTo(StreamingStatus.STREAMING);
        assertThat(info.stopReason()).isEqualTo(StopReason.NONE);
    }

    @Test
    @DisplayName("destroy 호출 시 실행 중인 프로세스가 종료되어야 함 (@PreDestroy)")
    void destroy_ShouldStopProcess() throws IOException {
        // given
        when(ffmpegExecutor.startProcess(any())).thenReturn(mockProcess);
        when(mockProcess.isAlive()).thenReturn(true);
        ffmpegService.executeStreamingFfmpeg("test.mp4");

        // when
        ffmpegService.destroy();

        // then
        verify(ffmpegExecutor, times(1)).stopProcess(eq(mockProcess), anyLong());
    }
}
