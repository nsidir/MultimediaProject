package server;

import shared.*;
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

public class ClientHandler implements Runnable {
    private static final Logger logger = Logger.getLogger(ClientHandler.class.getName());
    private final Socket clientSocket;
    private static final int STREAM_TIMEOUT = 30000;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try {
            clientSocket.setSoTimeout(STREAM_TIMEOUT);

            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                String requestType = in.readLine();
                logger.info("Request type: " + requestType);

                switch (requestType) {
                    case Protocol.LIST:
                        handleListRequest(in, out);
                        break;
                    case Protocol.STREAM:
                        handleStreamRequest(in, out);
                        break;
                    default:
                        logger.warning("Unknown request type: " + requestType);
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Client error: " + e.getMessage(), e);
        } finally {
            closeQuietly(clientSocket);
        }
    }

    private void handleListRequest(BufferedReader in, PrintWriter out) throws IOException {
        // Read client's connection speed and preferred format
        double connectionSpeed = Double.parseDouble(in.readLine());
        String preferredFormat = in.readLine();

        logger.info("Client speed: " + connectionSpeed + " Mbps, preferred format: " + preferredFormat);

        List<VideoInfo> allVideos = FFmpegProcessor.getAvailableVideos();
        List<VideoInfo> suitableVideos = filterVideosBySpeed(allVideos, connectionSpeed, preferredFormat);

        out.println(suitableVideos.size());
        for (VideoInfo video : suitableVideos) {
            out.println(video.getName() + "," + video.getResolution());
        }
        out.flush();
    }

    private List<VideoInfo> filterVideosBySpeed(List<VideoInfo> videos, double speed, String format) {
        List<VideoInfo> filtered = new ArrayList<>();

        for (VideoInfo video : videos) {
            if (video.getFormat().equals(format) && canSupportResolution(speed, video.getResolution())) {
                filtered.add(video);
            }
        }

        return filtered;
    }

    private boolean canSupportResolution(double speed, String resolution) {
        return switch (resolution) {
            case "240p" -> speed >= Constants.BITRATE_240P;
            case "360p" -> speed >= Constants.BITRATE_360P;
            case "480p" -> speed >= Constants.BITRATE_480P;
            case "720p" -> speed >= Constants.BITRATE_720P;
            case "1080p" -> speed >= Constants.BITRATE_1080P;
            default -> false;
        };
    }

    private void handleStreamRequest(BufferedReader in, PrintWriter out) throws IOException {
        String movieName = in.readLine();
        String resolution = in.readLine();
        String format = in.readLine();
        String protocol = in.readLine();

        logger.info("Stream request - Movie: " + movieName + ", Resolution: " + resolution +
                ", Format: " + format + ", Protocol: " + protocol);

        String videoPath = Constants.VIDEO_DIR + movieName + "-" + resolution + "." + format;
        File videoFile = new File(videoPath);

        if (videoFile.exists()) {
            out.println(Protocol.STREAMING);
            out.flush();

            // Stream based on protocol
            switch (protocol.toUpperCase()) {
                case "TCP":
                    VideoStreamer.streamViaTCP(videoPath, clientSocket.getOutputStream());
                    break;
                case "UDP":
                    VideoStreamer.streamViaUDP(videoPath, clientSocket);
                    break;
                case "RTP":
                    VideoStreamer.streamViaRTP(videoPath, clientSocket);
                    break;
                default:
                    VideoStreamer.streamViaTCP(videoPath, clientSocket.getOutputStream());
            }
        } else {
            out.println(Protocol.NOT_FOUND);
            out.flush();
            logger.warning("Video not found: " + videoPath);
        }
    }

    private void closeQuietly(Socket socket) {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
    }
}