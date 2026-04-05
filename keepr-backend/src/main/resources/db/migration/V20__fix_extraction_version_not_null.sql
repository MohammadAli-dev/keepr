-- V20: Sprint 5.3 - Final Remediation
-- Safe migration for extraction_version NOT NULL constraint.

-- 1. Ensure no NULL values exist before applying NOT NULL
UPDATE extraction_jobs
SET extraction_version = 1
WHERE extraction_version IS NULL;

-- 2. Apply NOT NULL constraint
ALTER TABLE extraction_jobs
ALTER COLUMN extraction_version SET NOT NULL;
