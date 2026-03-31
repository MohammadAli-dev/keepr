-- V12: Add unique constraint to prevent duplicate active devices within a household

CREATE UNIQUE INDEX idx_devices_unique_active 
ON devices (household_id, name, brand, model) 
WHERE deleted_at IS NULL;
