-- V4: Create warranties table

CREATE TABLE warranties (
    id              UUID PRIMARY KEY,
    household_id    UUID NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    device_id       UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    invoice_id      UUID REFERENCES invoices(id) ON DELETE SET NULL,
    type            VARCHAR(30) NOT NULL CHECK (type IN ('MANUFACTURER', 'EXTENDED', 'AMC')),
    provider        VARCHAR(255),
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
