package com.backend.backend.repository;

import com.backend.backend.entity.AgentSession;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentSessionRepository extends JpaRepository<AgentSession, UUID> {

    @EntityGraph(attributePaths = "cv")
    Page<AgentSession> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = "cv")
    Page<AgentSession> findByCvIdOrderByUpdatedAtDesc(UUID cvId, Pageable pageable);
}
