package server;

import shared.Constants;
import java.io.*;
import java.net.*;
import java.util.logging.Logger;

public class VideoStreamer {
    private static final Logger logger = Logger.getLogger(VideoStreamer.class.getName());

    // TCP: FFMPEG listens, client connects
    public static void streamViaTCP(String videoPath, int port) throws IOException {
        logger.info("Starting TCP stream for video: " + videoPath + " on port " + port);
        ProcessBuilder pb = new ProcessBuilder(
                Constants.FFMPEG_PATH,
                "-re",
                "-i", videoPath,
                "-f", "mpegts",
                "tcp://0.0.0.0:" + port + "?listen"
        );
        pb.inheritIO();
        logger.info("FFmpeg command: " + String.join(" ", pb.command()));
        Process process = pb.start();
        try { process.waitFor(); } catch (InterruptedException ignored) {}
    }

    // UDP: FFMPEG sends to client IP/port, client listens
    public static void streamViaUDP(String videoPath, String clientIP, int clientPort) throws IOException {
        logger.info("Starting UDP stream for video: " + videoPath + " to " + clientIP + ":" + clientPort);
        ProcessBuilder pb = new ProcessBuilder(
                Constants.FFMPEG_PATH,
                "-re",
                "-i", videoPath,
                "-f", "mpegts",
                "udp://" + clientIP + ":" + clientPort
        );
        pb.inheritIO();
        logger.info("FFmpeg command: " + String.join(" ", pb.command()));
        Process process = pb.start();
        try { process.waitFor(); } catch (InterruptedException ignored) {}
    }

    // RTP: FFMPEG generates SDP, sends RTP to client IP/port
    public static void streamViaRTP(String videoPath, String clientIP, int clientPort, String sdpPath) throws IOException {
        logger.info("Starting RTP stream for video: " + videoPath + " to " + clientIP + ":" + clientPort);
        ProcessBuilder pb = new ProcessBuilder(
                Constants.FFMPEG_PATH,
                "-re",
                "-i", videoPath,
                "-map", "0:v:0",
                "-an",
                "-c:v", "libx264",
                "-preset", "ultrafast", // for fast CPU encoding
                "-f", "rtp",
                "-sdp_file", sdpPath,
                "rtp://" + clientIP + ":" + clientPort
        );

        pb.inheritIO();
        logger.info("FFmpeg command: " + String.join(" ", pb.command()));
        Process process = pb.start();
        try { process.waitFor(); } catch (InterruptedException ignored) {}
    }
}
