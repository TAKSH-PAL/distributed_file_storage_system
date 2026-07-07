package com.titanfs.storage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChunkMetadata {
    private UUID chunkId;
    private String storageNodeId;
    private long size;
    private String checksum;
    private Instant createdAt;
}
