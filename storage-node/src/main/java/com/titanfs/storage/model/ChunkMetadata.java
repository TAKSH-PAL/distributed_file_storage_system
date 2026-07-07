package com.titanfs.storage.model;

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
public class ChunkMetadata {
    private UUID chunkId;
    private long size;
    private String checksum;
    private Instant createdAt;
    private String path;
}
