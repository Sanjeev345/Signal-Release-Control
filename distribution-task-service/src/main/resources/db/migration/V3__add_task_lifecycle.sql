ALTER TABLE distribution_task
    ADD COLUMN completed_at TIMESTAMPTZ,
    ADD COLUMN version      BIGINT NOT NULL DEFAULT 0;

ALTER TABLE distribution_task
    ADD CONSTRAINT chk_task_completed_at CHECK (
        (status = 'DONE' AND completed_at IS NOT NULL)
        OR (status <> 'DONE' AND completed_at IS NULL)
    );
