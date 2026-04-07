-- ============================================================
-- SECRETARY CRM — COMPLETE REPAIR MIGRATION
-- Date: 2026-04-02
-- Run in Railway Query Editor (Ctrl+Enter)
-- BACKUP FIRST!
-- ============================================================

SET search_path TO crm, public;

-- ============================================================
-- PHASE 1: ADD MISSING COLUMNS TO EXISTING TABLES
-- ============================================================

-- CLIENTS: missing tenant_id, company_registration_no, vat_no
ALTER TABLE crm.clients ADD COLUMN IF NOT EXISTS tenant_id INT NOT NULL DEFAULT 1;
ALTER TABLE crm.clients ADD COLUMN IF NOT EXISTS company_registration_no TEXT;
ALTER TABLE crm.clients ADD COLUMN IF NOT EXISTS vat_no TEXT;

-- LEADS: missing 7 columns (ALTER never applied due to schema path bug)
ALTER TABLE crm.leads ADD COLUMN IF NOT EXISTS contact_name TEXT;
ALTER TABLE crm.leads ADD COLUMN IF NOT EXISTS contact_email TEXT;
ALTER TABLE crm.leads ADD COLUMN IF NOT EXISTS contact_phone TEXT;
ALTER TABLE crm.leads ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE crm.leads ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE crm.leads ADD COLUMN IF NOT EXISTS job_id BIGINT;
ALTER TABLE crm.leads ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT now();
ALTER TABLE crm.leads ADD COLUMN IF NOT EXISTS tenant_id INT NOT NULL DEFAULT 1;

-- COMMUNICATIONS: missing 3 columns
ALTER TABLE crm.communications ADD COLUMN IF NOT EXISTS comm_type TEXT DEFAULT 'telefon';
ALTER TABLE crm.communications ADD COLUMN IF NOT EXISTS job_id BIGINT;
ALTER TABLE crm.communications ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE crm.communications ADD COLUMN IF NOT EXISTS tenant_id INT NOT NULL DEFAULT 1;

-- JOBS: property_id must be nullable + add tenant_id
ALTER TABLE crm.jobs ALTER COLUMN property_id DROP NOT NULL;
ALTER TABLE crm.jobs ALTER COLUMN property_id SET DEFAULT NULL;
ALTER TABLE crm.jobs ADD COLUMN IF NOT EXISTS tenant_id INT NOT NULL DEFAULT 1;

-- INVOICES: add tenant_id
ALTER TABLE crm.invoices ADD COLUMN IF NOT EXISTS tenant_id INT NOT NULL DEFAULT 1;

-- PROPERTIES: add tenant_id
ALTER TABLE crm.properties ADD COLUMN IF NOT EXISTS tenant_id INT NOT NULL DEFAULT 1;

-- ACTIVITY_TIMELINE (crm): missing tenant_id, user_id_ref
ALTER TABLE crm.activity_timeline ADD COLUMN IF NOT EXISTS tenant_id INT DEFAULT 1;
ALTER TABLE crm.activity_timeline ADD COLUMN IF NOT EXISTS user_id_ref TEXT;

-- TASKS (crm): add tenant_id
ALTER TABLE crm.tasks ADD COLUMN IF NOT EXISTS tenant_id INT NOT NULL DEFAULT 1;

-- PHOTOS (crm): add tenant_id
ALTER TABLE crm.photos ADD COLUMN IF NOT EXISTS tenant_id INT NOT NULL DEFAULT 1;

-- WASTE_LOADS: add tenant_id
ALTER TABLE crm.waste_loads ADD COLUMN IF NOT EXISTS tenant_id INT NOT NULL DEFAULT 1;

-- CLIENT_NOTES: add tenant_id
ALTER TABLE crm.client_notes ADD COLUMN IF NOT EXISTS tenant_id INT NOT NULL DEFAULT 1;

-- JOB_NOTES: add tenant_id
ALTER TABLE crm.job_notes ADD COLUMN IF NOT EXISTS tenant_id INT NOT NULL DEFAULT 1;

-- TASK_HISTORY: add tenant_id
ALTER TABLE crm.task_history ADD COLUMN IF NOT EXISTS tenant_id INT NOT NULL DEFAULT 1;

-- ============================================================
-- PHASE 2: CREATE MISSING TABLES (from schema.sql)
-- ============================================================

