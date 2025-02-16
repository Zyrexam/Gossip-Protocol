package org.example;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import static java.lang.System.out;

public class SeedNode {
    private static final String CONFIG_FILE = "config.txt";
    private static final String LOG_FILE = "seed_log.txt"; // Log file for SeedNode
    private static final int HEARTBEAT_TIMEOUT = 15000;
    private static Set<PeerNode.PeerInfo> connectedPeers = new HashSet<>();
    private static Map<String, PeerNode.PeerInfo> peerList = new HashMap<>();
    private static Map<String, Long> lastHeartbeat = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService heartbeatChecker = Executors.newScheduledThreadPool(1);


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

//    // Load existing peers from config.txt
//    private static void loadSeedsFromFile() {
//        File configFile = new File(CONFIG_FILE);
//
//        if (!configFile.exists()) {
//            System.out.println("⚠️ Config file not found: " + configFile.getAbsolutePath());
//            return;
//        }
//
//        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
//            String line;
//            while ((line = reader.readLine()) != null) {
//                line = line.trim();
//                if (line.isEmpty()) continue;
//
//                String[] parts = line.split(":");
//                if (parts.length == 2) {
//                    String ip = parts[0].trim();
//                    int port = Integer.parseInt(parts[1].trim());
//
//                    peerList.put(ip, new PeerNode.PeerInfo(ip, port));
//                    logMessage("✅ Loaded Seed: " + ip + ":" + port);
//                }
//            }
//        } catch (IOException e) {
//            System.out.println("❌ Failed to load peers: " + e.getMessage());
//        }
//    }
//
//    // Save updated peer list to config.txt
//    private static void savePeersToFile() {
//        File configFile = new File(CONFIG_FILE);
//
//        try (PrintWriter writer = new PrintWriter(new FileWriter(configFile))) {
//            for (PeerNode.PeerInfo peer : peerList.values()) {
//                writer.println(peer.ip + ":" + peer.port);
//            }
//            logMessage("📄 Updated config.txt with new peers.");
//        } catch (IOException e) {
//            System.out.println("❌ Error saving peers: " + e.getMessage());
//        }
//    }


    // Load existing peers from config.txt

//    private static void loadSeedsFromFile() throws IOException {
//        try (
//                InputStream inputStream = SeedNode.class.getClassLoader().getResourceAsStream(CONFIG_FILE);
//                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
//
//            if (inputStream == null) {
//                throw new FileNotFoundException("config.txt not found in resources");
//            }
//
//            String line;
//            while ((line = reader.readLine()) != null) {
//                line = line.trim();
//                if (line.isEmpty()) continue;
//
//                String[] parts = line.split(":");
//                if (parts.length == 2) {
//                    String ip = parts[0].trim();
//                    String portStr = parts[1].trim();
//                    try {
//                        int port = Integer.parseInt(portStr);
//                        peerList.put(ip, new PeerNode.PeerInfo(ip, port));
//                        logMessage("Loaded Seeds: " + ip + ":" + port);
//
//                        // 🔥 Start the server for this seed immediately
//                        new Thread(() -> startServer(port)).start();
//
//                    } catch (NumberFormatException e) {
//                        logMessage("Error parsing port number: " + portStr);
//                    }
//                }
//            }
//        } catch (IOException e) {
//            System.out.println("Failed to load peers from config.txt: " + e.getMessage());
    //        }
//    }


    private static void loadSeedsFromFile() throws IOException {
        File configFile = new File(CONFIG_FILE); // Use writable config.txt in root folder

        if (!configFile.exists()) {
            throw new FileNotFoundException("config.txt not found: " + configFile.getAbsolutePath());
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
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

                        // Start the server for this seed immediately
                        new Thread(() -> startServer(port)).start();

                    } catch (NumberFormatException e) {
                        logMessage("Error parsing port number: " + portStr);
                    }
                }
            }
        } catch (IOException e) {
            out.println("Failed to load peers from config.txt: " + e.getMessage());
        }
    }


