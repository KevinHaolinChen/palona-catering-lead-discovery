ALTER TABLE campaigns ADD COLUMN supports_catering BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE campaigns ADD COLUMN primary_daypart VARCHAR(32) NOT NULL DEFAULT 'all_day';
ALTER TABLE campaigns ADD COLUMN price_tier VARCHAR(16) NOT NULL DEFAULT 'mid';
ALTER TABLE campaigns ADD COLUMN delivery_radius_miles INTEGER NOT NULL DEFAULT 5;

ALTER TABLE discovered_prospects ADD COLUMN latitude DOUBLE PRECISION;
ALTER TABLE discovered_prospects ADD COLUMN longitude DOUBLE PRECISION;
ALTER TABLE discovered_prospects ADD COLUMN profile_fit_score INTEGER NOT NULL DEFAULT 0;
ALTER TABLE discovered_prospects ADD COLUMN evidence_score INTEGER NOT NULL DEFAULT 0;
ALTER TABLE discovered_prospects ADD COLUMN evidence_summary VARCHAR(2000);
ALTER TABLE discovered_prospects ADD COLUMN evidence_source_url VARCHAR(1000);
