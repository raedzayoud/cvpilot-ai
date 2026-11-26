# CVPilot AI

Tailor an existing CV to a specific job without inventing anything. Upload a CV (PDF, DOCX or LaTeX),
add a job posting, get an explainable match score, chat with an AI coach about the gaps, approve each
rewritten section, and export DOCX, PDF or LaTeX.

```
cvpilot-ai/          Angular 21 frontend (standalone components, signals, SSR shell)
backend/backend/     Spring Boot 4.2 / Java 25 REST API
docker-compose.yml   PostgreSQL (+ optional full stack)
```

## Quick start (local development)

Prerequisites: Java 25, Maven 3.9+, Node 22+, Docker (for PostgreSQL).

```bash
# 1. Database (host port 5433, so it does not clash with a local PostgreSQL on 5432)
docker compose up -d postgres

# 2. Backend: create backend/backend/.env from .env.example and set GROQ_API_KEY
cd backend/backend
cp .env.example .env         # then edit: GROQ_API_KEY, DB_PORT=5433, DB_USERNAME/PASSWORD=cvpilot
mvn spring-boot:run          # http://localhost:8080  ·  Swagger UI: /swagger-ui.html

# 3. Frontend
cd cvpilot-ai
npm install
npm start                    # http://localhost:4200
```

Flyway creates the schema on first start (`src/main/resources/db/migration`). There is no seed
data: every number on the dashboard comes from the database.

Full stack in Docker: `docker compose --profile full up -d --build` (frontend on :4000, API on :8080).

## AI CV Agent

Conversational CV editing at `/agent`. You give instructions in plain language ("Tailor my CV for this job",
"Rewrite my summary to emphasize backend", "Add a project…"); the agent plans the edits, calls tools, and
every edit becomes a **proposal** you accept, edit, regenerate or reject.

```
message ─▶ IntentClassifier ─▶ AgentOrchestrator ──(≤4 Groq tool-calling steps)──▶ AgentToolbox
                                   │  fast path: unambiguous requests (skills removal, global
                                   │  technology swap) run without a model call
                                   │  planning step: pasted job → analyze_job_description        │
                                   │                                                             ├─▶ ClaimValidator (blocks unbacked claims)
                                   │                                                             └─▶ ProposedChange (before/after, reason,
                                   ▼                                                                   job requirements, keywords, evidence)
                          AgentSessionService ── accept/reject/edit/regenerate ─▶ ChangeApplier ─▶ working copy
                                   └── "Save to CV" is the only operation that changes the CV itself
```

Backend: `service/agent/*`, `integration/ai/AiToolRequest|AiToolTurn` (provider-neutral tool calling),
`controller/AgentController` (`/api/agent/...`), tables `agent_session`, `agent_message`, `agent_proposal`
(migration `V2__ai_cv_agent.sql`).

**Factual accuracy is enforced in code, not only in the prompt** (`ClaimValidator`):
- a technology may appear only if it is in the CV or the user stated it about themselves; `record_user_fact`
  only accepts an exact, first-person quote from the user's own message, so a pasted job description never
  counts as evidence;
- every number (metrics, years, team sizes) must appear in the CV or the user's messages;
- company, role, dates and location of an experience cannot change unless the user stated the new value;
- new experiences/projects must be grounded in what the user told the agent;
- applying a proposal whose target changed meanwhile is refused (marked "Outdated").

Rejected attempts are shown to the user ("Not added: unverified claim") and the model is told to retry
without them or ask. Agent endpoints are rate limited like the other AI endpoints.

**Groq free tier:** a key allows roughly 8,000 tokens/minute and 30 requests/minute. One agent step costs
~3.3–3.4k prompt tokens, so about two or three turns per minute fit before Groq answers `429 Limit 8000`
(the client honors `Retry-After` up to 30s). The default `openai/gpt-oss-20b` is small enough for this
budget; extra keys on the same organization do not add capacity. A paid tier removes the waits.

## CV export (LaTeX template)

