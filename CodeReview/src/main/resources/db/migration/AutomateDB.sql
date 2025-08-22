CREATE TABLE IF NOT EXISTS users (
    user_id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
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
