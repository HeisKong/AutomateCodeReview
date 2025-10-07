-- Enable once
CREATE EXTENSION IF NOT EXISTS pgcrypto;@@

CREATE TABLE IF NOT EXISTS public.users (
                                            user_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                            username      VARCHAR(100) NOT NULL UNIQUE,
                                            email         VARCHAR(255) NOT NULL UNIQUE,
                                            phone         VARCHAR(15),
                                            password_hash TEXT NOT NULL,
                                            role          VARCHAR(50) DEFAULT 'USER',
                                            created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);@@

CREATE TABLE IF NOT EXISTS public.projects (
                                               project_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                               user_id           UUID NOT NULL REFERENCES public.users(user_id) ON DELETE CASCADE,
                                               name              VARCHAR(255) NOT NULL,
                                               repository_url    TEXT NOT NULL,
                                               project_type      VARCHAR(50),
                                               sonar_project_key VARCHAR(255),
                                               created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
                                               updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);@@

CREATE TABLE IF NOT EXISTS public.scans (
                                            scan_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                            project_id    UUID NOT NULL REFERENCES public.projects(project_id) ON DELETE CASCADE,
                                            status        VARCHAR(50),
                                            started_at    TIMESTAMPTZ,
                                            completed_at  TIMESTAMPTZ,
                                            quality_gate  VARCHAR(20),
                                            metrics       JSONB,
                                            log_file_path TEXT
);@@

CREATE TABLE IF NOT EXISTS public.issues (
                                             issues_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                             scan_id     UUID NOT NULL REFERENCES public.scans(scan_id) ON DELETE CASCADE,
                                             issue_key   VARCHAR(255),
                                             type        VARCHAR(50),
                                             severity    VARCHAR(20),
                                             component   TEXT,
                                             message     TEXT,
                                             assigned_to UUID,
                                             status      VARCHAR(50),
                                             created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);@@

CREATE TABLE IF NOT EXISTS public.comments (
                                               comments_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                               issue_id    UUID NOT NULL REFERENCES public.issues(issues_id) ON DELETE CASCADE,
                                               user_id     UUID NOT NULL REFERENCES public.users(user_id) ON DELETE CASCADE,
                                               comment     TEXT,
                                               created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);@@

CREATE TABLE IF NOT EXISTS public.gate_history (
                                                   gate_id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                                   scan_id               UUID NOT NULL REFERENCES public.scans(scan_id) ON DELETE CASCADE,
                                                   reliability_gate      VARCHAR(1),
                                                   security_gate         VARCHAR(1),
                                                   maintainability_gate  VARCHAR(1),
                                                   security_review_gate  VARCHAR(1),
                                                   created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);@@

-- Helpful indexes for FKs
CREATE INDEX IF NOT EXISTS idx_projects_user_id        ON public.projects(user_id);@@
CREATE INDEX IF NOT EXISTS idx_scans_project_id        ON public.scans(project_id);@@
CREATE INDEX IF NOT EXISTS idx_issues_scan_id          ON public.issues(scan_id);@@
CREATE INDEX IF NOT EXISTS idx_comments_issue_id       ON public.comments(issue_id);@@
CREATE INDEX IF NOT EXISTS idx_comments_user_id        ON public.comments(user_id);@@
CREATE INDEX IF NOT EXISTS idx_gate_history_scan_id    ON public.gate_history(scan_id);@@

<<<<<<< Updated upstream
-- (แนะนำครั้งเดียว) ใช้ uuid ฟังก์ชันถ้ายังไม่ได้เปิด
-- CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1) ฟังก์ชันทริกเกอร์: บันทึก 4 gates + quality_gate + created_at
=======
-- Trigger function (INSERT + UPDATE)
>>>>>>> Stashed changes
CREATE OR REPLACE FUNCTION public.trg_scans_gate_history_cols()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.reliability_gate IS NOT NULL
            OR NEW.security_gate IS NOT NULL
            OR NEW.maintainability_gate IS NOT NULL
            OR NEW.security_review_gate IS NOT NULL
            OR NEW.quality_gate IS NOT NULL
        THEN
            INSERT INTO public.gate_history (
                gate_id, scan_id, reliability_gate, security_gate,
                maintainability_gate, security_review_gate, created_at, quality_gate
            ) VALUES (
                         gen_random_uuid(), NEW.scan_id, NEW.reliability_gate, NEW.security_gate,
                         NEW.maintainability_gate, NEW.security_review_gate,
                         COALESCE(NEW.created_at, NEW.started_at, NOW()), NEW.quality_gate
                     );
        END IF;
        RETURN NEW;

    ELSIF TG_OP = 'UPDATE' THEN
        IF (OLD.reliability_gate     IS DISTINCT FROM NEW.reliability_gate)
            OR (OLD.security_gate        IS DISTINCT FROM NEW.security_gate)
            OR (OLD.maintainability_gate IS DISTINCT FROM NEW.maintainability_gate)
            OR (OLD.security_review_gate IS DISTINCT FROM NEW.security_review_gate)
            OR (OLD.quality_gate         IS DISTINCT FROM NEW.quality_gate)
        THEN
            INSERT INTO public.gate_history (
                gate_id, scan_id, reliability_gate, security_gate,
                maintainability_gate, security_review_gate, created_at, quality_gate
            ) VALUES (
                         gen_random_uuid(), NEW.scan_id, NEW.reliability_gate, NEW.security_gate,
                         NEW.maintainability_gate, NEW.security_review_gate,
                         COALESCE(NEW.created_at, NEW.started_at, NOW()), NEW.quality_gate
                     );
        END IF;
        RETURN NEW;
    END IF;

    RETURN NEW;
END;
$$;

-- 2) ลบทริกเกอร์เก่าทิ้ง
DROP TRIGGER IF EXISTS trg_scans_gate_history_ai ON public.scans;
DROP TRIGGER IF EXISTS trg_scans_gate_history_au ON public.scans;

-- 3) สร้างทริกเกอร์ใหม่
CREATE TRIGGER trg_scans_gate_history_ai
    AFTER INSERT ON public.scans
    FOR EACH ROW
EXECUTE FUNCTION public.trg_scans_gate_history_cols();

CREATE TRIGGER trg_scans_gate_history_au
    AFTER UPDATE OF reliability_gate, security_gate, maintainability_gate, security_review_gate, quality_gate
    ON public.scans
    FOR EACH ROW
EXECUTE FUNCTION public.trg_scans_gate_history_cols();

