-- V9: Create indexes for performance

CREATE INDEX idx_devices_household_id ON devices(household_id);
CREATE INDEX idx_invoices_household_id ON invoices(household_id);
CREATE INDEX idx_warranties_household_id ON warranties(household_id);
CREATE INDEX idx_warranties_device_id ON warranties(device_id);
CREATE INDEX idx_warranties_end_date ON warranties(end_date);
CREATE INDEX idx_extraction_jobs_household_id ON extraction_jobs(household_id);
CREATE INDEX idx_extraction_jobs_invoice_id ON extraction_jobs(invoice_id);
CREATE INDEX idx_extraction_jobs_status ON extraction_jobs(status);
CREATE INDEX idx_notifications_household_id ON notifications(household_id);
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE UNIQUE INDEX idx_users_phone ON users(phone_number);
