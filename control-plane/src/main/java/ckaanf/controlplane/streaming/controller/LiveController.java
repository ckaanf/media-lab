package ckaanf.controlplane.streaming.controller;

import ckaanf.controlplane.streaming.response.StreamingInfo;
import ckaanf.controlplane.streaming.service.FfmpegService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/live")
@RequiredArgsConstructor
public class LiveController {

    private final FfmpegService ffmpegService;

    @GetMapping("/start")
    public StreamingInfo start(@RequestParam(defaultValue = "sample.mp4") String fileName) {
        ffmpegService.startStreaming(fileName);
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
