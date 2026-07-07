package com.titanfs.storage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.titanfs.storage.config.StorageConfig;
import com.titanfs.storage.exception.ChunkNotFoundException;
import com.titanfs.storage.exception.StorageException;
import com.titanfs.storage.model.ChunkMetadata;
import com.titanfs.storage.model.NodeInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
public class StorageService {

    private final StorageConfig storageConfig;
    private final ObjectMapper objectMapper;
    private final String nodeId;

    @Autowired
    public StorageService(
            StorageConfig storageConfig,
            ObjectMapper objectMapper,
            @Value("${titanfs.node.id:storage-node-default}") String nodeId) {
        this.storageConfig = storageConfig;
        this.objectMapper = objectMapper;
        this.nodeId = nodeId;
    }

    public ChunkMetadata store(MultipartFile file) {
        return store(file, null);
    }

    public ChunkMetadata store(MultipartFile file, String chunkIdStr) {
        if (file.isEmpty()) {
            throw new StorageException("Failed to store empty file");
        }

        UUID chunkId = (chunkIdStr != null && !chunkIdStr.trim().isEmpty())
                ? UUID.fromString(chunkIdStr)
                : UUID.randomUUID();
        String filename = chunkId + ".bin";
        Path targetPath = storageConfig.getRootPath().resolve(filename);
        Path metaPath = storageConfig.getRootPath().resolve(chunkId + ".meta");

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = 0;

            try (InputStream is = file.getInputStream();
                 DigestInputStream dis = new DigestInputStream(is, digest);
                 OutputStream os = Files.newOutputStream(targetPath)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = dis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                    size += bytesRead;
                }
            }

            String checksum = HexFormat.of().formatHex(digest.digest());
            Instant createdAt = Instant.now();

            ChunkMetadata metadata = ChunkMetadata.builder()
                    .chunkId(chunkId)
                    .storageNodeId(nodeId)
                    .size(size)
                    .checksum(checksum)
                    .createdAt(createdAt)
                    .build();

            // Persist metadata to sidecar JSON file
            objectMapper.writeValue(metaPath.toFile(), metadata);

            log.info("Successfully stored chunk: id={}, size={}, checksum={}", chunkId, size, checksum);
            return metadata;

        } catch (NoSuchAlgorithmException e) {
            throw new StorageException("SHA-256 algorithm not available", e);
        } catch (IOException e) {
            // Clean up files in case of failure
            try {
                Files.deleteIfExists(targetPath);
                Files.deleteIfExists(metaPath);
            } catch (IOException ignored) {}
            throw new StorageException("Failed to store chunk " + chunkId, e);
        }
    }

    public Resource read(String chunkIdStr) {
        UUID chunkId = parseUUID(chunkIdStr);
        Path file = storageConfig.getRootPath().resolve(chunkId + ".bin");
        if (!Files.exists(file)) {
            log.warn("Chunk data not found: id={}", chunkId);
            throw new ChunkNotFoundException("Chunk data not found for ID: " + chunkId);
        }

        try {
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                log.info("Successfully read chunk: id={}", chunkId);
                return resource;
            } else {
                throw new StorageException("Could not read chunk file: " + chunkId);
            }
        } catch (MalformedURLException e) {
            throw new StorageException("Malformed URL for chunk: " + chunkId, e);
        }
  }

    public void delete(String chunkIdStr) {
        UUID chunkId = parseUUID(chunkIdStr);
        Path targetPath = storageConfig.getRootPath().resolve(chunkId + ".bin");
        Path metaPath = storageConfig.getRootPath().resolve(chunkId + ".meta");

        boolean dataDeleted;
        boolean metaDeleted;

        try {
            dataDeleted = Files.deleteIfExists(targetPath);
            metaDeleted = Files.deleteIfExists(metaPath);
        } catch (IOException e) {
            throw new StorageException("Failed to delete chunk files for ID: " + chunkId, e);
        }

        if (!dataDeleted && !metaDeleted) {
            log.warn("Chunk to delete not found: id={}", chunkId);
            throw new ChunkNotFoundException("Chunk not found for ID: " + chunkId);
        }

        log.info("Successfully deleted chunk: id={}", chunkId);
    }

    public ChunkMetadata getMetadata(String chunkIdStr) {
        UUID chunkId = parseUUID(chunkIdStr);
        Path metaPath = storageConfig.getRootPath().resolve(chunkId + ".meta");
        if (!Files.exists(metaPath)) {
            log.warn("Chunk metadata not found: id={}", chunkId);
            throw new ChunkNotFoundException("Chunk metadata not found for ID: " + chunkId);
        }

        try {
            ChunkMetadata metadata = objectMapper.readValue(metaPath.toFile(), ChunkMetadata.class);
            log.info("Successfully retrieved metadata for chunk: id={}", chunkId);
            return metadata;
        } catch (IOException e) {
            throw new StorageException("Failed to read chunk metadata for ID: " + chunkId, e);
        }
    }

    public NodeInfoResponse getNodeInfo() {
        Path rootPath = storageConfig.getRootPath();
        long freeSpace = 0;
        long chunksCount = 0;

        try {
            freeSpace = Files.getFileStore(rootPath).getUsableSpace();
        } catch (IOException e) {
            log.error("Failed to read free space for path: " + rootPath, e);
            freeSpace = -1;
        }

        try (java.util.stream.Stream<Path> filesStream = Files.list(rootPath)) {
            chunksCount = filesStream
                    .filter(path -> path.toString().endsWith(".bin"))
                    .count();
        } catch (IOException e) {
            log.error("Failed to list chunks in path: " + rootPath, e);
        }

        return NodeInfoResponse.builder()
                .nodeId(nodeId)
                .freeSpace(freeSpace)
                .chunks(chunksCount)
                .status("UP")
                .build();
    }

    private UUID parseUUID(String chunkIdStr) {
        try {
            return UUID.fromString(chunkIdStr);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid chunk ID format: '{}'", chunkIdStr);
            throw new ChunkNotFoundException("Invalid chunk ID format: " + chunkIdStr);
        }
    }
}
