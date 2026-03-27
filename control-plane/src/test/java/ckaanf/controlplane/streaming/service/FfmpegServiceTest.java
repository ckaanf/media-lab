package ckaanf.controlplane.streaming.service;

import ckaanf.controlplane.streaming.constant.StopReason;
import ckaanf.controlplane.streaming.constant.StreamingStatus;
import ckaanf.controlplane.streaming.response.StreamingInfo;
import ckaanf.controlplane.streaming.service.FfmpegService;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FfmpegServiceTest {

    private FfmpegService ffmpegService;
    private Process mockProcess;
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        mockProcess = mock(Process.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        ffmpegService = spy(new FfmpegService(eventPublisher));
        
        when(mockProcess.getInputStream()).thenReturn(new ByteArrayInputStream("test log".getBytes()));
        when(mockProcess.onExit()).thenReturn(new CompletableFuture<>());
    }

    @Test
    @DisplayName("RTMP 스트리밍 시작 시 상태가 STREAMING으로 변경되어야 함")
    void executeStreamingFfmpeg_Success() throws IOException {
        // given
        doReturn(mockProcess).when(ffmpegService).startProcess(any());

        // when
        ffmpegService.executeStreamingFfmpeg("test-stream");

        // then
        assertThat(ffmpegService.getStreamingStatus()).isEqualTo(StreamingStatus.STREAMING);
        assertThat(ffmpegService.getLastStopReason()).isEqualTo(StopReason.NONE);
        verify(ffmpegService, times(1)).startProcess(any());
    }

    @Test
    @DisplayName("파일 기반 스트리밍 시작 시 상태가 STREAMING으로 변경되어야 함")
    void executeFileFfmpeg_Success() throws IOException {
        // given
        doReturn(mockProcess).when(ffmpegService).startProcess(any());

        // when
        ffmpegService.executeFileFfmpeg("sample.mp4");

        // then
        assertThat(ffmpegService.getStreamingStatus()).isEqualTo(StreamingStatus.STREAMING);
        assertThat(ffmpegService.getLastStopReason()).isEqualTo(StopReason.NONE);
        verify(ffmpegService, times(1)).startProcess(any());
    }

    @Test
    @DisplayName("이미 스트리밍 중일 때 다시 시작하면 추가 프로세스가 실행되지 않아야 함")
    void executeFfmpeg_AlreadyStreaming() throws IOException {
        // given
        doReturn(mockProcess).when(ffmpegService).startProcess(any());
        ffmpegService.executeStreamingFfmpeg("test-stream");

        // when
        ffmpegService.executeStreamingFfmpeg("test-stream");

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
        ffmpegService.executeStreamingFfmpeg("test.mp4");

        // when
        ffmpegService.stopStreaming();

        // then
        assertThat(ffmpegService.getStreamingStatus()).isEqualTo(StreamingStatus.STOPPED);
        assertThat(ffmpegService.getLastStopReason()).isEqualTo(StopReason.USER_STOP);
        verify(mockProcess, times(1)).destroy();
    }

    @Test
    @DisplayName("이미 종료된 방송에 stop이 다시 들어오면 destroy가 추가 호출되지 않아야 함")
    void stopStreaming_When_AlreadyStopped() throws IOException {
        // given
        doReturn(mockProcess).when(ffmpegService).startProcess(any());
        when(mockProcess.isAlive()).thenReturn(true, false);
        when(mockProcess.onExit()).thenReturn(new CompletableFuture<>());

        ffmpegService.executeStreamingFfmpeg("test.mp4");

        // when
        ffmpegService.stopStreaming(); // 최초 stop
        ffmpegService.stopStreaming(); // 이미 종료된 상태에서 재호출

        // then
        assertThat(ffmpegService.getStreamingStatus()).isEqualTo(StreamingStatus.STOPPED);
        assertThat(ffmpegService.getLastStopReason()).isEqualTo(StopReason.USER_STOP);
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

        ffmpegService.executeStreamingFfmpeg("test.mp4");
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
    @DisplayName("로그에서 인코딩 속도를 정상적으로 파싱해야 함")
    void readLogs_EncodingSpeed_Parsing() throws IOException {
        // given
        String logs = "frame=  100 fps= 30 q=28.0 size=     512kB time=00:00:03.33 bitrate=1258.3kbits/s speed= 1.50x\n";
        when(mockProcess.getInputStream()).thenReturn(new ByteArrayInputStream(logs.getBytes()));
        doReturn(mockProcess).when(ffmpegService).startProcess(any());

        // when
        ffmpegService.executeStreamingFfmpeg("test.mp4");

        // then
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(ffmpegService.getCurrentEncodingSpeed()).isEqualTo(1.50);
        });
    }

    @Test
    @DisplayName("인코딩 속도가 1.0 미만일 때 경고 로직이 동작해야 함 (부하 감지)")
    void readLogs_LowSpeed_Warning() throws IOException {
        // given
        String logs = "frame=  200 fps= 20 q=28.0 size=    1024kB time=00:00:06.66 bitrate=1258.3kbits/s speed=0.85x\n";
        when(mockProcess.getInputStream()).thenReturn(new ByteArrayInputStream(logs.getBytes()));
        doReturn(mockProcess).when(ffmpegService).startProcess(any());

        // when
        ffmpegService.executeStreamingFfmpeg("test.mp4");

        // then
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(ffmpegService.getCurrentEncodingSpeed()).isEqualTo(0.85);
        });
    }

    @Test
    @DisplayName("getStreamingInfo는 현재 상태와 마지막 중지 사유를 포함해야 함")
    void getStreamingInfo_ShouldReturnCorrectInfo() throws IOException {
        // given
        doReturn(mockProcess).when(ffmpegService).startProcess(any());
        ffmpegService.executeStreamingFfmpeg("test.mp4");

        // when
        StreamingInfo info = ffmpegService.getStreamingInfo();

        // then
        assertThat(info.status()).isEqualTo(StreamingStatus.STREAMING);
        assertThat(info.stopReason()).isEqualTo(StopReason.NONE);
    }

    @Test
    @DisplayName("destroy 호출 시 실행 중인 프로세스가 종료되어야 함 (@PreDestroy)")
    void destroy_ShouldStopProcess() throws IOException, InterruptedException {
        // given
        doReturn(mockProcess).when(ffmpegService).startProcess(any());
        when(mockProcess.isAlive()).thenReturn(true);
        when(mockProcess.waitFor(anyLong(), any())).thenReturn(true);
        ffmpegService.executeStreamingFfmpeg("test.mp4");

        // when
        ffmpegService.destroy();

        // then
        verify(mockProcess, times(1)).destroy();
    }
}
