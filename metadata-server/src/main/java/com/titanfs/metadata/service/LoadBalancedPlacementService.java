package com.titanfs.metadata.service;

import com.titanfs.metadata.model.StorageNode;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class LoadBalancedPlacementService implements PlacementService {

    @Override
    public StorageNode selectNode(List<StorageNode> activeNodes, long chunkSize) {
        if (activeNodes == null || activeNodes.isEmpty()) {
            throw new IllegalArgumentException("Active nodes list cannot be empty");
        }

        return activeNodes.stream()
                .filter(node -> node.getFreeSpace() >= chunkSize)
                .max(Comparator.comparingLong(StorageNode::getFreeSpace))
                .orElseThrow(() -> new IllegalStateException(
                        "Insufficient storage space: No online storage node has " + chunkSize + " bytes of free space."));
    }

    @Override
    public List<StorageNode> selectNodes(List<StorageNode> activeNodes, long chunkSize, int replicationFactor) {
        if (activeNodes == null || activeNodes.isEmpty()) {
            throw new IllegalArgumentException("Active nodes list cannot be empty");
        }

        List<StorageNode> selected = activeNodes.stream()
                .filter(node -> node.getFreeSpace() >= chunkSize)
                .sorted(Comparator.comparingLong(StorageNode::getFreeSpace).reversed())
                .limit(replicationFactor)
                .collect(java.util.stream.Collectors.toList());

        if (selected.isEmpty()) {
            throw new IllegalStateException(
                    "Insufficient storage space: No online storage node has " + chunkSize + " bytes of free space.");
        }

        return selected;
    }
}
