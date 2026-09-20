# Gather — Restaurant Prospect Intelligence

Gather is a Java/Spring Boot application that helps restaurants discover and prioritize nearby B2B prospects for catering, group orders, team meals, meetings, and local events.

The project started as a single-location catering discovery prototype. v0.3 removes the fixed restaurant and fixed-city assumptions: a user now enters **their own restaurant name, type, address, and search radius**, and Gather builds a location-specific discovery run from that profile.

## Product thesis

Restaurants often have valuable organizations nearby — offices, campuses, hospitals, schools, community spaces, and event venues — but turning a neighborhood into a useful outbound list is manual.

Gather asks:

> **Which nearby organizations are worth contacting, why are they ranked highly, and what public source supports the recommendation?**

The core object is an explainable prospect recommendation rather than an opaque contact row.

## Current user flow

```text
Restaurant name + type + address
  -> address geocoding
  -> restaurant campaign
  -> discovery run
  -> nearby organization discovery
  -> canonicalization / dedupe
  -> explainable scoring
  -> persisted prospect list
  -> source-linked review
```

The frontend at `http://localhost:8000` now drives this flow directly.

## What makes it restaurant-agnostic

There is no hard-coded restaurant identity in the primary workflow.

Each campaign stores:
- restaurant name
- restaurant type
- normalized origin address
- latitude / longitude
- search radius

That lets the same application run for a cafe in San Francisco, a pizza shop in Oakland, a breakfast restaurant in San Jose, or a catering-focused operation in another market.

The current ranking is still a **cold-start catering/group-order heuristic**. Restaurant type is persisted so future scoring can learn different ideal-customer profiles instead of pretending every restaurant converts the same kinds of accounts.

## Architecture

```text
Restaurant Campaign
  -> Discovery Run
  -> Prospect Sources
  -> Canonicalization / dedupe
  -> Persisted candidates
  -> Evidence enrichment (next)
  -> Explainable scoring
  -> Grounded outreach (next)
  -> Outcome feedback (next)
```

### Discovery
- OpenStreetMap / Overpass for geographic organization discovery
- actual restaurant coordinates drive proximity scoring
- public website/phone availability drives contactability

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

Cold-start score (100 points):
- proximity: 25
- organization scale: 20
- event / meeting signal: 25
- likely group-food need: 20
- public contactability: 10

Proximity uses actual distance. Contactability uses available public website/phone fields. The other dimensions still use category priors until evidence extraction is automated.

## Next product milestones

1. Extract structured claims from prospect websites.
2. Add evidence freshness, confidence, and conflict handling.
3. Make scoring restaurant-type-aware (pizza vs breakfast vs full-service vs catering).
4. Add menu/service constraints: minimum order, delivery radius, dayparts, lead time, dietary support.
5. Add natural-language ICP controls.
6. Add salesperson outcomes (`BAD_LEAD`, `REPLIED`, `MEETING`, `WON`) and learn from them.
7. Add CRM / CSV import and export.
8. Add multi-tenant organizations and authentication.
9. Add production geocoding and licensed enrichment providers.
10. Measure whether evidence-backed ranking improves reply and conversion rates.

## Positioning

Gather is not trying to be another giant contact database.

Its wedge is:

> **Explainable, location-aware prospect intelligence for restaurants that want more local B2B revenue.**

The longer-term platform can generalize beyond restaurants by making the scoring profile, buying signals, and source adapters configurable by business vertical.

<!-- v0.8 work in progress -->
