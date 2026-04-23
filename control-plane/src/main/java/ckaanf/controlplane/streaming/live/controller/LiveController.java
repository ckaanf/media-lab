package ckaanf.controlplane.streaming.live.controller;

import ckaanf.controlplane.streaming.live.dto.response.StreamingInfo;
import ckaanf.controlplane.streaming.live.service.FfmpegService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/live")
@RequiredArgsConstructor
@Slf4j
public class LiveController {

    private final FfmpegService ffmpegService;

    @PostMapping("/publish")
    public ResponseEntity<Void> publish(@RequestParam("name") String streamKey) {
        log.info("OBS 방송 수신 확인! StreamKey: {}", streamKey);
        ffmpegService.startStreaming(streamKey);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/done")
    public ResponseEntity<Void> done(@RequestParam("name") String streamKey) {
        log.info("🛑 OBS 방송 종료 수신! StreamKey: {}", streamKey);
        ffmpegService.stopStreaming();
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
