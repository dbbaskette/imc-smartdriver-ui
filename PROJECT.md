# Project Instructions: Smart Driver Monitoring UI

> **Instructions for the User**:
> This document outlines the plan for the Smart Driver Monitoring UI project. It will be used as the primary source of truth for the project's context, goals, and constraints.

---

## 1. Project Overview & Goal

* **What is the primary goal of this project?**
    * To build a dynamic, single-page Spring Boot web application that provides a real-time monitoring dashboard for the "Smart Driver" data pipeline. The UI will visually represent the flow of data from vehicle telemetry through various processing stages, culminating in a driver score. This application is primarily intended for live demonstrations to stakeholders.

* **Who are the end-users?**
    * Internal development teams, project managers, and business stakeholders who need to understand and visualize the data pipeline's health and activity during a presentation.

---

## 2. Tech Stack

* **Language(s) & Version(s)**: Java 21
* **Framework(s)**:
    * Spring Boot 3.5.4
    * Spring WebFlux (for WebSocket communication)
    * Spring AMQP (for RabbitMQ integration)
    * Spring Data JDBC (for Greenplum connectivity)
    * Spring Scheduling (for periodic metric collection)
* **Database(s)**:
    * Greenplum (as the primary analytics database)
    * RabbitMQ (as the message broker)
* **Key Libraries**:
    * **Frontend**: D3.js (for data visualization and animation), JavaScript (ES6+)
    * **Backend**: Lombok, `hadoop-client` (for HDFS interaction)
* **Build/Package Manager**: Maven

---

## 3. Architecture & Design

* **High-Level Architecture**: Monolithic Spring Boot application serving a static HTML/JS frontend and a WebSocket backend.

* **Key Design Patterns & Flow**:
    1.  **Backend (Spring Boot App)**:
        * A central `MetricsCollectorService` will be implemented as a `@Scheduled` bean. This service will run at a fixed interval (e.g., every 2 seconds).
        * On each run, the service will connect to:
            * **RabbitMQ**: Using the RabbitMQ Management HTTP API or AMQP admin client to get the message count for the `vehicle-events` queue.
            * **HDFS**: Using the `hadoop-client` library to get file counts and total size for the target directory.
            * **Greenplum**: Using JDBC to execute queries that retrieve:
                * The latest "Smart Driver Score" from `driver_safety_scores`.
                * Row count from `accidents`.
                * Row count from `vehicle_telemetry_data` (PXF external/foreign table).
        * The collected metrics will be aggregated into a single JSON object (a DTO like `PipelineMetrics.java`).
        * This DTO will be broadcast over a WebSocket endpoint (e.g., `/ws/metrics`) to all connected clients using Spring WebFlux's `Sinks.Many`.

    2.  **Frontend (index.html)**:
        * A single HTML page will contain the UI structure and the D3.js visualization.
         * On page load, a JavaScript client will establish a WebSocket connection to the backend's `/ws/metrics` endpoint.
         * An `onmessage` event listener will parse the incoming JSON metrics.
         * The D3.js code will use the received metrics to:
             * Update the "Queue Count" number under the RabbitMQ logo.
             * Animate data packets based on activity.
             * Update the "Smart Driver Score" gauge.
        * The emoji icons will be replaced with official SVG logos for RabbitMQ, Apache Spark, and Greenplum for a more professional look.

* **Directory Structure**:
    * `src/main/java/com/insurancemegacorp/monitoring/`: Main application source.
        * `config/`: WebSocket, Scheduler, and Database configurations.
        * `controller/`: (Optional, if any REST endpoints are needed).
        * `service/`: `MetricsCollectorService`.
        * `web/`: WebSocket handler (`MetricsWebSocketHandler`).
        * `dto/`: `PipelineMetrics.java`.
    * `src/main/resources/`:
        * `application.properties`: All external connection details (hosts, ports, credentials).
        * `static/`: Contains `index.html`, CSS, JS files, and SVG logos.

---

## 4. Coding Standards & Conventions

* **Code Style**: Google Java Style Guide.
* **Naming Conventions**:
    * Use `camelCase` for variables and methods.
    * Services suffixed with `Service`, DTOs with `DTO` or as records.
* **API Design**:
    * Primary communication via WebSockets.
    * The WebSocket payload will be a consistent JSON object (`PipelineMetrics`).
* **Error Handling**:
    * The `MetricsCollectorService` must gracefully handle connection failures to any of the external systems (RabbitMQ, HDFS, Greenplum).
    * Use `try-catch` blocks for each external call. If a service is down, log the error and send a default/stale value for that metric (e.g., -1 or the last known value) to the UI to prevent crashes.

