package org.example.simplestream.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Slf4j
@Service
public class StreamingService {
    private final Path rootLocation;

    public StreamingService() {
        this.rootLocation = Paths.get("./videos").toAbsolutePath().normalize();

        try {
            if (!Files.exists(this.rootLocation)) {
                Files.createDirectories(this.rootLocation);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ResourceRegion streamVideo(String fileName, HttpHeaders headers) throws IOException {
        Path destination = this.rootLocation.resolve(fileName).normalize().toAbsolutePath();
        preventPathTraversal(fileName, destination);

        Resource resource = new FileSystemResource(destination);
        if (!resource.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        long contentLength = resource.contentLength();

        Optional<HttpRange> range = headers.getRange().stream().findFirst();
        if (range.isPresent()) {
            HttpRange httpRange = range.get();

            long start = httpRange.getRangeStart(contentLength);
            long end = httpRange.getRangeEnd(contentLength);

            if (start >= contentLength || start > end) {
                throw new ResponseStatusException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
            }

            long rangeLength = end - start + 1;

            log.info("Range request: start={}, length={}, total={}", start, rangeLength, contentLength);
            return new ResourceRegion(resource, start, rangeLength);
        } else {
            log.info("Full content request: length={}", contentLength);
            return new ResourceRegion(resource, 0, contentLength);
        }
    }

    private void preventPathTraversal(String fileName, Path destination) {
        if (!destination.startsWith(this.rootLocation)) {
            log.warn("Path Traversal Attempt Detected! Request: {}", fileName);
            throw new SecurityException("Cannot access file outside of the allowed directory");
        }
    }
}
