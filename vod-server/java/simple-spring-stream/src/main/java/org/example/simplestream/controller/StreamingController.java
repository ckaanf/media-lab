package org.example.simplestream.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.simplestream.service.StreamingService;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/stream")
@RequiredArgsConstructor
@Slf4j
public class StreamingController {
    private final StreamingService streamingService;

    @Deprecated
    @GetMapping(value = "/v0/{fileName:.+}")
    public ResponseEntity<ResourceRegion> streamVideo(@PathVariable String fileName,
                                                      @RequestHeader HttpHeaders headers) throws IOException {
        ResourceRegion region = streamingService.streamVideo(fileName, headers);
        HttpStatus status = headers.getRange().isEmpty() ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT;

        return ResponseEntity.status(status)
                .contentType(MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .body(region);
    }

    @GetMapping(value = "/v/{fileName:.+}")
    public ResponseEntity<StreamingResponseBody> streamVideoZeroCopy(@PathVariable String fileName,
                                                             @RequestHeader HttpHeaders headers) throws IOException {
        long[] rangeInfo = new long[3]; // [start, end, total]
        StreamingResponseBody responseBody = streamingService.streamVideo(fileName, headers, rangeInfo);

        long start = rangeInfo[0];
        long end = rangeInfo[1];
        long total = rangeInfo[2];

        boolean isPartial = !headers.getRange().isEmpty();

        return ResponseEntity.status(isPartial ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                .contentType(MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d", start, end, total))
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(end - start + 1))
                .body(responseBody);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<String> handleSecurityException(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }
}
