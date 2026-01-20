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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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

    public StreamingResponseBody streamVideo(String fileName, HttpHeaders headers, long[] rangeOut) throws IOException {
        Path destination = this.rootLocation.resolve(fileName).normalize().toAbsolutePath();
        preventPathTraversal(fileName, destination);

        if (!Files.exists(destination)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        long fileSize = Files.size(destination);
        long start = 0;
        long end = fileSize - 1;

        Optional<HttpRange> range = headers.getRange().stream().findFirst();
        if (range.isPresent()) {
            HttpRange httpRange = range.get();
            start = httpRange.getRangeStart(fileSize);
            end = httpRange.getRangeEnd(fileSize);
        }

        long finalStart = start;
        long finalLength = end - start + 1;
        rangeOut[0] = finalStart;
        rangeOut[1] = end;
        rangeOut[2] = fileSize;

        // StreamingResponseBody를 통해 transferTo 수행
        return outputStream -> {
            try (FileChannel fileChannel = FileChannel.open(destination, StandardOpenOption.READ)) {
                // 핵심: 유저 공간의 버퍼를 거치지 않고 소켓으로 바로 쏘는 transferTo
                long position = finalStart;
                long remaining = finalLength;

                while (remaining > 0) {
                    long transferred = fileChannel.transferTo(position, remaining, Channels.newChannel(outputStream));
                    if (transferred <= 0) break;
                    position += transferred;
                    remaining -= transferred;
                }
            } catch (Exception e) {
                log.error("Error during zero-copy transfer", e);
            }
        };
    }

    private void preventPathTraversal(String fileName, Path destination) {
        if (!destination.startsWith(this.rootLocation)) {
            log.warn("Path Traversal Attempt Detected! Request: {}", fileName);
            throw new SecurityException("Cannot access file outside of the allowed directory");
        }
    }
}
