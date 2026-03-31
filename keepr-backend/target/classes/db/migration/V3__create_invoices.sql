-- V3: Create invoices and device_invoices tables

CREATE TABLE invoices (
    id                      UUID PRIMARY KEY,
    household_id            UUID NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    raw_file_url            VARCHAR(1024),
    source                  VARCHAR(50) NOT NULL,
    store_name              VARCHAR(255),
    invoice_number          VARCHAR(100),
    purchase_date           DATE,
    total_amount            DECIMAL(12, 2),
    extraction_confidence   FLOAT,
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE device_invoices (
    device_id    UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    invoice_id   UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    is_primary   BOOLEAN DEFAULT true,
    PRIMARY KEY (device_id, invoice_id)
);
