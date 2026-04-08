CREATE TABLE review_tasks (
    id              UUID PRIMARY KEY,
    job_id          UUID NOT NULL REFERENCES extraction_jobs(id),
    household_id    UUID NOT NULL REFERENCES households(id),
    raw_text        TEXT NOT NULL,
    extraction_json JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING', 'COMPLETED')),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_review_tasks_household_status
    ON review_tasks(household_id, status)
    WHERE status = 'PENDING';

CREATE UNIQUE INDEX idx_review_tasks_job_id_unique
    ON review_tasks(job_id);
