-- V6: Create notifications table

CREATE TABLE notifications (
    id              UUID PRIMARY KEY,
    household_id    UUID NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id       UUID REFERENCES devices(id) ON DELETE SET NULL,
    warranty_id     UUID REFERENCES warranties(id) ON DELETE SET NULL,
    channel         VARCHAR(20) NOT NULL CHECK (channel IN ('PUSH', 'WHATSAPP', 'SMS')),
    title           VARCHAR(255),
    body            TEXT,
    sent_at         TIMESTAMP WITH TIME ZONE,
    acted_on        BOOLEAN DEFAULT false,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
