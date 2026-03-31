-- V1: Create users, households, and household_members tables

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    phone_number    VARCHAR(20) UNIQUE NOT NULL,
    email           VARCHAR(255),
    name            VARCHAR(255),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    is_active       BOOLEAN DEFAULT true
);

CREATE TABLE households (
    id              UUID PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    owner_user_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE household_members (
    household_id    UUID NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'MEMBER')),
    joined_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (household_id, user_id)
);
