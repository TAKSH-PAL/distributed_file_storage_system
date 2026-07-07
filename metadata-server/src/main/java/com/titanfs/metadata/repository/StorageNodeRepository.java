package com.titanfs.metadata.repository;

import com.titanfs.metadata.model.StorageNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StorageNodeRepository extends JpaRepository<StorageNode, String> {
    List<StorageNode> findByStatus(String status);
}
