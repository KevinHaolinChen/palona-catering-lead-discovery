# Gather — Local B2B Sales System for Restaurants

Gather is a Java/Spring Boot application that helps restaurants continuously discover, prioritize, contact, follow up with, and learn from nearby B2B prospects for catering, group orders, team meals, meetings, and local events.

The project started as a single-location catering discovery prototype. Gather v0.9 shifts the product from a one-time lead finder into a persistent sales workspace: Radius discovery, saved prospect stages, follow-up scheduling, recurring Radius Watch refreshes, outcome-aware ranking, evidence enrichment, grounded generative analysis, and outreach assistance.

## Product thesis

Restaurants often have valuable organizations nearby — offices, campuses, hospitals, schools, community spaces, and event venues — but turning a neighborhood into a useful outbound list is manual.

Gather asks:

> **Who should this restaurant pursue next, what should happen today, and what has actually worked for this restaurant before?**

The core object is no longer a disposable lead list. Each restaurant campaign is a persistent local sales territory whose prospects retain pipeline state, follow-ups, evidence, and outcomes across Radius refreshes.

## Current user flow

```text
Restaurant/franchise search
  -> persistent restaurant sales workspace
  -> inferred + optional restaurant profile
  -> choose Radius
  -> nearby organization discovery
  -> evidence-aware ranking
  -> Radius map
  -> pipeline stage + notes + next follow-up
  -> outreach
  -> replies / wins / losses
  -> outcome learning
  -> manual refresh or recurring Radius Watch
  -> new prospects inherit the restaurant's learned preferences
```

The frontend at `http://localhost:8000` now drives this flow directly.

## What makes it restaurant-agnostic

There is no hard-coded restaurant identity in the primary workflow.

Each campaign stores:
- restaurant name
- inferred restaurant type
- normalized origin address
- latitude / longitude
- search radius
- catering/group-order capability
- primary daypart
- price position
- practical delivery radius

That lets the same application run for a cafe in San Francisco, a pizza shop in Oakland, a breakfast restaurant in San Jose, or a catering-focused operation in another market.

The first pass still uses a cold-start catering/group-order heuristic, but v0.9 layers restaurant-profile fit, public website evidence, and historical outcome learning onto future refreshes. The numeric ranking remains deterministic; the LLM is used for grounded explanation and outreach rather than inventing the score.

## Architecture

```text
Restaurant Campaign
  -> Discovery Run
  -> Prospect Sources
  -> Canonicalization / dedupe
  -> Persisted candidates
  -> Restaurant-fit assessment
  -> Public-site evidence extraction
  -> Evidence-aware reranking
  -> Radius map
  -> Grounded AI explanation / outreach
  -> Persistent sales pipeline
  -> Follow-up queue
  -> Outcome feedback
  -> Learned category adjustment
  -> Radius Watch refresh loop
```

### Discovery
- Photon / OpenStreetMap for fast nearby organization discovery
- Overpass remains a secondary source when additional coverage is needed
- actual restaurant and prospect coordinates drive proximity scoring and the Radius map
- public website/phone availability drives contactability

### Restaurant profile + Evidence Engine
Gather infers a default profile from the selected restaurant and keeps the profile controls collapsed by default. Users can refine:
- catering / group-order capability
- primary daypart
- price position
- practical delivery radius

After discovery, Gather automatically evidence-enriches the strongest prospects. It scans reachable public website text for meeting/event, group-food, and organization-scale signals. The adjusted score uses the original cold-start score plus explicit **Profile Fit** and **Evidence** components. Evidence snippets retain their public source URL.

### Sales Loop + Radius Watch
Each discovered prospect can move through:

`NEW -> REVIEWED -> CONTACTED -> FOLLOW_UP -> REPLIED -> WON | LOST`

Gather stores:
- pipeline stage
- next follow-up date
- salesperson note
- first seen / last seen timestamps
- last contacted timestamp
- learned ranking adjustment

A restaurant workspace can be reopened after a browser refresh and manually refreshed without losing pipeline history. **Radius Watch** can also rerun enabled campaigns on a recurring interval (daily through every 30 days). New discovery runs inherit the latest pipeline state for already-known accounts.

The **Today** dashboard shows follow-ups due, genuinely new prospects discovered this week, contacted accounts, replies, and wins.

### Address lookup
The prototype uses the public OpenStreetMap Nominatim service for user-triggered address lookup only. Results are cached in-process, requests are serialized/rate-limited, the application sends an identifying User-Agent, and the provider URL is configurable with `GEOCODING_BASE_URL`.

For production/commercial scale, switch to a commercial geocoding provider or a self-hosted instance rather than depending on the public service.

