package org.example;

import java.io.*;
import java.util.*;

public class NetworkConfig {
    private static final String CONFIG_FILE = "config.txt";  // Network configuration file for peers and seed nodes
    private static List<PeerInfo> seedNodes = new ArrayList<>();

    // PeerInfo class to store IP and Port for peers and seed nodes
    static class PeerInfo {
        String ip;
        int port;

        PeerInfo(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }

        @Override
        public String toString() {
            return ip + ":" + port;
        }
    }

    // Load the seed nodes from the config.txt file
    public static void loadConfig() throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(CONFIG_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    seedNodes.add(new PeerInfo(parts[0], Integer.parseInt(parts[1])));
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading config file: " + e.getMessage());
            throw e;
        }
    }

    // Get the list of seed nodes
    public static List<PeerInfo> getSeedNodes() {
        return seedNodes;
    }

    // Get a random seed node from the list
    public static PeerInfo getRandomSeedNode() {
        Random rand = new Random();
        return seedNodes.get(rand.nextInt(seedNodes.size()));
    }

    // Save updated peer list to the config file
    public static void saveConfig(List<PeerInfo> peers) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            for (PeerInfo peer : peers) {
                writer.println(peer.ip + ":" + peer.port);
            }
        }
    }

    // Load peers from the config.txt file (same format as seed nodes)
    public static List<PeerInfo> loadPeers() throws IOException {
        List<PeerInfo> peers = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(CONFIG_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    String ip = parts[0];
                    int port = Integer.parseInt(parts[1]);
                    peers.add(new PeerInfo(ip, port));
                }
            }
        }
        return peers;
    }

    // Update the configuration by overwriting the existing file with new peers
    public static void updateConfig(List<PeerInfo> newPeers) throws IOException {
        saveConfig(newPeers);  // Overwrites config.txt with updated list of peers
    }

    public static void main(String[] args) {
        // Example usage of saving/loading network configuration
        try {
            // Example: Save some peers to the config file
            List<PeerInfo> peers = new ArrayList<>();
            peers.add(new PeerInfo("127.0.0.1", 5001));
            peers.add(new PeerInfo("127.0.0.1", 5002));
            peers.add(new PeerInfo("127.0.0.1", 5003));
            saveConfig(peers);

            // Example: Load the peers back from the config file
            List<PeerInfo> loadedPeers = loadPeers();
            for (PeerInfo peer : loadedPeers) {
                System.out.println("Loaded Peer: " + peer.ip + ":" + peer.port);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
