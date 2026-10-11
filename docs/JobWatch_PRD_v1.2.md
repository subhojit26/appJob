# JobWatch: PRD and Implementation Plan

**Version:** 1.2 (Spring Boot 4.1.1 dependency refresh) | **Owner:** You | **Stack:** Java 21, Spring Boot 4.1.1 (Spring Framework 7, Jackson 3), SQLite | **Target cost:** $0-6/month

> **v1.2 change summary:** The PRD is now consistently based on Spring Boot 4.1.1. All Bootstrap/Phase 0 dependency references have been updated from stale Spring Boot 3.x guidance to Boot 4.1.1. The dependency strategy uses Boot 4.1.1-managed versions wherever possible; no Spring Boot 3.x starters, APIs, or dependency examples should be introduced. All functional requirements and business logic are unchanged.

> **v1.3 planned additions (not yet implemented):** Two new capabilities are specified in this document but deliberately scheduled for later phases so they do not disrupt the MVP. (1) **Analytics dashboard** — a lightweight local web UI that shows how many jobs were emailed to you over time and lets you track how many you applied to (see §4.7 and Phase 9). (2) **Zero hardcoded companies** — company slugs are no longer hand-maintained in `application.yml`; they are discovered dynamically from your role profiles and persisted in the DB (see §4.1 FR-1a/FR-1b, §7.10, and Phase 6). These sections are additive; the MVP (Phases 0-5) is unchanged. Implement them only when their phase is reached.

> How to use this document: Part A defines *what* we build and why. Part B defines *how*, phase by phase, with acceptance criteria. Do not start a phase until the previous phase's acceptance criteria pass. Tick the checkboxes as you go.

---

# PART A: PRODUCT REQUIREMENTS

## 1. Problem and goal

Job openings are spread across LinkedIn, company career pages, and aggregators. Early applicants get a real advantage, but checking every source manually is slow. The goal is a personal tool that watches many sources continuously, filters for the roles you want, and emails you a clean digest with everything needed to decide and apply.

**Success criteria (measurable):**

| # | Metric | Target |
|---|---|---|
| S1 | Detection latency for jobs on career-page sources | New job appears in your inbox within 60 minutes of being posted |
| S2 | Duplicate rate | Under 5% of listed jobs are duplicates of another listed job |
| S3 | Relevance | At least 70% of emailed jobs match a role you'd actually apply to (after 2 weeks of tuning) |
| S4 | Reliability | One failing source never blocks the others. A dead pipeline is detected within 2 hours |
| S5 | Cost | $0-6/month, under 2 hours/month maintenance |

## 2. Non-goals (v1)

These are deliberately excluded. Adding them early is how this project doubles in size.

- Referral or connection discovery (dropped by decision)
- Scraping LinkedIn, Indeed, or Naukri (ToS violations, account risk, constant breakage)
- Auto-applying, resume tailoring, or resume-to-job matching
- A multi-user web app with accounts, authentication, or public hosting **in v1** (the planned v1.3 analytics dashboard in §4.7 is a **single-user, local-only, read-only** UI). A full multi-user hosted product *is* the intended **v2 direction** and is specified separately in §4.8 — but it is deliberately out of scope until the single-user MVP ships
- LLM-based extraction (a later optional add-on)
- Automatic application tracking from your inbox (the dashboard only lets you *manually* mark a job as applied)

## 3. Assumptions and open decisions

I still have not received answers to some questions. Each has a **default** so development is not blocked. Confirm or change them before Phase 2.

| # | Question | Default assumption | Affects |
|---|---|---|---|
| Q1 | Target locations (country or cities)? Is remote acceptable? | India plus remote-worldwide, configurable | Which aggregators matter, location filter |
| Q2 | Experience level (years)? | Filter by your range, configurable | Seniority filter |
| Q3 | Where will it run? | ~$5 VPS or Oracle free VM, systemd | Deployment (Phase 7) |
| Q4 | How urgent is speed? | Hourly digest is enough. Telegram instant push is optional (Phase 8) | Scheduling |
| Q5 | Gmail as the sender? | Yes, Gmail with an app password (needs 2FA on) | Notify module |
| Q6 | Which companies do you want watched? | **No hardcoded list.** Companies are discovered dynamically from your role profiles/`company-types` and stored in the DB (FR-1a/FR-1b, §7.10). You supply preferences, not a roster | Career-page sources |
| Q7 | Do you want LinkedIn/Naukri coverage at all? | Yes, via their own alert emails kept on in parallel (Phase 8, optional Gmail ingestion) | Scope |
| Q8 | Which "Spring 4.1.1"? | **Spring Boot 4.1.1** (Spring Framework 7). Spring *Framework* 4.1.1 is a 2014 release for Java 6-8 and would make most of this plan unusable. If you truly mean that, tell me, because the design changes substantially | Whole build |
| Q9 | Single-user tool or multi-user hosted app? | **Single-user for v1** (config-driven, no login). Multi-user accounts with login, per-user delivery email, and a shared system sender are the **v2 direction** (§4.8), started only after the MVP ships | Whole architecture, auth, data model, email provider |

## 4. Functional requirements

Priority: **M** = must (MVP), **S** = should (v1.1), **C** = could (later).

### 4.1 Source ingestion

> **Coverage model (user intent — authoritative).** The user never selects companies. The user selects **what** they want — role profiles and tech stack (e.g. backend, java) — and the system must return matching openings from **as many companies as possible**, not a fixed list. Companies are a *derived consequence* of those preferences, discovered automatically. Two mechanisms deliver this:
> - **Query-driven sources (aggregators like Adzuna/Remotive)** already return jobs from *any* company for a given query (e.g. "java developer"). No company list is involved.
> - **Company-board sources (Greenhouse/Lever/Ashby)** technically can only answer "jobs at company X"; they have no "all java jobs" endpoint. To honor the intent, the **discovery job (FR-1b)** derives the company roster automatically from the user's profiles/`company-types` and stores it in the DB, so the user still never types a company name.
>
> The slugs currently in `application.yml` (stripe, airbnb, …) are **temporary placeholders** to be removed in Phase 6.

| ID | Requirement | Pri |
|---|---|---|
| FR-1 | Fetch jobs from Greenhouse, Lever, and Ashby public board endpoints for a list of company slugs that is **resolved dynamically** (see FR-1a), not hand-maintained in config | M |
| FR-1a | **No hardcoded companies.** `application.yml` must not contain a static list of company slugs. Company slugs come from a `companies` table in the DB, populated by the discovery process in FR-1b. Config holds only *preferences* (role profiles, `company-types`, discovery toggles), never an explicit company roster | S |
| FR-1b | A **company-discovery** job derives candidate company slugs from your role profiles / `company-types` (e.g. by probing Greenhouse/Lever/Ashby endpoints for candidate names, and/or an optional seed-discovery API) and persists the ones that return a live board. Re-runs on a schedule and prunes slugs whose board has 404'd N times | S |
| FR-2 | Fetch jobs from at least one aggregator with India coverage (Adzuna) and one remote-focused source (Remotive or Arbeitnow) | M |
| FR-3 | Fetch from RSS feeds (e.g. We Work Remotely) | S |
| FR-4 | Add a new source by implementing one interface, with no changes to pipeline code | M |
| FR-5 | Each source runs independently. A failure is logged and reported but never aborts the run | M |
| FR-6 | Fetch sources concurrently with per-request timeout (10 s) and retry (max 2, exponential backoff) | M |
| FR-7 | Ingest LinkedIn/Naukri/Indeed alert emails from Gmail | C |

