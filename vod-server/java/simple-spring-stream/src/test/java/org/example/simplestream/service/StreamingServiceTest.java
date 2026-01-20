package org.example.simplestream.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class StreamingServiceTest {

    private StreamingService streamingService;
    private Path testVideoPath;
    private final String testFileName = "test_video.mp4";

    @BeforeEach
    void setUp() throws IOException {
        streamingService = new StreamingService();
        Path rootLocation = Paths.get("./videos").toAbsolutePath().normalize();
        if (!Files.exists(rootLocation)) {
            Files.createDirectories(rootLocation);
        }
        testVideoPath = rootLocation.resolve(testFileName);
        // Create a 2MB test file
        byte[] data = new byte[2 * 1024 * 1024];
        Files.write(testVideoPath, data);
    }

    @Test
    void testStreamVideo_FileNotFound() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            streamingService.streamVideo("non_existent.mp4", new HttpHeaders());
        });
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void testStreamVideo_ExactRange() throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setRange(HttpRange.parseRanges("bytes=0-1500000"));
        ResourceRegion region = streamingService.streamVideo(testFileName, headers);
        
        assertEquals(1500001, region.getCount());
        assertEquals(0, region.getPosition());
    }

    @Test
    void testStreamVideo_FullContent() throws IOException {
        HttpHeaders headers = new HttpHeaders();
        ResourceRegion region = streamingService.streamVideo(testFileName, headers);
        
        assertEquals(2 * 1024 * 1024, region.getCount());
        assertEquals(0, region.getPosition());
    }

    @Test
    void testStreamVideo_UnsatisfiableRange_StartTooLarge() {
        HttpHeaders headers = new HttpHeaders();
        headers.setRange(HttpRange.parseRanges("bytes=3000000-4000000"));
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            streamingService.streamVideo(testFileName, headers);
        });
        assertEquals(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, exception.getStatusCode());
    }
}
