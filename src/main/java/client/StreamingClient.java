package client;

import shared.*;
import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.logging.Logger;

public class StreamingClient {
    private static final Logger logger = Logger.getLogger(StreamingClient.class.getName());

    public static void main(String[] args) {
        logger.info("Starting Video Streaming Client...");

        try {
            // Measure connection speed
            logger.info("Measuring connection speed...");
            double connectionSpeed = NetworkSpeedTest.measureDownloadSpeed();
            logger.info("Connection speed: " + connectionSpeed + " Mbps");

            // Get format preference from user
            String[] formats = {"mp4", "mkv", "avi"};
            String selectedFormat = (String) JOptionPane.showInputDialog(
                    null, "Select video format:", "Format Selection",
                    JOptionPane.QUESTION_MESSAGE, null, formats, formats[0]);

            if (selectedFormat == null) {
                System.exit(0);
            }

            // Get available videos
            Map<String, List<String>> availableVideos = getAvailableVideos(connectionSpeed, selectedFormat);

            if (!availableVideos.isEmpty()) {
                VideoPlayerPanel videoPlayerPanel = new VideoPlayerPanel();
                ClientHandler.setVideoPlayerPanel(videoPlayerPanel);

                SwingUtilities.invokeLater(() ->
                        new VideoClientUI(availableVideos, videoPlayerPanel, connectionSpeed, selectedFormat));
            } else {
                JOptionPane.showMessageDialog(null, "No videos available from server.",
                        "No Videos", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException e) {
            logger.severe("Connection error: " + e.getMessage());
            JOptionPane.showMessageDialog(null, "Cannot connect to server: " + e.getMessage(),
                    "Connection Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static Map<String, List<String>> getAvailableVideos(double speed, String format) throws IOException {
        try (Socket socket = new Socket(Constants.SERVER_IP, Constants.PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            // Request video list
            out.println(Protocol.LIST);
            out.println(speed);
            out.println(format);
            out.flush();

            int videoCount = Integer.parseInt(in.readLine());
            Map<String, List<String>> availableVideos = new HashMap<>();

            for (int i = 0; i < videoCount; i++) {
                String[] parts = in.readLine().split(",");
                if (parts.length == 2) {
                    availableVideos.computeIfAbsent(parts[0], k -> new ArrayList<>()).add(parts[1]);
                }
            }
            return availableVideos;
        }
    }
}