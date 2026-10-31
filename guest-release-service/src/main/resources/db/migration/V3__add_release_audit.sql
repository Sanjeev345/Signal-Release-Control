CREATE TABLE release_audit (
    id              UUID PRIMARY KEY,
    release_id      UUID         NOT NULL REFERENCES guest_release (id) ON DELETE CASCADE,
    scope_id        UUID         NOT NULL REFERENCES release_scope (id) ON DELETE CASCADE,
    excerpt_id      UUID         NOT NULL,
    action          VARCHAR(32)  NOT NULL,
    actor_id        VARCHAR(64)  NOT NULL,
    reason          TEXT         NOT NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    event_id        UUID         NOT NULL,
    correlation_id  VARCHAR(128) NOT NULL,
    CONSTRAINT uq_release_audit_event UNIQUE (event_id),
    CONSTRAINT chk_release_audit_action CHECK (action IN ('WITHDRAWAL'))
);

CREATE INDEX idx_release_audit_release ON release_audit (release_id, occurred_at);
