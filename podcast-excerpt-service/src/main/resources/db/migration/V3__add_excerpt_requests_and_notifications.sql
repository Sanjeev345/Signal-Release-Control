CREATE TABLE excerpt_request (
    id               UUID PRIMARY KEY,
    episode_id       UUID         NOT NULL REFERENCES podcast_episode (id),
    guest_id         VARCHAR(64)  NOT NULL,
    title            VARCHAR(255) NOT NULL,
    description      TEXT,
    start_ms         BIGINT       NOT NULL,
    end_ms           BIGINT       NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    rejection_reason VARCHAR(2000),
    decided_by       VARCHAR(64),
    decided_at       TIMESTAMPTZ,
    excerpt_id       UUID REFERENCES podcast_excerpt (id),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_request_time CHECK (end_ms > start_ms),
    CONSTRAINT chk_request_start CHECK (start_ms >= 0),
    CONSTRAINT chk_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_request_decision CHECK (
        (status = 'PENDING' AND decided_at IS NULL AND decided_by IS NULL)
        OR (status <> 'PENDING' AND decided_at IS NOT NULL AND decided_by IS NOT NULL)
    ),
    CONSTRAINT chk_request_approved_excerpt CHECK (status <> 'APPROVED' OR excerpt_id IS NOT NULL)
);

CREATE INDEX idx_request_guest ON excerpt_request (guest_id, created_at DESC);
CREATE INDEX idx_request_episode ON excerpt_request (episode_id, status);
CREATE INDEX idx_request_status ON excerpt_request (status, created_at DESC);

CREATE TABLE notification (
    id              UUID PRIMARY KEY,
    recipient_id    VARCHAR(64)   NOT NULL,
    message         VARCHAR(1000) NOT NULL,
    event_type      VARCHAR(32)   NOT NULL,
    is_read         BOOLEAN       NOT NULL DEFAULT false,
    related_id      UUID,
    source_event_id UUID UNIQUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_recipient ON notification (recipient_id, created_at DESC);
CREATE INDEX idx_notification_unread ON notification (recipient_id) WHERE is_read = false;