Every download uses one LaTeX résumé template (`backend/backend/src/main/resources/latex/cv-preamble.tex`):
centered header with linked profiles, ruled section titles, "Role — Company" with grey location/dates on
the right, bullets and a bold Technologies line.

- **PDF**: the template compiled with [Tectonic](https://tectonic-typesetting.github.io/). Locally it is in
  `backend/backend/tools/tectonic/` (gitignored, set via `LATEX_COMPILER`); the Docker image installs it.
  The first compile downloads TeX packages (needs internet once). Without a compiler, a built-in PDF layout is used.
- **LaTeX (.tex)**: the template source, ready for Overleaf or any XeLaTeX/LuaLaTeX.
- **Word (.docx)**: same structure, editable.

## Tests

```bash
cd backend/backend && mvn test        # 38 tests: scoring, tailoring, agent loop + tools, claims, export
cd cvpilot-ai && npx ng test --watch=false   # 18 specs
```

On Windows, run the frontend tests from a path with an uppercase drive letter (`C:\...`); vitest
fails to start from `c:\...`.

## Architecture

```
Angular ──HTTP/JSON──▶ Controller ─▶ Service ─▶ Repository ─▶ PostgreSQL
                                        │
                                        ├─▶ matching/  (deterministic scoring + tailoring, no AI)
                                        └─▶ ai/ ─▶ AiChatClient (interface) ─▶ GroqChatClient ─▶ Groq API
```

Backend packages (`com.backend.backend`): `config`, `controller`, `service` (+ `ai`, `matching`,
`export`), `repository`, `entity`, `dto`, `model`, `mapper`, `exception`, `integration` (`ai`, `groq`),
`validation`, `util`.

**Where AI is used, and where it is not.**

| Feature | How |
|---|---|
| CV upload → structured profile | Groq (JSON mode). Fallback without AI: contact details + known skills, user completes the rest |
| Job posting → positions & requirements | Groq (JSON mode). Fallback: title + technology keywords |
| Coach chat | Groq, with persisted history (last 20 messages sent as context) |
| Agent edit planning | Groq tool calling: ≤4 steps, 6 history messages, 2000 output tokens |
| Skills removal, global technology swap | **Deterministic fast path** — no model call when the phrasing is unambiguous |
| Match score, plan, section text, claim checking, staleness | **Deterministic Java code** — same input, same output, every point explained |

In the coach, the model never writes into the CV directly. It returns a reply plus at most one structured
action (`decline_skill`, `add_transferable`); the backend validates that action against the CV before
applying it, and every section is re-checked for unsupported claims before it can be approved or exported.

To add another provider, implement `integration/ai/AiChatClient` and make it the active bean;
nothing in `service/` changes.

## Project analysis

**Product loop.** Upload a CV (PDF, DOCX or LaTeX) → the backend extracts an editable profile → add a
posting, pasted or uploaded, split into up to 15 positions → read a deterministic 0–100 match, the section
plan and the coach's gap analysis → approve sections and export DOCX, PDF or LaTeX. The AI CV agent is a
second editing path: a conversation on a working copy, where every model edit becomes a proposal. One
analysis is one CV × one target position. The dashboard, history and activity feed are queries over the
same rows; there is no seed or demo data.

**Frontend** (`cvpilot-ai`, Angular 21, standalone components, signals, SSR shell):

| Route | Purpose |
|---|---|
| `/` | stats, recent scores, recent jobs, activity |
| `/cvs`, `/cvs/:id` | upload/list; edit the extracted profile against a live preview (leave guard) |
| `/jobs` | pick a CV, paste or upload a posting, start an analysis |
| `/analyses`, `/analyses/:id/review` | history; section review, decisions, export |
| `/agent`, `/agent/:id` | start a session; chat, proposals, working-copy preview, save/export |

### Agent internals

- **Loop:** `IntentClassifier` (deterministic, English/French) → `AgentOrchestrator` → `AgentToolbox` →
  `ClaimValidator` → proposals. Bounded to 4 model steps per turn, 6 history messages and 2000 output
  tokens. A posting pasted in the first message triggers `analyze_job_description` before planning.
- **Tools (14):** `analyze_job_description`, `record_user_fact`, `update_headline`, `rewrite_summary`,
  `set_skills`, `update_experience`, `add_experience`, `remove_experience`, `update_project`,
  `add_project`, `remove_project`, `reorder_sections`, `replace_technology`, `remove_section`.
  Partial edits are allowed: `update_experience` without bullets and `update_project` without a
  description keep the existing content instead of clearing it.
- **Deterministic fast paths:** requests that need no judgement skip the model. "Remove the technologies"
  clears the skills list; "replace all the technologies in my CV from Spring Boot to .NET" swaps the
  technology across skills, experience bullets, experience technology lines and project descriptions in
  one operation. Scoped phrasing ("in my Novadev experience") goes to the model. This removes cost and
  latency, and keeps the common edits out of the Groq TPM ceiling.
- **Proposals, not writes:** each edit is a `ProposedChange` with before/after, reason and evidence.
  Accepting applies it to the session's working copy; "Save to CV" is the only write to the CV itself.
  Only PENDING proposals are superseded, so an accepted-but-unsaved change survives later turns. A
  proposal whose target changed meanwhile is refused as outdated; `replace_technology` keeps no snapshot
  and applies to the latest working copy.

### Scoring and tailoring

`ScoringEngine` is pure Java: technical 35, experience 25, projects 20, education 10, other 10 = 100.
Each requirement is evidenced as matched, partial, missing or unverified; the result also carries
strengths, weaknesses, improvements, skill recommendations and a fixed disclaimer. `CustomizationEngine`
classifies every skill, experience and project as highlight, keep, deprioritize or remove, and refuses to
generate text the CV cannot support. Same CV and posting → same score, same plan.

### Deliberate trade-offs

- **No authentication, by design for a local tool.** CORS, signature-checked uploads, Bean Validation,
  per-client limits and a daily AI budget exist, but anyone who can reach the API can read every CV. Run
  it on localhost, a private network/VPN, or behind an authenticating proxy.
- **Rate limits and the daily budget are in-memory**, per instance, and reset on restart. Move them to
  Redis or the gateway before running more than one instance.
- **Fast paths are phrase-based** (English/French patterns). They cover the common unambiguous requests
  and fall back to the model otherwise; they are not a general natural-language parser.
- **Groq capacity is the binding constraint** on the agent: ~8k tokens/minute per organization. The 20b
  model, the 4-step ceiling and the fast paths keep a typical turn inside it.
- **Tests are unit and Spring MVC level** with a stub AI client (38 backend tests, 18 frontend specs).
  There is no browser E2E and no live model call in CI.
- **Extension points:** another AI provider = implement `integration/ai/AiChatClient`; another
  deterministic intent = add it to `AgentOrchestrator` with a test in `CvAgentTest`; another export
  format = `service/export`.

## Database

| Table | Purpose |
|---|---|
| `cv` | file metadata, language, extracted profile (JSONB) |
| `cv_file` | original upload byte-for-byte + extracted text (separate so CV reads never load the file) |
| `job_posting` | raw text, parsed positions (JSONB), FK → cv |
| `analysis` | one CV × one job: plan + sections (JSONB), chat facts, score, export state, optimistic-lock `version` |
| `chat_message` | conversation history, ordered by identity id |
| `activity` | dashboard feed |
| `agent_session` | working copy: base + working profile (JSONB), section order, confirmed facts, job target, `saved_at`, optimistic-lock `version` |
| `agent_message` | agent conversation turns, ordered |
| `agent_proposal` | one proposed edit: operation, before/after, reason, evidence, status (pending/accepted/rejected/superseded/stale) |

All child rows use `ON DELETE CASCADE`: deleting a CV removes its jobs, analyses, chats and agent sessions.
Deleting a session removes its messages and proposals; the CV itself is untouched.

## REST API (summary)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/dashboard` | stats, recent jobs, recent scores, activity |
| GET | `/api/system/status` | AI configured? model, remaining daily budget |
| GET | `/api/cvs?search=&page=&size=&sort=` | paginated |
| POST | `/api/cvs` (multipart `file`) · `/api/cvs/text` | AI extraction · rate limited |
| GET/DELETE | `/api/cvs/{id}` · PUT `/api/cvs/{id}/profile` · GET `/api/cvs/{id}/original` | |
| POST | `/api/jobs` · `/api/jobs/upload?cvId=` (multipart) | AI parsing · rate limited |
| GET | `/api/jobs/{id}` · `/api/jobs?cvId=` | |
| GET/POST | `/api/analyses?search=&status=all\|draft\|exported&sort=` | |
| GET/DELETE | `/api/analyses/{id}` · POST `/{id}/regenerate` | |
| GET/POST | `/api/analyses/{id}/chat` | POST is rate limited |
| POST | `/{id}/sections/{key}` · `/{id}/decisions/{decisionId}` · `/{id}/export` | |
| GET/POST | `/api/agent/sessions?cvId=&page=&size=` | list · start a session on a working copy (201) |
| GET/DELETE | `/api/agent/sessions/{id}` | full workspace · delete the session (the CV is untouched) |
| POST | `/api/agent/sessions/{id}/messages` | run the agent · rate limited |
| POST | `/api/agent/sessions/{id}/accept-all` · `/reset` · `/save` · `/export` | working copy: accept all · discard accepted changes · write to the CV · download PDF/LaTeX/Word |
| POST | `/api/agent/proposals/{id}/accept` · `/reject` · `/regenerate` | accept (optional user edit) · reject · re-run the agent |

Errors always have the shape `{ status, error, message, path, timestamp, fieldErrors? }`.

## Configuration

All settings are environment variables (or `backend/backend/.env`, which is gitignored):

| Variable | Default | |
|---|---|---|
| `GROQ_API_KEY` | — | required for AI features; never sent to the browser or logged |
| `GROQ_MODEL` | `openai/gpt-oss-20b` | |
| `GROQ_REASONING_EFFORT` | `low` | passed to the model; low keeps tokens inside the free TPM |
| `GROQ_READ_TIMEOUT` | `60s` | |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | `localhost` / `5432` / `cvpilot` / `cvpilot` / — | compose maps Postgres to host `5433`, so set `DB_PORT=5433` locally |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | comma-separated; required in `prod` |
| `AI_DAILY_REQUEST_LIMIT` | `500` | server-wide AI calls per UTC day |
| `AI_RATE_LIMIT_REQUESTS` / `AI_RATE_LIMIT_WINDOW` | `20` / `10m` | per client on AI endpoints |
| `LATEX_COMPILER` | `tectonic` | path to the Tectonic executable |
| `SPRING_PROFILES_ACTIVE` | `dev` | `prod` disables Swagger unless `SWAGGER_ENABLED=true` |

Frontend API URL: `cvpilot-ai/src/environments/environment*.ts` (Docker: build arg `API_BASE_URL`).

## Security & privacy — read before deploying

The API has **no authentication by design** (no Spring Security, no tokens). What is in place:
CORS restricted to configured origins, upload type checks by content signature, 10 MB uploads,
1 MB JSON bodies, Bean Validation on every input, per-client rate limiting and a daily AI budget,
whitelisted sort fields, and sanitized error messages.

What that means in practice:

- **CVs are personal data** (name, email, phone, work history). Anyone who can reach the API can list,
  download and delete every CV. CORS does not stop non-browser clients. Run it on `localhost`, a
  private network/VPN, or behind a reverse proxy that adds authentication (e.g. basic auth or SSO).
  Do not expose port 8080 to the internet as-is.
- **CV text is sent to Groq** for extraction and chat. Tell users, and check Groq's data-retention terms
  against your obligations (e.g. GDPR).
- Rate limits are in-memory, per instance; behind multiple instances move them to Redis or the gateway.
- Logs record AI call metadata (purpose, latency, token counts) — never prompts, CV content or the key.
