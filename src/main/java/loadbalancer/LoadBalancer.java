package loadbalancer;

import shared.Constants;
import java.io.*;
import java.net.*;
import java.util.*;

public class LoadBalancer {
    private static final List<InetSocketAddress> SERVERS = new ArrayList<>();
    private static int nextServer = 0;

    static {
        // Dynamically populate the SERVERS list
        for (int i = 0; i < Constants.NUM_SERVERS; i++) {
            int port = Constants.PORT + i;
            SERVERS.add(new InetSocketAddress(Constants.SERVER_IP, port));
        }
    }

    public static void main(String[] args) throws IOException {

        ServerSocket balancerSocket = new ServerSocket(Constants.LOAD_BALANCER_PORT);
        System.out.println("Load Balancer listening on port " + Constants.LOAD_BALANCER_PORT);

        while (true) {
            Socket clientSocket = balancerSocket.accept();
            InetSocketAddress backend = SERVERS.get(nextServer);
            nextServer = (nextServer + 1) % SERVERS.size();
            new Thread(() -> handle(clientSocket, backend)).start();
        }
    }

    private static void handle(Socket client, InetSocketAddress backendAddr) {
        try (Socket backend = new Socket(backendAddr.getAddress(), backendAddr.getPort())) {
            Thread c2s = new Thread(() -> forward(client, backend));
            Thread s2c = new Thread(() -> forward(backend, client));
            c2s.start(); s2c.start();
            c2s.join(); s2c.join();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    private static void forward(Socket inSock, Socket outSock) {
        try (InputStream in = inSock.getInputStream();
             OutputStream out = outSock.getOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                out.flush();
            }
        } catch (Exception ignored) {}
    }
}
