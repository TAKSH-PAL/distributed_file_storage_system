package com.titanfs.metadata.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "storage_nodes")
public class StorageNode {

    @Id
    private String nodeId;
    private String host;
    private int port;
    private long freeSpace;
    private Instant lastHeartbeat;
    private String status; // ACTIVE, INACTIVE
}
