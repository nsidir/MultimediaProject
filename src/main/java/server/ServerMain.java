package server;

import shared.Constants;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.logging.Level;

public class ServerMain {
    private static final Logger logger = Logger.getLogger(ServerMain.class.getName());
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(10);

    public static void main(String[] args) {
        logger.info("Starting Video Streaming Server...");

        try {
            // Generate missing videos first
            logger.info("Generating missing video files...");
            VideoGenerator.generateMissingVideos();
            logger.info("Video generation complete!");

            // Start the server
            try (ServerSocket serverSocket = new ServerSocket(Constants.PORT)) {
                logger.info("Streaming Server is running on port " + Constants.PORT);

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    logger.info("New client connected: " + clientSocket.getInetAddress());
                    threadPool.execute(new ClientHandler(clientSocket));
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Server error: " + e.getMessage(), e);
        } finally {
            threadPool.shutdown();
        }
    }
}