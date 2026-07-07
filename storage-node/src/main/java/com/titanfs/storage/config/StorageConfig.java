package com.titanfs.storage.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class StorageConfig {

    @Value("${titanfs.storage.dir:storage}")
    private String storageDir;

    private Path rootPath;

    @PostConstruct
    public void init() {
        this.rootPath = Paths.get(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootPath);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage directory at " + rootPath, e);
        }
    }

    public Path getRootPath() {
        return rootPath;
    }
}
