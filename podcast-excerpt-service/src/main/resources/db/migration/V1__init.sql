CREATE TABLE podcast_episode (
    id            UUID PRIMARY KEY,
    code          VARCHAR(64)  NOT NULL UNIQUE,
    title         VARCHAR(255) NOT NULL,
    producer_id   VARCHAR(64)  NOT NULL,
    published_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE podcast_excerpt (
    id          UUID PRIMARY KEY,
    episode_id  UUID         NOT NULL REFERENCES podcast_episode (id),
    code        VARCHAR(64)  NOT NULL UNIQUE,
    guest_id    VARCHAR(64)  NOT NULL,
    start_ms    BIGINT       NOT NULL,
    end_ms      BIGINT       NOT NULL,
    transcript  TEXT,
    tag_status  VARCHAR(16)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_excerpt_time CHECK (end_ms > start_ms),
    CONSTRAINT chk_excerpt_start CHECK (start_ms >= 0)
);

CREATE INDEX idx_excerpt_episode ON podcast_excerpt (episode_id);
CREATE INDEX idx_excerpt_guest ON podcast_excerpt (guest_id);
