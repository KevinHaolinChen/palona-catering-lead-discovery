ALTER TABLE campaigns
    ADD COLUMN origin_address VARCHAR(500) NOT NULL DEFAULT 'Unknown';
