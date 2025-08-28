
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
    user_id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(10),
    password_hash TEXT,
    role VARCHAR(50),
    created_at TIMESTAMP
    );

CREATE TABLE IF NOT EXISTS projects (
    project_id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(user_id),
    name VARCHAR(255) NOT NULL,
    repository_url TEXT NOT NULL,
    project_type VARCHAR(50),
    sonar_project_key VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
    );

CREATE TABLE IF NOT EXISTS scans (
    scan_id UUID PRIMARY KEY,
    project_id UUID REFERENCES projects(project_id),
    status VARCHAR(50),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    quality_gate VARCHAR(20),
    metrics JSONB,
    log_file_path TEXT
    );

CREATE TABLE IF NOT EXISTS issues (
    issues_id UUID PRIMARY KEY,
    scan_id UUID REFERENCES scans(scan_id),
    issue_key VARCHAR(255),
    type VARCHAR(50),
    severity VARCHAR(20),
    component TEXT,
    message TEXT,
    assigned_to UUID,
    status VARCHAR(50),
    created_at TIMESTAMP
    );

CREATE TABLE IF NOT EXISTS comments (
    comments_id UUID PRIMARY KEY,
    issue_id UUID REFERENCES issues(issues_id),
    user_id UUID REFERENCES users(user_id),
    comment TEXT,
    created_at TIMESTAMP
    );
CREATE TABLE IF NOT EXISTS gate_history (
    gate_id UUID PRIMARY KEY,
    scan_id UUID REFERENCES scans(scan_id),
    quality_gate VARCHAR(10),
    created_at TIMESTAMP
    );


CREATE OR REPLACE FUNCTION log_gate_history()
    RETURNS TRIGGER AS $$
BEGIN
    IF NEW.quality_gate IS NOT NULL THEN
        INSERT INTO gate_history (gate_id, scan_id, quality_gate, created_at)
        VALUES (
                   gen_random_uuid(),
                   NEW.scan_id,
                   NEW.quality_gate,
                   COALESCE(NEW.completed_at, NEW.started_at, now())
               );
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_log_gate_history ON scans;
CREATE TRIGGER trg_log_gate_history
    AFTER INSERT OR UPDATE OF quality_gate ON scans
    FOR EACH ROW
EXECUTE FUNCTION log_gate_history();
