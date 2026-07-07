package com.titanfs.storage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NodeInfoResponse {
    private String nodeId;
    private long freeSpace;
    private long chunks;
    private String status;
}
