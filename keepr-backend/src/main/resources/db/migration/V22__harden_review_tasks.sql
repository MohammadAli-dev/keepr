-- V22: Harden review_tasks table

-- 1. Ensure unique constraint on job_id is idempotent
CREATE UNIQUE INDEX IF NOT EXISTS idx_review_tasks_job_id_unique ON review_tasks(job_id);

-- 2. Update foreign keys to ON DELETE RESTRICT
ALTER TABLE review_tasks DROP CONSTRAINT IF EXISTS review_tasks_job_id_fkey;
ALTER TABLE review_tasks ADD CONSTRAINT review_tasks_job_id_fkey
    FOREIGN KEY (job_id) REFERENCES extraction_jobs(id) ON DELETE RESTRICT;

ALTER TABLE review_tasks DROP CONSTRAINT IF EXISTS review_tasks_household_id_fkey;
ALTER TABLE review_tasks ADD CONSTRAINT review_tasks_household_id_fkey
    FOREIGN KEY (household_id) REFERENCES households(id) ON DELETE RESTRICT;

-- 3. Add updated_at trigger function and trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_review_tasks_updated_at
    BEFORE UPDATE ON review_tasks
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