### 4.2 Normalization and extraction

| ID | Requirement | Pri |
|---|---|---|
| FR-8 | Convert every source's payload into one normalized `Job` record | M |
| FR-9 | Extract and store: title, company, location, remote flag, apply URL, posted date (if real), employment type (if given) | M |
| FR-10 | Extract experience range from the description (regex) | M |
| FR-11 | Extract salary only when given as structured data, or clearly stated in text | S |
| FR-12 | Extract tech stack tags by matching the description against a keyword list | M |
| FR-13 | Strip and sanitize HTML from descriptions | M |

### 4.3 Filtering and scoring

| ID | Requirement | Pri |
|---|---|---|
| FR-14 | Define multiple **role profiles** (e.g. backend-java, golang, frontend-react, fullstack), each with include keywords, exclude keywords, and optional stack keywords | M |
| FR-15 | Global filters: location/remote, minimum and maximum experience, company blocklist, title blocklist | M |
| FR-16 | A job may match several profiles. It appears once, under its highest-scoring profile | M |
| FR-17 | Rule-based match score used to sort within a group | S |
| FR-18 | Record why a job matched ("Matched on: java, spring in title") | M |

### 4.4 Deduplication and state

| ID | Requirement | Pri |
|---|---|---|
| FR-19 | A job is never notified twice (unique by `source + source_job_id`) | M |
| FR-20 | The same job across sources is detected via a normalized `company + title + location` key. The highest-priority source's link is kept | M |
| FR-21 | First run for a source seeds the DB without emailing everything (see 8.6) | M |
| FR-22 | A job that disappears and reappears is not re-notified | M |

### 4.5 Notification

| ID | Requirement | Pri |
|---|---|---|
| FR-23 | Send an HTML digest email with plain-text fallback, grouped by profile, newest first | M |
| FR-24 | Each job shows: title, company, "posted X ago" or "first seen X ago", location/remote, apply link, source, plus experience, salary, and stack when available | M |
| FR-25 | Never render empty fields (no "Salary: N/A") | M |
| FR-26 | Subject line includes job count, per-profile counts, and freshest age | M |
| FR-27 | Send nothing if there are zero new jobs | M |
| FR-28 | If a digest exceeds 50 jobs, show the top 50 by score and link the rest in a plain list | S |
| FR-29 | Optional Telegram instant alert for jobs above a score threshold | C |

### 4.6 Operations

| ID | Requirement | Pri |
|---|---|---|
| FR-30 | Log per-source stats each run (fetched, new, matched, errors) to the DB and log file | M |
| FR-31 | Ping a healthchecks.io URL after each successful run | M |
| FR-32 | If a source fails N consecutive runs, include a warning line in the next digest | S |
| FR-33 | All behavior configurable via `application.yml` and env vars. No code changes to edit profiles | M |

### 4.7 Analytics dashboard (v1.3, later phase)

A lightweight, single-user, local-only web UI that visualizes activity. It is **read-only over the pipeline data** plus a single manual action ("mark as applied"). It never changes filtering, sending, or source behavior.

| ID | Requirement | Pri |
|---|---|---|
| FR-34 | Serve a local dashboard (default `http://127.0.0.1:8080`, bound to loopback only) showing summary KPI cards: total jobs seen, total jobs emailed/notified, total marked applied, and applied-rate | C |
| FR-35 | Show "emails received till now" — i.e. count of jobs with `notified_at NOT NULL` — and a time-series chart (per day/week) of jobs emailed | C |
| FR-36 | Show "jobs applied to" — count and time-series — sourced from a new `applied_at` state on each job | C |
| FR-37 | Let the user **manually** mark a job as applied / un-applied from the dashboard (the only write action). This sets/clears `applied_at` | C |
| FR-38 | Break down KPIs by profile and by source (e.g. applied-rate per profile, jobs emailed per source) | C |
| FR-39 | List recent emailed jobs with their apply link, profile, score, and applied status; support basic filtering (by profile/source/applied) | C |
| FR-39a | **Active-profile selection (single active profile for email).** The user can define many profiles but selects **one** profile as *active* from the dashboard. Only jobs matching the active profile are included in the email digest. Switching the active profile changes which jobs are emailed going forward; it does not retroactively re-send past jobs. Exactly one profile is active at a time (default: the first/only profile). This is a per-profile `is_active` flag on the `profiles` table and a single write action from the UI | C |
| FR-39b | **All-jobs table with faceted filters.** The dashboard shows a table of **all jobs the user has received** (emailed, i.e. `notified_at NOT NULL`) with columns: title, company, location, remote, profile, source, posted/first-seen age, score, applied status, apply link. The table supports faceted filtering and per-facet counts, e.g. "Pune: 12", "Remote: 30", "Bengaluru: 8", by profile, by source, and by applied/not-applied. Filters are combinable (e.g. Remote + backend-java) and the counts update with the active filter set. Location facets are derived from the normalized `location` and `remote` fields | C |
| FR-40 | The dashboard is optional and off by default. Enabling it must not pull a servlet container into the headless MVP run; it ships as a separate run profile/module so the scheduled pipeline stays `web-application-type=none` | C |
| FR-41 | No authentication is required because it binds to loopback on the single-user host. It must never be exposed publicly without the user adding their own reverse proxy/auth | C |

### 4.8 Multi-user accounts and hosted mode (v2 direction, not in v1)

> **Status: vision / future.** This describes the intended evolution into a hosted, multi-user web application where users sign up with their own profile and interests, set their delivery email, and receive job alerts from a shared system sender. It is **deliberately out of scope for v1** and must not be started until the single-user MVP (Phases 0-5) is stable. It is recorded here so the architecture can leave room for it.

| ID | Requirement | Pri |
|---|---|---|
| FR-42 | Users **sign up and log in** to a web app. Each account stores its own profile(s)/interests, filters, and delivery preferences in the DB — **replacing** per-deployment `application.yml` for user-specific settings | C |
| FR-43 | The user's **delivery ("to") email** is taken from their account, verified via a confirmation link, not from config | C |
| FR-44 | A single **system sender ("from")** mailbox sends alerts to all users. Start with a Google/Workspace mailbox (app password or OAuth), with a clear migration path to a transactional provider (Resend / Amazon SES / Postmark) to survive volume and deliverability limits | C |
| FR-45 | The pipeline runs **per-user**: jobs are matched against each user's profiles, and each user receives only their own matches. Job data may be fetched once and fanned out, but notification state (`notified_at`) is tracked **per user** | C |
| FR-46 | **Instant notification option:** when a new matching job is detected, optionally email that user immediately (near-real-time) instead of waiting for the digest. Respect a per-user frequency/throttle setting (instant vs hourly/daily digest) | C |
| FR-47 | Per-user data isolation and basic security: password hashing or OAuth login, session management, no cross-user data leakage, and an unsubscribe/delete-account path | C |
| FR-48 | The multi-user dashboard (§4.7) becomes **per-account** and authenticated (each user sees only their own emailed/applied analytics) | C |

**Key open decisions for v2 (to resolve before starting it):**
- **Sender deliverability:** a single Gmail account caps around ~500 recipients/day and risks throttling/spam flags. Decide the email provider early; a transactional provider is strongly recommended beyond a handful of users.
- **"Instant" vs digest:** true real-time per-job email increases send volume and cost, and conflicts with the batched-digest design. Consider per-user throttling and a minimum-score threshold.
- **Data model:** introduce a `users` table and make `jobs.notified_at`/`applied_at` per-user (e.g. a `user_jobs` join table), since the same job can be relevant to many users.
- **Cost/hosting:** multi-user hosting is no longer "$0-6/month"; budget for a real web host, a database, and email sending.

