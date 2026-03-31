-- V2: Create devices table

CREATE TABLE devices (
    id              UUID PRIMARY KEY,
    household_id    UUID NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    brand           VARCHAR(255),
    model           VARCHAR(255),
    serial_number   VARCHAR(255),
    category        VARCHAR(100) NOT NULL,
    purchase_date   DATE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
