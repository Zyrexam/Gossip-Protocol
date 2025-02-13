package org.example;


import java.io.*;
import java.net.*;
import java.util.*;

public class PeerNode {
    private static final String CONFIG_FILE = "config.txt";
    private static final int MESSAGE_INTERVAL = 5000; // 5 seconds
    private static final int PING_INTERVAL = 13000; // 13 seconds
    private static final int MAX_MISSED_PINGS = 3;

    private static String peerIp;
    private static int peerPort;
    private static Map<String, PeerInfo> connectedPeers = new HashMap<>();
    private static Set<Integer> messageList = new HashSet<>();

    static class PeerInfo {
        String ip;
        int port;
        int missedPings;

        PeerInfo(String ip, int port) {
            this.ip = ip;
            this.port = port;
            this.missedPings = 0;
        }
    }

    // Load seeds from config.txt
    private static List<PeerInfo> loadSeeds() throws IOException {
        List<PeerInfo> seeds = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(CONFIG_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    String ip = parts[0];
                    int port = Integer.parseInt(parts[1]);
                    seeds.add(new PeerInfo(ip, port));
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to load seeds from config.txt: " + e.getMessage());
            throw e;
        }
        return seeds;
    }

    private static void registerWithSeeds() throws IOException {
        List<PeerInfo> seeds = loadSeeds();
        Collections.shuffle(seeds);
        int count = Math.max(1, seeds.size() / 2 + 1);
        for (int i = 0; i < count; i++) {
            PeerInfo seed = seeds.get(i);
            sendToSeed(seed, "register:" + peerIp + ":" + peerPort);
        }
    }

    private static void sendToSeed(PeerInfo seed, String message) {
        try (Socket socket = new Socket(seed.ip, seed.port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println(message);
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

        // Power-Law Degree Distribution: Preferential Attachment
        Map<String, Integer> peerDegrees = new HashMap<>();
        for (PeerInfo peer : peerList) {
            peerDegrees.put(peer.ip, 0); // Initialize degree to 0
        }

        int numPeers = Math.max(1, (int) Math.pow(peerList.size(), 0.7)); // Number of peers to connect to
        for (int i = 0; i < numPeers; i++) {
            PeerInfo peer = selectPeerBasedOnDegree(peerList, peerDegrees);
            connectedPeers.put(peer.ip, peer);
            peerDegrees.put(peer.ip, peerDegrees.get(peer.ip) + 1); // Increase degree of selected peer
        }
    }

    private static PeerInfo selectPeerBasedOnDegree(List<PeerInfo> peerList, Map<String, Integer> peerDegrees) {
        double totalDegree = peerDegrees.values().stream().mapToInt(Integer::intValue).sum();
        double rand = Math.random() * totalDegree;
        double cumulativeDegree = 0;
        for (PeerInfo peer : peerList) {
            cumulativeDegree += peerDegrees.get(peer.ip);
            if (cumulativeDegree >= rand) {
                return peer;
            }
        }
        return peerList.get(peerList.size() - 1); // Fallback in case something goes wrong
    }

    private static List<PeerInfo> getPeersFromSeed(PeerInfo seed) {
        List<PeerInfo> peerList = new ArrayList<>();
        try (Socket socket = new Socket(seed.ip, seed.port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println("get_peers");
            String response = in.readLine();
            String[] peerData = response.split(",");
            for (String peer : peerData) {
                String[] parts = peer.split(":");
                if (parts.length == 2) {
                    String ip = parts[0];
                    int port = Integer.parseInt(parts[1]);
                    peerList.add(new PeerInfo(ip, port));
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to get peers from seed " + seed);
        }
        return peerList;
    }

    private static void gossipMessage() {
        while (true) {
            try {
                String message = System.currentTimeMillis() + ":" + peerIp + ":" + new Random().nextInt(1000);
                int messageHash = message.hashCode();
                messageList.add(messageHash);
                for (PeerInfo peer : connectedPeers.values()) {
                    sendMessage(peer.ip, peer.port, message);
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
            String[] parts = data.split(":");
            if (parts[0].equals("gossip")) {
                String message = data.substring(parts[0].length() + 1);
                int messageHash = message.hashCode();
                if (!messageList.contains(messageHash)) {
                    messageList.add(messageHash);
                    System.out.println("Received gossip: " + message);
                    for (PeerInfo peer : connectedPeers.values()) {
                        sendMessage(peer.ip, peer.port, message);
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
                for (Map.Entry<String, PeerInfo> entry : connectedPeers.entrySet()) {
                    PeerInfo peer = entry.getValue();
                    if (peer.missedPings >= MAX_MISSED_PINGS) {
                        System.out.println("Peer " + peer.ip + " is dead!");
                        connectedPeers.remove(peer.ip);
                    } else {
                        sendPing(peer);
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static void sendPing(PeerInfo peer) {
        try (Socket socket = new Socket(peer.ip, peer.port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println("ping");
            peer.missedPings = 0; // Reset missed pings on successful ping response
        } catch (IOException e) {
            System.out.println("Failed to ping peer " + peer.ip + ". Incrementing missed pings.");
            peer.missedPings++;
        }
    }

    public static void main(String[] args) {
        try {
            peerIp = InetAddress.getLocalHost().getHostAddress();
            peerPort = new Random().nextInt(1000) + 5001;
            registerWithSeeds();
            establishConnections();

            new Thread(PeerNode::gossipMessage).start();
            new Thread(PeerNode::receiveMessages).start();
            new Thread(PeerNode::pingPeers).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