| Area | Requirement |
|---|---|
| Performance | A full run over 300 company boards completes in under 3 minutes |
| Memory | Runs within `-Xmx256m` |
| Reliability | A run is idempotent. If it crashes mid-way, the next run does not lose or double-send jobs (mark `notified_at` only after a successful send) |
| Security | No secrets in the repo. SMTP password and API keys come from env vars |
| Politeness | Descriptive `User-Agent`, at most 4 concurrent requests per host, and respect `429`/`Retry-After` |
| Maintainability | Fixture-based tests for each source. Adding a source takes under 2 hours |
| Legal | Use only public, documented endpoints and APIs. Follow each API's terms (e.g. attribution for RemoteOK and Remotive) |

---

# PART B: TECHNICAL DESIGN

## 6. Architecture

```
            ┌─────────────┐
 scheduler ─►  RunOrchestrator ──► for each JobSource (parallel, virtual threads)
 (cron or                          │  fetch() → List<RawJob> → normalize() → List<Job>
  @Scheduled)                      ▼
                        ┌──────────────────────┐
                        │ Pipeline (per run)   │
                        │ 1 extract fields     │
                        │ 2 global filters     │
                        │ 3 profile match+score│
                        │ 4 dedupe vs DB       │
                        │ 5 persist            │
                        └──────────┬───────────┘
                                   ▼
                       DigestBuilder → EmailSender ─► mark notified_at
                                   ▼
                              Healthcheck ping
```

**Key design rule:** sources only fetch and normalize. All business logic (filtering, scoring, dedupe) lives in the pipeline, so it is tested once and shared by every source.

### 6.1 Package layout

```
jobwatch/
├── pom.xml
├── config/                       (optional external config overrides)
├── src/main/java/com/you/jobwatch/
│   ├── JobWatchApplication.java
│   ├── config/                   AppProperties (@ConfigurationProperties), HttpConfig
│   ├── model/                    Job (record), SourceType, RunStats
│   ├── source/                   JobSource (interface), GreenhouseSource, LeverSource,
│   │                             AshbySource, AdzunaSource, RemotiveSource, RssSource
│   ├── pipeline/                 RunOrchestrator, ExtractionService, FilterService,
│   │                             ProfileMatcher, ScoreService, DedupeService
│   ├── store/                    JobRepository, SourceRunRepository, schema.sql
│   ├── notify/                   DigestBuilder, EmailSender, TelegramSender (later)
│   └── util/                     TextUtils (HTML strip, normalize), TimeAgo
├── src/main/resources/
│   ├── application.yml
│   ├── schema.sql
│   └── templates/digest.html, digest.txt
└── src/test/
    ├── java/...
    └── resources/fixtures/       greenhouse.json, lever.json, ashby.json, adzuna.json ...
```

### 6.2 Core types

```java
public record Job(
    String source,            // "greenhouse", "lever", ...
    String sourceJobId,
    String company,
    String title,
    String location,          // raw string
    boolean remote,
    String applyUrl,
    Instant postedAt,         // null if source has no real date
    Instant firstSeenAt,      // set by pipeline
    String descriptionText,   // plain text, HTML stripped
    String employmentType,    // nullable
    String salaryText,        // nullable, structured or clearly stated only
    Integer expMin,           // nullable, extracted
    Integer expMax,           // nullable
    Set<String> stack         // extracted tags, may be empty
) {}

public interface JobSource {
    String name();
    int priority();                    // lower = more trusted, wins on dedupe
    List<Job> fetch() throws Exception;
}
```

### 6.3 Source specifications

> **Verify every endpoint and field name against a live response on day one and save it as a test fixture.** Public APIs evolve, and the shapes below are from documentation and memory, so treat them as a starting point.

| Source | Endpoint (per company slug unless noted) | Key fields | Posted date reliability | Priority |
|---|---|---|---|---|
| Greenhouse | `GET https://boards-api.greenhouse.io/v1/boards/{slug}/jobs?content=true` | `id`, `title`, `absolute_url`, `location.name`, `updated_at`, `content` (HTML-escaped) | **Low**: `updated_at` can reflect edits. Label as "updated" or use first-seen | 1 |
| Lever | `GET https://api.lever.co/v0/postings/{slug}?mode=json` | `id`, `text`, `hostedUrl`, `applyUrl`, `createdAt` (epoch ms), `categories.location`, `categories.commitment`, `descriptionPlain`, `workplaceType` | **High** | 1 |
| Ashby | `GET https://api.ashbyhq.com/posting-api/job-board/{slug}?includeCompensation=true` | `title`, `location`, `isRemote`, `publishedAt`, `jobUrl`, `applyUrl`, `descriptionPlain`, `compensation` | **High** | 1 |
| Adzuna | `GET https://api.adzuna.com/v1/api/jobs/{country}/search/{page}?app_id=..&app_key=..&what=..&sort_by=date&max_days_old=2` (free key) | `id`, `title`, `company.display_name`, `location.display_name`, `created`, `redirect_url`, `salary_min/max` (often *predicted*, flag it) | Medium | 3 |
| Remotive | `GET https://remotive.com/api/remote-jobs?category=software-dev` | `id`, `title`, `company_name`, `url`, `publication_date`, `candidate_required_location`, `salary` | Medium. **Attribution required** | 3 |
| Arbeitnow | `GET https://www.arbeitnow.com/api/job-board-api` | `slug`, `title`, `company_name`, `url`, `created_at`, `remote`, `location` | Medium. Europe-heavy | 3 |
| RemoteOK | `GET https://remoteok.com/api` | Check ToS: **link-back required**. Skip if unsure | Medium | 3 |
| RSS (WWR etc.) | Feed URL list in config | Rome `SyndEntry`: title, link, publishedDate | Medium | 4 |
| Jooble (optional) | POST API, free key | Verify current terms and India support | Medium | 3 |

Workable and other ATS boards can be added later using the same interface. Check their current public endpoints first.

**Company slug discovery:** for each target company, open its careers page. The URL usually reveals the ATS (`boards.greenhouse.io/{slug}`, `jobs.lever.co/{slug}`, `jobs.ashbyhq.com/{slug}`). Write a tiny one-off script or JUnit test that takes a list of company names, probes all three endpoints, and prints which one returns 200. This saves hours of manual checking.

### 6.4 Database schema (SQLite)

```sql
CREATE TABLE IF NOT EXISTS jobs (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  source          TEXT NOT NULL,
  source_job_id   TEXT NOT NULL,
  dedupe_key      TEXT NOT NULL,
  company         TEXT NOT NULL,
  title           TEXT NOT NULL,
  location        TEXT,
  remote          INTEGER NOT NULL DEFAULT 0,
  apply_url       TEXT NOT NULL,
  posted_at       TEXT,               -- ISO-8601, nullable
  first_seen_at   TEXT NOT NULL,
  salary_text     TEXT,
  exp_min         INTEGER,
  exp_max         INTEGER,
  stack           TEXT,               -- comma-separated tags
  profile         TEXT,               -- winning profile
  score           INTEGER,
  matched_on      TEXT,
  matched         INTEGER NOT NULL DEFAULT 0,  -- passed filters
  notified_at     TEXT,               -- null until digest sent
  applied_at      TEXT,               -- null until user marks applied in dashboard (v1.3, FR-37)
  UNIQUE (source, source_job_id)
);
CREATE INDEX IF NOT EXISTS idx_jobs_dedupe ON jobs(dedupe_key);
CREATE INDEX IF NOT EXISTS idx_jobs_pending ON jobs(matched, notified_at);
-- v1.3: dynamically discovered company roster (replaces hardcoded slugs in config, FR-1a/FR-1b)
CREATE TABLE IF NOT EXISTS companies (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  source          TEXT NOT NULL,        -- greenhouse | lever | ashby
  slug            TEXT NOT NULL,
  display_name    TEXT,
  discovered_at   TEXT NOT NULL,
  last_ok_at      TEXT,                 -- last time the board returned 200
  consecutive_404 INTEGER NOT NULL DEFAULT 0,
  enabled         INTEGER NOT NULL DEFAULT 1,
  UNIQUE (source, slug)
);

CREATE TABLE IF NOT EXISTS source_runs (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  source        TEXT NOT NULL,
  started_at    TEXT NOT NULL,
  duration_ms   INTEGER,
  status        TEXT NOT NULL,        -- OK | FAILED
  fetched       INTEGER,
  new_jobs      INTEGER,
  error         TEXT
);
```

