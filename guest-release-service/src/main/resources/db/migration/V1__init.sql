CREATE TABLE guest_release (
    id                 UUID PRIMARY KEY,
    guest_id           VARCHAR(64) NOT NULL,
    episode_id         UUID        NOT NULL,
    status             VARCHAR(16) NOT NULL,
    granted_at         TIMESTAMPTZ NOT NULL,
    withdrawn_at       TIMESTAMPTZ,
    withdrawn_by       VARCHAR(64),
    withdrawal_reason  TEXT,
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT chk_release_status CHECK (status IN ('ACTIVE', 'WITHDRAWN'))
);

CREATE INDEX idx_release_guest ON guest_release (guest_id);
CREATE INDEX idx_release_episode ON guest_release (episode_id);

CREATE TABLE release_scope (
    id                 UUID PRIMARY KEY,
    release_id         UUID        NOT NULL REFERENCES guest_release (id) ON DELETE CASCADE,
    excerpt_id         UUID        NOT NULL,
    scope_type         VARCHAR(32) NOT NULL,
    scope_status       VARCHAR(16) NOT NULL,
    granted_at         TIMESTAMPTZ NOT NULL,
    withdrawn_at       TIMESTAMPTZ,
    withdrawn_by       VARCHAR(64),
    withdrawal_reason  TEXT,
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_release_scope UNIQUE (release_id, excerpt_id),
    CONSTRAINT chk_scope_status CHECK (scope_status IN ('ACTIVE', 'WITHDRAWN'))
);

CREATE INDEX idx_scope_excerpt ON release_scope (excerpt_id);

CREATE TABLE event_outbox (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(64) NOT NULL,
    aggregate_id    UUID        NOT NULL,
    event_type      VARCHAR(64) NOT NULL,
    payload_json    TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    published_at    TIMESTAMPTZ,
    attempt_count   INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT uq_outbox_aggregate_event UNIQUE (aggregate_id, event_type)
);

CREATE INDEX idx_outbox_unpublished ON event_outbox (created_at) WHERE published_at IS NULL;

-- Local read-model projections. They are owned by this service and seeded for the demo.
CREATE TABLE produced_episode (
    episode_id   UUID PRIMARY KEY,
    producer_id  VARCHAR(64) NOT NULL
);

CREATE TABLE excerpt_reference (
    excerpt_id  UUID PRIMARY KEY,
    episode_id  UUID        NOT NULL,
    guest_id    VARCHAR(64) NOT NULL,
    code        VARCHAR(64) NOT NULL,
    start_ms    BIGINT      NOT NULL,
    end_ms      BIGINT      NOT NULL
);
