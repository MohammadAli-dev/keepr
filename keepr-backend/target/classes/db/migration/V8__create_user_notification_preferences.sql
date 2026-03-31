-- V8: Create user_notification_preferences table

CREATE TABLE user_notification_preferences (
    user_id             UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    push_enabled        BOOLEAN NOT NULL DEFAULT true,
    whatsapp_enabled    BOOLEAN NOT NULL DEFAULT true,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
