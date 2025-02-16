package org.example;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class PeerNode {


    private static final String CONFIG_FILE = "config.txt";
    private static final String LOG_FILE = "peer_log.txt";
    private static final int MESSAGE_INTERVAL = 5000; // 5 seconds
    private static final int PING_INTERVAL = 13000; // 13 seconds
    private static final int MAX_MISSED_PINGS = 3;
    private static final int HEARTBEAT_INTERVAL = 10000; // 10 seconds


    private static String peerIp;
    private static int peerPort;
    private static final Map<String, PeerInfo> connectedPeers = new HashMap<>();
    private static final Set<Integer> messageList = new HashSet<>();
    private static final Random random = new Random(); // Single Random instance


    static class PeerInfo {
        String ip;
        int port;
        int missedPings;

        PeerInfo(String ip, int port) {
            this.ip = ip;
            this.port = port;
            this.missedPings = 0;
        }

        @Override
        public String toString() {
            return ip + ":" + port;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            PeerInfo peerInfo = (PeerInfo) obj;
            return port == peerInfo.port && ip.equals(peerInfo.ip);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ip, port);
        }
    }

    private static void sendHeartbeatToSeeds() {
        while (true) {
            try {
                Thread.sleep(HEARTBEAT_INTERVAL);
                List<PeerInfo> seeds = loadSeeds();
                for (PeerInfo seed : seeds) {
                    JSONObject heartbeatMessage = new JSONObject();
                    heartbeatMessage.put("type", "heartbeat");
                    heartbeatMessage.put("ip", peerIp);
                    heartbeatMessage.put("port", peerPort);
                    sendToSeed(seed, heartbeatMessage.toString());
                }
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }
    }


    private static List<PeerInfo> loadSeeds() throws IOException {
        List<PeerInfo> seeds = new ArrayList<>();
        try (InputStream inputStream = PeerNode.class.getClassLoader().getResourceAsStream(CONFIG_FILE);
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
                        seeds.add(new PeerInfo(ip, port));
                        logMessage("Loaded seed: " + ip + ":" + port);
                    } catch (NumberFormatException e) {
                        logMessage("Error parsing port number: " + portStr);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to load seeds from config.txt: " + e.getMessage());
            throw e;
        }
        return seeds;
    }

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

    private static void registerWithSeeds() throws IOException {
        List<PeerInfo> seeds = loadSeeds();
        Collections.shuffle(seeds);
        int count = Math.floorDiv(seeds.size(), 2) + 1;

        for (int i = 0; i < count; i++) {
            PeerInfo seed = seeds.get(i);
            JSONObject registerMessage = new JSONObject();
            registerMessage.put("type", "register");
            registerMessage.put("ip", peerIp);
            registerMessage.put("port", peerPort);
            sendToSeed(seed, registerMessage.toString());

        }
    }

    private static void sendToSeed(PeerInfo seed, String message) {
        try (Socket socket = new Socket(seed.ip, seed.port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println(message); // Send JSON message
            String response = in.readLine(); // Read response

            logMessage("Sent to seed " + seed.ip + ":" + seed.port + ": " + message + ", Received: " + response);

        } catch (JSONException e) {
            System.err.println("JSON error: " + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            System.out.println("Failed to contact seed " + seed);
        }
    }

    private static void establishConnections() throws IOException {
        List<PeerInfo> seeds = loadSeeds();
        Set<PeerInfo> peerSet = new HashSet<>();
        for (PeerInfo seed : seeds) {
            peerSet.addAll(getPeersFromSeed(seed));
        }

        List<PeerInfo> peerList = new ArrayList<>(peerSet);
        Collections.shuffle(peerList);

        Map<PeerInfo, Integer> peerDegrees = new HashMap<>();
        for (PeerInfo peer : peerList) {
            peerDegrees.put(peer, 0);
        }

        // Initialize the network with a few initial connections (e.g., fully connected)
        int initialConnections = Math.min(3, peerList.size());  // Start with a small fully connected network if possible.
        for (int i = 0; i < initialConnections; i++) {
            if (i + 1 < initialConnections) {
                PeerInfo peer1 = peerList.get(i);
                PeerInfo peer2 = peerList.get(i + 1);  // Connect each peer to the next one.
                connectToPeer(peer1, peer2);
                connectToPeer(peer2, peer1); // Ensure bidirectional connection

                peerDegrees.put(peer1, peerDegrees.get(peer1) + 1);
                peerDegrees.put(peer2, peerDegrees.get(peer2) + 1);
            }
        }

        // Iteratively add connections based on preferential attachment.
        Random random = new Random();
        for (PeerInfo newPeer : peerList) {
            int connectionsToMake = Math.min(peerList.size() / 2, peerList.size() - connectedPeers.size()); // Connect to a limited number of peers
            for (int i = 0; i < connectionsToMake; i++) {

                PeerInfo selectedPeer = selectPeerBasedOnDegree(peerDegrees, random);  // Use the random instance

                if (selectedPeer != null && !connectedPeers.containsKey(newPeer.ip) && !newPeer.equals(selectedPeer)) {  // Ensure not already connected

                    connectToPeer(newPeer, selectedPeer);
                    connectToPeer(selectedPeer, newPeer); // ensure bidirectional connection

                    peerDegrees.put(newPeer, peerDegrees.get(newPeer) + 1);
                    peerDegrees.put(selectedPeer, peerDegrees.get(selectedPeer) + 1);
                }
            }
        }
    }

    private static PeerInfo selectPeerBasedOnDegree(Map<PeerInfo, Integer> peerDegrees, Random random) {
        double totalDegree = peerDegrees.values().stream().mapToInt(Integer::intValue).sum();
        if (totalDegree <= 0) {
            // Handle the case where total degree is zero (e.g., all nodes have degree 0)
            List<PeerInfo> peers = new ArrayList<>(peerDegrees.keySet());
            if (peers.isEmpty()) return null;
            return peers.get(random.nextInt(peers.size()));  // Select a random peer.
        }

        double rand = random.nextDouble() * totalDegree;
        double cumulativeDegree = 0;
        for (Map.Entry<PeerInfo, Integer> entry : peerDegrees.entrySet()) {
            cumulativeDegree += entry.getValue();
            if (cumulativeDegree >= rand) {
                return entry.getKey();
            }
        }
        return null; // Should not reach here, but handle if it does.
    }


    private static void connectToPeer(PeerInfo peer1, PeerInfo peer2) {
        String peerKey = peer2.ip + ":" + peer2.port;

        // Avoid reconnecting to the same peer
        if (connectedPeers.containsKey(peerKey)) {
            System.out.println("Already connected to peer: " + peerKey);
            return;
        }

        try (Socket socket = new Socket(peer2.ip, peer2.port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("connect:" + peer1.ip + ":" + peer1.port);

            // Expect a response from the peer
            String response = in.readLine();
            if ("ack".equalsIgnoreCase(response)) {
                connectedPeers.put(peerKey, peer2);
                logMessage("Successfully connected to peer: " + peerKey);
            } else {
                logMessage("Peer " + peerKey + " did not acknowledge connection.");
            }

        } catch (IOException e) {
            System.out.println("Failed to connect to peer: " + peerKey);
            logMessage("Failed to connect to peer: " + peerKey);
        }
    }


    private static List<PeerInfo> getPeersFromSeed(PeerInfo seed) throws IOException {
        List<PeerInfo> peerList = new ArrayList<>();
        try (Socket socket = new Socket(seed.ip, seed.port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            JSONObject request = new JSONObject();
            request.put("type", "get_peers");
            out.println(request);


            String response = in.readLine();
            System.out.println("DEBUG: Received response -> " + response); // Check what's received

            if (response == null || response.trim().isEmpty()) {
                throw new IOException("Empty response from SeedNode");
            }

            JSONObject jsonResponse = new JSONObject(response);

            if (jsonResponse.getString("status").equals("success")) {
                JSONArray peers = jsonResponse.getJSONArray("peers");
                for (int i = 0; i < peers.length(); i++) {
                    JSONObject peerJson = peers.getJSONObject(i);
                    String ip = peerJson.getString("ip");
                    int port = peerJson.getInt("port");
                    peerList.add(new PeerInfo(ip, port));
                }
            }
            logMessage("Retrieved peers from seed " + seed.ip + ":" + seed.port + ": " + response);

        } catch (JSONException e) {
            System.err.println("JSON error: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Error parsing JSON response", e);
        } catch (IOException e) {
            System.out.println("Failed to get peers from seed " + seed);
        }
        return peerList;
    }

    private static void gossipMessage() {
        while (true) {
            try {
                UUID messageUUID = UUID.randomUUID();
                String message = System.currentTimeMillis() + ":" + peerIp + ":" + messageUUID.toString();
                //int messageHash = messageUUID.hashCode();
                UUID uuid = UUID.nameUUIDFromBytes(message.getBytes(StandardCharsets.UTF_8));
                int messageHash = uuid.hashCode();

                if (!messageList.contains(messageHash)) {
                    messageList.add(messageHash);
                    for (PeerInfo peer : connectedPeers.values()) {
                        sendMessage(peer.ip, peer.port, message);
                    }
                }
                Thread.sleep(MESSAGE_INTERVAL);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static void sendMessage(String ip, int port, String message) {
        try (Socket socket = new Socket(ip, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println("gossip:" + message);
        } catch (IOException e) {
            System.out.println("Failed to send message to " + ip);
        }
    }

    private static void receiveMessages() {
        try (ServerSocket serverSocket = new ServerSocket(peerPort)) {
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handlePeer(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handlePeer(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String data = in.readLine();
            if (data == null) return;

            String[] parts = data.split(":");
            if (parts[0].equals("gossip")) {
                String message = data.substring(parts[0].length() + 1);
                UUID messageUUID = UUID.nameUUIDFromBytes(message.getBytes(StandardCharsets.UTF_8));  // Create UUID from message content.

                if (!messageList.contains(messageUUID.hashCode())) { // Use UUID's hashcode.  Still not perfect, but better than message.hashcode directly
                    messageList.add(messageUUID.hashCode());//messageHash);

                    // Log the received gossip message, including timestamp and sender IP
                    String logMessage = String.format("Received gossip at %d from %s: %s", System.currentTimeMillis(), socket.getInetAddress().getHostAddress(), message);
                    logMessage(logMessage);
                    for (PeerInfo peer : connectedPeers.values()) {
                        sendMessage(peer.ip, peer.port, message);
                    }
                }
            } else if (parts[0].equals("ping")) {
                // Respond to ping message
                try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                    out.println("pong");
                    // Reset missed pings AFTER sending pong
                    String peerIp = socket.getInetAddress().getHostAddress();  // Extract IP from socket
                    PeerInfo peer = connectedPeers.get(peerIp);
                    if (peer != null) {
                        peer.missedPings = 0;
                    }

                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void pingPeers() {
        while (true) {
            try {
                Thread.sleep(PING_INTERVAL);
                List<String> deadPeers = new ArrayList<>(); // Collect dead peers to avoid ConcurrentModificationException
                for (Map.Entry<String, PeerInfo> entry : connectedPeers.entrySet()) {
                    PeerInfo peer = entry.getValue();
                    if (peer.missedPings >= MAX_MISSED_PINGS) {
                        System.out.println("Peer " + peer.ip + " is dead!");
                        deadPeers.add(peer.ip); // Collect dead peer IPs

                        // Notify seeds about the dead node
                        reportDeadNodeToSeeds(peer);

                    } else {
                        sendPing(peer);
                    }
                }

                // Remove dead peers after iteration to avoid ConcurrentModificationException
                for (String deadPeerIp : deadPeers) {
                    connectedPeers.remove(deadPeerIp);

                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void sendPing(PeerInfo peer) {
        try (Socket socket = new Socket(peer.ip, peer.port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println("ping");
            // peer.missedPings = 0; // Reset missed pings on successful ping response
        } catch (IOException e) {
            System.out.println("Failed to ping peer " + peer.ip + ". Incrementing missed pings.");
            peer.missedPings++;
        }
    }

    private static void reportDeadNodeToSeeds(PeerInfo deadPeer) throws IOException {
        List<PeerInfo> seeds = loadSeeds();
        for (PeerInfo seed : seeds) {
            // Construct the "Dead Node" message as per assignment requirements
            String deadNodeMessage = "Dead Node:" + deadPeer.ip + ":" + deadPeer.port + ":" + System.currentTimeMillis() + ":" + peerIp;
            sendToSeed(seed, deadNodeMessage); // Reuse the sendToSeed method
            logMessage("Sent dead node message to seed " + seed.ip + ":" + seed.port + ": " + deadNodeMessage);
        }
    }


    private static int findAvailablePort() {
        for (int i = 0; i < 10; i++) {
            int port = random.nextInt(1000) + 5001; // Use global random instance
            try (ServerSocket ss = new ServerSocket(port)) {
                return port; // Successfully found an available port
            } catch (IOException e) {
                System.out.println("Port " + port + " is in use. Trying another...");
            }
        }
        System.err.println("Failed to find an available port.");
        return -1;
    }


    private static boolean isGraphConnected() {
        if (connectedPeers.isEmpty()) return false;

        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        String start = connectedPeers.keySet().iterator().next();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            String peerKey = queue.poll();
            for (PeerInfo peer : connectedPeers.values()) {
                String key = peer.ip + ":" + peer.port;
                if (!visited.contains(key)) {
                    visited.add(key);
                    queue.add(key);
                }
            }
        }
        return visited.size() == connectedPeers.size();
    }

    public static void main(String[] args) {
        try {
            peerIp = InetAddress.getLocalHost().getHostAddress();
            peerPort = findAvailablePort();
            if (peerPort == -1) {
                System.err.println("Could not start peer due to port issues.");
                return; // Exit if no port found
            }
            registerWithSeeds();
            establishConnections();

            new Thread(PeerNode::gossipMessage).start();
            new Thread(PeerNode::receiveMessages).start();
            new Thread(PeerNode::pingPeers).start();
            new Thread(PeerNode::sendHeartbeatToSeeds).start();


            isGraphConnected();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
