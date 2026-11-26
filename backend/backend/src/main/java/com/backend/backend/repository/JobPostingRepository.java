package com.backend.backend.repository;

import com.backend.backend.entity.JobPosting;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPostingRepository extends JpaRepository<JobPosting, UUID> {

    List<JobPosting> findTop3ByOrderByCreatedAtDesc();

    Page<JobPosting> findByCvId(UUID cvId, Pageable pageable);
}
