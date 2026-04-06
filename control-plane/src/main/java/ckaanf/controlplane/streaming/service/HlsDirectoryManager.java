package ckaanf.controlplane.streaming.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
public class HlsDirectoryManager {

    public Path createDirectory(Path path) throws IOException {
        Files.createDirectories(path);
        log.info("HLS directory created: {}", path);
        return path;
    }

    public Path createTempDirectory(String prefix) throws IOException {
        Path path = Files.createTempDirectory(prefix);
        log.info("HLS temp directory created: {}", path);
        return path;
    }

    public void cleanup(Path path) {
        if (path == null) {
            return;
        }

        File dir = path.toFile();
        if (dir.exists()) {
            try {
                FileSystemUtils.deleteRecursively(dir);
                log.info("HLS directory cleaned up: {}", path);
            } catch (Exception e) {
                log.warn("Failed to cleanup HLS directory: {}", path, e);
            }
        }
    }
}
