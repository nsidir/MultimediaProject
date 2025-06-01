package client;

import shared.Constants;
import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.util.*;

public class StreamingClient {
    public static void main(String[] args) {
        try {
            // Get available videos
            Map<String, List<String>> availableVideos = getAvailableVideos();

            if (!availableVideos.isEmpty()) {
                VideoPlayerPanel videoPlayerPanel = new VideoPlayerPanel();
                ClientHandler.setVideoPlayerPanel(videoPlayerPanel);

                SwingUtilities.invokeLater(() ->
                        new VideoClientUI(availableVideos, videoPlayerPanel));
            } else {
                System.out.println("No videos available from server.");
            }
        } catch (IOException e) {
            System.out.println("Connection error: " + e.getMessage());
        }
    }

    private static Map<String, List<String>> getAvailableVideos() throws IOException {
        try (Socket socket = new Socket(Constants.SERVER_IP, Constants.PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            // Request video list
            out.println("LIST");
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