### Persistence
Flyway manages:
- `campaigns`
- `discovery_runs`
- `discovered_prospects`

Docker uses PostgreSQL 17. Local/test mode uses H2 in PostgreSQL compatibility mode.

## Enable generative AI

Gather keeps ranking deterministic and explainable, then uses generative AI for prospect-specific analysis and outreach drafts.

Set an OpenAI API key before starting the app:

```bash
OPENAI_API_KEY=your_key_here
```

Optional model override:

```bash
OPENAI_MODEL=gpt-5.6-luna
```

If `OPENAI_API_KEY` is missing or an AI request fails, Gather labels the result as a **heuristic fallback** rather than presenting template output as generated AI.

## Run locally

Requires Java 21 and Maven.

```bash
mvn test
mvn spring-boot:run
```

Open:

```text
http://localhost:8000
```

Docker + PostgreSQL is also supported:

```bash
docker compose up --build
```

## API

### Geocode a restaurant address

```http
GET /api/geocode?address=123%20Main%20St%2C%20San%20Francisco%2C%20CA
```

### Create a restaurant campaign

```http
POST /api/campaigns
Content-Type: application/json
```

```json
{
  "name": "Harbor Pizza",
  "business_type": "pizza",
  "address": "123 Main St, San Francisco, CA",
  "latitude": 37.7749,
  "longitude": -122.4194,
  "radius_meters": 8047
}
```

### Start discovery

```http
POST /api/campaigns/{campaignId}/runs
```

Run states:

```text
QUEUED -> DISCOVERING -> COMPLETED | FAILED
```

Inspect results:

```http
GET /api/runs/{runId}
GET /api/runs/{runId}/prospects
GET /api/campaigns/{campaignId}/prospects
GET /api/campaigns/{campaignId}/dashboard
```

Update a prospect's sales state:

```http
PATCH /api/discovered-prospects/{prospectId}/pipeline
Content-Type: application/json
```

```json
{
  "stage": "FOLLOW_UP",
  "next_follow_up_at": "2026-09-24T19:00:00Z",
  "note": "Sent catering menu; follow up Wednesday"
}
```

Enable recurring Radius Watch:

```http
PATCH /api/campaigns/{campaignId}/monitoring
Content-Type: application/json
```

```json
{
  "enabled": true,
  "interval_days": 7
}
```

## Legacy evidence demo

The original hand-reviewed Redwood City prospect dataset remains available through `GET /api/prospects` as offline evidence-model seed material. It is **not** the default frontend or the product's restaurant identity.

The legacy direct discovery endpoint `POST /api/discover` now requires explicit latitude, longitude, and radius; it no longer silently defaults to one city.

## Scoring today

Gather intentionally separates ranking from generative AI.

The cold-start score is based on:
- proximity
- organization scale prior
- event / meeting prior
- likely group-food need prior
- public contactability

v0.9 computes and persists:
- **Profile Fit (0-10):** restaurant concept, catering capability, daypart, price position, delivery radius, prospect category, and distance
- **Evidence (0-10):** grounded signals found on the prospect's reachable public website
- **Learning (-8 to +8):** category-level adjustment derived from the latest `REPLIED`, `WON`, and `LOST` outcomes in that restaurant workspace

The evidence-adjusted score is `round(base_score * 0.80) + profile_fit + evidence`, and the learned outcome adjustment is then applied, capped to 0–100.

The top prospects are enriched automatically after the initial list appears, so users see results quickly instead of waiting for every external website. Lower-ranked prospects can still be investigated through the evidence and AI analysis actions.

## Next product milestones

1. Add reply/sent synchronization through Gmail so pipeline state does not rely on manual updates.
2. Add structured evidence claims with freshness/confidence and monitor material prospect changes.
3. Add revenue/order value to `WON` outcomes so learning optimizes for dollars, not only conversions.
4. Add menu/service constraints: minimum order, capacity, lead time, dietary support, and delivery rules.
5. Add campaign playbooks for office lunch, schools, holiday events, universities, and other recurring motions.
6. Add notification delivery for due follow-ups and newly discovered high-fit prospects.
7. Add multi-tenant organizations, authentication, roles, and production billing.
8. Move autocomplete/discovery/contact enrichment to production-grade licensed providers.
9. Measure retention, reply rate, win rate, and catering revenue influenced by Gather.

## Positioning

Gather is not trying to be another giant contact database.

Its wedge is:

> **The local B2B sales system for restaurants that want recurring catering and group-order revenue.**

The longer-term platform can generalize beyond restaurants by making the scoring profile, buying signals, and source adapters configurable by business vertical.
