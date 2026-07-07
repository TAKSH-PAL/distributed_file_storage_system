package com.titanfs.metadata.controller;

import com.titanfs.metadata.dto.SystemStatsResponse;
import com.titanfs.metadata.repository.FileChunkRepository;
import com.titanfs.metadata.repository.StoredFileRepository;
import com.titanfs.metadata.service.NodeRegistryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stats")
public class SystemStatsController {

    private final NodeRegistryService nodeRegistryService;
    private final StoredFileRepository fileRepository;
    private final FileChunkRepository chunkRepository;

    public SystemStatsController(NodeRegistryService nodeRegistryService,
                                 StoredFileRepository fileRepository,
                                 FileChunkRepository chunkRepository) {
        this.nodeRegistryService = nodeRegistryService;
        this.fileRepository = fileRepository;
        this.chunkRepository = chunkRepository;
    }

    @GetMapping
    public ResponseEntity<SystemStatsResponse> getStats() {
        long activeNodes = nodeRegistryService.getActiveNodes().size();
        long storedFiles = fileRepository.count();
        long chunks = chunkRepository.count();

        SystemStatsResponse stats = SystemStatsResponse.builder()
                .registeredNodes(activeNodes)
                .storedFiles(storedFiles)
                .chunks(chunks)
                .build();

        return ResponseEntity.ok(stats);
    }
}
