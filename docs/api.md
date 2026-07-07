# TitanFS REST API Specifications

This document outlines the API endpoints exposed by the **Metadata Server (Control Plane)** and the **Storage Nodes (Data Plane)**.

---

## 1. Metadata Server (Control Plane)
Exposed on default port `8081`.

### GET `/stats`
Returns current global metrics about the cluster.
* **Response**: `200 OK`
  ```json
  {
    "registeredNodes": 3,
    "storedFiles": 1,
    "chunks": 3
  }
  ```

### POST `/files/upload`
Uploads a new file to the cluster. The gateway splits the stream into 1MB chunks and distributes replicas.
* **Query Parameters**:
  * `path` (String, required): Virtual path of the file (e.g. `/data/movie.mp4`)
* **Request Body**: Multipart form data with key `file` containing the binary stream.
* **Response**: `201 Created`
  ```json
  {
    "fileId": "920370d0-9930-4ec0-9c82-1bab5c3c0103",
    "filePath": "/data/movie.mp4",
    "totalSize": 2621440,
    "createdAt": "2026-07-08T00:30:10Z",
    "chunks": [
      {
        "chunkId": "b97c18e2-9f6d-4b60-a8ee-452d0cad2ba6",
        "sequenceNumber": 0,
        "size": 1048576,
        "checksum": "81f08b0695f56414...",
        "targetNodes": [
          {"nodeId": "storage-node-1", "host": "storage-node-1", "port": 8080},
          {"nodeId": "storage-node-2", "host": "storage-node-2", "port": 8080}
        ]
      }
    ]
  }
  ```

### GET `/files/lookup`
Resolves chunk storage locations for download.
* **Query Parameters**:
  * `path` (String, required): Virtual path of the file to lookup.
* **Response**: `200 OK` (returns JSON allocation mapping, matching structure above).

---

## 2. Storage Node (Data Plane)
Exposed on internal port `8080` (mapped to `8082`, `8083`, `8084` externally in compose).

### GET `/chunks/info`
Returns current storage capacity telemetry and blocks count.
* **Response**: `200 OK`
  ```json
  {
    "nodeId": "storage-node-1",
    "freeSpace": 43518009344,
    "chunks": 14,
    "status": "UP"
  }
  ```

### POST `/chunks`
Saves a raw binary chunk block.
* **Query Parameters**:
  * `chunkId` (UUID, optional): Specify exact ID for replication coordination. If absent, a random one is generated.
* **Request Body**: Multipart form data with key `file`.
* **Response**: `200 OK` (returns chunk metadata sidecar representation).

### GET `/chunks/{chunkId}`
Downloads the raw binary block stream.
* **Response**: `200 OK` with binary octet stream attachment.

### DELETE `/chunks/{chunkId}`
Deletes the chunk and its sidecar metadata.
* **Response**: `204 No Content`
