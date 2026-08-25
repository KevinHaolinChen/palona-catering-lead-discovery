# Technical Discussion Prep — Java Version

## 30-second framing

I treated the assignment as an evidence-first lead recommendation problem rather than a scraping problem. The application uses public geographic data to discover candidate organizations, separates sourced facts from inference, prioritizes leads with an explainable score, recommends an honest public contact path, and generates outreach only from stored evidence. The cached result keeps the demo reliable even if external services fail.

## Architecture mapping

- `ProspectController`: HTTP boundary. It validates requests, delegates work, and translates expected failures into HTTP responses.
- `ProspectService`: owns the saved prospect set and enforces startup-time data invariants.
- `DiscoveryService`: high-recall candidate generation from Overpass.
- `EnrichmentService`: bounded direct-page fetcher for high-precision verification.
- `ScoringService`: deterministic cold-start ranking.
- `OutreachService`: cached outreach plus optional grounded LLM generation.
- Java records: immutable DTO/domain structures with Bean Validation constraints.

## Why Spring Boot?

The project is a small HTTP service, so Spring Boot gives me routing, dependency injection, request validation, JSON serialization, embedded server support, and test infrastructure without introducing application-specific infrastructure. It also matches how I would naturally structure backend production code: controller -> service -> typed domain model.

## Why Java 21?

Java 21 is an LTS release and lets me use records and text blocks to keep DTOs and request construction concise while still staying in a conservative production Java baseline.

## Why records?

Most objects here are immutable data carriers: evidence, contact paths, score breakdowns, prospects, and API responses. Records make that intent explicit and remove boilerplate getters/constructors without hiding business logic in code generation.

## Why claim-level provenance?

A source might verify an office address without verifying event activity. Attaching provenance to each claim prevents one URL from appearing to support every statement about a prospect.

## Why is discovery separate from enrichment?

OpenStreetMap is useful for high-recall geographic candidate generation but is not authoritative enough for important sales claims. I deliberately separate candidate discovery from direct-source verification so noisy map records do not silently become facts.

## What is currently automated vs hand-reviewed?

Automated now:
- serve cached results
- validate cached data invariants
- discover nearby organizations through Overpass
- classify candidates
- compute deterministic cold-start scores
- optionally regenerate outreach from stored evidence

Hand-reviewed in the take-home:
- direct-source research for the 12 final prospects
- evidence-conditioned scoring adjustments
- conflict/staleness resolution
- review of AI-assisted outreach

The main next engineering step is to orchestrate those hand-reviewed enrichment steps automatically.

## Why deterministic scoring?

For a take-home with no historical conversion labels, an ML ranker would create false sophistication. The deterministic score is inspectable, debuggable, and easy for a salesperson to challenge. If feedback data accumulated, I could later learn or tune the weights.

## Why event signal is weighted heavily

Catering is driven by groups gathering. Organization size is only an indirect proxy; evidence of meetings, events, trainings, receptions, or community programs is closer to the behavior we are trying to predict.

## Good failure-mode example

DPR demonstrates stale-data conflict. An older page looked like evidence for a Redwood City office, but newer official information pointed to Santa Clara. I rejected the candidate and preserved the reason. In production I would retain observation timestamps, revalidate high-impact facts on TTLs, and quarantine conflicting records until resolved.

## Weaknesses to admit clearly

1. Live discovery is not yet wired to automatic direct-source enrichment.
2. Cold-start scoring is category-based rather than evidence-derived.
3. The enrichment helper needs stronger production SSRF/rate-limit/robots controls.
4. Source freshness is stored in the cached evidence but not yet automatically revalidated.
5. There is no salesperson feedback loop yet.

These are not things to hide. They define the next iteration.

## 90-second explanation

I built the backend as a Spring Boot service with a simple controller-service-domain structure. The first stage is candidate discovery: given an IHOP latitude, longitude, and radius, `DiscoveryService` queries OpenStreetMap through Overpass and returns nearby offices, campuses, hospitals, and event spaces. I deliberately treat those as candidates rather than facts because map data can be incomplete or stale.

For the curated results, every meaningful statement is represented as evidence and typed as a fact, inference, or generated content. Facts carry source URLs and confidence, and `ProspectService` fails at startup if a factual claim has no provenance or if a score disagrees with its component breakdown. Leads are prioritized with an explainable 100-point score across proximity, scale, meeting/event signal, food-need signal, and contactability.

I prefer public role-based contact paths over hallucinated individuals, and optional LLM outreach is grounded only in the evidence already stored for that prospect. The application also ships with 12 cached reviewable leads, so external-source failures do not break the demo. The biggest next step would be automating the transition from geographic discovery into direct-source enrichment, structured extraction, conflict handling, and evidence-conditioned re-ranking.
