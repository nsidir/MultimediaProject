package server;

import shared.Constants;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerMain {
    public static void main(String[] args) {
        try {
            // Generate missing videos first
            System.out.println("Generating missing video files...");
            VideoGenerator.generateMissingVideos();
            System.out.println("Video generation complete!");

            // Start the server
            try (ServerSocket serverSocket = new ServerSocket(Constants.PORT)) {
                System.out.println("Streaming Server is running on port " + Constants.PORT);

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected: " + clientSocket.getInetAddress());
                    new Thread(new ClientHandler(clientSocket)).start();
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}