CREATE TABLE IF NOT EXISTS crm.roles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    role_name TEXT NOT NULL UNIQUE,
    description TEXT,
    is_system BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS crm.permissions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    permission_code TEXT NOT NULL UNIQUE,
    module TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS crm.role_permissions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    role_id BIGINT NOT NULL REFERENCES crm.roles(id),
    permission_id BIGINT NOT NULL REFERENCES crm.permissions(id),
    UNIQUE(role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS crm.users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id INT NOT NULL DEFAULT 1,
    employee_code TEXT,
    role_id BIGINT REFERENCES crm.roles(id),
    first_name TEXT NOT NULL,
    last_name TEXT NOT NULL,
    display_name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    phone TEXT,
    status TEXT NOT NULL DEFAULT 'active',
    password_hash TEXT NOT NULL DEFAULT '',
    last_login_at TIMESTAMPTZ,
    timezone TEXT NOT NULL DEFAULT 'Europe/London',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS crm.audit_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id INT NOT NULL DEFAULT 1,
    user_id BIGINT,
    action TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT,
    old_values JSONB,
    new_values JSONB,
    ip_address TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS crm.quotes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id INT NOT NULL DEFAULT 1,
    quote_number TEXT UNIQUE,
    client_id BIGINT,
    job_id BIGINT,
    status TEXT NOT NULL DEFAULT 'draft',
    total NUMERIC(12,2) DEFAULT 0,
    valid_until DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS crm.tenants (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name TEXT NOT NULL,
    slug TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO crm.tenants (name, slug) VALUES ('DesignLeaf', 'designleaf')
ON CONFLICT (slug) DO NOTHING;

CREATE TABLE IF NOT EXISTS crm.migration_log (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    filename TEXT NOT NULL UNIQUE,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    applied_by TEXT DEFAULT 'manual'
);

-- Seed default admin user
INSERT INTO crm.roles (role_name, description, is_system)
VALUES ('admin', 'Full system access', true)
ON CONFLICT (role_name) DO NOTHING;

INSERT INTO crm.users (tenant_id, first_name, last_name, display_name, email, role_id)
SELECT 1, 'Marek', 'Sima', 'Marek Sima', 'marek@designleaf.co.uk', r.id
FROM crm.roles r WHERE r.role_name = 'admin'
ON CONFLICT (email) DO NOTHING;

-- ============================================================
-- PHASE 3: MIGRATE DATA FROM PUBLIC TO CRM (duplicates)
-- Then drop public duplicates
-- ============================================================

-- Migrate any data from public.tasks to crm.tasks (if public has data crm doesn't)
INSERT INTO crm.tasks (id, title, description, task_type, status, priority, created_at,
    deadline, planned_date, time_window_start, time_window_end, estimated_minutes,
    actual_minutes, created_by, assigned_to, delegated_by, client_id, client_name,
    job_id, property_id, property_address, is_recurring, recurrence_rule, result,
    notes, communication_method, source, is_billable, has_cost, waiting_for_payment,
    checklist, is_completed, updated_at)
SELECT id, title, description, task_type, status, priority, created_at,
    deadline, planned_date, time_window_start, time_window_end, estimated_minutes,
    actual_minutes, created_by, assigned_to, delegated_by, client_id, client_name,
    job_id, property_id, property_address, is_recurring, recurrence_rule, result,
    notes, communication_method, source, is_billable, has_cost, waiting_for_payment,
    checklist, is_completed, updated_at
FROM public.tasks WHERE id NOT IN (SELECT id FROM crm.tasks);

-- Migrate voice_sessions
INSERT INTO crm.voice_sessions (id, tenant_id, user_id, session_type, state, dialog_step, context, created_at, updated_at, expires_at)
SELECT id, tenant_id, user_id, session_type, state, dialog_step, context, created_at, updated_at, expires_at
FROM public.voice_sessions WHERE id NOT IN (SELECT id FROM crm.voice_sessions);

-- Migrate work_reports
INSERT INTO crm.work_reports (id, tenant_id, client_id, property_id, job_id, work_date, total_hours, total_price, currency, notes, created_by, input_type, status, created_at, updated_at)
SELECT id, tenant_id, client_id, property_id, job_id, work_date, total_hours, total_price, currency, notes, created_by, input_type, status, created_at, updated_at
FROM public.work_reports wr WHERE NOT EXISTS (SELECT 1 FROM crm.work_reports cr WHERE cr.id = wr.id);

-- Migrate activity_timeline
INSERT INTO crm.activity_timeline (id, entity_type, entity_id, action, description, user_name, created_at)
SELECT id, entity_type, entity_id, action, description, user_name, created_at
FROM public.activity_timeline pa WHERE NOT EXISTS (SELECT 1 FROM crm.activity_timeline ca WHERE ca.id = pa.id);

-- ============================================================
-- PHASE 4: DROP PUBLIC DUPLICATES
-- ============================================================

DROP TABLE IF EXISTS public.work_report_waste CASCADE;
DROP TABLE IF EXISTS public.work_report_materials CASCADE;
DROP TABLE IF EXISTS public.work_report_entries CASCADE;
DROP TABLE IF EXISTS public.work_report_workers CASCADE;
DROP TABLE IF EXISTS public.work_reports CASCADE;
DROP TABLE IF EXISTS public.voice_sessions CASCADE;
DROP TABLE IF EXISTS public.tasks CASCADE;
DROP TABLE IF EXISTS public.task_history CASCADE;
DROP TABLE IF EXISTS public.activity_timeline CASCADE;
DROP TABLE IF EXISTS public.photos CASCADE;
DROP TABLE IF EXISTS public.client_notes CASCADE;
DROP TABLE IF EXISTS public.job_notes CASCADE;
DROP TABLE IF EXISTS public.pricing_rules CASCADE;

-- ============================================================
-- PHASE 5: LOG THIS MIGRATION
-- ============================================================

INSERT INTO crm.migration_log (filename, applied_by)
VALUES ('001_full_repair.sql', 'Marek manual')
ON CONFLICT (filename) DO NOTHING;

-- ============================================================
-- VERIFICATION: Run this after to confirm
-- ============================================================
-- SELECT table_schema, table_name FROM information_schema.tables
-- WHERE table_schema IN ('crm','public') AND table_type='BASE TABLE'
-- ORDER BY 1,2;
-- Expected: ALL tables in crm, NONE in public
