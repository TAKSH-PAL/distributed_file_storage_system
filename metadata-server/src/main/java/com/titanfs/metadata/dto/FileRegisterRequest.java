package com.titanfs.metadata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileRegisterRequest {
    private String filePath;
    private long totalSize;
    private List<ChunkInfo> chunks;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChunkInfo {
        private int sequenceNumber;
        private long size;
        private String checksum;
    }
}
