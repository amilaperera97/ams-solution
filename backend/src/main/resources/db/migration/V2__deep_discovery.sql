-- Add deep discovery fields to certificate table
ALTER TABLE certificate ADD COLUMN fingerprint VARCHAR(255);
ALTER TABLE certificate ADD COLUMN serial_number VARCHAR(255);
ALTER TABLE certificate ADD COLUMN subject VARCHAR(1024);
ALTER TABLE certificate ADD COLUMN source_type VARCHAR(100);
ALTER TABLE certificate ADD COLUMN key_size INTEGER;
ALTER TABLE certificate ADD COLUMN usages_json TEXT;
ALTER TABLE certificate ADD COLUMN tags_json TEXT;