---

## 5. Important "Do's and Don'ts"

* **DO**: Externalize all connection credentials and hostnames into `application.properties`. Use Spring Cloud Connectors or environment variables for binding in a Cloud Foundry environment.
* **DON'T**: Hardcode any secrets or hostnames directly in the Java code.
* **DO**: Use the official logos for RabbitMQ, Greenplum, and Apache Spark to enhance the visual fidelity of the demo.
* **DO**: Implement comprehensive logging around the metric collection process to easily debug connection issues.
* **DON'T**: Couple the frontend and backend tightly. The frontend should only react to the data it receives over the WebSocket, and the backend should not contain any UI logic.

---

## 6. Phased Development Plan

The plan is organized into small, verifiable phases with clear acceptance criteria. Tasks are intentionally granular to support stepwise execution and tracking [[memory:2964278]].

### Phase 0 — PoC Stabilization (Static)
- **Goal**: Ensure `PoC.html` runs cleanly as a static demo.
- **Tasks**:
  - [x] Verify animations render and score color transitions work
  - [ ] Move `PoC.html` into `src/main/resources/static/index.html` once app skeleton exists
  - [ ] Extract inline CSS/JS to `/static/css/` and `/static/js/` for maintainability
- **Acceptance**: Opening the page shows animated packets, queue count changes, and score updates locally with no console errors.

### Phase 1 — Backend Skeleton (Spring Boot + Maven Wrapper)
- **Goal**: Create a runnable Spring Boot app that serves the static UI.
- **Tasks**:
  - [ ] Initialize Maven project with wrapper (`./mvnw`) and parent POM
  - [ ] Add Spring Boot 3.x dependencies (WebFlux, Scheduling); align versions per `versions.txt` (TBD)
  - [ ] Add `Application` class and basic config structure
  - [ ] Serve static assets from `src/main/resources/static/`
- **Acceptance**: `./mvnw spring-boot:run` serves the PoC at `/` and builds with `mvn clean package` [[memory:2615786]].

### Phase 2 — WebSocket Mock Metrics
- **Goal**: Broadcast mocked `PipelineMetrics` over WebSocket.
- **Tasks**:
  - [ ] Define `PipelineMetrics` DTO (queueDepth, hdfsFileCount, hdfsBytes, greenplumRowCount, latestScore, ts)
  - [ ] Implement `MetricsWebSocketHandler` using Spring WebFlux and `Sinks.Many`
  - [ ] Implement `MockMetricsGenerator` scheduled at fixed rate
  - [ ] Expose endpoint `/ws/metrics`
- **Acceptance**: Frontend receives periodic JSON metrics over WS; no external systems required.

### Phase 3 — Frontend Integration (D3 wired to WS)
- **Goal**: Replace PoC timers with real-time updates from WS payloads.
- **Tasks**:
  - [ ] Establish WS client in the page and parse metrics
  - [ ] Drive queue counter, packet animations, and score from metrics
   - [ ] Replace emoji with SVG logos (RabbitMQ, Spark, Greenplum); keep emoji fallback
- **Acceptance**: Visuals update strictly based on incoming metrics; no setInterval simulation needed.

### Phase 4 — Real Integrations (Feature-flagged)
- **Goal**: Collect real metrics with safe fallbacks.
- **Tasks**:
   - [ ] RabbitMQ: query Management API (or AMQP admin) for `vehicle-events` queue depth
  - [ ] HDFS: use `hadoop-client` to list target dir and aggregate counts/sizes
   - [ ] Greenplum: JDBC query for latest driver score and table row counts (`accidents`, `vehicle_telemetry_data`)
  - [ ] Add `metrics.mode=mock|real` feature flag; mock remains default
  - [ ] Fail-safe error handling; log and send last-known/default values on errors
- **Acceptance**: With `metrics.mode=real`, metrics reflect live systems; with `mock`, the app runs without external deps.

### Phase 5 — Hardening & Packaging
- **Goal**: Productionize the demo.
- **Tasks**:
  - [ ] Externalize config in `application.properties` with env overrides
  - [ ] Security baseline (limit origins, simple auth if needed for WS)
  - [ ] Dockerfile and run docs (profiles: `mock`, `real`)
  - [ ] Health endpoint and readiness checks
- **Acceptance**: Run locally via Docker and on a demo host with a single documented command.

