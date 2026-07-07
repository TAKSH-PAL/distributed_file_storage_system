package com.titanfs.metadata.service;

import com.titanfs.metadata.dto.ChunkUploadResponse;
import com.titanfs.metadata.dto.FileRegisterRequest;
import com.titanfs.metadata.dto.FileRegisterResponse;
import com.titanfs.metadata.model.ChunkReplica;
import com.titanfs.metadata.model.FileChunk;
import com.titanfs.metadata.model.StorageNode;
import com.titanfs.metadata.model.StoredFile;
import com.titanfs.metadata.repository.ChunkReplicaRepository;
import com.titanfs.metadata.repository.FileChunkRepository;
import com.titanfs.metadata.repository.StoredFileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NamespaceService {

    private final StoredFileRepository fileRepository;
    private final FileChunkRepository chunkRepository;
    private final ChunkReplicaRepository replicaRepository;
    private final NodeRegistryService nodeRegistryService;
    private final StorageNodeClient storageNodeClient;
    private final PlacementService placementService;

    @Autowired
    public NamespaceService(StoredFileRepository fileRepository,
                            FileChunkRepository chunkRepository,
                            ChunkReplicaRepository replicaRepository,
                            NodeRegistryService nodeRegistryService,
                            StorageNodeClient storageNodeClient,
                            PlacementService placementService) {
        this.fileRepository = fileRepository;
        this.chunkRepository = chunkRepository;
        this.replicaRepository = replicaRepository;
        this.nodeRegistryService = nodeRegistryService;
        this.storageNodeClient = storageNodeClient;
        this.placementService = placementService;
    }

    @Transactional
    public FileRegisterResponse registerFile(FileRegisterRequest request) {
        if (fileRepository.findByFilePath(request.getFilePath()).isPresent()) {
            throw new IllegalArgumentException("File already exists: " + request.getFilePath());
        }

        List<StorageNode> activeNodes = nodeRegistryService.getActiveNodes();
        if (activeNodes.isEmpty()) {
            throw new IllegalStateException("No active storage nodes available to allocate chunks.");
        }

        StoredFile storedFile = StoredFile.builder()
                .filePath(request.getFilePath())
                .totalSize(request.getTotalSize())
                .createdAt(Instant.now())
                .build();

        StoredFile savedFile = fileRepository.save(storedFile);
        List<FileRegisterResponse.ChunkAllocation> allocations = new ArrayList<>();

        int replicationFactor = 1;
        int nodeIndex = 0;

        for (FileRegisterRequest.ChunkInfo chunkInfo : request.getChunks()) {
            UUID chunkId = UUID.randomUUID();
            FileChunk chunk = FileChunk.builder()
                    .chunkId(chunkId)
                    .storedFile(savedFile)
                    .sequenceNumber(chunkInfo.getSequenceNumber())
                    .size(chunkInfo.getSize())
                    .checksum(chunkInfo.getChecksum())
                    .build();

            FileChunk savedChunk = chunkRepository.save(chunk);
            savedFile.getChunks().add(savedChunk);

            int actualReplicas = Math.min(replicationFactor, activeNodes.size());
            List<StorageNode> allocatedNodes = new ArrayList<>();
            
            for (int r = 0; r < actualReplicas; r++) {
                StorageNode node = activeNodes.get((nodeIndex + r) % activeNodes.size());
                allocatedNodes.add(node);

                ChunkReplica replica = ChunkReplica.builder()
                        .fileChunk(savedChunk)
                        .storageNode(node)
                        .build();
                replicaRepository.save(replica);
            }
            nodeIndex = (nodeIndex + actualReplicas) % activeNodes.size();

            List<FileRegisterResponse.NodeInfo> nodeInfos = allocatedNodes.stream()
                    .map(node -> FileRegisterResponse.NodeInfo.builder()
                            .nodeId(node.getNodeId())
                            .host(node.getHost())
                            .port(node.getPort())
                            .build())
                    .collect(Collectors.toList());

            allocations.add(FileRegisterResponse.ChunkAllocation.builder()
                    .chunkId(chunkId)
                    .sequenceNumber(chunkInfo.getSequenceNumber())
                    .size(chunkInfo.getSize())
                    .checksum(chunkInfo.getChecksum())
                    .targetNodes(nodeInfos)
                    .build());
        }

        log.info("Registered file: path={}, id={}, chunks={}", savedFile.getFilePath(), savedFile.getFileId(), allocations.size());

        return FileRegisterResponse.builder()
                .fileId(savedFile.getFileId())
                .filePath(savedFile.getFilePath())
                .totalSize(savedFile.getTotalSize())
                .createdAt(savedFile.getCreatedAt())
                .chunks(allocations)
                .build();
    }

    @Transactional
    public FileRegisterResponse uploadFile(String filePath, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Failed to upload empty file");
        }
        if (fileRepository.findByFilePath(filePath).isPresent()) {
            throw new IllegalArgumentException("File already exists: " + filePath);
        }

        List<StorageNode> activeNodes = nodeRegistryService.getActiveNodes();
        if (activeNodes.isEmpty()) {
            throw new IllegalStateException("No active storage nodes available for upload.");
        }

        StoredFile storedFile = StoredFile.builder()
                .filePath(filePath)
                .totalSize(0)
                .createdAt(Instant.now())
                .build();
        StoredFile savedFile = fileRepository.save(storedFile);

        List<FileRegisterResponse.ChunkAllocation> allocations = new ArrayList<>();
        long totalSize = 0;
        int sequenceNumber = 0;
        int chunkSize = 1 * 1024 * 1024; // 1MB chunk size
        byte[] buffer = new byte[chunkSize];

        try (InputStream is = file.getInputStream()) {
            int bytesRead;
            int replicationFactor = 2; // Replicate each chunk to up to 2 nodes
            while ((bytesRead = readFully(is, buffer)) > 0) {
                byte[] chunkBytes = Arrays.copyOf(buffer, bytesRead);
                totalSize += bytesRead;

                // 1. Select storage nodes using PlacementService
                int actualReplication = Math.min(replicationFactor, activeNodes.size());
                List<StorageNode> targetNodes = placementService.selectNodes(activeNodes, bytesRead, actualReplication);

                UUID chunkId = UUID.randomUUID();
                String originalFilename = String.format("%s_chunk_%d", file.getOriginalFilename(), sequenceNumber);
                String checksum = null;
                long size = 0;
                List<StorageNode> successfulNodes = new ArrayList<>();

                for (StorageNode targetNode : targetNodes) {
                    try {
                        ChunkUploadResponse uploadResponse = storageNodeClient.uploadChunk(
                                targetNode.getHost(),
                                targetNode.getPort(),
                                chunkBytes,
                                originalFilename,
                                chunkId
                        );
                        if (checksum == null) {
                            checksum = uploadResponse.getChecksum();
                            size = uploadResponse.getSize();
                        }
                        successfulNodes.add(targetNode);
                        targetNode.setFreeSpace(targetNode.getFreeSpace() - bytesRead);
                    } catch (Exception e) {
                        log.warn("Failed to upload chunk replica to node: " + targetNode.getNodeId(), e);
                    }
                }

                if (successfulNodes.isEmpty()) {
                    throw new RuntimeException("Failed to upload chunk to any selected storage nodes");
                }

                // 3. Create FileChunk metadata record
                FileChunk chunk = FileChunk.builder()
                        .chunkId(chunkId)
                        .storedFile(savedFile)
                        .sequenceNumber(sequenceNumber)
                        .size(size)
                        .checksum(checksum)
                        .build();

                FileChunk savedChunk = chunkRepository.save(chunk);
                savedFile.getChunks().add(savedChunk);

                // 4. Create ChunkReplica placement records for each successful storage
                for (StorageNode successfulNode : successfulNodes) {
                    ChunkReplica replica = ChunkReplica.builder()
                            .fileChunk(savedChunk)
                            .storageNode(successfulNode)
                            .build();
                    replicaRepository.save(replica);
                }

                List<FileRegisterResponse.NodeInfo> targetNodeInfos = successfulNodes.stream()
                        .map(node -> FileRegisterResponse.NodeInfo.builder()
                                .nodeId(node.getNodeId())
                                .host(node.getHost())
                                .port(node.getPort())
                                .build())
                        .collect(Collectors.toList());

                allocations.add(FileRegisterResponse.ChunkAllocation.builder()
                        .chunkId(chunkId)
                        .sequenceNumber(sequenceNumber)
                        .size(size)
                        .checksum(checksum)
                        .targetNodes(targetNodeInfos)
                        .build());

                sequenceNumber++;
            }
        } catch (IOException e) {
            log.error("Failed to read file input stream during upload", e);
            throw new RuntimeException("Failed to upload file " + filePath, e);
        }

        savedFile.setTotalSize(totalSize);
        fileRepository.save(savedFile);

        log.info("Successfully uploaded file: path={}, size={}, chunks={}", filePath, totalSize, sequenceNumber);

        return FileRegisterResponse.builder()
                .fileId(savedFile.getFileId())
                .filePath(savedFile.getFilePath())
                .totalSize(savedFile.getTotalSize())
                .createdAt(savedFile.getCreatedAt())
                .chunks(allocations)
                .build();
    }

    @Transactional(readOnly = true)
    public FileRegisterResponse lookupFile(String filePath) {
        StoredFile storedFile = fileRepository.findByFilePath(filePath)
                .orElseThrow(() -> new NoSuchElementException("File not found: " + filePath));

        List<FileRegisterResponse.ChunkAllocation> allocations = new ArrayList<>();

        List<FileChunk> sortedChunks = storedFile.getChunks().stream()
                .sorted(Comparator.comparingInt(FileChunk::getSequenceNumber))
                .collect(Collectors.toList());

        for (FileChunk chunk : sortedChunks) {
            List<ChunkReplica> replicas = replicaRepository.findByFileChunk(chunk);
            List<FileRegisterResponse.NodeInfo> nodeInfos = replicas.stream()
                    .map(replica -> {
                        StorageNode node = replica.getStorageNode();
                        return FileRegisterResponse.NodeInfo.builder()
                                .nodeId(node.getNodeId())
                                .host(node.getHost())
                                .port(node.getPort())
                                .build();
                    })
                    .collect(Collectors.toList());

            allocations.add(FileRegisterResponse.ChunkAllocation.builder()
                    .chunkId(chunk.getChunkId())
                    .sequenceNumber(chunk.getSequenceNumber())
                    .size(chunk.getSize())
                    .checksum(chunk.getChecksum())
                    .targetNodes(nodeInfos)
                    .build());
        }

        return FileRegisterResponse.builder()
                .fileId(storedFile.getFileId())
                .filePath(storedFile.getFilePath())
                .totalSize(storedFile.getTotalSize())
                .createdAt(storedFile.getCreatedAt())
                .chunks(allocations)
                .build();
    }

    private int readFully(InputStream is, byte[] buffer) throws IOException {
        int offset = 0;
        int toRead = buffer.length;
        while (toRead > 0) {
            int read = is.read(buffer, offset, toRead);
            if (read < 0) {
                break;
            }
            offset += read;
            toRead -= read;
        }
        return offset;
    }
}
