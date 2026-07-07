package com.titanfs.metadata.service;

import com.titanfs.metadata.dto.NodeRegisterRequest;
import com.titanfs.metadata.model.StorageNode;
import com.titanfs.metadata.repository.StorageNodeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NodeRegistryService {

    private final StorageNodeRepository nodeRepository;

    @Autowired
    public NodeRegistryService(StorageNodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    @Transactional
    public StorageNode registerNode(NodeRegisterRequest request) {
        Optional<StorageNode> existing = nodeRepository.findById(request.getNodeId());
        StorageNode node;
        if (existing.isPresent()) {
            node = existing.get();
            node.setHost(request.getHost());
            node.setPort(request.getPort());
            node.setFreeSpace(request.getFreeSpace());
            node.setLastHeartbeat(Instant.now());
            node.setStatus("ACTIVE");
            log.info("Storage node re-registered: id={}, host={}, port={}, freeSpace={}", node.getNodeId(), node.getHost(), node.getPort(), node.getFreeSpace());
        } else {
            node = StorageNode.builder()
                    .nodeId(request.getNodeId())
                    .host(request.getHost())
                    .port(request.getPort())
                    .freeSpace(request.getFreeSpace())
                    .lastHeartbeat(Instant.now())
                    .status("ACTIVE")
                    .build();
            log.info("New storage node registered: id={}, host={}, port={}, freeSpace={}", node.getNodeId(), node.getHost(), node.getPort(), node.getFreeSpace());
        }
        return nodeRepository.save(node);
    }

    @Transactional
    public void handleHeartbeat(String nodeId, long freeSpace) {
        StorageNode node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Node not registered: " + nodeId));
        node.setLastHeartbeat(Instant.now());
        node.setFreeSpace(freeSpace);
        node.setStatus("ACTIVE");
        log.debug("Heartbeat received from node: {}, freeSpace={}", nodeId, freeSpace);
        nodeRepository.save(node);
    }

    @Transactional(readOnly = true)
    public List<StorageNode> getActiveNodes() {
        Instant threshold = Instant.now().minus(300, ChronoUnit.SECONDS);
        return nodeRepository.findByStatus("ACTIVE").stream()
                .filter(node -> node.getLastHeartbeat().isAfter(threshold))
                .collect(Collectors.toList());
    }

    // Mark nodes as INACTIVE if they haven't sent a heartbeat in over 300 seconds.
    // Runs every 10 seconds.
    @Scheduled(fixedRate = 10000)
    @Transactional
    public void checkNodeHealth() {
        Instant threshold = Instant.now().minus(300, ChronoUnit.SECONDS);
        List<StorageNode> activeNodes = nodeRepository.findByStatus("ACTIVE");
        for (StorageNode node : activeNodes) {
            if (node.getLastHeartbeat().isBefore(threshold)) {
                node.setStatus("INACTIVE");
                nodeRepository.save(node);
                log.warn("Storage node detected offline, marked INACTIVE: id={}", node.getNodeId());
            }
        }
    }
}
