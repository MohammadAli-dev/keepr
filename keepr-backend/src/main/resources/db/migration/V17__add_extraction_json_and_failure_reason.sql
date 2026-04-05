-- V17: Add extraction snapshot and failure reason for Intelligence Layer hardening
-- extraction_json (JSONB): Structured snapshot for re-parsing and debugging
-- failure_reason (VARCHAR): Explicit code for validation failures (e.g., LOW_CONFIDENCE)

ALTER TABLE extraction_jobs
ADD COLUMN extraction_json JSONB,
ADD COLUMN failure_reason VARCHAR(255);
