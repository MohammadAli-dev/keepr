-- V10: Create auth_otp table for OTP-based login

CREATE TABLE auth_otp (
    id              UUID PRIMARY KEY,
    phone_number    VARCHAR(20) NOT NULL,
    otp_code        VARCHAR(6) NOT NULL,
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Index for fast OTP lookups during verification
CREATE INDEX idx_auth_otp_phone ON auth_otp(phone_number);
