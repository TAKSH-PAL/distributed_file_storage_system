package com.titanfs.metadata.repository;

import com.titanfs.metadata.model.ChunkReplica;
import com.titanfs.metadata.model.FileChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChunkReplicaRepository extends JpaRepository<ChunkReplica, Long> {
    List<ChunkReplica> findByFileChunk(FileChunk fileChunk);
}
