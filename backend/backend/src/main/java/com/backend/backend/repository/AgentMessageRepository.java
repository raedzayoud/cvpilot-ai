package com.backend.backend.repository;

import com.backend.backend.entity.AgentMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentMessageRepository extends JpaRepository<AgentMessage, Long> {

    List<AgentMessage> findBySessionIdOrderByIdAsc(UUID sessionId);
}
