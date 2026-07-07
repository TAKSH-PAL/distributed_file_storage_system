# TitanFS: Distributed File Storage System

TitanFS is a highly-available, horizontally scalable, chunk-based distributed file storage system built on **Java 21 (Virtual Threads)** and **Spring Boot 3**. 

It decouples logical filesystem structures (namespaces and paths) from raw binary storage blocks (data plain). Uploaded files are segmented into 1MB chunks, load-balanced across active nodes based on system capacity, and replicated for fault tolerance.

---

## Architecture

```text
                  Client
                     │
                     ▼ (REST / HTTP)
          Metadata Server
        (Control Plane)
                     │
      ┌──────────────┼──────────────┐ (Docker bridge network)
      ▼              ▼              ▼
  Storage 1      Storage 2      Storage 3
```

For a comprehensive view of the write sequences and heartbeat loops, see [docs/architecture.md](docs/architecture.md).

---

## Key Features

* **Control Plane / Data Plane Separation**: Storage nodes are completely stateless key-value object stores (Key = UUID, Value = Bytes), making it trivial to scale the cluster horizontally.
* **On-the-Fly Upload Gateway**: Files uploaded to the control plane are chunked into 1MB blocks in memory and streamed directly to storage nodes, maintaining a constant low memory footprint.
* **N-Way Replication**: Chunks are replicated across multiple nodes (default: 2 replicas). If a single node goes offline, files can still be reconstructed from other active nodes.
* **Load-Balanced Placement Selection**: Blocks are routed to nodes dynamically based on active filesystem capacity (`freeSpace` telemetry) reported by the node.
* **Auto-Registration & Heartbeat Sweep**: Storage nodes dynamically self-register with the control plane and update capacity stats every 10 seconds. The server marks nodes `INACTIVE` if heartbeats stop.
* **Zero-Config Local Profile**: Runs an in-memory database configuration (H2) for instant local validation, alongside PostgreSQL environments.

---

## How to Run

### Option 1: Multi-Node Orchestration (Recommended)
You can boot a complete cluster containing 1 Metadata Server and 3 Storage Nodes using Docker Compose.

1. Compile and build the project JARs:
   ```bash
   export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
   mvn clean package -DskipTests
   ```
2. Launch the dockerized cluster:
   ```bash
   docker compose up --build
   ```
* Ports exposed:
  * Control Plane: `http://localhost:8081`
  * Storage Node 1: `http://localhost:8082` (internal port 8080)
  * Storage Node 2: `http://localhost:8083` (internal port 8080)
  * Storage Node 3: `http://localhost:8084` (internal port 8080)

### Option 2: Local Manual Startup
1. Start `storage-node` on port `8080`:
   ```bash
   mvn -pl storage-node spring-boot:run
   ```
2. Start `metadata-server` on port `8081`:
   ```bash
   mvn -pl metadata-server spring-boot:run
   ```

---

## REST API Summary

* **Get Cluster Stats**: `GET http://localhost:8081/stats`
* **Upload File**: `POST http://localhost:8081/files/upload?path=/data/movie.mp4`
* **Lookup Chunk Mappings**: `GET http://localhost:8081/files/lookup?path=/data/movie.mp4`
* **Check Node Telemetry**: `GET http://localhost:8080/chunks/info`

For detailed parameter structures, view [docs/api.md](docs/api.md).

---

## System Documentation Logs

* [docs/decisions.md](docs/decisions.md) - Chronological design history and trade-offs.
* [ENGINEERING.md](ENGINEERING.md) - Deep architectural decisions log.
