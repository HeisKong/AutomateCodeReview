-- Enable once
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS public.users (
                                            user_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                            username      VARCHAR(100) NOT NULL UNIQUE,
                                            email         VARCHAR(255) NOT NULL UNIQUE,
                                            phone         VARCHAR(15),
                                            password_hash TEXT NOT NULL,
                                            role          VARCHAR(50) DEFAULT 'USER',
                                            created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.projects (
                                               project_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                               user_id           UUID NOT NULL REFERENCES public.users(user_id) ON DELETE CASCADE,
                                               name              VARCHAR(255) NOT NULL,
                                               repository_url    TEXT NOT NULL,
                                               project_type      VARCHAR(50),
                                               sonar_project_key VARCHAR(255),
                                               created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
                                               updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.scans (
                                            scan_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                            project_id   UUID NOT NULL REFERENCES public.projects(project_id) ON DELETE CASCADE,
                                            status       VARCHAR(50),
                                            started_at   TIMESTAMPTZ,
                                            completed_at TIMESTAMPTZ,
                                            quality_gate VARCHAR(20),
                                            metrics      JSONB,
                                            log_file_path TEXT
);

CREATE TABLE IF NOT EXISTS public.issues (
                                             issues_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                             scan_id    UUID NOT NULL REFERENCES public.scans(scan_id) ON DELETE CASCADE,
                                             issue_key  VARCHAR(255),
                                             type       VARCHAR(50),
                                             severity   VARCHAR(20),
                                             component  TEXT,
                                             message    TEXT,
                                             assigned_to UUID,
                                             status     VARCHAR(50),
                                             created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.comments (
                                               comments_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                               issue_id    UUID NOT NULL REFERENCES public.issues(issues_id) ON DELETE CASCADE,
                                               user_id     UUID NOT NULL REFERENCES public.users(user_id) ON DELETE CASCADE,
                                               comment     TEXT,
                                               created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.gate_history (
                                                gate_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                                scan_id     UUID NOT NULL REFERENCES public.scans(scan_id) ON DELETE CASCADE,
                                                reliability_gate varchar(1),
                                                security_gate varchar(1),
                                                maintainability_gate varchar(1),
                                                security_review_gate varchar(1),
                                                created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Helpful indexes for FKs
CREATE INDEX IF NOT EXISTS idx_projects_user_id   ON public.projects(user_id);
CREATE INDEX IF NOT EXISTS idx_scans_project_id   ON public.scans(project_id);
CREATE INDEX IF NOT EXISTS idx_issues_scan_id     ON public.issues(scan_id);
CREATE INDEX IF NOT EXISTS idx_comments_issue_id  ON public.comments(issue_id);
CREATE INDEX IF NOT EXISTS idx_comments_user_id   ON public.comments(user_id);
CREATE INDEX IF NOT EXISTS idx_gate_history_scan_id on public.gate_history(scan_id)