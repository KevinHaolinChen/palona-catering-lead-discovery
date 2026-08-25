# Palona Engineering Take-Home — Catering Lead Discovery (Java / Spring Boot)

A small evidence-first application that helps **IHOP Redwood City (491 Veterans Blvd, Redwood City, CA 94063)** discover and prioritize nearby organizations that may purchase catering.

This version is implemented in **Java 21 + Spring Boot 4.1 + Maven**. The product behavior is intentionally the same as the original prototype: a reviewer can inspect ranked leads, sourced facts, explicit inferences, public contact paths, uncertainty, and grounded outreach.

## Why this architecture

The application is best understood as an evidence pipeline:

```text
candidate discovery
    -> direct-source verification / enrichment
    -> claim-level evidence
    -> explainable ranking
    -> public contact path
    -> grounded outreach
```

Discovery is allowed to be noisy. Claims shown to a salesperson should be traceable and uncertainty-aware.

## Run it

### Option A — Docker (one command)

```bash
docker compose up --build
```

Open `http://localhost:8000`.

### Option B — local Java

Requirements: Java 21 and Maven 3.6.3+.

```bash
mvn spring-boot:run
```

Open `http://localhost:8000`.

Run tests with:

```bash
mvn test
```

## Main API

- `GET /api/prospects` — returns the saved, reviewable prospect set.
- `GET /api/prospects/{id}` — returns one prospect with evidence, score, contact path, outreach, and uncertainties.
- `POST /api/discover` — queries OpenStreetMap/Overpass for nearby candidate organizations and applies the deterministic cold-start score.
- `POST /api/prospects/{id}/outreach?useLlm=true` — optionally regenerates a grounded outreach draft when `OPENAI_API_KEY` is set.
- `GET /health` — confirms the app is alive and cached prospect data loaded successfully.

Example discovery request:

```json
{
  "latitude": 37.4914,
  "longitude": -122.2280,
  "radius_meters": 8000
}
```

## Java repository tour

```text
src/main/java/com/palona/cateringleads/
  CateringLeadApplication.java
  controller/
    ProspectController.java      HTTP API / orchestration boundary
  model/
    Prospect.java                lead aggregate
    Evidence.java                claim-level provenance
    ContactPath.java             public contact route
    ScoreBreakdown.java          explainable score dimensions
    Discovery*.java              live discovery DTOs
  service/
    ProspectService.java         cached dataset load + fail-fast invariants
    DiscoveryService.java        OpenStreetMap / Overpass candidate discovery
    EnrichmentService.java       bounded direct-public-page fetcher
    ScoringService.java          deterministic cold-start ranking
    OutreachService.java         cached + optional grounded LLM generation

src/main/resources/
  data/example_prospects.json    12 hand-reviewed prospects
  data/rejected_candidates.json stale/conflicting-source example
  static/                        lightweight dashboard
```

## Key design decisions

### 1. Claims, not organizations, own provenance

Each `Evidence` item is typed as `fact`, `inference`, or `ai_generated`. A fact can point to the exact public source that supports it instead of giving one vague URL for the whole organization.

### 2. Fail fast on cached-data invariants

At startup, `ProspectService` validates the saved dataset with Bean Validation and business rules:

- score components stay inside their ranges
- total score equals the breakdown sum
- every sourced fact has a source URL

This converts silent demo-data corruption into a startup failure that is easy to diagnose.

### 3. Explainable ranking over an opaque model

Scores have five dimensions totaling 100 points:

| Dimension | Max | Meaning |
|---|---:|---|
| Proximity | 25 | Delivery convenience / local relevance |
| Organization scale | 20 | Potential for recurring group demand |
| Event & meeting signal | 25 | Evidence of gatherings |
| Food-need signal | 20 | Likelihood group meals are operationally useful |
| Contactability | 10 | Whether a reasonable public route exists |

The score is a prioritization heuristic, **not** a conversion probability.

### 4. Live discovery and verified enrichment are deliberately separate

`POST /api/discover` demonstrates reusable geographic candidate generation. It does **not** claim that OpenStreetMap records are verified sales leads. The 12 cached prospects demonstrate the higher-confidence, hand-reviewed output with direct-source evidence.

With more time, the next major feature would be an orchestration layer that automatically sends discovered candidates through source enrichment, structured extraction, conflict resolution, and evidence-conditioned re-ranking.

### 5. Cached output makes the demo resilient

The reviewer can inspect the full workflow even if Overpass, a company webpage, or the optional LLM is unavailable during review.

### 6. Contact paths over fabricated people

When a reliable named individual is unavailable, the system recommends a public role and channel such as workplace operations, events, facilities, an office phone, or a general email. It never invents an employee.

## OpenAI integration

The app works without OpenAI. If `OPENAI_API_KEY` is present, `OutreachService` calls the Responses API and gives the model only the prospect's stored facts, explicit inferences, and target role. The prompt explicitly forbids inventing names, employee counts, events, budgets, or needs.

## Data-quality example: DPR

`rejected_candidates.json` preserves a concrete stale-data case. An older DPR source suggested Redwood City, but newer official information identified Santa Clara as the current Silicon Valley location. The candidate is rejected rather than quietly kept as a lead.

This is useful in the interview because it demonstrates that source existence is not enough: freshness and conflict handling matter too.

## Testing philosophy

The tests focus on business invariants rather than frontend cosmetics:

- at least 10 cached prospects exist
- score totals match their breakdowns
- every fact has provenance
- contact paths do not depend on invented people
- scoring stays bounded
- event-oriented organizations outrank generic cold-start candidates

## Tradeoffs

Optimized for:

- traceability over scraping breadth
- graceful uncertainty over confident fabrication
- deterministic, explainable scoring over opaque ML
- one-command reviewability over production infrastructure
- direct public sources over noisy aggregators

Intentionally not built:

- CRM integration
- email sending
- authentication
- production-scale crawling
- complex cloud infrastructure
- a polished design system

## With more time

1. Wire discovery -> enrichment -> structured extraction into one automated orchestration pipeline.
2. Add domain/address canonicalization and fuzzy deduplication.
3. Add source-specific adapters and explicit source confidence.
4. Schedule source revalidation with TTLs and conflict quarantine.
5. Derive ranking dimensions from extracted evidence rather than category defaults.
6. Add salesperson feedback to tune ranking weights.
7. Add an evidence-diff view for changed or stale claims.
8. Add request-level rate limiting, retry/backoff, and stronger SSRF controls for enrichment.
