-- V5: Create extraction_jobs table

CREATE TABLE extraction_jobs (
    id                UUID PRIMARY KEY,
    household_id      UUID NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    invoice_id        UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count       INT NOT NULL DEFAULT 0,
    error_message     TEXT,
    last_attempted_at TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