Store non-matching jobs too (`matched = 0`). It costs almost nothing, prevents re-processing, and lets you later query "why didn't I see X?" while tuning filters.

Use WAL mode (`PRAGMA journal_mode=WAL`) and a single connection or small pool. Only one process writes.

### 6.5 Configuration schema (`application.yml`)

```yaml
jobwatch:
  email:
    from: you@gmail.com
    to: you@gmail.com
    # password from env: SPRING_MAIL_PASSWORD
  healthcheck-url: ${HEALTHCHECK_URL:}
  digest:
    max-jobs: 50
  filters:
    locations: ["india", "bengaluru", "pune", "hyderabad", "remote"]
    allow-remote-worldwide: true
    exp-min: 0
    exp-max: 6
    blocked-companies: ["SomeStaffingFirm"]
    blocked-title-words: ["intern", "trainee", "manager", "director", "sales"]
  profiles:
    - name: backend-java
      include-title: ["java", "spring", "backend", "back-end", "back end"]
      exclude-title: ["javascript"]
      stack: ["java", "spring boot", "kafka", "microservices", "aws", "postgresql"]
    - name: golang
      include-title: ["golang", "go developer", "go engineer", "go backend"]
      stack: ["go", "golang", "grpc", "kubernetes"]
    - name: frontend-react
      include-title: ["react", "frontend", "front-end", "front end"]
      stack: ["react", "typescript", "redux", "next.js"]
    - name: fullstack
      include-title: ["full stack", "fullstack", "full-stack"]
      stack: ["react", "java", "node", "typescript"]
  sources:
    greenhouse:
      enabled: true
      # v1.3: NO hardcoded `companies` list here. Slugs live in the DB `companies`
      # table and are populated by the discovery job (FR-1b). Until discovery is
      # implemented, slugs may be seeded once via a one-off import, not committed here.
    lever:
      enabled: true
    ashby:
      enabled: true
    adzuna:
      enabled: true
      country: in
      queries: ["java developer", "golang developer", "react developer"]
      # keys from env: ADZUNA_APP_ID, ADZUNA_APP_KEY
    remotive:
      enabled: true
    rss:
      enabled: false
      feeds: []
  # v1.3: company discovery (FR-1b) — replaces hardcoded slugs
  discovery:
    enabled: false            # turn on in Phase 6; derives slugs from profiles/company-types
    probe-ats: [greenhouse, lever, ashby]
    prune-after-404: 3
  # v1.3: analytics dashboard (§4.7) — optional, local-only, off by default
  dashboard:
    enabled: false
    bind-address: 127.0.0.1
    port: 8080
```

Bind with `@ConfigurationProperties` and validate on startup (`@Validated`) so a typo fails fast instead of silently matching nothing. Use Java records for the properties classes (constructor binding works out of the box).

## 6.6 Spring Boot 4.1.1 specifics

Boot 4 is a platform upgrade, not a version bump: it moved to Spring Framework 7, split the codebase into small modules with focused starters, and prefers Jackson 3. Do **not** copy Boot 3-era tutorials or blog snippets blindly. Section 6.6 lists what changes for this project.

> **Verify against your generated project.** I checked the Boot 4.0 migration guide and the 4.1 release notes for the items below. Exact artifact names should be confirmed by generating the project on start.spring.io (choose Boot 4.1.1) rather than typing them from this document.

### Baseline

