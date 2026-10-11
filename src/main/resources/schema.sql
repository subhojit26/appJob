-- JobWatch schema. Idempotent: safe to run on every startup.
-- SQLite, single writer. WAL is a persistent per-database setting, enabled here on init.
PRAGMA journal_mode=WAL;

-- Normalized job (mirrors Job.java) plus notification/audit state.
CREATE TABLE IF NOT EXISTS jobs (
  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
  source                TEXT    NOT NULL,
  source_job_id         TEXT    NOT NULL,
  title                 TEXT    NOT NULL,
  company               TEXT,
  location              TEXT,
  remote                INTEGER NOT NULL DEFAULT 0,
  workplace_type        TEXT,
  apply_url             TEXT,
  job_url               TEXT,
  description           TEXT,
  posted_at             TEXT,               -- ISO-8601, nullable
  min_experience_years  INTEGER,
  max_experience_years  INTEGER,
  salary_min            REAL,
  salary_max            REAL,
  salary_currency       TEXT,
  salary_estimated      INTEGER NOT NULL DEFAULT 0,
  employment_type       TEXT,
  fetched_at            TEXT,               -- ISO-8601
  stack                 TEXT,               -- comma-separated tech tags
  profile               TEXT,               -- winning profile name (null for suppressed non-matches)
  score                 INTEGER,            -- match score (null for suppressed non-matches)
  notified_at           TEXT,               -- ISO-8601, null until digest sent
  applied_at            TEXT,               -- ISO-8601, null until user marks applied (dashboard)
  created_at            TEXT    NOT NULL,   -- ISO-8601
  updated_at            TEXT,               -- ISO-8601, set on update
  UNIQUE (source, source_job_id)
);
CREATE INDEX IF NOT EXISTS idx_jobs_pending ON jobs(notified_at);

-- Per-source run statistics (maps from SourceStats).
CREATE TABLE IF NOT EXISTS source_runs (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  source        TEXT    NOT NULL,
  started_at    TEXT    NOT NULL,
  completed_at  TEXT,
  success       INTEGER NOT NULL,
  jobs_fetched  INTEGER,
  error         TEXT
);
CREATE INDEX IF NOT EXISTS idx_source_runs_source ON source_runs(source);

-- Dynamically discovered company roster (Phase 6 / FR-1a/FR-1b).
-- Replaces hardcoded slugs in application.yml. One row per (ats source, slug).
CREATE TABLE IF NOT EXISTS companies (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  source           TEXT    NOT NULL,            -- greenhouse | lever | ashby
  slug             TEXT    NOT NULL,
  display_name     TEXT,
  discovered_at    TEXT    NOT NULL,            -- ISO-8601
  last_ok_at       TEXT,                        -- last time the board returned jobs
  consecutive_404  INTEGER NOT NULL DEFAULT 0,
  enabled          INTEGER NOT NULL DEFAULT 1,
  UNIQUE (source, slug)
);
CREATE INDEX IF NOT EXISTS idx_companies_enabled ON companies(source, enabled);

-- User-editable role profiles (Phase 10 increment). Replaces application.yml profiles as the
-- source of truth once seeded; editable via the dashboard UI. Lists stored comma-separated.
CREATE TABLE IF NOT EXISTS profiles (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  name           TEXT    NOT NULL UNIQUE,
  include_title  TEXT,                        -- role keywords (csv)
  stack          TEXT,                        -- skill/stack keywords (csv)
  exclude_title  TEXT,                        -- disqualifying keywords (csv)
  companies      TEXT,                        -- preferred companies (csv, empty means any)
  exp_min        INTEGER,                     -- desired min years (nullable)
  exp_max        INTEGER,                     -- desired max years (nullable)
  enabled        INTEGER NOT NULL DEFAULT 1,
  is_active      INTEGER NOT NULL DEFAULT 0,  -- FR-39a: the single active profile gates emails
  created_at     TEXT    NOT NULL,
  updated_at     TEXT
);

