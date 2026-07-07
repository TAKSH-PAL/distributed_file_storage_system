# Distributed File Storage System

A distributed file storage system consisting of a metadata server, storage nodes, a client library/CLI, and shared common utilities.

## Project Structure

- `metadata-server/`: Manages file metadata, namespace registry, chunk/block mappings, and storage node status.
- `storage-node/`: Stores the actual file chunks/blocks and handles data read/write requests.
- `client/`: Client-side library or CLI to interact with the distributed file storage system.
- `common/`: Shared code, protocols, configuration definitions, and helper utilities.
- `docs/`: System architecture diagrams, API specifications, and documentation.
- `docker/`: Dockerfiles and docker-compose configurations for local development and multi-container deployment.
