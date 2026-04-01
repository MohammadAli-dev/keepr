-- V13: Create ingestion tables for async document processing

CREATE TABLE raw_documents (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_url VARCHAR(512) NOT NULL,
    file_type VARCHAR(50) NOT NULL,
    uploaded_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE extraction_jobs (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL,
    raw_document_id UUID NOT NULL REFERENCES raw_documents(id),
    status VARCHAR(50) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- Performance index for the worker polling query
CREATE INDEX idx_extraction_jobs_status_created 
ON extraction_jobs (status, created_at) 
WHERE deleted_at IS NULL;

-- Index for household-scoped document listing
CREATE INDEX idx_raw_documents_household 
ON raw_documents (household_id) 
WHERE deleted_at IS NULL;
