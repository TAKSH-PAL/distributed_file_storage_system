package com.titanfs.metadata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileRegisterResponse {
    private UUID fileId;
    private String filePath;
    private long totalSize;
    private Instant createdAt;
    private List<ChunkAllocation> chunks;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChunkAllocation {
        private UUID chunkId;
        private int sequenceNumber;
        private long size;
        private String checksum;
        private List<NodeInfo> targetNodes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NodeInfo {
        private String nodeId;
        private String host;
        private int port;
    }
}
