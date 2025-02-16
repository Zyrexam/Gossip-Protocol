package org.example;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

public class SeedNode {
    private static final String CONFIG_FILE = "config.txt";
    private static final String LOG_FILE = "seed_log.txt"; // Log file for SeedNode
    private static Set<PeerNode.PeerInfo> connectedPeers = new HashSet<>();
    private static Map<String, PeerNode.PeerInfo> peerList = new HashMap<>();
    private static Map<String, Long> lastHeartbeat = new ConcurrentHashMap<>();
    private static final long TIMEOUT = 10000; // 10 seconds timeout


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
    private static void loadSeedsFromFile() throws IOException {
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
                        logMessage("Loaded Seeds: " + ip + ":" + port);

                        // 🔥 Start the server for this seed immediately
                        new Thread(() -> startServer(port)).start();

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
    private static void startServer(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            logMessage("Seed Node listening on port " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                logMessage("Accepted connection from " + socket.getInetAddress());
                new Thread(() -> handlePeerRegistration(socket)).start();
            }
        } catch (IOException e) {
            logMessage("Error starting server on port " + port + ": " + e.getMessage());
        }
    }


    private static void handlePeerRegistration(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {


            String message = in.readLine();
            logMessage("DEBUG: Received raw message -> " + message);

            if (message == null || message.trim().isEmpty()) {
                logMessage("ERROR: Received empty message. Ignoring...");
                return;
            }

// Check if the message starts with '{' (valid JSON object)
            if (!message.trim().startsWith("{")) {
                logMessage("ERROR: Message is not JSON! Received -> " + message);
                return;
            }



            JSONObject jsonMessage = new JSONObject(message);
            String type = jsonMessage.getString("type");

            if (type.equals("register")) {
                // Register new peer
                String peerIp = jsonMessage.getString("ip");
                int peerPort = jsonMessage.getInt("port");

                PeerNode.PeerInfo peerInfo = new PeerNode.PeerInfo(peerIp, peerPort);
                peerList.put(peerIp, peerInfo);
                connectedPeers.add(peerInfo);

                JSONObject response = new JSONObject();
                response.put("status", "success");
                response.put("message", "Registered successfully: " + peerIp + ":" + peerPort);

                out.println(response.toString());  // Send response
                logMessage("Registered peer: " + peerIp + ":" + peerPort);
                savePeersToFile();

            } else if (type.equals("get_peers")) {
                // Send list of connected peers
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
                out.println(response);  // Send response
                logMessage("Sent peer list: " + response);

            } else if (type.equals("dead_node")) {
                // Handle dead node removal
                String deadIp = jsonMessage.getString("ip");
                int deadPort = jsonMessage.getInt("port");

                peerList.remove(deadIp);
                connectedPeers.removeIf(p -> p.ip.equals(deadIp) && p.port == deadPort);

                savePeersToFile();
                logMessage("Removed dead node: " + deadIp + ":" + deadPort);
            }

            out.println("ACK");  // Acknowledge message
        }
        catch (JSONException e) {
            logMessage("JSON error: " + e.getMessage());
            e.printStackTrace();
        }
        catch (IOException e) {
            logMessage("Error handling peer message: " + e.getMessage());
        }
    }
    private static void removeDeadPeers() {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> iterator = lastHeartbeat.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (currentTime - entry.getValue() > TIMEOUT) {
                String deadPeer = entry.getKey();
                iterator.remove();
                peerList.remove(deadPeer.split(":")[0]);
                logMessage("Removed dead peer: " + deadPeer);
            }
        }
    }


    public static void main(String[] args) {
        try {
            loadSeedsFromFile();  // Load seed nodes from config.txt

            for (PeerNode.PeerInfo seed : peerList.values()) {
                int port = seed.port;  // Get the port from the loaded seeds
                new Thread(() -> startServer(port));  // Start server dynamically
            }
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

}
