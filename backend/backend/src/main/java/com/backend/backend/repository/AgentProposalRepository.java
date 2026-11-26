package com.backend.backend.repository;

import com.backend.backend.entity.AgentProposal;
import com.backend.backend.model.agent.ProposalStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentProposalRepository extends JpaRepository<AgentProposal, UUID> {

    List<AgentProposal> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    List<AgentProposal> findBySessionIdAndStatusOrderByCreatedAtAsc(UUID sessionId, ProposalStatus status);
}
