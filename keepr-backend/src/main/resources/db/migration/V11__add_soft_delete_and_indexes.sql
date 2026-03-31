-- V11: Add soft delete columns and performance indexes for ownership/overlap queries

-- 1. Soft delete columns
ALTER TABLE devices ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE NULL;
ALTER TABLE warranties ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE NULL;

-- 2. Indexes for auth OTP lookup
CREATE INDEX idx_auth_otp_phone_number ON auth_otp(phone_number);

-- 3. Indexes for soft delete optimized scoping
CREATE INDEX idx_devices_household_deleted ON devices(household_id, deleted_at);
CREATE INDEX idx_warranties_household_deleted ON warranties(household_id, deleted_at);

-- 4. Compound index for overlap queries
CREATE INDEX idx_warranties_device_household_deleted ON warranties(device_id, household_id, deleted_at);
