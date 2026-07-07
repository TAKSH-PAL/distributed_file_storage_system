package com.titanfs.storage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.titanfs.storage.config.StorageConfig;
import com.titanfs.storage.exception.ChunkNotFoundException;
import com.titanfs.storage.exception.StorageException;
import com.titanfs.storage.model.ChunkMetadata;
import org.springframework.beans.factory.annotation.Autowired;
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

@Service
public class StorageService {

    private final StorageConfig storageConfig;
    private final ObjectMapper objectMapper;

    @Autowired
    public StorageService(StorageConfig storageConfig, ObjectMapper objectMapper) {
        this.storageConfig = storageConfig;
        this.objectMapper = objectMapper;
    }

    public ChunkMetadata store(MultipartFile file) {
        if (file.isEmpty()) {
            throw new StorageException("Failed to store empty file");
        }

        UUID chunkId = UUID.randomUUID();
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
                    .size(size)
                    .checksum(checksum)
                    .createdAt(createdAt)
                    .path(targetPath.toString())
                    .build();

            // Persist metadata to sidecar JSON file
            objectMapper.writeValue(metaPath.toFile(), metadata);

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

    public Resource read(String chunkId) {
        try {
            UUID.fromString(chunkId); // Validate UUID format
        } catch (IllegalArgumentException e) {
            throw new ChunkNotFoundException("Invalid chunk ID format: " + chunkId);
        }

        Path file = storageConfig.getRootPath().resolve(chunkId + ".bin");
        if (!Files.exists(file)) {
            throw new ChunkNotFoundException("Chunk data not found for ID: " + chunkId);
        }

        try {
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new StorageException("Could not read chunk file: " + chunkId);
            }
        } catch (MalformedURLException e) {
            throw new StorageException("Malformed URL for chunk: " + chunkId, e);
        }
    }

    public void delete(String chunkId) {
        try {
            UUID.fromString(chunkId); // Validate UUID format
        } catch (IllegalArgumentException e) {
            throw new ChunkNotFoundException("Invalid chunk ID format: " + chunkId);
        }

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
            throw new ChunkNotFoundException("Chunk not found for ID: " + chunkId);
        }
    }

    public ChunkMetadata getMetadata(String chunkId) {
        try {
            UUID.fromString(chunkId); // Validate UUID format
        } catch (IllegalArgumentException e) {
            throw new ChunkNotFoundException("Invalid chunk ID format: " + chunkId);
        }

        Path metaPath = storageConfig.getRootPath().resolve(chunkId + ".meta");
        if (!Files.exists(metaPath)) {
            throw new ChunkNotFoundException("Chunk metadata not found for ID: " + chunkId);
        }

        try {
            return objectMapper.readValue(metaPath.toFile(), ChunkMetadata.class);
        } catch (IOException e) {
            throw new StorageException("Failed to read chunk metadata for ID: " + chunkId, e);
        }
    }
}
