package client;

import shared.*;
import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.*;

public class ClientHandler {
    private static VideoPlayerPanel videoPlayerPanel;
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final int CONNECTION_TIMEOUT = 30000; // 30 seconds

    public static void setVideoPlayerPanel(VideoPlayerPanel panel) {
        videoPlayerPanel = panel;
    }

    public static void streamSelectedVideo(String movieName, String resolution) {
        executor.execute(() -> {
            try (Socket socket = new Socket(Constants.SERVER_IP, Constants.PORT)) {
                socket.setSoTimeout(CONNECTION_TIMEOUT);

                try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                     BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                    out.println(movieName);
                    out.println(resolution);
                    out.flush();

                    String response = in.readLine();
                    if (Protocol.STREAMING.equals(response)) {
                        streamToFile(socket);
                    } else if (Protocol.NOT_FOUND.equals(response)) {
                        showError("Video not found on server");
                    }
                }
            } catch (IOException e) {
                showError("Streaming error: " + e.getMessage());
            }
        });
    }

    private static void streamToFile(Socket socket) throws IOException {
        File tempFile = File.createTempFile("stream_", ".mkv");
        tempFile.deleteOnExit();

        try (InputStream in = socket.getInputStream();
             FileOutputStream out = new FileOutputStream(tempFile)) {

            byte[] buffer = new byte[65536];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }

        // Verify file was created and has content
        System.out.println("Temp file created: " + tempFile.getAbsolutePath());
        System.out.println("File size: " + tempFile.length() + " bytes");

        SwingUtilities.invokeLater(() -> {
            if (videoPlayerPanel != null) {
                try {
                    videoPlayerPanel.playVideo(tempFile);
                } catch (Exception e) {
                    System.err.println("Playback error: " + e.getMessage());
                    e.printStackTrace();
                    showError("Failed to play video: " + e.getMessage());
                }
            } else {
                showError("Video player not initialized");
            }
        });
    }
    private static void showError(String message) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE));
    }
}