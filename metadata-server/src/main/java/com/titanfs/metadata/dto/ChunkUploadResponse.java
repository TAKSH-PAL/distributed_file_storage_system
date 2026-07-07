package com.titanfs.metadata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChunkUploadResponse {
    private UUID chunkId;
    private String storageNodeId;
    private long size;
    private String checksum;
    private Instant createdAt;
}
