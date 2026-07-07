# TitanFS Engineering Journal

This document records chronological system design decisions, trade-offs, and technical rationales for the **TitanFS** distributed file storage system.

---

## Decision 001: Separation of Metadata Server and Storage Nodes

### Context
A distributed storage system requires namespace mapping (directory trees, paths, file ownership) and physical block persistence. We had to decide whether storage nodes should be aware of file layouts or remain stateless.

### Decision
We separated the architecture into two distinct layers:
* **Control Plane (`metadata-server`)**: Owns file-to-chunk mappings, replica locations, placement decisions, and heartbeats.
* **Data Plane (`storage-node`)**: Responsible only for persisting raw binary chunk data identified by UUID.

### Rationale
* **Stateless Storage Scaling**: Storage nodes are completely unaware of logical filesystem layouts, directory paths, or user details. This makes storage nodes stateless with respect to the global namespace, allowing them to scale horizontally.
* **Failure Domain Isolation**: If a storage node crashes, no metadata is lost. The metadata server detects the node offline via missed heartbeats and can re-route clients to another replica.
* **Simple Interface**: The storage node functions as a high-performance, single-key object store (Key = Chunk UUID, Value = Bytes), which is easier to optimize and maintain.

---

## Decision 002: Dynamic Chunking Gateway vs. Client-Direct Uploads

### Context
When a client uploads a file, we can either:
1. Have the client request chunk allocations from the metadata server, split the file locally, and upload chunks directly to the storage nodes (client-direct).
2. Have the client upload the file to the metadata server, which chunks the stream on the fly and forwards the chunks to the storage nodes (gateway-based).

### Decision
For Sprint 2, we implemented the **Gateway-based Upload** pattern. The metadata server acts as a gateway that receives the file, splits it into 1MB chunks on the fly, and streams them directly to storage nodes in parallel or sequentially.

### Rationale
* **Simplified Clients**: Clients do not need to implement complex chunking, hashing, and concurrent HTTP upload logic. A simple `curl` or `POST` request is sufficient.
* **Control over Placement**: The metadata server determines chunk placement in real time based on active node storage capacity and health telemetry.
* **Streaming Efficiency**: The metadata server does not save the uploaded file to its local disk. Instead, it reads the input stream, chunks it in memory, and pipes it directly to the target storage nodes, keeping its own memory footprint extremely low (dependent only on the 1MB buffer size).
