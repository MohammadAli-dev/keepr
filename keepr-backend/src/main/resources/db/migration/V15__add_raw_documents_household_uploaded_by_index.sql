-- V15: Add optimized compound index for multi-tenant document queries
-- Lead with household_id for high tenancy-based selectivity as per Staff Engineer review

CREATE INDEX idx_raw_documents_household_uploaded_by 
ON raw_documents(household_id, uploaded_by);
