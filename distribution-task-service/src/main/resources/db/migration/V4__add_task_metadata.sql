ALTER TABLE distribution_task
    ADD COLUMN notes        TEXT,
    ADD COLUMN evidence_url VARCHAR(2048),
    ADD COLUMN updated_by   VARCHAR(64);
