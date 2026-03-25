package ckaanf.controlplane.controller;

import ckaanf.controlplane.service.FfmpegService;
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
    public String start(@RequestParam(defaultValue = "sample.mp4") String fileName) {
        ffmpegService.startStreaming(fileName);
        return "방송 송출 명령이 전달되었습니다: " + fileName;
    }

    @GetMapping("/stop")
    public String stop() {
        ffmpegService.stopStreaming();
        return "방송 중지 명령이 전달되었습니다";
    }
}
