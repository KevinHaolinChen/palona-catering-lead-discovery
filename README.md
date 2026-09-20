# VerityScout — Evidence-backed B2B Prospect Intelligence

VerityScout is a Java/Spring Boot backend for discovering and prioritizing B2B prospects **without hiding why a lead was recommended**.

It began as a Palona engineering take-home for IHOP Redwood City catering leads. v0.2 productizes the reusable idea underneath that assignment: campaign-based prospect discovery with source provenance, explainable scoring, persistent run history, bounded enrichment, and grounded outreach.

## Product thesis

Most prospecting tools optimize for breadth: more contacts, more enrichment, more sequences.

VerityScout focuses on an adjacent problem:

> **Can a seller audit exactly why this account is worth contacting, which source supports each claim, how fresh/conflicted that evidence is, and how that evidence affected the score?**

That makes the core object an evidence-backed recommendation rather than a row in a contact database.

## Architecture

```text
Campaign
  -> Discovery Run
  -> Prospect Sources
  -> Canonicalization / dedupe
  -> Persisted candidates
  -> Enrichment
  -> Claim-level evidence
  -> Explainable scoring
  -> Grounded outreach
  -> Outcome feedback (next)
```

Current source adapter:
- OpenStreetMap / Overpass for high-recall geographic discovery

Current enrichment:
- bounded direct public-page retrieval
- public-network-only SSRF checks
- redirect revalidation
- content-type and body-size limits

Current ranking:
- real geographic distance for proximity
- category-based cold-start priors for scale/event/need signals
- public website/phone availability for contactability
- curated demo prospects preserve evidence-conditioned human review

## Run it

### Docker + PostgreSQL

```bash
docker compose up --build
```

Open http://localhost:8000.

### Local Java

Java 21 and Maven 3.6.3+:

```bash
mvn spring-boot:run
```

Local mode uses an in-memory H2 database in PostgreSQL compatibility mode so a reviewer does not need infrastructure.

Run tests:

```bash
mvn test
```

## Campaign API

Create a campaign:

```http
POST /api/campaigns
Content-Type: application/json
```

```json
{
  "name": "Peninsula Catering",
  "business_type": "restaurant_catering",
  "latitude": 37.4914,
  "longitude": -122.2280,
  "radius_meters": 8000
}
```

Start a durable discovery run:

```http
POST /api/campaigns/{campaignId}/runs
```

The API responds immediately with a run in `QUEUED` state. Processing continues asynchronously through:

```text
QUEUED -> DISCOVERING -> COMPLETED | FAILED
```

Inspect status and persisted results:

```http
GET /api/runs/{runId}
GET /api/runs/{runId}/prospects
```

## Legacy / curated evidence demo API

- `GET /api/prospects`
- `GET /api/prospects/{id}`
- `POST /api/discover`
- `POST /api/prospects/{id}/outreach?useLlm=true`
- `GET /health`

The original 12 hand-reviewed catering prospects remain as seed/demo material because they demonstrate a higher-confidence evidence model than raw discovery alone.

## Why the scoring changed

The take-home's original cold-start score was category-driven. That meant the “proximity” component did not actually depend on distance.

v0.2 fixes this:

- proximity is calculated from actual distance
- contactability is derived from public contact paths
- the remaining cold-start dimensions use category priors until evidence extraction is automated

This makes the score more honest and gives the next iteration a clear path: replace priors with extracted, source-backed evidence.

## Persistence

Flyway manages:
- `campaigns`
- `discovery_runs`
- `discovered_prospects`

Docker uses PostgreSQL 17. Local/test mode uses H2 PostgreSQL compatibility mode for zero-config reviewability.

## Source abstraction

`ProspectSource` is the extension point for discovery providers. OpenStreetMap is only the first adapter.

Potential future adapters:
- uploaded CSV / CRM account lists
- business directories
- customer-provided first-party sources
- licensed commercial enrichment providers

## Safety / data-quality principles

- discovery candidates are not automatically treated as verified facts
- factual claims should carry source-level provenance
- no invented employees or contact details
- LLM-generated outreach is grounded only in stored evidence
- private/local network targets are rejected by the enrichment layer
- redirect targets are revalidated
- external content is bounded by type and size
- cached evidence remains inspectable when external providers fail

## Next product milestones

1. Structured evidence extraction from public pages.
2. Source confidence + evidence freshness TTLs.
3. Conflict quarantine and evidence diffing.
4. Evidence-conditioned scoring rather than category priors.
5. Salesperson outcome feedback (`BAD_LEAD`, `REPLIED`, `MEETING`, `WON`).
6. CRM sync / CSV import.
7. Multi-tenant organizations and authentication.
8. Natural-language ICP -> structured campaign criteria.
9. Per-source rate limits, retry/backoff, and observability.
10. Measure whether evidence-backed ranking improves reply/meeting rates.

## Positioning

VerityScout is **not trying to be another giant contact database**.

Its wedge is:

> Explainable, source-auditable prospect intelligence for teams that care whether an AI recommendation can be verified.

That makes it useful both as a production-style backend portfolio project and as a business experiment that can be tested with a small number of real customers.