//    // Save updated peer list to config.txt
//    private static void savePeersToFile() throws IOException {
//        try (PrintWriter writer = new PrintWriter(new FileWriter(CONFIG_FILE))) {
//            for (PeerNode.PeerInfo peer : peerList.values()) {
//                writer.println(peer.ip + ":" + peer.port);
//            }
//        }
//
//    }
// Save updated peer list to config.txt without removing previous peers
private static void savePeersToFile() throws IOException {
    File configFile = new File(CONFIG_FILE);
    Set<String> uniquePeers = new HashSet<>();

    // Read existing peers
    if (configFile.exists()) {
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                uniquePeers.add(line.trim()); // Store existing peers
            }
        } catch (IOException e) {
            logMessage("⚠️ Error reading config file: " + e.getMessage());
        }
    }

    // Add new peers
    for (PeerNode.PeerInfo peer : peerList.values()) {
        uniquePeers.add(peer.ip + ":" + peer.port);
    }

    // Write back all unique peers
    try (PrintWriter writer = new PrintWriter(new FileWriter(CONFIG_FILE))) {
        for (String peer : uniquePeers) {
            writer.println(peer);
        }
    }

    logMessage("📄 Peer list updated in config.txt");
}

    // Accept connections from peers and handle their registration
    private static void startServer(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            logMessage("Seed Node listening on port " + port);
            while (true) {
                Socket socket = serverSocket.accept();
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
            logMessage("Received raw message -> " + message);

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

            } else if (type.equals("heartbeat")) {
                // Update last heartbeat time
                String peerIp = jsonMessage.getString("ip");
                int peerPort = jsonMessage.getInt("port");

                lastHeartbeat.put(peerIp, System.currentTimeMillis());

                JSONObject response = new JSONObject();
                response.put("status", "success");
                response.put("message", "Heartbeat received from " + peerIp + ":" + peerPort);
                out.println(response.toString());  // Send response

                logMessage("✅ Heartbeat received from " + peerIp + ":" + peerPort);
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
        } catch (JSONException e) {
            logMessage("JSON error: " + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            logMessage("Error handling peer message: " + e.getMessage());
        }
    }

    private static void checkHeartbeats() {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> iterator = lastHeartbeat.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            String peerIp = entry.getKey();
            long lastPingTime = entry.getValue();

            if (currentTime - lastPingTime > HEARTBEAT_TIMEOUT) {
                logMessage("Peer " + peerIp + " is unresponsive. Removing from peer list.");

                peerList.remove(peerIp);
                connectedPeers.removeIf(p -> p.ip.equals(peerIp));
                iterator.remove();
            }
        }
    }



    private static void saveDegreeDistribution() {
        Map<Integer, Integer> degreeCount = new HashMap<>();

        // Count the number of connections for each peer
        for (PeerNode.PeerInfo peer : peerList.values()) {
            int degree = getPeerDegree(peer.ip);
            degreeCount.put(degree, degreeCount.getOrDefault(degree, 0) + 1);
        }

        // Save to CSV file
        File file = new File("degree_distribution.csv");
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("degree,count"); // CSV Header
            for (Map.Entry<Integer, Integer> entry : degreeCount.entrySet()) {
                writer.println(entry.getKey() + "," + entry.getValue());
            }
            logMessage("📊 Degree distribution saved to degree_distribution.csv");
        } catch (IOException e) {
            logMessage("❌ Error saving degree distribution: " + e.getMessage());
        }
    }

    // Helper function to get peer degree
    private static int getPeerDegree(String peerIp) {
        int degree = 0;
        for (PeerNode.PeerInfo peer : peerList.values()) {
            if (peer.ip.equals(peerIp)) {
                degree++;
            }
        }
        return degree;
    }

    public static void main(String[] args) throws IOException {
        loadSeedsFromFile();  // Load seed nodes from config.txt

        for (PeerNode.PeerInfo seed : peerList.values()) {
            int port = seed.port;  // Get the port from the loaded seeds
            new Thread(() -> startServer(port)).start();  // Start server dynamically


        }
//        saveDegreeDistribution();

        heartbeatChecker.scheduleAtFixedRate(SeedNode::checkHeartbeats, 10, 10, TimeUnit.SECONDS);
    }

}
