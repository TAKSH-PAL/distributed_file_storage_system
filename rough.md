Yes! I am documenting every single architectural choice, technology selection, and design pattern directly in your repository under [docs/decisions.md](file:///Users/takshpal/Desktop/distributed_file_storage_system/docs/decisions.md). 

This file is built specifically to serve as a **personal study guide** and **interview preparation document** for you. It explains not just *what* we built, but the deep computer science and system design **rationales** behind *why* we built it that way. 

Here is a summary of how the documentation in [decisions.md](file:///Users/takshpal/Desktop/distributed_file_storage_system/docs/decisions.md) is structured so you can easily explain it to an interviewer:

---

### Key Interview Topics Documented in [decisions.md](file:///Users/takshpal/Desktop/distributed_file_storage_system/docs/decisions.md)

#### 1. Rationale for Java 21 (LTS) & Virtual Threads (Project Loom)
* **What you tell the interviewer**: *"A distributed file storage system is heavily I/O-bound. Instead of building a complex asynchronous reactive setup (like Spring WebFlux) or wasting resources on heavyweight OS threads, we used Java 21's Virtual Threads. This allows us to write simple, blocking, thread-per-request code that scales efficiently to thousands of concurrent connections with minimal memory overhead."*

#### 2. Rationale for Multi-Module Maven Structure
* **What you tell the interviewer**: *"We designed a nested parent-child Maven layout (`titanfs`). The root POM inherits from `spring-boot-starter-parent`, centralizing dependency version management (such as Spring Boot and Lombok) for all modules. This ensures version consistency across the entire cluster and simplifies dependency inheritance."*

#### 3. Rationale for Decoupling Metadata from Chunks
* **What you tell the interviewer**: *"We isolated concerns strictly between the control plane (`metadata-server`) and the data plane (`storage-node`). The storage nodes operate as generic, dumb block-stores. They only care about `chunkId` (UUID) and raw bytes, completely unaware of logical filesystem layouts, directory paths, or user access permissions. The `metadata-server` is the sole source of truth for file-to-chunk mappings, filenames, and replica placements. This decoupled design makes horizontal scaling of storage nodes trivial."*

#### 4. Rationale for Database-Free Storage Nodes (Sidecar Pattern)
* **What you tell the interviewer**: *"To keep storage nodes lightweight and adhere to a 'shared-nothing' architecture, they don't have SQL databases. Instead, they use a Sidecar Metadata pattern. When a chunk is stored, we write a raw data file (`.bin`) and a sidecar JSON file (`.meta`) containing the size, checksum, and creation details. This ensures the node's state is persistent across restarts without database latency."*

#### 5. Rationale for Single-Pass Streaming & Checksum Verification
* **What you tell the interviewer**: *"When uploading a chunk, we process the stream incrementally using `DigestInputStream` wrapping a SHA-256 `MessageDigest`. The chunk is written directly to disk without ever buffering the entire file into memory (preventing OutOfMemory errors). In the same single pass, we compute the size and SHA-256 checksum, which serves as a content-integrity identifier to prevent silent data corruption (bit-rot)."*

#### 6. Rationale for Dynamic Node Registry & Active Heartbeat Sweeper
* **What you tell the interviewer**: *"Our system implements dynamic node discovery. Storage nodes register themselves with the metadata server at startup. The metadata server tracks active nodes and runs a background scheduler every 10 seconds. If a node fails to send a heartbeat within 30 seconds, it's marked as `INACTIVE`. This real-time failure detection ensures chunk uploads are never routed to offline nodes."*

---

### What to study when preparing:
1. Open the [docs/decisions.md](file:///Users/takshpal/Desktop/distributed_file_storage_system/docs/decisions.md) in your editor.
2. Review the class and module relationship diagrams.
3. Understand the difference between the **Logical Namespace** (in `metadata-server` database tables) and the **Physical Blocks** (in the `storage-node` directories).

As we implement the client-side chunking, Docker Compose clustering, and replica copying, I will continue updating this file so that it remains a complete, end-to-end architecture guide for your resume.

# Dynamic Streaming Chunk Upload Gateway Walkthrough

I have successfully transformed **TitanFS** into a fully distributed chunking storage system. Files are now uploaded to the gateway (Metadata Server), which automatically chunks them into 1MB segments, routes them to storage nodes based on capacity, and persists logical mapping records.

---

## 1. Storage Node Telemetry Exposed
* **Telemetry endpoint `/chunks/info`**:
  * [StorageController.java](file:///Users/takshpal/Desktop/distributed_file_storage_system/storage-node/src/main/java/com/titanfs/storage/controller/StorageController.java) now maps `GET /chunks/info`.
  * [StorageService.java](file:///Users/takshpal/Desktop/distributed_file_storage_system/storage-node/src/main/java/com/titanfs/storage/service/StorageService.java) queries `Files.getFileStore().getUsableSpace()` to dynamically report available space in bytes, and counts the `.bin` files inside the storage directory.
  * *Response*:
    ```json
    {"nodeId":"storage-node-default","freeSpace":43518009344,"chunks":0,"status":"UP"}
    ```

---

## 2. Metadata Server Control Plane & Gateway Logic
* **Node Telemetry & Capacity Updates**:
  * Added `freeSpace` field to the `StorageNode` JPA entity.
  * Updated `/nodes/register` and `/nodes/{nodeId}/heartbeat` to receive and store `freeSpace` updates.
* **Placement Services**:
  * [PlacementService.java](file:///Users/takshpal/Desktop/distributed_file_storage_system/metadata-server/src/main/java/com/titanfs/metadata/service/PlacementService.java): Interface for node allocation.
  * [LoadBalancedPlacementService.java](file:///Users/takshpal/Desktop/distributed_file_storage_system/metadata-server/src/main/java/com/titanfs/metadata/service/LoadBalancedPlacementService.java): Selects the active node with the largest `freeSpace` that is greater than or equal to the requested chunk size.
* **Storage Node REST Client**:
  * [StorageNodeClient.java](file:///Users/takshpal/Desktop/distributed_file_storage_system/metadata-server/src/main/java/com/titanfs/metadata/service/StorageNodeClient.java): Builds multipart form requests wrapping chunk byte streams and fires POST requests to target storage nodes.
* **Dynamic Streaming Chunk Gateway**:
  * [NamespaceService.java](file:///Users/takshpal/Desktop/distributed_file_storage_system/metadata-server/src/main/java/com/titanfs/metadata/service/NamespaceService.java): Implements `uploadFile`. It reads the multipart upload stream incrementally, creating **1MB byte chunks** in a sliding buffer.
  * For each chunk, it:
    1. Directs placement to the least loaded node via `PlacementService`.
    2. Uploads the chunk to the node using `StorageNodeClient`.
    3. Saves `FileChunk` and `ChunkReplica` mapping records.
    4. Deducts space locally from the node's cached `freeSpace` so immediate next chunk allocations are load balanced correctly before heartbeats check in.
  * Exposes the gateway endpoint `/files/upload` on [FileNamespaceController.java](file:///Users/takshpal/Desktop/distributed_file_storage_system/metadata-server/src/main/java/com/titanfs/metadata/controller/FileNamespaceController.java).

---

## 3. Operational Settings
* **Multipart Size Configuration**: Configured both servers to allow up to **50MB** files (avoiding default Spring Boot 1MB limits):
  ```properties
  spring.servlet.multipart.max-file-size=50MB
  spring.servlet.multipart.max-request-size=50MB
  ```
* **Relaxed Testing Health Sweep**: Increased the heartbeats active check window from `30s` to `300s` (5 minutes) inside `NodeRegistryService.java` to prevent storage nodes from timing out during manual terminal testing.

---

## 4. Manual Verification & Integration Logs

1. **Started storage node (8080) and metadata server (8081)**.
2. **Registered storage-node**:
   ```bash
   curl -s -X POST -H "Content-Type: application/json" \
     -d '{"nodeId":"storage-node-default","host":"localhost","port":8080,"freeSpace":43518009344}' \
     http://localhost:8081/nodes/register
   ```
3. **Uploaded a 2.5MB test file via the Gateway**:
   ```bash
   dd if=/dev/urandom of=test_large.dat bs=1024 count=2560 && curl -s -X POST -F "file=@test_large.dat" "http://localhost:8081/files/upload?path=/data/test_large.dat"
   ```
   *Response*:
   ```json
   {
     "fileId": "920370d0-9930-4ec0-9c82-1bab5c3c0103",
     "filePath": "/data/test_large.dat",
     "totalSize": 2621440,
     "createdAt": "2026-07-07T18:57:41.251278Z",
     "chunks": [
       {
         "chunkId": "b97c18e2-9f6d-4b60-a8ee-452d0cad2ba6",
         "sequenceNumber": 0,
         "size": 1048576,
         "checksum": "81f08b0695f56414efc0895d37dfe1336f17dc0e61bf85ffa13a6556439a82bb",
         "targetNodes": [{"nodeId": "storage-node-default", "host": "localhost", "port": 8080}]
       },
       {
         "chunkId": "74c40235-967a-406a-801c-71ffadfd5230",
         "sequenceNumber": 1,
         "size": 1048576,
         "checksum": "4ef749325852f14b1e08d113c59b20db01c7c8be07517e0d27c7d6e70a160729",
         "targetNodes": [{"nodeId": "storage-node-default", "host": "localhost", "port": 8080}]
       },
       {
         "chunkId": "5223fa5b-df97-4e50-835c-bb51ef4ce8eb",
         "sequenceNumber": 2,
         "size": 524288,
         "checksum": "0f6323560d7cfb8b1002f94b3d2164f78bf595fae1fa18e2b8822c243098ea78",
         "targetNodes": [{"nodeId": "storage-node-default", "host": "localhost", "port": 8080}]
       }
     ]
   }
   ```
4. **Verified Local Storage Directory**:
   Running `ls -la storage-node/storage` shows 3 binary files (`.bin`) of exactly `1MB`, `1MB`, and `512KB` size alongside their `.meta` sidecars!
5. **Git Cleanup**: Added `*.bin`, `*.meta`, and storage directory mappings to `.gitignore` and untracked sample uploads from the repository.

---

## 5. Engineering Decisions Documented
* **[ENGINEERING.md](file:///Users/takshpal/Desktop/distributed_file_storage_system/ENGINEERING.md)**:
  * **Decision 001**: Decoupling Metadata from storage blocks.
  * **Decision 002**: Architectural selection of a Gateway-based dynamic chunk split upload pattern over client-directed uploads.
  * **Decision 003**: Implementation of Dynamic Storage Node Telemetry (`GET /chunks/info`) for real-time capacity monitoring and load balancing.

  # Sprint 4: Fault Tolerance, Orchestration & Documentation Suite Walkthrough

I have successfully finished Sprint 4. The distributed chunk storage system is now fault-tolerant, self-registering, fully documented, and configured for multi-node deployments.

---

## 1. Dynamic Replication & Chunk Mapping
* **Replication Coordination**:
  * [StorageService.java](file:///Users/takshpal/Desktop/distributed_file_storage_system/storage-node/src/main/java/com/titanfs/storage/service/StorageService.java) and [StorageController.java](file:///Users/takshpal/Desktop/distributed_file_storage_system/storage-node/src/main/java/com/titanfs/storage/controller/StorageController.java) now support an optional `chunkId` parameter on uploads. This allows the metadata server to coordinate and write the same chunk ID across replica nodes.
  * [NamespaceService.java](file:///Users/takshpal/Desktop/distributed_file_storage_system/metadata-server/src/main/java/com/titanfs/metadata/service/NamespaceService.java) replicates each logical chunk to **up to 2 nodes** using a capacity-aware placement engine.
  * *Successful Upload verification*:
    A 2.5MB upload segments into 3 chunks, and writes identical block files (`.bin` and `.meta` sidecars) to both `storage-node/storage1` and `storage-node/storage2` simultaneously!

---

## 2. Dynamic Heartbeats & Self-Registration
* **Zero-Config Storage Node Autostart**:
  * [StorageHeartbeatService.java](file:///Users/takshpal/Desktop/distributed_file_storage_system/storage-node/src/main/java/com/titanfs/storage/service/StorageHeartbeatService.java) handles automatic registration on startup and forwards periodic telemetry heartbeats every 10 seconds.
  * If the metadata server goes offline and comes back, the storage node automatically detects it and re-registers itself.

---

## 3. Metadata Stats Endpoint
* **GET `/stats` API**:
  * Exposes global cluster metrics: active registered nodes, total logical files, and total chunks stored inside the database.
  * *Response*:
    ```json
    {"registeredNodes":2,"storedFiles":1,"chunks":3}
    ```

---

## 4. Multi-Node Docker Orchestration
* **Dockerfiles**: Created runtime JRE targets for both modules inside [docker/](file:///Users/takshpal/Desktop/distributed_file_storage_system/docker).
* **Compose Orchestration**: [docker-compose.yml](file:///Users/takshpal/Desktop/distributed_file_storage_system/docker-compose.yml) boots:
  * 1 `metadata-server`
  * 3 `storage-node` instances (self-registering dynamically via environment overrides)

---

## 5. Professional Documentation Suite
* **[README.md](file:///Users/takshpal/Desktop/distributed_file_storage_system/README.md)**: Completely rewrote the root project layout with logical ASCII architecture map, feature guides, and compose instructions.
* **[docs/architecture.md](file:///Users/takshpal/Desktop/distributed_file_storage_system/docs/architecture.md)**: Added ASCII topological layouts, upload sequence maps, and auto-registration diagrams.
* **[docs/api.md](file:///Users/takshpal/Desktop/distributed_file_storage_system/docs/api.md)**: Documented all exposed REST APIs.
* **[docs/decisions.md](file:///Users/takshpal/Desktop/distributed_file_storage_system/docs/decisions.md)**: Updated design rationale logs to detail the dynamic heartbeating and replication.
