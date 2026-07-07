package com.titanfs.metadata.service;

import com.titanfs.metadata.model.StorageNode;
import java.util.List;

public interface PlacementService {
    StorageNode selectNode(List<StorageNode> activeNodes, long chunkSize);
    List<StorageNode> selectNodes(List<StorageNode> activeNodes, long chunkSize, int replicationFactor);
}