| Item | Guidance |
|---|---|
| Java | Java 17 is the minimum, and Java 21 (our target) works well, including virtual threads. Check the Boot 4.1 docs for the newest supported JDK if you want to go higher |
| Parent POM | `spring-boot-starter-parent` version `4.1.1`. Check for a newer 4.1.x patch when you start |
| Build | Maven or Gradle. The `spring-boot-maven-plugin` still builds the fat jar. (`layertools` jar mode was removed in 4.1, which doesn't affect us) |
| Virtual threads | `spring.threads.virtual.enabled=true`, plus our own `Executors.newVirtualThreadPerTaskExecutor()` for fetching |

### Dependencies (authoritative Boot 4.1.1 dependency list)

Generate from start.spring.io with **Spring Boot 4.1.1**, Java 21, and Maven. Select Mail, Thymeleaf, JDBC, Validation, and Test support as appropriate, then add the non-Spring dependencies manually:

| Purpose | Dependency | Boot 4 note |
|---|---|---|
| Core | `spring-boot-starter` | No web starter. We run as a CLI/scheduled app |
| Config validation | `spring-boot-starter-validation` | Validation is its **own** starter. `@Validated` on properties does nothing without it, so add it explicitly |
| Email | `spring-boot-starter-mail` | Configure with `spring.mail.*` |
| Templates | `spring-boot-starter-thymeleaf` | After adding, run `mvn dependency:tree` and confirm it did not pull in a web server. If it did, keep `spring.main.web-application-type=none` |
| DB | `spring-boot-starter-jdbc` + `org.xerial:sqlite-jdbc` | Set Hikari `maximum-pool-size=1` for SQLite. Schema via `spring.sql.init.*` or your own startup bean |
| HTML parsing | `org.jsoup:jsoup` | Not Spring-managed. Pin a current version |
| RSS | `com.rometools:rome` | Not Spring-managed. Pin a current version |
| Fuzzy match | `org.apache.commons:commons-text` | Boot's BOM may manage it, so check before pinning |
| Tests | `spring-boot-starter-test` | General test support. Boot 4.1.1 also provides focused `-test` starters such as `spring-boot-starter-jdbc-test` and `spring-boot-starter-thymeleaf-test`; add these only when the corresponding Spring integration tests require them |

**Dependency policy:** Treat Spring Boot 4.1.1 as the source of truth for managed dependency versions. Do not pin versions for Spring Boot starters or other dependencies managed by the Boot 4.1.1 BOM unless there is a documented reason. Verify the resolved dependency tree with `mvn dependency:tree` after bootstrap. The official Boot 4.1.1 dependency-management coordinates are authoritative.

Do **not** add: `spring-boot-starter-web`/`spring-boot-starter-webmvc`, actuator, JPA/Hibernate, or the old external Spring Retry library. If an HTTP client is needed, use JDK `HttpClient` or explicitly add Boot 4.1.1 `spring-boot-starter-restclient` as described below.

If something "used to just be there" and now is not, that is modularization. As a quick diagnostic (not a permanent fix) Boot offers `spring-boot-starter-classic` and `spring-boot-starter-test-classic` to restore the broad classpath. Use them to unblock yourself, then remove them.

### Jackson 3 (the biggest coding difference)

- Jackson databind now lives in package **`tools.jackson.databind`** (Maven group `tools.jackson.core`). Old `com.fasterxml.jackson.databind.*` imports from Boot 3 tutorials will not compile.
- Jackson **annotations** (`@JsonProperty`, `@JsonIgnoreProperties`) intentionally stay in `com.fasterxml.jackson.annotation`.
- The main class is `JsonMapper` (immutable, built with a builder), and Jackson 3 exceptions are unchecked (`JacksonException`), so you no longer need `throws` clauses.
- Jackson 3 ships **different defaults** from Jackson 2. Don't assume old behavior. Write explicit tests on the parsing.
- Boot config properties for it are under `spring.jackson.*` (Boot 4.1 also adds `spring.jackson.read.*` and `spring.jackson.write.*`).
- Fallback if you hit friction: Boot 4 still ships a Jackson 2 compatibility module (`spring-boot-jackson2`, configured under `spring.jackson2.*`), but it is deprecated, so I'd start on Jackson 3 directly. The project is new, and there is nothing to migrate.

**Design decision for this project:** parse external job payloads into a **`JsonNode` tree** and read fields with null-safe helpers (`node.path("title").asString("")`), instead of mapping onto rigid DTO classes. Third-party APIs add, rename, and drop fields, and tree parsing makes each source tolerant of that and needs less code. Check the current `JsonNode` accessor names in the Jackson 3 Javadoc, since some accessors changed from `asText()`-style names.

### HTTP client

Choose one:

1. **JDK `java.net.http.HttpClient` (my recommendation).** No Spring dependency, virtual-thread friendly, and you fully control timeouts and retries.
2. **Spring `RestClient`.** In Boot 4 its auto-configuration lives in a dedicated starter, `spring-boot-starter-restclient`. If you pick this, add it explicitly, or `RestClient.Builder` will not be injectable.

Retries: write a ~30-line helper (2 retries, 500 ms then 2 s backoff, honor `Retry-After`). Spring Framework 7 also includes built-in retry support (`RetryTemplate` moved into core) and you can use that instead, but the helper is simpler to reason about and test. Do not add the old Spring Retry library.

### Testing

- `@MockBean` and `@SpyBean` were removed. Use Spring Framework's `@MockitoBean` and `@MockitoSpyBean`, or better, avoid Spring for most tests: extraction, filtering, scoring, dedupe, and source parsing are plain Java classes and should be plain JUnit 5.
- Test annotations and auto-configure packages were reorganized per module. If you use `@JdbcTest`-style slices, use the matching test starter and check the import.
- For source tests, use saved JSON fixtures. For HTTP-level tests, WireMock or the JDK's `com.sun.net.httpserver.HttpServer` both work without Spring.
- For email tests, verify your chosen mail test library's compatibility with Boot 4 / Jakarta Mail versions before committing to it, or simply mock `JavaMailSender`.

### Other Boot 4 changes to know

- **Null-safety:** Spring Framework 7 adopts JSpecify annotations. Optional for us.
- **Removed APIs:** anything deprecated in Boot 3.x/4.0 is gone. If a tutorial uses a deprecated method, look for its replacement in the migration guide.
- **Binding:** in value-object binding, `Optional` parameters now bind to `Optional.empty()` rather than `null` (4.1). Using records with plain nullable fields sidesteps this.
- **Tooling:** if you ever port Boot 3 code, OpenRewrite has a recipe for Boot 4 upgrades. It is not needed for a greenfield project.

### Suggested bootstrap (do this first, 20 minutes)

1. Generate the project at start.spring.io: Maven, Java 21, **Spring Boot 4.1.1**, with Mail, Thymeleaf, JDBC, Validation, and Test support. Do not select any Spring Boot 3.x starter or copy a Boot 3.x dependency list.
2. Add `sqlite-jdbc`, `jsoup`, `rome`, `commons-text`.
3. Set `spring.main.web-application-type=none`.
4. Run `mvn dependency:tree | grep -i -E "tomcat|jetty|netty"`. It should print nothing.
5. Run the app and confirm it starts and exits cleanly.

## 7. Detailed logic specifications

### 7.1 Normalization rules

- `company`: trim, collapse whitespace. Company name for ATS sources is the config slug's display name, since the API usually omits it. Keep a `slug → display name` map in config.
- `title`: trim only. Do not alter case in storage (lowercase only in matching).
- `descriptionText`: HTML-unescape, strip tags with Jsoup (`Jsoup.parse(html).text()`), collapse whitespace. Greenhouse's `content` is HTML-escaped, so unescape before parsing.
- `remote`: true if the source flag says so, or location/description matches `remote|work from home|wfh|anywhere`.
- `applyUrl`: prefer the direct ATS `applyUrl`/`absolute_url`. Strip tracking params (`utm_*`).
- `postedAt`: set only when the source gives a trustworthy date. Otherwise null, and the email uses `firstSeenAt`.

### 7.2 Extraction

**Experience.** Run these patterns against title + description, in order, and take the first hit. Keep the regexes in one class with unit tests.

```
(\d{1,2})\s*(?:-|–|to)\s*(\d{1,2})\s*\+?\s*(?:years|yrs)     → range
(\d{1,2})\s*\+\s*(?:years|yrs)                                 → min only
(?:minimum|at least)\s*(\d{1,2})\s*(?:years|yrs)             → min only
```

If nothing matches, leave null. Do **not** guess. Jobs with null experience pass the experience filter (do not exclude by default) but never show an experience line.

**Salary.** Use only structured fields (Ashby compensation, Adzuna min/max, Remotive salary). For Adzuna, tag as "estimated". Skip regex salary extraction in v1 because currency and period parsing is error-prone.

**Stack tags.** Lowercase description, match each configured keyword with word boundaries (`\bkafka\b`). Handle special tokens: `c++`, `c#`, `.net`, `node.js`, `go` (match `\bgolang\b`, and `\bgo\b` only if the profile is golang and the title or description also mentions `golang|goroutine|gRPC`, since "go" is a common English word). Cap the display at 6 tags.

### 7.3 Filtering order

Cheap checks first. Log the rejection reason for each dropped job at DEBUG level.

1. Company on blocklist → drop
2. Title contains blocked word → drop
3. Location check: pass if `remote && allowRemoteWorldwide`, or location text contains any configured location token
4. Experience: if `expMin != null && expMin > cfg.expMax` → drop. If `expMax != null && expMax < cfg.expMin` → drop
5. Profile match (7.4). No profile matched → drop

### 7.4 Profile matching and scoring

For each profile, compute:

| Signal | Points |
|---|---|
| Include keyword in **title** | +50 (first hit), +10 for each additional |
| Any exclude keyword in title | Profile disqualified |
| Each stack keyword found in description | +5 (max 30) |
| Location is exact city match | +10 |
| Remote | +5 |
| Experience within your range (known) | +10 |
| Posted or first seen within 3 hours | +10 |

A job's profile is the highest-scoring qualified one. `matched_on` records the title keywords and top stack hits. A profile qualifies only if it has a title-keyword hit, or at least 3 stack hits (this catches "Software Engineer II" postings whose descriptions clearly point to Java).

Tune weights over the first two weeks using the `matched=0` rows plus your judgment. Keep the weights in config, not code.

### 7.5 Deduplication

Two layers:

1. **Exact:** unique index on `(source, source_job_id)`. Insert with `INSERT OR IGNORE`.
2. **Cross-source:** compute `dedupe_key = normalize(company) + "|" + normalize(title) + "|" + normalize(city)`.
   - `normalize(company)`: lowercase, strip suffixes (`inc`, `llc`, `ltd`, `pvt`, `private limited`, `technologies`, `corp`), remove punctuation.
   - `normalize(title)`: lowercase, remove level words in parentheses, collapse whitespace.
   - `normalize(city)`: first token before a comma, lowercased. `remote` if remote.
   - If the key already exists in DB from a *different* source, treat as a duplicate. If the new one has a *better* (lower) priority and the old one is not yet notified, replace the URL. Otherwise drop the new one.
   - As a fallback for near-misses, if the company matches and title Jaro-Winkler similarity ≥ 0.93, treat as duplicate.

Known limitation: companies posting the same role in many cities generate legitimately separate jobs. The city component of the key keeps these separate.

### 7.6 First-run seeding (critical)

On the first successful fetch of a source (no rows in `jobs` for that `source`):
- Insert everything with `notified_at = now` so it is never emailed, **except** jobs whose real `postedAt` is within the last 48 hours, which are emailed.
- Without this rule, your first email contains hundreds of stale postings.

Track "source seeded" as `EXISTS (SELECT 1 FROM source_runs WHERE source = ? AND status = 'OK')`.

### 7.7 Digest composition

- Query: `matched = 1 AND notified_at IS NULL`, ordered by profile, score desc, then recency.
- **Freshness window (no stale jobs):** only jobs whose effective posted time (`COALESCE(posted_at, fetched_at)`) is within the last **3 days** (`JobRepository.FRESH_DAYS`) are eligible for the digest. Stale postings are never emailed and are hidden from the dashboard; they are simply filtered out (left un-notified), not deleted.
- **Active-profile gate (v1.3, FR-39a):** when an active profile is set, restrict the query to `profile = <active profile>` so the digest only emails jobs for the user's currently selected profile. Non-active-profile jobs are still stored and scored (so they appear in the all-jobs table and can be surfaced if the user switches active profile) but are **not** emailed while another profile is active. If no active profile is set, fall back to emailing all matched profiles (current MVP behavior).
- Send. **Only after the SMTP call succeeds**, update `notified_at` for exactly those job IDs, in one transaction. If the send fails, nothing is marked, and the next run retries.
- Age label: `postedAt` exists → "Posted 3h ago". Else → "First seen 3h ago". Under 60 min → "12 min ago". Over 48 h → days.
- Subject: `7 new jobs: 3 Backend Java, 2 Golang, 2 React (freshest 12 min ago)`.
- Footer: source-health line, and attribution lines where required (Remotive, RemoteOK).

**Email layout per job:**

```
Senior Backend Engineer, Acme Corp                 ⏱ Posted 12 min ago
Bengaluru (Hybrid) · 5+ yrs · ₹30-45L (est.)
Stack: Java, Spring Boot, Kafka, AWS
Matched on: java, spring (title)
[Apply →]  via Greenhouse
```

Rules: omit any line with no data, keep the HTML to inline CSS and single-column (mobile-first), and always include the plain-text alternative.

### 7.8 Error handling

| Failure | Behavior |
|---|---|
| HTTP timeout / 5xx | Retry twice (500 ms, 2 s). Then mark the source FAILED for this run |
| HTTP 404 on a company slug | Log a warning, skip that company, and flag it in the health line after 3 consecutive 404s (the company probably changed ATS) |
| HTTP 429 | Honor `Retry-After`, then reduce concurrency |
| Malformed JSON / unexpected shape | Skip the record, count it, log it. One bad job never kills the source |
| SMTP failure | Do not mark notified. Retry next run. Log at ERROR |
| DB failure | Fail the run loudly. Do **not** ping healthcheck |

Ping the healthcheck **only** when the run reaches the end without a fatal error, even if some sources failed. Send `/fail` on fatal errors.

### 7.9 Concurrency

Use `Executors.newVirtualThreadPerTaskExecutor()`. Run sources in parallel, and companies within a source in parallel, guarded by a per-host `Semaphore(4)`. All DB writes happen on the main thread after fetching completes, which avoids SQLite write contention.

### 7.10 Company discovery (v1.3, FR-1a/FR-1b) — replaces hardcoded slugs

Goal: never hand-maintain a company list in config. The roster lives in the `companies` table and is kept current by a discovery job.

- **Inputs:** your role profiles and their `company-types` (e.g. product, fintech, saas), plus an optional candidate-name seed list (from a one-off import, a public company dataset, or an aggregator's company facets). No slugs are stored in `application.yml`.
- **Probe:** for each candidate name, derive a slug (lowercase, strip spaces/punctuation, common variants) and probe `greenhouse`, `lever`, and `ashby` board endpoints. Any that return `200` with ≥1 posting are inserted into `companies` with `discovered_at` and `last_ok_at`.
- **Refresh:** re-run on a schedule (e.g. weekly). On a live board, update `last_ok_at` and reset `consecutive_404`. On `404`, increment `consecutive_404`; when it reaches `discovery.prune-after-404`, set `enabled = 0` (the company likely changed ATS).
- **Fetch loop reads from the DB:** each source's fetcher iterates `SELECT slug FROM companies WHERE source=? AND enabled=1`, not a config list.
- **Politeness:** discovery obeys the same per-host semaphore, timeouts, and `429` handling as normal fetching (§7.8, §7.9).

Until Phase 6 implements discovery, the MVP may run with an empty roster or a one-off seeded import; slugs are never committed to the repo.

### 7.11 Dashboard data model and behavior (v1.3, §4.7)

All dashboard numbers are plain queries over existing tables; no new analytics pipeline is needed.

| KPI / view | Query basis |
|---|---|
| Jobs seen | `COUNT(*) FROM jobs` |
| Emails received (jobs emailed) | `COUNT(*) WHERE notified_at IS NOT NULL` |
| Jobs applied | `COUNT(*) WHERE applied_at IS NOT NULL` |
| Applied rate | applied / emailed |
| Emailed over time | `GROUP BY date(notified_at)` |
| Applied over time | `GROUP BY date(applied_at)` |
| Per-profile / per-source breakdown | add `GROUP BY profile` / `GROUP BY source` |

- The only write path is FR-37: `UPDATE jobs SET applied_at = ? WHERE id = ?` (or set to `NULL` to un-apply).
- **Active-profile selection (FR-39a):** a `profiles.is_active` flag holds exactly one active profile. Selecting one from the UI runs, in a single transaction, `UPDATE profiles SET is_active = 0; UPDATE profiles SET is_active = 1 WHERE id = ?`. The digest query (§7.7) reads the active profile to decide which jobs to email. Default on first run: the first/only profile is active.
- **All-jobs faceted table (FR-39b):** the base query is `SELECT ... FROM jobs WHERE notified_at IS NOT NULL` **and the 3-day freshness window** (`COALESCE(posted_at, fetched_at) >= now - 3 days`), so stale jobs drop off the dashboard automatically. Facet counts come from grouped queries over the same filtered set:

| Facet | Query basis |
|---|---|
| By location (e.g. Pune, Bengaluru) | `GROUP BY location` (normalized) over emailed jobs |
| Remote vs on-site | `GROUP BY remote` |
| By profile | `GROUP BY profile` |
| By source | `GROUP BY source` |
| Applied vs not | `GROUP BY (applied_at IS NOT NULL)` |

  Filters are combinable: applying "Remote" + "backend-java" adds both predicates to the `WHERE` clause, and the per-facet counts are recomputed within the currently active filter set so each count reflects what the user would see if they also selected that facet.
- The dashboard runs as a **separate web run profile/module** so the scheduled headless pipeline keeps `spring.main.web-application-type=none`. Enabling it adds `spring-boot-starter-webmvc` only in that profile/module.
- Charts can be rendered server-side (Thymeleaf + a tiny JS chart lib) or as a small JSON endpoint consumed by the page. Keep it single-page and dependency-light.

---

# PART C: IMPLEMENTATION PLAN

**Total estimate: 20-32 hours** (MVP through deployment). Phases 0-5 are the MVP. Estimates assume you are comfortable with Spring Boot.

## Phase 0: Setup and spikes (2-3 h)

**Goal:** prove the data sources really work before writing structure around them.

- [ ] Create the repo with **Spring Boot 4.1.1**, Java 21, Maven, using `spring-boot-starter-parent` 4.1.1. Add these Boot 4 starters: `spring-boot-starter`, `spring-boot-starter-mail`, `spring-boot-starter-thymeleaf`, `spring-boot-starter-jdbc`, `spring-boot-starter-validation`, and `spring-boot-starter-test`. Add non-Spring dependencies `org.xerial:sqlite-jdbc`, `org.jsoup:jsoup`, `com.rometools:rome`, and `org.apache.commons:commons-text`; use WireMock only if needed for HTTP integration tests.
- [ ] Disable the web server (`spring.main.web-application-type=none`)
- [ ] `curl` each API in 6.3 for 1-2 real companies. Save responses to `src/test/resources/fixtures/`
- [ ] Register for the Adzuna API key. Create a Gmail app password (requires 2-step verification)
- [ ] Create a healthchecks.io check (free) and note its ping URL
- [ ] Decide Q1-Q2 defaults and write your real profiles into `application.yml`

**Acceptance:** you have real fixture files for at least Greenhouse, Lever, Ashby, and Adzuna, and a test email sent from a scratch `JavaMailSender` call arrives in your inbox.

## Phase 1: Model, config, and storage (2-3 h)

- [ ] `Job` record and `JobSource` interface
- [ ] `AppProperties` with `@ConfigurationProperties` + `@Validated`
- [ ] `schema.sql`, executed at startup. Enable WAL
- [ ] `JobRepository`: `insertIfNew`, `findByDedupeKey`, `findPendingNotifications`, `markNotified(ids)`, `sourceHasRuns(source)`
- [ ] `SourceRunRepository.record(...)`
- [ ] Unit tests with an in-memory or temp-file SQLite

**Acceptance:** tests prove that inserting the same `(source, sourceJobId)` twice yields one row, and `markNotified` only touches the given IDs.

## Phase 2: First two sources, Greenhouse and Lever (3-4 h)

- [ ] Shared `HttpClient` config: timeouts, `User-Agent`, retry helper, per-host semaphore
- [ ] `GreenhouseSource` → Jackson mapping → `Job`
- [ ] `LeverSource` (convert epoch ms → `Instant`)
- [ ] `TextUtils.htmlToText`, `TextUtils.normalizeCompany/Title/City`
- [ ] Fixture-based tests: parse saved JSON, assert fields. Test a malformed record is skipped
- [ ] A throwaway `CommandLineRunner` that fetches and prints jobs

**Acceptance:** running the app prints normalized jobs from 5+ real companies, with correct company, title, URL, and remote flag.

## Phase 3: Extraction and filtering (3-4 h)

- [ ] `ExtractionService`: experience regexes, stack tags, salary passthrough. 15+ unit tests with real-looking description snippets, including the failure cases ("no years mentioned", "10+ years")
- [ ] `FilterService`: blocklists, location, experience
- [ ] `ProfileMatcher` + `ScoreService` with weights from config
- [ ] Print matched jobs with `matched_on` and score. Read them by eye

**Acceptance:** on your saved fixtures, "Java Developer" matches `backend-java`, "JavaScript Engineer" does not, "Intern" is dropped, and "Golang Backend Engineer" lands in `golang`. Each has a test.

## Phase 4: Pipeline, dedupe, and first-run seeding (3-4 h)

- [ ] `DedupeService` with the key and similarity fallback
- [ ] `RunOrchestrator`: parallel fetch, per-source try/catch, collect results, pipeline, persist, record `source_runs`
- [ ] First-run seeding rule (7.6)
- [ ] Integration test: run the orchestrator twice against fixtures via WireMock or stub sources. The second run must produce zero new jobs

**Acceptance:** run 1 seeds, run 2 finds nothing, and adding a new job to a stub source makes exactly that one show up as pending. A duplicate posted through a second stub source with a different ID is dropped.

## Phase 5: Digest email and end-to-end (3-4 h)

- [ ] `DigestBuilder` (Thymeleaf HTML + text templates), `TimeAgo` helper
- [ ] `EmailSender` (`JavaMailSender`, multipart)
- [ ] Wire: pending jobs → digest → send → `markNotified` in a transaction after success
- [ ] Subject-line generator, empty-digest suppression, 50-job cap
- [ ] `HealthcheckPinger`
- [ ] Look at the email on your phone and in Gmail web. Fix layout

**Acceptance:** a real end-to-end run against 5-10 real company boards sends you one digest with correct fields. Forcing an SMTP failure (wrong password) leaves jobs unnotified, and fixing it delivers them on the next run. **This is your MVP milestone.**

## Phase 6: More sources (3-5 h)

- [ ] `AshbySource` (with compensation)
- [ ] `AdzunaSource` (one request per configured query, `max_days_old=2`, dedupe by ID). Mark salary as estimated
- [ ] `RemotiveSource` (with attribution footer)
- [ ] `RssSource` via Rome (optional)
- [x] **Company discovery (FR-1b, §7.10) — DONE:** `companies` table + `CompanyDiscoveryService` probes Greenhouse/Lever/Ashby from a bundled seed + Adzuna-harvested names, persists live slugs (`CompanyRepository`), and `JobSourceProvider` builds ATS sources per-run from the DB roster. Hardcoded `companies:` lists removed from `application.yml`. First live bootstrap discovered 30 boards; coverage grew from ~1.7k to ~6.8k jobs/run.
- [ ] Grow the discovered company roster to 100+ (expand seed / Adzuna harvest over time)

**Acceptance:** each new source has a fixture test, and failing one source (unplug it, break its URL) does not affect the others or the email. `application.yml` contains **no** company slugs, yet the ATS sources still fetch from a DB-backed roster populated by discovery. ✅ met

## Phase 7: Deployment and reliability (2-3 h)

- [ ] Choose the mode: **(a)** one-shot jar via cron (`*/30 * * * *`, lower memory) or **(b)** long-lived service with `@Scheduled(fixedDelay=...)` under systemd. Default to (a) on small VPSes
- [ ] Build a fat jar (`spring-boot-maven-plugin`), and run with `-Xmx256m`
- [ ] Put secrets in an env file (`chmod 600`), never in the repo
- [ ] Ensure the SQLite file is on persistent disk. Add a nightly `cp` backup
- [ ] Log to a rolling file, with `logrotate` or Logback rolling policy
- [ ] Healthchecks.io schedule with a grace period of about 2x your interval
- [ ] Optional Dockerfile

**Acceptance:** the app runs unattended for 48 hours. Stopping the cron job triggers a healthcheck alert email. A reboot resumes without duplicates.

## Phase 8: Tuning and extras (ongoing)

Do these only after living with the MVP for 1-2 weeks.

- [ ] Review `matched=0` rows and the junk you received. Adjust profile keywords, blocklists, and weights
- [ ] Source-health warning line in the digest (FR-32)
- [ ] Telegram instant alerts for score ≥ threshold (Bot API `sendMessage`, about 1 hour of work)
- [ ] Gmail alert ingestion for LinkedIn/Naukri/Indeed emails (Gmail API + OAuth, about 6-10 hours, fragile. Do this only if you still miss jobs)
- [ ] Optional small-LLM extraction for messy descriptions (cents/month)
- [ ] Application tracking, only if you want it, via a spreadsheet first

## Phase 9: Analytics dashboard (v1.3, 3-5 h) — optional, do last

Do this only after the MVP is stable. It must not touch the headless pipeline's behavior.

- [x] Add `applied_at` to the `jobs` schema (done via `SchemaMigrator`; also `profile`, `score`, `stack`)
- [x] Create the dashboard as a **separate web run profile** (`dashboard`) that enables `spring-boot-starter-webmvc`; the scheduled pipeline stays `web-application-type=none`
- [x] Bind to `127.0.0.1` only; gate behind `jobwatch.dashboard.enabled`
- [x] Read-only KPI/query layer per §7.11 (jobs seen, emailed, applied, applied-rate; per-profile/per-source/per-company; day/week/month + time-series)
- [x] Single page: KPI cards + CSS-bar charts + recent-emailed-jobs table
- [x] One write action: mark applied / un-applied (FR-37)
- [x] **Active-profile selection (FR-39a):** added `profiles.is_active` (schema + `SchemaMigrator`), a "set active" control on the home/profiles page with an ACTIVE badge, `ProfileRepository.setActive/activeProfileName/ensureOneActive`, and the digest gate (`JobRepository.findPendingNotifications(limit, activeProfile)` wired into `DigestNotificationService`) so only the active profile's jobs are emailed.
- [x] **All-jobs faceted table (FR-39b):** new `/jobs` page — a table of all emailed jobs with combinable sidebar facets (location e.g. Pune, remote/on-site, profile, source, applied) and live per-facet counts, plus removable filter chips. Backed by `AnalyticsRepository.filteredEmailed/facet/remoteFacet`.
- [x] **Dashboard IA restructured:** home (`/`) = My Profiles, `/analytics` = KPIs/charts, `/jobs` = faceted job table, with a shared top nav.
- [x] **Editable DB-backed profiles (Phase 10 increment):** `profiles` table + `ProfileRepository` + `ProfileProvider` (pipeline reads profiles from DB per-run), `ProfileSeeder` (YAML→DB on first run), per-profile experience/company constraints, and a full profile CRUD UI (`/profiles`). Launch with `run-local.ps1 -Dashboard`.

**Acceptance:** with the pipeline running headless, launching the dashboard profile shows correct counts for jobs emailed and applied, charts render, marking a job "applied" updates the applied count and persists, and the dashboard is unreachable from outside the host. ✅ met (dashboard live at http://127.0.0.1:8080, 201 tests green)

## Phase 10: Multi-user hosted mode (v2, large — separate project milestone)

> Do **not** start until the single-user MVP is in daily use. This is a product pivot, not a phase add-on; expect it to roughly double the project. Scope it and re-estimate before starting. See §4.8 (FR-42–FR-48) for requirements and open decisions.

High-level work (to be detailed when we commit to v2):
- [ ] `users` table, sign-up/login (password hash or OAuth), sessions, email verification
- [ ] Move per-user settings (profiles, filters, delivery email) from `application.yml` into the DB, editable via the web UI
- [ ] Make notification state per-user (`user_jobs` join table for `notified_at`/`applied_at`)
- [ ] Per-user matching + fan-out in the pipeline
- [ ] Shared system sender with a transactional email provider (Resend/SES/Postmark); keep Gmail only for tiny scale
- [ ] Optional instant (near-real-time) alerts with per-user throttle and score threshold
- [ ] Per-account authenticated dashboard (§4.7 becomes per-user)
- [ ] Hosting, backups, and a cost model that is no longer "$0-6/month"

**Acceptance:** two separate users can sign up with different interests, each verifies their own delivery email, and each receives only their own matching jobs from the shared sender, with no cross-user data leakage.

---

# PART D: QUALITY, RISKS, AND CHECKLISTS

## 8. Test strategy

| Level | What | How |
|---|---|---|
| Unit | Regex extraction, normalization, scoring, dedupe key, time-ago | JUnit 5, table-driven cases. **Add a case for every wrong result you notice in real emails** |
| Source | Each fetcher maps fixture JSON correctly, tolerates missing fields | Saved JSON fixtures |
| Integration | Two-run idempotency, first-run seeding, SMTP failure retry | Temp SQLite, stub sources, GreenMail or a mocked mail sender |
| Manual | Real email on phone, real end-to-end run | Phase 5 and 7 acceptance |

Refresh fixtures every couple of months. A source format change shows up as a failing test instead of a silent gap.

## 9. Risk register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| ATS endpoint or field changes | Medium | Source silently returns nothing | Fixture tests, per-source `fetched = 0 for N runs` warning |
| Company changes ATS and its slug 404s | High over time | Missed jobs | 404 counter and health line |
| Filters too loose (noise) or too tight (missed jobs) | High at first | Erodes trust | Store `matched=0`, tune weekly, blocklists |
| Aggregator free tier limits change | Medium | Source stops | Check limits in Phase 0, cache, keep queries few |
| Posted date wrong or missing | High | Misleading "freshness" | "First seen" fallback, label clearly |
| Gmail blocks or throttles sending | Low | No emails | Digest volume is tiny. Monitor with healthcheck. Fall back to Resend or SES free tier |
| VPS or free VM goes down or is reclaimed | Medium | Silent outage | Healthchecks.io, DB backups |
| Scope creep (scrapers, UI, LLM) | High | Project never finishes | Non-goals list (Section 2). Ship the MVP first |
| Company discovery yields too few/wrong slugs | Medium | Thin job coverage | Seed list + weekly re-probe, keep manual one-off import as fallback (§7.10) |
| Dashboard exposed beyond localhost | Low | Data leak on shared host | Loopback-only bind, off by default, no public exposure without user-added auth (FR-40/FR-41) |

## 10. Definition of done (MVP)

- [ ] Runs unattended on the chosen host for 48 h with no manual intervention
- [ ] At least 3 source types and 50+ companies producing jobs
- [ ] Digest email contains correct link, role, company, and freshness for every job, with extra fields only when known
- [ ] No job is emailed twice, including across a restart
- [ ] The first run did not spam you
- [ ] One source failing is visible but harmless
- [ ] A dead pipeline alerts you within about 2 hours
- [ ] Secrets are out of the repo. README explains setup, config, and how to add a source

## 11. Cost summary

| Item | Monthly |
|---|---|
| Hosting (Oracle free VM, or home box) | $0 |
| Hosting (small VPS, recommended for reliability) | ~$4-6 |
| Gmail SMTP, Adzuna free tier, Remotive, ATS endpoints, healthchecks.io free | $0 |
| **Total** | **$0-6** |

## 12. Suggested schedule

| Pace | Timeline |
|---|---|
| 2 h/day | About 2 weeks to MVP, 3 weeks including Phase 6 and 7 |
| Weekend sprints (8 h) | MVP in 2 weekends, full v1 by the third |

## 13. Working agreement

- Build **vertically**: get one source to an email first (Phases 1-5), then widen. Do not build all fetchers up front.
- Commit fixtures alongside code. Every bug you spot in a real email becomes a test before it becomes a fix.
- Review your first week of emails critically, and change config before changing code.
