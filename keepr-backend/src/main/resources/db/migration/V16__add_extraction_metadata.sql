-- V16: Add extraction metadata columns for Intelligence Layer results
-- raw_text stores the full OCR output for auditing and re-parsing
-- confidence_score stores the machine-calculated extraction confidence (0.0 - 1.0)

ALTER TABLE extraction_jobs
ADD COLUMN raw_text TEXT,
ADD COLUMN confidence_score DOUBLE PRECISION;

-- Note: No defaults or non-null constraints yet to allow for backward compatibility
-- with existing pending/processing jobs during the deployment window.
