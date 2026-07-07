package com.titanfs.metadata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemStatsResponse {
    private long registeredNodes;
    private long storedFiles;
    private long chunks;
}
