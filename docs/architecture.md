# TitanFS Architectural Layout

This document describes the logical layout and write sequence flow of the TitanFS Distributed File Storage System.

---

## 1. Logical Cluster Architecture

```text
                  +-----------------------+
                  |         Client        |
                  +-----------------------+
                              |
                              | REST / HTTP API
                              v
                  +-----------------------+
                  |    Metadata Server    |
                  |     (Control Plane)   |
                  +-----------------------+
                              |
       +----------------------+----------------------+
       | Bridge Network       |                      |
       v (internal HTTP)      v                      v
+--------------+       +--------------+       +--------------+
| storage-node |       | storage-node |       | storage-node |
|    (Node 1)  |       |    (Node 2)  |       |    (Node 3)  |
+--------------+       +--------------+       +--------------+
| Local Disk 1 |       | Local Disk 2 |       | Local Disk 3 |
+--------------+       +--------------+       +--------------+
```

---

## 2. Dynamic Streaming Chunk Upload & Replication Sequence

This sequence diagram details the lifecycle of a client file write. The metadata server segments the file stream on the fly and replicates the blocks across healthy storage nodes.

```text
Client          Metadata Server          PlacementService          StorageNode 1          StorageNode 2
  |                     |                        |                       |                      |
  |--- POST /upload --->|                        |                       |                      |
  |    (movie.mp4)      |                        |                       |                      |
  |                     |-- Read 1MB chunk ----->|                       |                      |
  |                     |   from input stream    |                       |                      |
  |                     |                        |                       |                      |
  |                     |------- selectNodes() ->|                       |                      |
  |                     |                        |                       |                      |
  |                     |<-- [Node 1, Node 2] ---|                       |                      |
  |                     |    (Based on freeSpace)|                       |                      |
  |                     |                        |                       |                      |
  |                     |----------------- POST /chunks?chunkId=A ------>|                      |
  |                     |                 (Replicate Chunk 1)            |                      |
  |                     |<---------------- ChunkMetadata response -------|                      |
  |                     |                                                |                      |
  |                     |--------------------------------- POST /chunks?chunkId=A ------------->|
  |                     |                                 (Replicate Chunk 1)                   |
  |                     |<-------------------------------- ChunkMetadata response --------------|
  |                     |                                                |                      |
  |                     |-- Reserve capacity locally                     |                      |
  |                     |   for Node 1 & Node 2                          |                      |
  |                     |                                                |                      |
  |                     |-- Repeat for remaining chunks...               |                      |
  |                     |                                                |                      |
  |                     |-- Save StoredFile, FileChunk,                  |                      |
  |                     |   and ChunkReplica mapping to DB               |                      |
  |                     |                        |                       |                      |
  |<- 201 Response -----|                        |                       |                      |
  |   (Logical Map)     |                        |                       |                      |
```

---

## 3. Dynamic Telemetry & Auto-Registration Loop

To keep capacity metrics completely up-to-date and maintain a zero-config setup, the storage nodes run a dynamic scheduling thread:

```text
+-----------------------+              +-----------------------+
|  Storage Node (8082)  |              |    Metadata Server    |
+-----------------------+              +-----------------------+
            |                                      |
            |-- 1. Inspect Filesystem Usable Space |
            |                                      |
            |-- 2. Check Local Chunks Count        |
            |                                      |
            |------------- POST /nodes/register -->|
            |              (Node ID, Host, Port)   |
            |                                      |
            |<------------ Registration OK --------|
            |                                      |
            |-- 3. Loop: POST Heartbeat (10s) ---->|
            |            (Pass freeSpace)          |
```
