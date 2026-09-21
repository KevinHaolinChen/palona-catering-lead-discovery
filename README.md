# Gather — Restaurant Prospect Intelligence

Gather is a Java/Spring Boot application that helps restaurants discover and prioritize nearby B2B prospects for catering, group orders, team meals, meetings, and local events.

The project started as a single-location catering discovery prototype. Gather v0.8 now supports restaurant/franchise autocomplete, optional restaurant-profile refinement, evidence-aware reranking, grounded generative analysis, outreach assistance, and a Radius map of discovered prospects.

## Product thesis

Restaurants often have valuable organizations nearby — offices, campuses, hospitals, schools, community spaces, and event venues — but turning a neighborhood into a useful outbound list is manual.

Gather asks:

> **Which nearby organizations are worth contacting, why are they ranked highly, and what public source supports the recommendation?**

The core object is an explainable prospect recommendation rather than an opaque contact row.

## Current user flow

```text
Restaurant/franchise search
  -> choose location
  -> inferred + optional restaurant profile
  -> choose Radius
  -> nearby organization discovery
  -> canonicalization / dedupe
  -> cold-start score
  -> public-site evidence extraction for top prospects
  -> restaurant-fit + evidence reranking
  -> Radius map + ranked prospect list
  -> grounded AI analysis / outreach
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

The first pass still uses a cold-start catering/group-order heuristic, but v0.8 automatically enriches the strongest candidates with restaurant-profile fit and public website evidence before reranking them. The numeric ranking remains deterministic; the LLM is used for grounded explanation and outreach rather than inventing the score.

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
  -> Outcome feedback (next)
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

v0.8 then computes:
- **Profile Fit (0-10):** restaurant concept, catering capability, daypart, price position, delivery radius, prospect category, and distance
- **Evidence (0-10):** grounded signals found on the prospect's reachable public website

The adjusted score is:

`round(base_score * 0.80) + profile_fit + evidence`, capped at 100.

The top prospects are enriched automatically after the initial list appears, so users see results quickly instead of waiting for every external website. Lower-ranked prospects can still be investigated through the evidence and AI analysis actions.

## Next product milestones

1. Convert keyword evidence into structured claims with freshness/confidence.
2. Follow linked event, meeting, team, department, and contact pages during evidence extraction.
3. Add menu/service constraints: minimum order, order capacity, lead time, dietary support, and delivery rules.
4. Add natural-language ICP controls.
5. Add prospect outcomes (`BAD_LEAD`, `REPLIED`, `MEETING`, `WON`) and use them as ranking feedback.
6. Add saved campaigns, notes, CSV/CRM export, and Gmail workflow integration.
7. Add multi-tenant organizations and authentication.
8. Move autocomplete/discovery/contact enrichment to production-grade licensed providers.
9. Measure whether evidence-aware ranking improves reply and conversion rates.

## Positioning

Gather is not trying to be another giant contact database.

Its wedge is:

> **Explainable, location-aware prospect intelligence for restaurants that want more local B2B revenue.**

The longer-term platform can generalize beyond restaurants by making the scoring profile, buying signals, and source adapters configurable by business vertical.
