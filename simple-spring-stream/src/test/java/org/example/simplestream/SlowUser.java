package org.example.simplestream;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class SlowUser {
    public static void main(String[] args) {
        System.out.println("Slow User::");

        try {
            URL url = java.net.URI.create("http://localhost:8080/api/v1/stream/v/sample_1080p_60fps.mp4").toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            try (InputStream inputStream = connection.getInputStream()) {
                System.out.println("connection created");

                byte[] buffer = new byte[1];
                int bytesRead;
                int totalBytes = 0;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    totalBytes = bytesRead;

                    if (totalBytes % 150 == 0) {
                        System.out.println(totalBytes + " bytes read");
                        Thread.sleep(100);
                    }
                }
            } finally {
                connection.disconnect();
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
