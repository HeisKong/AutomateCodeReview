
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
    user_id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(10),
    password_hash TEXT,
    phone VARCHAR(10),
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


-- ใช้ใน AutomateDB (schema public)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ตารางหลัก
CREATE TABLE IF NOT EXISTS public.sonar_projects(
                                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                                    project_key TEXT UNIQUE NOT NULL,
                                                    name TEXT,
                                                    created_at TIMESTAMP DEFAULT now(),
                                                    updated_at TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.sonar_analyses(
                                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                                    task_id TEXT UNIQUE NOT NULL,
                                                    project_key TEXT NOT NULL,
                                                    status TEXT,
                                                    quality_gate TEXT,
                                                    analysed_at TIMESTAMP,
                                                    created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.sonar_measures(
                                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                                    analysis_task_id TEXT NOT NULL,
                                                    project_key TEXT NOT NULL,
                                                    metric TEXT NOT NULL,
                                                    value TEXT,
                                                    UNIQUE (analysis_task_id, project_key, metric)
);

CREATE TABLE IF NOT EXISTS public.sonar_issues(
                                                  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                                  issue_key TEXT UNIQUE NOT NULL,
                                                  project_key TEXT NOT NULL,
                                                  type TEXT,
                                                  severity TEXT,
                                                  status TEXT,
                                                  message TEXT,
                                                  component TEXT,
                                                  created_at TIMESTAMP DEFAULT now()
);

-- ===== FKs (ใช้ DO-block เช็กก่อนค่อยเพิ่ม) =====
DO $$
    BEGIN
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint WHERE conname = 'fk_sonar_analyses_project'
        ) THEN
            ALTER TABLE public.sonar_analyses
                ADD CONSTRAINT fk_sonar_analyses_project
                    FOREIGN KEY (project_key)
                        REFERENCES public.sonar_projects(project_key)
                        ON DELETE CASCADE;
        END IF;
    END$$;

DO $$
    BEGIN
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint WHERE conname = 'fk_sonar_measures_project'
        ) THEN
            ALTER TABLE public.sonar_measures
                ADD CONSTRAINT fk_sonar_measures_project
                    FOREIGN KEY (project_key)
                        REFERENCES public.sonar_projects(project_key)
                        ON DELETE CASCADE;
        END IF;
    END$$;

DO $$
    BEGIN
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint WHERE conname = 'fk_sonar_issues_project'
        ) THEN
            ALTER TABLE public.sonar_issues
                ADD CONSTRAINT fk_sonar_issues_project
                    FOREIGN KEY (project_key)
                        REFERENCES public.sonar_projects(project_key)
                        ON DELETE CASCADE;
        END IF;
    END$$;

-- ===== Indexes =====
CREATE INDEX IF NOT EXISTS ix_sonar_analyses_project_time
    ON public.sonar_analyses (project_key, analysed_at DESC);

CREATE INDEX IF NOT EXISTS ix_sonar_measures_project_metric
    ON public.sonar_measures (project_key, metric);

CREATE INDEX IF NOT EXISTS ix_sonar_issues_project
    ON public.sonar_issues (project_key);

CREATE INDEX IF NOT EXISTS ix_sonar_issues_type
    ON public.sonar_issues (type);

CREATE INDEX IF NOT EXISTS ix_sonar_issues_severity
    ON public.sonar_issues (severity);

CREATE INDEX IF NOT EXISTS ix_sonar_issues_status
    ON public.sonar_issues (status);
