package com.titanfs.storage.exception;

public class ChunkNotFoundException extends RuntimeException {
    public ChunkNotFoundException(String message) {
        super(message);
    }
}
