ALTER TABLE discovered_prospects ADD COLUMN campaign_id VARCHAR(36);
ALTER TABLE discovered_prospects ADD COLUMN prospect_key VARCHAR(500);
ALTER TABLE discovered_prospects ADD COLUMN pipeline_stage VARCHAR(32) NOT NULL DEFAULT 'NEW';
ALTER TABLE discovered_prospects ADD COLUMN next_follow_up_at TIMESTAMP;
ALTER TABLE discovered_prospects ADD COLUMN last_contacted_at TIMESTAMP;
ALTER TABLE discovered_prospects ADD COLUMN pipeline_note VARCHAR(2000);
ALTER TABLE discovered_prospects ADD COLUMN learned_adjustment INTEGER NOT NULL DEFAULT 0;
ALTER TABLE discovered_prospects ADD COLUMN first_seen_at TIMESTAMP;
ALTER TABLE discovered_prospects ADD COLUMN last_seen_at TIMESTAMP;

UPDATE discovered_prospects SET first_seen_at = created_at WHERE first_seen_at IS NULL;
UPDATE discovered_prospects SET last_seen_at = created_at WHERE last_seen_at IS NULL;

UPDATE discovered_prospects
SET campaign_id = (
    SELECT discovery_runs.campaign_id
    FROM discovery_runs
    WHERE discovery_runs.id = discovered_prospects.run_id
)
WHERE campaign_id IS NULL;

UPDATE discovered_prospects
SET prospect_key = COALESCE(source_url, organization || '|' || address)
WHERE prospect_key IS NULL;
