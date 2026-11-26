package com.backend.backend.repository;

import com.backend.backend.entity.CvFile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CvFileRepository extends JpaRepository<CvFile, UUID> {
}
