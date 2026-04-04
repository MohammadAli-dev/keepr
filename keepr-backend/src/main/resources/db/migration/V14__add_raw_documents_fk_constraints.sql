-- V14: Add foreign key constraints for multi-tenancy integrity in ingestion tables

-- Ensure raw_documents belongs to a valid household
ALTER TABLE raw_documents
ADD CONSTRAINT fk_raw_documents_household
FOREIGN KEY (household_id) REFERENCES households(id);

-- Ensure raw_documents belongs to a valid user (uploader)
ALTER TABLE raw_documents
ADD CONSTRAINT fk_raw_documents_user
FOREIGN KEY (uploaded_by) REFERENCES users(id);

-- Ensure extraction_jobs belongs to a valid household
ALTER TABLE extraction_jobs
ADD CONSTRAINT fk_extraction_jobs_household
FOREIGN KEY (household_id) REFERENCES households(id);
