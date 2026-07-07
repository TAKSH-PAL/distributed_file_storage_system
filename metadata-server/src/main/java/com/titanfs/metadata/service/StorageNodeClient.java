package com.titanfs.metadata.service;

import com.titanfs.metadata.dto.ChunkUploadResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class StorageNodeClient {

    private final RestTemplate restTemplate;

    public StorageNodeClient() {
        this.restTemplate = new RestTemplate();
    }

    public ChunkUploadResponse uploadChunk(String host, int port, byte[] chunkBytes, String originalFilename) {
        String url = String.format("http://%s:%d/chunks", host, port);
        log.info("Forwarding chunk upload to storage node: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource fileResource = new ByteArrayResource(chunkBytes) {
            @Override
            public String getFilename() {
                return originalFilename != null ? originalFilename : "chunk.bin";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<ChunkUploadResponse> response = restTemplate.postForEntity(url, requestEntity, ChunkUploadResponse.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            } else {
                throw new RuntimeException("Failed to upload chunk to node at " + url + ", status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error communicating with storage node at {}", url, e);
            throw new RuntimeException("Failed to connect to storage node at " + url, e);
        }
    }
}
