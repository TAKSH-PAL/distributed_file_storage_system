package com.titanfs.storage.controller;

import com.titanfs.storage.model.ChunkMetadata;
import com.titanfs.storage.service.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/chunks")
public class StorageController {

      private final StorageService storageService;

      @Autowired
      public StorageController(StorageService storageService) {
          this.storageService = storageService;
      }

      @PostMapping
      public ResponseEntity<ChunkMetadata> uploadChunk(@RequestParam("file") MultipartFile file) {
          ChunkMetadata metadata = storageService.store(file);
          return ResponseEntity.ok(metadata);
      }

      @GetMapping("/{chunkId}")
      public ResponseEntity<Resource> downloadChunk(@PathVariable("chunkId") String chunkId) {
          Resource resource = storageService.read(chunkId);
          return ResponseEntity.ok()
                  .contentType(MediaType.APPLICATION_OCTET_STREAM)
                  .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + chunkId + ".bin\"")
                  .body(resource);
      }

      @GetMapping("/{chunkId}/metadata")
      public ResponseEntity<ChunkMetadata> getChunkMetadata(@PathVariable("chunkId") String chunkId) {
          ChunkMetadata metadata = storageService.getMetadata(chunkId);
          return ResponseEntity.ok(metadata);
      }

      @DeleteMapping("/{chunkId}")
      public ResponseEntity<Void> deleteChunk(@PathVariable("chunkId") String chunkId) {
          storageService.delete(chunkId);
          return ResponseEntity.noContent().build();
      }
}
