package server;

import shared.Constants;
import java.io.*;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VideoStreamer {
    private static final Logger logger = Logger.getLogger(VideoStreamer.class.getName());
    private static final int BUFFER_SIZE = 65536;

    public static void streamViaTCP(String videoPath, OutputStream clientOut) throws IOException {
        logger.info("Starting TCP-style stream for video: " + videoPath);
        File videoFile = new File(videoPath);
        if (!videoFile.exists() || !videoFile.isFile()) {
            logger.severe("Video file not found or is not a file: " + videoPath);
            try {
                clientOut.close();
            } catch (IOException e) {
                logger.warning("Could not close client output stream: " + e.getMessage());
            }
            throw new FileNotFoundException("Video file not found: " + videoPath);
        }

        ProcessBuilder pb = new ProcessBuilder(
                Constants.FFMPEG_PATH,
                "-re",
                "-i", videoPath,
                "-c:v", "libx264",
                "-preset", "ultrafast",
                "-tune", "zerolatency",
                "-crf", "23",
                "-c:a", "aac",
                "-b:a", "128k",
                "-f", "mp4",
                "-movflags", "frag_keyframe+empty_moov+default_base_moof+faststart",
                "pipe:1"
        );

        logger.info("FFmpeg command: " + String.join(" ", pb.command()));

        Process process = null;
        try {
            process = pb.start();

            StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(), "ERROR");
            errorGobbler.start();

            try (InputStream ffmpegStdout = process.getInputStream();
                 BufferedInputStream bufferedFfmpegOut = new BufferedInputStream(ffmpegStdout, BUFFER_SIZE)) {

                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                long totalBytesSent = 0;

                logger.info("Streaming video bytes to client...");
                while ((bytesRead = bufferedFfmpegOut.read(buffer)) != -1) {
                    try {
                        clientOut.write(buffer, 0, bytesRead);
                        clientOut.flush();
                        totalBytesSent += bytesRead;
                    } catch (IOException e) {
                        logger.warning("Client write failed (disconnected?): " + e.getMessage());
                        break;
                    }
                }

                logger.info("Streaming completed. Total bytes sent: " + totalBytesSent);
                if (totalBytesSent == 0) {
                    logger.warning("No bytes sent. FFmpeg likely failed. See ERROR logs.");
                }

            } catch (IOException e) {
                logger.log(Level.SEVERE, "Stream I/O error: " + e.getMessage(), e);
            } finally {
                try {
                    clientOut.close();
                } catch (IOException e) {
                    logger.warning("Error closing client output stream: " + e.getMessage());
                }
            }

            int exitCode = process.waitFor();
            logger.info("FFmpeg exited with code: " + exitCode);
            if (exitCode != 0) {
                logger.severe("FFmpeg failed. Check error output.");
            }

            errorGobbler.join(5000);

        } catch (IOException e) {
            logger.log(Level.SEVERE, "FFmpeg start error: " + e.getMessage(), e);
            throw e;
        } catch (InterruptedException e) {
            logger.warning("Interrupted while waiting for FFmpeg: " + e.getMessage());
            Thread.currentThread().interrupt();
        } finally {
            if (process != null) {
                closeQuietly(process.getOutputStream());
                closeQuietly(process.getInputStream());
                closeQuietly(process.getErrorStream());
                process.destroy();
                logger.info("FFmpeg process destroyed.");
            }
        }
    }

    private static class StreamGobbler extends Thread {
        private final InputStream inputStream;
        private final String type;
        private static final Logger sgLogger = Logger.getLogger(StreamGobbler.class.getName());

        StreamGobbler(InputStream inputStream, String type) {
            this.inputStream = inputStream;
            this.type = type;
            this.setName("FFmpeg-" + type + "-Gobbler");
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sgLogger.info("FFMPEG " + type + ": " + line);
                }
            } catch (IOException e) {
                sgLogger.warning("StreamGobbler " + type + " error: " + e.getMessage());
            } finally {
                sgLogger.info("StreamGobbler for " + type + " finished.");
            }
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {}
        }
    }

    public static void streamViaUDP(String videoPath, Socket clientControlSocket) throws IOException {
        logger.info("UDP stream not implemented. Falling back to TCP stream.");
        streamViaTCP(videoPath, clientControlSocket.getOutputStream());
    }

    public static void streamViaRTP(String videoPath, Socket clientControlSocket) throws IOException {
        logger.info("RTP stream not implemented. Falling back to TCP stream.");
        streamViaTCP(videoPath, clientControlSocket.getOutputStream());
    }
}
