package com.backend.backend.entity;

import com.backend.backend.model.agent.AgentOperation;
import com.backend.backend.model.agent.ProposalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** One proposed edit, waiting for the user's decision. Applied to the working copy only on accept. */
@Entity
@Table(name = "agent_proposal")
@Getter
@Setter
@NoArgsConstructor
public class AgentProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AgentSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ProposalStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AgentOperation operation;

    /** JSON-serialized {@link com.backend.backend.model.agent.ProposedChange}. */
    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String change;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;
}
