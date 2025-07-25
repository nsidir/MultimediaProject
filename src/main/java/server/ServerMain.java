package server;

import shared.Constants;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import javax.net.ssl.*;

public class ServerMain {
    private static final Logger logger = LogManager.getLogger(ServerMain.class);
    private static final ExecutorService threadPool = Executors.newCachedThreadPool();

    public static void main(String[] args) throws InterruptedException {
        logger.info("Starting Video Streaming Server...");
        VideoGenerator.generateMissingVideos();
        logger.info("Video generation complete!");

        ExecutorService pool = Executors.newFixedThreadPool(Constants.NUM_SERVERS);
        for (int i = 0; i < Constants.NUM_SERVERS; i++) {
            final int serverPort = Constants.PORT + i;
            pool.execute(() -> {
                try (ServerSocket serverSocket = createServerSocket(serverPort)) {
                    logger.info("Streaming Server is running on port " + serverPort + (Constants.USE_SSL ? " (SSL)" : ""));
                    while (true) {
                        Socket clientSocket = serverSocket.accept();
                        logger.info("New client connected: " + clientSocket.getInetAddress() + ":" + clientSocket.getPort()
                                + " on server port " + serverPort);
                        threadPool.execute(new ClientHandler(clientSocket));
                    }
                } catch (IOException e) {
                    logger.error("Server error on port " + serverPort + ": " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }
    }

    private static ServerSocket createServerSocket(int port) throws IOException {
        if (!Constants.USE_SSL) return new ServerSocket(port);
        try {
            // Set system properties (needed for default factory)
            System.setProperty("javax.net.ssl.keyStore", Constants.KEYSTORE_PATH);
            System.setProperty("javax.net.ssl.keyStorePassword", Constants.KEYSTORE_PASSWORD);

            // Use explicit SSL context
            char[] pass = Constants.KEYSTORE_PASSWORD.toCharArray();
            java.security.KeyStore ks = java.security.KeyStore.getInstance("JKS");
            ks.load(new java.io.FileInputStream(Constants.KEYSTORE_PATH), pass);

            javax.net.ssl.KeyManagerFactory kmf = javax.net.ssl.KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, pass);

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(kmf.getKeyManagers(), null, null);

            SSLServerSocketFactory ssf = sc.getServerSocketFactory();
            return ssf.createServerSocket(port);
        } catch (Exception e) {
            throw new IOException("Could not start SSL server socket", e);
        }
    }
}