### Phase 6 — Testing & Observability
- **Goal**: Confidence and visibility.
- **Tasks**:
  - [ ] Unit tests for DTOs and services
  - [ ] Integration tests for WS stream (mock mode)
  - [ ] Optional Testcontainers for RabbitMQ/Greenplum (time-box)
  - [ ] Structured logging and minimal metrics (JVM/uptime)
- **Acceptance**: CI green; tests cover core flow; logs actionable.

### Phase 7 — Optional Enhancements
- **Goal**: Boost demo value.
- **Options**:
  - [ ] UI controls: start/stop simulation, speed sliders, tooltips
  - [ ] Trend sparkline charts for score and queue depth
- **Acceptance**: Enhancements are toggleable and documented.

---

## 7. Environments & Configuration
- `application.properties` keys (draft):
  - `metrics.mode=mock` (mock|real)
  - RabbitMQ: `rabbit.host`, `rabbit.port`, `rabbit.user`, `rabbit.pass`, `rabbit.mgmt.port`, `rabbit.queue`
    - Defaults (dev): `rabbit.host=localhost`, `rabbit.port=5672`, `rabbit.mgmt.port=15672`, `rabbit.user=guest`, `rabbit.pass` via env var, `rabbit.queue=vehicle-events` (from imc-vehicle-events manifest)
    - Cloud Foundry (Spring profile `cloud`, file `application-cloud.yml`): RabbitMQ credentials/host provided via bound service (VCAP_SERVICES). Activate with `SPRING_PROFILES_ACTIVE=cloud`.
  - HDFS: `hdfs.namenodeUri`, `hdfs.targetPath`
    - Defaults (dev/demo): `hdfs.namenodeUri=hdfs://big-data-005.kuhn-labs.com:8020`, `hdfs.targetPath=/insurance-megacorp/telemetry-data-v2` (per imc-vehicle-events/manifest and PXF external table)
  - Greenplum: `gp.host`, `gp.port`, `gp.db`, `gp.user`, `gp.pass`, `gp.schema`, `gp.scoreTable`, `gp.eventTable`, `gp.telemetryTable`
    - Defaults (demo): `gp.host=big-data-001.kuhn-labs.com`, `gp.port=5432`, `gp.db=insurance_megacorp`, `gp.user=gpadmin`, `gp.pass` via env var, `gp.schema=public`, `gp.scoreTable=driver_safety_scores`, `gp.eventTable=accidents`, `gp.telemetryTable=vehicle_telemetry_data`
  - WS: `ws.path=/ws/metrics`, CORS/origin settings

### Profiles
- **default (local)**: `application.properties` with local overrides via env vars.
- **cloud (Cloud Foundry)**: `application-cloud.yml` consumed when `SPRING_PROFILES_ACTIVE=cloud`; uses bound service credentials (e.g., RabbitMQ) and any cloud-specific overrides.

---

## 8. Dependencies & Versions
- Build: Maven (with wrapper). Parent POM to centralize versions.
- Spring Boot: 3.x (confirm exact version)
- Java: 21
- Key libs: Spring WebFlux, Spring Scheduling, Spring AMQP, Hadoop client, Greenplum JDBC, Lombok, D3.js
- Action: If a `versions.txt` is maintained, please provide. Otherwise, confirm desired versions and repository sources for Greenplum JDBC and Hadoop.

---

## 9. Risks & Mitigations
- External systems unavailable during demo → Default to `mock` mode with clear indicator
- HDFS/Hadoop dependency bloat → Shade/relocate or use lightweight probes; time-box investigation
- Greenplum JDBC sourcing/licensing → Confirm driver coordinates and distribution policy early
- WS connectivity across networks → CORS/origin and proxy headers tested beforehand

---

## 10. Open Questions (Input Needed)
1. Confirm Spring Boot version and BOM preferences (or provide `versions.txt`).
2. Confirm RabbitMQ management API availability/credentials (host defaults to `localhost:15672`).
3. Confirm HDFS accessibility from the app network to `big-data-005.kuhn-labs.com:8020`.
4. Confirm Greenplum DB name (`insurance_megacorp`) and table names (`driver_safety_scores`, `accidents`, `vehicle_telemetry_data`).
5. Do you want this created as a new Git repository from the start? (Requested: yes)

---

## 11. Next Actions
- Await answers to Open Questions (especially versions and endpoints)
- Initialize project skeleton (Phase 1)
- Wire mock WS metrics (Phase 2) and integrate UI (Phase 3)
- Document as we go (this file), plus `implementation_details.md`, `gotchas.md`, `quick_reference.md` per changes