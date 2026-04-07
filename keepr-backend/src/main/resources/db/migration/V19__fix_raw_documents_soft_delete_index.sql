-- V19: Fix raw_documents index to include soft-delete predicate
-- CodeRabbit review: idx_raw_documents_household_uploaded_by missed consistent soft-delete filtering

DROP INDEX IF EXISTS idx_raw_documents_household_uploaded_by;

CREATE INDEX idx_raw_documents_household_uploaded_by 
ON raw_documents(household_id, uploaded_by)
WHERE deleted_at IS NULL;
