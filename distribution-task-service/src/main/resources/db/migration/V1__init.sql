CREATE TABLE distributed_edit (
    id                   UUID PRIMARY KEY,
    code                 VARCHAR(64)  NOT NULL UNIQUE,
    episode_id           UUID         NOT NULL,
    provider             VARCHAR(64)  NOT NULL,
    external_reference   VARCHAR(255) NOT NULL,
    distribution_status  VARCHAR(16)  NOT NULL,
    distributed_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_edit_provider_ref UNIQUE (provider, external_reference),
    CONSTRAINT chk_distribution_status CHECK (distribution_status IN ('DISTRIBUTED', 'PENDING', 'REMOVED'))
);

CREATE INDEX idx_edit_episode ON distributed_edit (episode_id);

CREATE TABLE edit_excerpt (
    edit_id     UUID NOT NULL REFERENCES distributed_edit (id) ON DELETE CASCADE,
    excerpt_id  UUID NOT NULL,
    PRIMARY KEY (edit_id, excerpt_id)
);

CREATE INDEX idx_edit_excerpt_excerpt ON edit_excerpt (excerpt_id);

CREATE TABLE distribution_task (
    id          UUID PRIMARY KEY,
    edit_id     UUID        NOT NULL REFERENCES distributed_edit (id),
    excerpt_id  UUID        NOT NULL,
    event_id    UUID        NOT NULL,
    task_type   VARCHAR(32) NOT NULL,
    status      VARCHAR(16) NOT NULL,
    priority    VARCHAR(16) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_task_event_edit UNIQUE (event_id, edit_id)
);

CREATE INDEX idx_task_excerpt ON distribution_task (excerpt_id);
CREATE INDEX idx_task_edit ON distribution_task (edit_id);

CREATE TABLE processed_event (
    event_id      UUID PRIMARY KEY,
    event_type    VARCHAR(64) NOT NULL,
    processed_at  TIMESTAMPTZ NOT NULL
);

-- Local read-model projections for record-level authorization.
CREATE TABLE episode_owner (
    episode_id   UUID PRIMARY KEY,
    producer_id  VARCHAR(64) NOT NULL
);

CREATE TABLE excerpt_guest (
    excerpt_id  UUID PRIMARY KEY,
    episode_id  UUID        NOT NULL,
    guest_id    VARCHAR(64) NOT NULL
);
