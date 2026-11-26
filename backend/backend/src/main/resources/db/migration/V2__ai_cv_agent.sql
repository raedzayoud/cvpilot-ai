-- AI CV Agent: conversational editing sessions over a working copy of a CV.
-- The CV itself is only changed when the user explicitly saves a session.

CREATE TABLE agent_session (
    id              UUID PRIMARY KEY,
    cv_id           UUID         NOT NULL REFERENCES cv (id) ON DELETE CASCADE,
    title           VARCHAR(200) NOT NULL,
    base_profile    JSONB        NOT NULL,  -- the CV profile when the session started
    working_profile JSONB        NOT NULL,  -- base + accepted changes
    section_order   JSONB        NOT NULL,  -- display/export order of sections
    confirmed_facts JSONB        NOT NULL,  -- facts the user stated about themselves, with exact quotes
    job_target      JSONB,                  -- analyzed job description, when the user gave one
    saved_at        TIMESTAMPTZ,            -- last time accepted changes were written to the CV
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_agent_session_cv ON agent_session (cv_id, updated_at DESC);

CREATE TABLE agent_message (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id UUID        NOT NULL REFERENCES agent_session (id) ON DELETE CASCADE,
    role       VARCHAR(10) NOT NULL CHECK (role IN ('USER', 'AGENT')),
    text       TEXT        NOT NULL,
    meta       JSONB,                       -- intent, tools used, blocked claims (agent turns only)
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_agent_message_session ON agent_message (session_id, id);

CREATE TABLE agent_proposal (
    id         UUID PRIMARY KEY,
    session_id UUID        NOT NULL REFERENCES agent_session (id) ON DELETE CASCADE,
    status     VARCHAR(12) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'SUPERSEDED', 'STALE')),
    operation  VARCHAR(30) NOT NULL,
    change     JSONB       NOT NULL,        -- before/after, reason, keywords, evidence
    created_at TIMESTAMPTZ NOT NULL,
    decided_at TIMESTAMPTZ
);

CREATE INDEX idx_agent_proposal_session ON agent_proposal (session_id, created_at);
