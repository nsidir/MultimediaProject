package server;

import shared.*;
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private static final int STREAM_TIMEOUT = 30000; // 30 seconds

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
                if ("LIST".equals(requestType)) {
                    handleListRequest(out);
                } else {
                    handleStreamRequest(requestType, in, out);
                }
            }
        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
        } finally {
            closeQuietly(clientSocket);
        }
    }

    private void handleListRequest(PrintWriter out) throws IOException {
        List<VideoInfo> videos = FFmpegProcessor.getAvailableVideos();
        out.println(videos.size());
        for (VideoInfo video : videos) {
            out.println(video.getName() + "," + video.getResolution());
        }
        out.flush();
    }

    private void handleStreamRequest(String movieName, BufferedReader in, PrintWriter out) throws IOException {
        String resolution = in.readLine();
        String videoPath = Constants.VIDEO_DIR + movieName + "-" + resolution + ".mp4";

        if (new File(videoPath).exists()) {
            out.println(Protocol.STREAMING);
            out.flush();
            VideoStreamer.streamVideo(videoPath, clientSocket.getOutputStream());
        } else {
            out.println(Protocol.NOT_FOUND);
            out.flush();
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