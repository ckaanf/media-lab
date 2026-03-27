package ckaanf.controlplane.streaming.controller;

import ckaanf.controlplane.streaming.response.StreamingInfo;
import ckaanf.controlplane.streaming.service.FfmpegService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/live")
@RequiredArgsConstructor
@Slf4j
public class LiveController {

    private final FfmpegService ffmpegService;

    @PostMapping("/publish")
    public ResponseEntity<Void> publish(@RequestParam("name") String streamKey) {
        log.info("OBS 방송 수신 확인! StreamKey: {}", streamKey);

        // [핵심] FFmpeg 실행은 별도 스레드에 맡깁니다.
        CompletableFuture.runAsync(() -> {
            ffmpegService.startStreaming(streamKey);
        });

        // [핵심] 메인 스레드는 즉시 200 OK를 반환하여 NGINX의 잠금을 해제합니다.
        return ResponseEntity.ok().build();
    }

    @GetMapping("/start")
    public StreamingInfo start(@RequestParam(defaultValue = "sample.mp4") String fileName) {
        ffmpegService.startFileStreaming(fileName);
        return ffmpegService.getStreamingInfo();
    }

    @GetMapping("/stop")
    public StreamingInfo stop() {
        ffmpegService.stopStreaming();
        return ffmpegService.getStreamingInfo();
    }

    @GetMapping("/status")
    public StreamingInfo status() {
        return ffmpegService.getStreamingInfo();
    }
}
