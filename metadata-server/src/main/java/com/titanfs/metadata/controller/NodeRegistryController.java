package com.titanfs.metadata.controller;

import com.titanfs.metadata.dto.NodeRegisterRequest;
import com.titanfs.metadata.model.StorageNode;
import com.titanfs.metadata.service.NodeRegistryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/nodes")
public class NodeRegistryController {

    private final NodeRegistryService nodeRegistryService;

    public NodeRegistryController(NodeRegistryService nodeRegistryService) {
        this.nodeRegistryService = nodeRegistryService;
    }

    @PostMapping("/register")
    public ResponseEntity<StorageNode> registerNode(@RequestBody NodeRegisterRequest request) {
        StorageNode node = nodeRegistryService.registerNode(request);
        return ResponseEntity.ok(node);
    }

    @PostMapping("/{nodeId}/heartbeat")
    public ResponseEntity<Void> heartbeat(
            @PathVariable("nodeId") String nodeId,
            @RequestParam(value = "freeSpace", defaultValue = "0") long freeSpace) {
        try {
            nodeRegistryService.handleHeartbeat(nodeId, freeSpace);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/active")
    public ResponseEntity<List<StorageNode>> getActiveNodes() {
        List<StorageNode> activeNodes = nodeRegistryService.getActiveNodes();
        return ResponseEntity.ok(activeNodes);
    }
}
