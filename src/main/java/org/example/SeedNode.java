package org.example;

import java.io.*;
import java.net.*;
import java.util.*;

public class SeedNode {
    private static final int SEED_PORT = 5000;
    private static final String CONFIG_FILE = "config.txt";  // Updated to config.txt
    private static Set<PeerNode.PeerInfo> connectedPeers = new HashSet<>();
    private static Map<String, PeerNode.PeerInfo> peerList = new HashMap<>();

    // Load existing peers from config.txt
    private static void loadPeersFromFile() throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(CONFIG_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    String ip = parts[0];
                    int port = Integer.parseInt(parts[1]);
                    peerList.put(ip, new PeerNode.PeerInfo(ip, port));
                }
            }
        }
    }

    // Save updated peer list to config.txt
    private static void savePeersToFile() throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            for (PeerNode.PeerInfo peer : peerList.values()) {
                writer.println(peer.ip + ":" + peer.port);
            }
        }
    }

    // Accept connections from peers and handle their registration
    private static void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(SEED_PORT)) {
            System.out.println("Seed Node listening on port " + SEED_PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handlePeerRegistration(socket)).start();
            }
        } catch (IOException e) {
            System.out.println("Error starting server: " + e.getMessage());
        }
    }

    private static void handlePeerRegistration(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            String message = in.readLine();
            if (message != null && message.startsWith("register:")) {
                String[] parts = message.split(":");
                if (parts.length == 3) {
                    String peerIp = parts[1];
                    int peerPort = Integer.parseInt(parts[2]);
                    PeerNode.PeerInfo peerInfo = new PeerNode.PeerInfo(peerIp, peerPort);
                    peerList.put(peerIp, peerInfo);
                    connectedPeers.add(peerInfo);
                    out.println("Registered successfully: " + peerIp + ":" + peerPort);
                    savePeersToFile();
                }
            }
        } catch (IOException e) {
            System.out.println("Error handling peer registration: " + e.getMessage());
        }
    }

    // Retrieve a list of peers to send to new peer nodes
    private static List<String> getPeers() {
        List<String> peers = new ArrayList<>();
        for (PeerNode.PeerInfo peer : peerList.values()) {
            peers.add(peer.ip + ":" + peer.port);
        }
        return peers;
    }

    public static void main(String[] args) {
        try {
            loadPeersFromFile();
            startServer();
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
