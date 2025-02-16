package org.example;

import java.io.*;
import java.net.*;
import java.util.*;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

public class SeedNode {
    private static final int SEED_PORT = 5000;
    private static final String CONFIG_FILE = "config.txt";
    private static final String LOG_FILE = "seed_log.txt"; // Log file for SeedNode
    private static Set<PeerNode.PeerInfo> connectedPeers = new HashSet<>();
    private static Map<String, PeerNode.PeerInfo> peerList = new HashMap<>();

    private static void logMessage(String message) {
        try (FileWriter fw = new FileWriter(LOG_FILE, true); // Append mode
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println(message);
            System.out.println(message); // Also print to console
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }


    // Load existing peers from config.txt (integrated from NetworkConfig)
    private static void loadPeersFromFile() throws IOException {
        try (InputStream inputStream = SeedNode.class.getClassLoader().getResourceAsStream(CONFIG_FILE);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            if (inputStream == null) {
                throw new FileNotFoundException("config.txt not found in resources");
            }

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    String ip = parts[0].trim();
                    String portStr = parts[1].trim();
                    try {
                        int port = Integer.parseInt(portStr);
                        peerList.put(ip, new PeerNode.PeerInfo(ip, port));
                        logMessage("Loaded peer: " + ip + ":" + port);
                    } catch (NumberFormatException e) {
                        logMessage("Error parsing port number: " + portStr);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to load peers from config.txt: " + e.getMessage());
        }
    }


    // Save updated peer list to config.txt (integrated from NetworkConfig)
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
            logMessage("Received message: " + message);  // Add this line
            if (message == null) return;

            JSONObject jsonMessage = new JSONObject(message);
            String type = jsonMessage.getString("type");

            if (type.equals("register")) {
                String peerIp = jsonMessage.getString("ip");
                int peerPort = jsonMessage.getInt("port");
                PeerNode.PeerInfo peerInfo = new PeerNode.PeerInfo(peerIp, peerPort);
                peerList.put(peerIp, peerInfo);
                connectedPeers.add(peerInfo);
                JSONObject response = new JSONObject();
                response.put("status", "success");
                response.put("message", "Registered successfully: " + peerIp + ":" + peerPort);
                out.println(response.toString());
                savePeersToFile();
                logMessage("Registered peer: " + peerIp + ":" + peerPort);  // Add this line

            } else if (type.equals("get_peers")) {
                // Send list of peers to requesting node
                JSONObject response = new JSONObject();
                response.put("status", "success");
                JSONArray peersArray = new JSONArray();
                for (PeerNode.PeerInfo peer : peerList.values()) {
                    JSONObject peerJson = new JSONObject();
                    peerJson.put("ip", peer.ip);
                    peerJson.put("port", peer.port);
                    peersArray.put(peerJson);
                }
                response.put("peers", peersArray);
                out.println(response.toString());
                logMessage("Sent peer list: " + response.toString());  // Add this line
            } else if (type.startsWith("Dead Node:")) {
                // Handle dead node notification
                String[] parts = message.split(":");
                if (parts.length >= 3) {
                    String deadIp = parts[1];
                    int deadPort = Integer.parseInt(parts[2]);
                    peerList.remove(deadIp);
                    connectedPeers.removeIf(p -> p.ip.equals(deadIp) && p.port == deadPort);
                    savePeersToFile();
                    System.out.println("Removed dead node: " + deadIp + ":" + deadPort);
                    logMessage("Removed dead node: " + deadIp + ":" + deadPort);  // Add this line
                }
            }
        }  catch (JSONException e) {
            System.err.println("JSON error: " + e.getMessage());
            e.printStackTrace();
        }
        catch (IOException e) {
            System.out.println("Error handling peer message: " + e.getMessage());
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
