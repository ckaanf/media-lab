package org.example.simplestream.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.simplestream.service.StreamingService;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/stream")
@RequiredArgsConstructor
@Slf4j
public class StreamingController {
    private final StreamingService streamingService;

    @GetMapping(value = "/v/{fileName:.+}")
    public ResponseEntity<ResourceRegion> streamVideo(@PathVariable String fileName,
                                                      @RequestHeader HttpHeaders headers) throws IOException {
        ResourceRegion region = streamingService.streamVideo(fileName, headers);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .body(region);
    }

    @GetMapping("/v/block")
    public String blockingTest() throws InterruptedException {
        log.info("Blocking Request Started: " + Thread.currentThread().getName());
        Thread.sleep(10000);

        log.info("Blocking Request Finished: " + Thread.currentThread().getName());
        return "ok";
    }


    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<String> handleSecurityException(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }
}
