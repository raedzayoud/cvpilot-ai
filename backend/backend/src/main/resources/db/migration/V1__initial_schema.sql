-- CVPilot AI initial schema.
-- Structured documents that are always read/written whole (CV profile, job positions,
-- customization plan) are JSONB; everything that is filtered, joined or sorted is a column.

CREATE TABLE cv (
    id               UUID PRIMARY KEY,
    file_name        VARCHAR(255) NOT NULL,
    mime_type        VARCHAR(150) NOT NULL,
    source_format    VARCHAR(10)  NOT NULL CHECK (source_format IN ('DOCX', 'PDF', 'TEX')),
    size_bytes       BIGINT       NOT NULL CHECK (size_bytes >= 0),
    language         VARCHAR(60)  NOT NULL,
    profile          JSONB        NOT NULL,
    extraction_note  VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_cv_updated_at ON cv (updated_at DESC);
CREATE INDEX idx_cv_file_name_lower ON cv (LOWER(file_name));

-- The uploaded file, kept byte-for-byte, in its own table so CV reads never load it.
CREATE TABLE cv_file (
    cv_id          UUID PRIMARY KEY REFERENCES cv (id) ON DELETE CASCADE,
    content        BYTEA NOT NULL,
    extracted_text TEXT  NOT NULL
);

CREATE TABLE job_posting (
    id         UUID PRIMARY KEY,
    cv_id      UUID         NOT NULL REFERENCES cv (id) ON DELETE CASCADE,
    raw_text   TEXT         NOT NULL,
    source     VARCHAR(10)  NOT NULL CHECK (source IN ('PASTE', 'PDF', 'DOCX')),
    file_name  VARCHAR(255),
    positions  JSONB        NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_job_posting_cv_id ON job_posting (cv_id);
CREATE INDEX idx_job_posting_created_at ON job_posting (created_at DESC);

CREATE TABLE analysis (
    id            UUID PRIMARY KEY,
    cv_id         UUID         NOT NULL REFERENCES cv (id) ON DELETE CASCADE,
    job_id        UUID         NOT NULL REFERENCES job_posting (id) ON DELETE CASCADE,
    job_title     VARCHAR(300) NOT NULL,
    language      VARCHAR(60)  NOT NULL,
    source_format VARCHAR(10)  NOT NULL CHECK (source_format IN ('DOCX', 'PDF', 'TEX')),
    customization JSONB        NOT NULL,
    chat_state    JSONB        NOT NULL,
    score         INTEGER      NOT NULL CHECK (score BETWEEN 0 AND 100),
    exported      BOOLEAN      NOT NULL DEFAULT FALSE,
    export_count  INTEGER      NOT NULL DEFAULT 0 CHECK (export_count >= 0),
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_analysis_cv_id ON analysis (cv_id);
CREATE INDEX idx_analysis_job_id ON analysis (job_id);
CREATE INDEX idx_analysis_created_at ON analysis (created_at DESC);
CREATE INDEX idx_analysis_job_title_lower ON analysis (LOWER(job_title));

CREATE TABLE chat_message (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    analysis_id UUID        NOT NULL REFERENCES analysis (id) ON DELETE CASCADE,
    role        VARCHAR(10) NOT NULL CHECK (role IN ('BOT', 'USER')),
    text        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_chat_message_analysis ON chat_message (analysis_id, id);

CREATE TABLE activity (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type       VARCHAR(40)  NOT NULL,
    label      VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_activity_created_at ON activity (created_at DESC);
