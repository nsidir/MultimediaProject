package server;

import shared.*;
import java.io.*;
import java.util.concurrent.*;

public class VideoStreamer {
    private static final int BUFFER_SIZE = 65536; // 64KB buffer

    public static void streamVideo(String videoFile, OutputStream clientOut) throws IOException {
        Process process = null;
        try {
            System.out.println("Starting to stream: " + videoFile);

            ProcessBuilder pb = new ProcessBuilder(
                    Constants.FFMPEG_PATH,
                    "-i", videoFile,
                    "-c:v", "libx264",  // Use h264 codec
                    "-preset", "fast",   // Faster encoding
                    "-crf", "23",        // Quality balance
                    "-c:a", "aac",       // Audio codec
                    "-b:a", "128k",      // Audio bitrate
                    "-f", "matroska",
                    "-movflags", "frag_keyframe+empty_moov",
                    "pipe:1"
            );

            pb.redirectErrorStream(true);
            process = pb.start();

            // Debug: Print FFmpeg output
            Process finalProcess = process;
            Thread errorReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(finalProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("FFmpeg: " + line);
                    }
                } catch (IOException e) {
                    System.err.println("FFmpeg output reader error: " + e.getMessage());
                }
            });
            errorReader.setDaemon(true);
            errorReader.start();

            // Stream video data
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            try (InputStream ffmpegOut = process.getInputStream()) {
                while ((bytesRead = ffmpegOut.read(buffer)) != -1) {
                    clientOut.write(buffer, 0, bytesRead);
                    clientOut.flush();
                }
                System.out.println("Finished streaming video");
            }
        } finally {
            if (process != null) {
                process.destroy();
                try {
                    if (!process.waitFor(2, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}