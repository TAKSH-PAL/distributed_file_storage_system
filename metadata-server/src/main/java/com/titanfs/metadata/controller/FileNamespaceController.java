package com.titanfs.metadata.controller;

import com.titanfs.metadata.dto.FileRegisterRequest;
import com.titanfs.metadata.dto.FileRegisterResponse;
import com.titanfs.metadata.service.NamespaceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/files")
public class FileNamespaceController {

    private final NamespaceService namespaceService;

    public FileNamespaceController(NamespaceService namespaceService) {
        this.namespaceService = namespaceService;
    }

    @PostMapping
    public ResponseEntity<?> registerFile(@RequestBody FileRegisterRequest request) {
        try {
            FileRegisterResponse response = namespaceService.registerFile(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
        }
    }

    @GetMapping("/lookup")
    public ResponseEntity<?> lookupFile(@RequestParam("path") String path) {
        try {
            FileRegisterResponse response = namespaceService.lookupFile(path);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("path") String path,
            @RequestParam("file") MultipartFile file) {
        try {
            FileRegisterResponse response = namespaceService.uploadFile(path, file);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
}
