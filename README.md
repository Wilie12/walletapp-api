# WalletApp API

RESTful backend service simulating a virtual financial wallet and peer-to-peer (P2P) transaction ledger. Developed as the practical implementation for a Bachelor of Engineering (B.Eng.) thesis focusing on OAuth 2.0 resource server security, ACID transaction concurrency, and application-level rate limiting.

## Overview

The project addresses common security and concurrency challenges in financial web services:
- **Stateless Authentication & RBAC**: Acts as an OAuth 2.0 Resource Server verifying RS256 JWTs issued by Keycloak via JWKS. Custom converters map realm roles (`ROLE_USER`, `ROLE_ADMIN`) to method-level security guards.
- **Object & Property Authorization (BOLA / BOPLA)**: Enforces ownership checks (`ownerSub == JWT sub`) at the domain and service layers. Uses immutable Java records for request payloads and one-way MapStruct projections to prevent Mass Assignment.
- **Concurrency & Deadlock Prevention**: Uses PostgreSQL `SELECT ... FOR UPDATE` pessimistic write locks during balance mutations. P2P transfers lock participating wallets in deterministic ascending UUID order to prevent circular deadlocks under concurrent load.
- **Idempotency**: Financial mutations (`top-up`, `transfer`) require an `Idempotency-Key` (`UUID`) header validated against a unique database constraint, returning `409 Conflict` on duplicate requests.
- **Differentiated Rate Limiting**: Implements the Token Bucket algorithm in local memory via Bucket4j (`ConcurrentHashMap`). Assigns higher token costs to state-mutating endpoints ($K = 5$ for transactions, $K = 2$ for status changes, $K = 1$ for reads) and returns `429 Too Many Requests` with a `Retry-After` header.
- **Audit Logging**: Tracks balance and status revisions in `wallets_aud` using Hibernate Envers, recording the actor's JWT `sub` (`author_id`) on every modification.

## Tech Stack

- **Language & Framework**: Java 21, Spring Boot 4.1.1 (Spring Security, Spring Data JPA, Validation, Actuator)
- **Database & Migrations**: PostgreSQL 16, Liquibase, Hibernate Envers
- **Identity Provider**: Keycloak 26.0.0 (OAuth 2.0 / OIDC)
- **Rate Limiting & Mapping**: Bucket4j 8.10.1, MapStruct 1.5.5, Lombok
- **Observability**: Micrometer, Prometheus
- **Testing & CI**: JUnit 5, BDDMockito, AssertJ, Testcontainers (PostgreSQL & Keycloak), GitHub Actions
- **Containerization**: Docker, Docker Compose (non-root Alpine JRE runtime, read-only root filesystem, internal database bridge network)

## API Endpoints

The complete OpenAPI 3.1.0 specification is located in [`openapi.yaml`](./openapi.yaml) and can be imported directly into Postman or OWASP ZAP. All error responses follow the RFC 7807 (`application/problem+json`) standard.

| Method  | Path                                | Access                     | Cost ($K$) | Description                                                              |
|:--------|:------------------------------------|:---------------------------|:----------:|:-------------------------------------------------------------------------|
| `GET`   | `/api/v1/wallets/me`                | `ROLE_USER`                |     1      | Retrieves or lazily provisions the user's default wallet (`0.0000 PLN`). |
| `GET`   | `/api/v1/wallets/{id}`              | `ROLE_USER` / `ROLE_ADMIN` |     1      | Returns wallet details (enforces ownership check for standard users).    |
| `PATCH` | `/api/v1/wallets/{id}`              | `ROLE_USER`                |     1      | Updates wallet label (ignores unmapped JSON fields).                     |
| `POST`  | `/api/v1/transactions/top-up`       | `ROLE_USER`                |     5      | Credits an active wallet (requires `Idempotency-Key` header).            |
| `POST`  | `/api/v1/transactions/transfer`     | `ROLE_USER`                |     5      | Executes an atomic P2P transfer (requires `Idempotency-Key` header).     |
| `GET`   | `/api/v1/transactions`              | `ROLE_USER`                |     1      | Paginated transaction history for the authenticated user.                |
| `GET`   | `/api/v1/transactions/{id}`         | `ROLE_USER` / `ROLE_ADMIN` |     1      | Returns transaction details if the user is a sender/recipient or admin.  |
| `GET`   | `/api/v1/admin/wallets`             | `ROLE_ADMIN`               |     1      | Paginated list of all user wallets.                                      |
| `PATCH` | `/api/v1/admin/wallets/{id}/status` | `ROLE_ADMIN`               |     2      | Locks (`LOCKED`) or unlocks (`ACTIVE`) a wallet.                         |
| `GET`   | `/api/v1/admin/wallets/{id}/audit`  | `ROLE_ADMIN`               |     1      | Returns the Hibernate Envers revision history for a wallet.              |
| `GET`   | `/api/v1/admin/transactions`        | `ROLE_ADMIN`               |     1      | Paginated global ledger of all transactions.                             |

## Getting Started

### Prerequisites

- Docker Engine & Docker Compose
- JDK 21 (for running local builds or tests outside Docker)

### Running with Docker Compose

1. Copy the default environment configuration and start the containers:
   ```bash
   cp .env.example .env
   docker compose up --build -d
   ```

2. Verify container health:
   ```bash
   docker compose ps
   ```

Service endpoints:
- **REST API**: `http://localhost:8080`
- **Readiness Probe**: `http://localhost:8080/actuator/health/readiness`
- **Prometheus Metrics**: `http://localhost:8080/actuator/prometheus`
- **Keycloak Console**: `http://localhost:8081` (credentials in `.env.example`)

## Testing & Security Verification

### Unit, Slice, and Integration Tests

The test suite uses Testcontainers to spin up ephemeral PostgreSQL 16 and Keycloak 26.0.0 instances, verifying security rules, Envers auditing, and multi-threaded P2P transfer concurrency:

```bash
export MAVEN_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=512m"
./mvnw clean verify -B -ntp
```

### OWASP ZAP API Scan (DAST)

With the Docker Compose stack running, execute an automated OpenAPI scan using OWASP ZAP:

```bash
docker run --rm --network host \
  -v "$(pwd):/zap/wrk/:rw" \
  ghcr.io/zaproxy/zaproxy:stable zap-api-scan.py \
  -t /zap/wrk/openapi.yaml \
  -f openapi \
  -O http://localhost:8080 \
  -r zap-dast-report.html
```

## Performance Benchmarking

To reproduce the performance overhead measurements across the three security configurations analyzed in the thesis:

- **Variant A (Unauthenticated baseline, rate limiting disabled)**:
  ```bash
  SPRING_PROFILES_ACTIVE=benchmark APP_SECURITY_RATE_LIMIT_ENABLED=false docker compose up -d walletapp-api
  ```
  Target: `GET http://localhost:8080/api/v1/benchmark/baseline`

- **Variant B (RS256 JWT verification enabled, rate limiting disabled)**:
  ```bash
  APP_SECURITY_RATE_LIMIT_ENABLED=false docker compose up -d walletapp-api
  ```
  Target: `GET http://localhost:8080/api/v1/wallets/me`

- **Variant C (Full stack: RS256 JWT + ownership check + Bucket4j enabled)**:
  ```bash
  APP_SECURITY_RATE_LIMIT_ENABLED=true APP_SECURITY_RATE_LIMIT_CAPACITY=10000 APP_SECURITY_RATE_LIMIT_REFILL_TOKENS=10000 docker compose up -d walletapp-api
  ```
  Target: `GET http://localhost:8080/api/v1/wallets/me`