# Peer-to-Peer Gossip Protocol

## Project Overview
This project implements a **peer-to-peer gossip protocol** using Java. The goal is to create a decentralized network of peer nodes that can communicate and exchange information efficiently, ensuring resilience and network connectivity.

The network consists of **peer nodes** and **seed nodes** that help with peer discovery and maintaining connectivity. Each peer must connect to at least \\(\lfloor(n/2)\\) + 1 seed nodes out of `n` available seed nodes. Once connected, peers maintain a random subset of connections with other peers, following a **power-law degree distribution** to simulate realistic network structures.

The system ensures the network is **connected**, meaning every peer is reachable either directly or indirectly. The **gossip protocol** allows peers to exchange messages periodically with randomly selected neighbors, propagating information throughout the network. Additionally, peers **monitor each other's liveness** and report failures to seed nodes to maintain an updated peer list.

## Git Repository
- **Repository URL:** [https://github.com/Zyrexam/Gossip-Protocol.git](https://github.com/Zyrexam/Gossip-Protocol.git)

## Folder Directory Structure
```
project-root/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── org.example/
│   │   │   │   ├── PeerNode.java
│   │   │   │   ├── SeedNode.java 
│   ├── test/
├── pom.xml
├── README.md
├── config.txt

```

## Execution Steps
### 1. **Clone the Repository**
```sh
git clone https://github.com/Zyrexam/Gossip-Protocol
cd project-root
```

### 2. **Build the Project**
Ensure you have Maven installed.
```sh
mvn clean install
```

### 3. **Run the Seed Node**
#### Using Command Line:
```sh
mvn exec:java -Dexec.mainClass="org.example.SeedNode"
mvn exec:java -Dexec.mainClass="org.example.PeerNode"
```
#### Using IntelliJ IDEA or Eclipse:
- Open the project in the IDE.
- Navigate to `SeedNode.java`.
- Click on **Run** to execute the file.
- Navigate to `PeerNode.java`.
- Click on **Run** to execute the file.
## File Descriptions
- **SeedNode.java**
  - Implements the seed node functionality.
- **PeerNode.java**
  - Implements the peer node functionality.
- **config.txt**
  - Contains the IP addresses and ports of seed nodes.
- **seed_log.txt**
  - Logs all activities of seed nodes.
- **peer_log.txt**
  - Logs all activities of peer nodes.


---
**Contributors:** Mohit Kumar (B22CS035)  Satyam Sharma (B22CS047)

