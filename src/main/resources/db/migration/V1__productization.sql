CREATE TABLE campaigns (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    business_type VARCHAR(120) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    radius_meters INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE discovery_runs (
    id VARCHAR(36) PRIMARY KEY,
    campaign_id VARCHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL,
    candidate_count INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(1000),
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_discovery_run_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(id)
);
CREATE INDEX idx_discovery_runs_campaign ON discovery_runs(campaign_id);
CREATE TABLE discovered_prospects (
    id VARCHAR(36) PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL,
    organization VARCHAR(255) NOT NULL,
    category VARCHAR(120) NOT NULL,
    address VARCHAR(500) NOT NULL,
    website VARCHAR(1000),
    phone VARCHAR(120),
    distance_miles DOUBLE PRECISION NOT NULL,
    source_url VARCHAR(1000),
    source_name VARCHAR(255),
    score INTEGER NOT NULL,
    score_explanation VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_discovered_prospect_run FOREIGN KEY (run_id) REFERENCES discovery_runs(id)
);
CREATE INDEX idx_discovered_prospects_run ON discovered_prospects(run_id);
CREATE INDEX idx_discovered_prospects_score ON discovered_prospects(score DESC);