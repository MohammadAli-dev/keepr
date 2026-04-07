/**
 * Sprint 5.2 - Operational Hardening of Intelligence Layer.
 * Adds versioning, confidence breakdown, and high-precision stage timings.
 */
ALTER TABLE extraction_jobs 
ADD COLUMN extraction_version INT DEFAULT 1,
ADD COLUMN confidence_breakdown JSONB,
ADD COLUMN ocr_ms INT,
ADD COLUMN parse_ms INT,
ADD COLUMN validate_ms INT,
ADD COLUMN total_fields_extracted INT,
ADD COLUMN successful_fields INT;
