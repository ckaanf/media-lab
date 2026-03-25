package ckaanf.controlplane.service;

import ckaanf.controlplane.domain.Streaming.StreamingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FfmpegServiceTest {

    private FfmpegService ffmpegService;
    private Process mockProcess;

    @BeforeEach
    void setUp() {
        mockProcess = mock(Process.class);
        // Spy를 사용하여 startProcess만 모킹함
        ffmpegService = spy(new FfmpegService());
        
        // Mock process setup
        when(mockProcess.getInputStream()).thenReturn(new ByteArrayInputStream("test log".getBytes()));
        // 기본적으로 종료되지 않은 프로세스를 시뮬레이션
        when(mockProcess.onExit()).thenReturn(new CompletableFuture<>());
    }

    @Test
    @DisplayName("스트리밍 시작 시 상태가 STREAMING으로 변경되어야 함")
    void startStreaming_Success() throws IOException {
        // given
        doReturn(mockProcess).when(ffmpegService).startProcess(any());

        // when
        ffmpegService.startStreaming("test.mp4");

        // then
        assertThat(ffmpegService.getStreamingStatus()).isEqualTo(StreamingStatus.STREAMING);
        verify(ffmpegService, times(1)).startProcess(any());
    }

    @Test
    @DisplayName("이미 스트리밍 중일 때 다시 시작하면 추가 프로세스가 실행되지 않아야 함")
    void startStreaming_AlreadyStreaming() throws IOException {
        // given
        doReturn(mockProcess).when(ffmpegService).startProcess(any());
        ffmpegService.startStreaming("test.mp4"); // First call

        // when
        ffmpegService.startStreaming("test.mp4"); // Second call

        // then
        assertThat(ffmpegService.getStreamingStatus()).isEqualTo(StreamingStatus.STREAMING);
        verify(ffmpegService, times(1)).startProcess(any());
    }

    @Test
    @DisplayName("스트리밍 중지 시 프로세스가 종료되어야 함")
    void stopStreaming_Success() throws IOException {
        // given
        doReturn(mockProcess).when(ffmpegService).startProcess(any());
        when(mockProcess.isAlive()).thenReturn(true);
        ffmpegService.startStreaming("test.mp4");

        // when
        ffmpegService.stopStreaming();

        // then
        verify(mockProcess, times(1)).destroy();
    }

    @Test
    @DisplayName("프로세스가 정상 종료(0)되면 상태가 IDLE로 변경되어야 함")
    void onExit_Success() throws IOException {
        // given
        CompletableFuture<Process> exitFuture = new CompletableFuture<>();
        when(mockProcess.onExit()).thenReturn(exitFuture);
        when(mockProcess.exitValue()).thenReturn(0);
        doReturn(mockProcess).when(ffmpegService).startProcess(any());

        ffmpegService.startStreaming("test.mp4");
        assertThat(ffmpegService.getStreamingStatus()).isEqualTo(StreamingStatus.STREAMING);

        // when
        exitFuture.complete(mockProcess);

        // then
        assertThat(ffmpegService.getStreamingStatus()).isEqualTo(StreamingStatus.IDLE);
    }

    @Test
    @DisplayName("프로세스가 비정상 종료(1)되면 상태가 ERROR로 변경되어야 함")
    void onExit_Failure() throws IOException {
        // given
        CompletableFuture<Process> exitFuture = new CompletableFuture<>();
        when(mockProcess.onExit()).thenReturn(exitFuture);
        when(mockProcess.exitValue()).thenReturn(1);
        doReturn(mockProcess).when(ffmpegService).startProcess(any());

        ffmpegService.startStreaming("test.mp4");

        // when
        exitFuture.complete(mockProcess);

        // then
        assertThat(ffmpegService.getStreamingStatus()).isEqualTo(StreamingStatus.ERROR);
    }
}
