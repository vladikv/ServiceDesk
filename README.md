# Service Desk

Java 21 internal IT service desk MVP built as a modular monolith with Spring Boot, Vaadin, PostgreSQL, and Flyway. Application screens and business behavior are implemented in Java.

## Prerequisites

- Docker Desktop with Docker Compose v2+

## Run with Docker Compose

Docker Compose builds the Java application image in Vaadin production mode, starts PostgreSQL, waits for the database health check, then starts the app. The database uses a named volume so its data survives container recreation. Set secrets in the current PowerShell session before the first start:

```powershell
$env:DATABASE_PASSWORD = 'choose-a-unique-database-password'
$env:BOOTSTRAP_ADMIN_USERNAME = 'admin'
$env:BOOTSTRAP_ADMIN_PASSWORD = 'choose-a-unique-admin-password'
docker compose up --build -d
docker compose ps
docker compose logs -f app
```

The app is available at <http://localhost:8080>. Sign in with the bootstrap admin. The bootstrap account is created only when the user table is empty; its credentials are needed only on the first run. If using a `.env` file instead of session variables, keep it untracked and do not put real secrets in source control.

Useful commands:

```powershell
docker compose logs -f app       # Follow application logs
docker compose stop              # Stop containers, keep database data
docker compose down              # Remove containers/network, keep database data
docker compose down -v            # Also delete the database volume and all stored data
```

The app health check uses Spring Boot's readiness endpoint. Compose does not start the app until PostgreSQL reports healthy. The health endpoint is unauthenticated for container probes and exposes only health information.

On first startup, if the database is empty, both `BOOTSTRAP_ADMIN_USERNAME` and `BOOTSTRAP_ADMIN_PASSWORD` are required. Usernames are normalized to lowercase. Passwords must be at least 12 characters and no more than 72 UTF-8 bytes because BCrypt limits the input size.

There is no public registration. Open **Manage users** as the bootstrap admin to create requester, agent, or admin accounts. Administrators can list accounts, enable/disable them, and reset passwords. Usernames are immutable and accounts are not deleted so historic request and audit references remain meaningful. The last active admin cannot be disabled. Passwords are stored as BCrypt hashes; disabled accounts cannot authenticate or be assigned to new requests.

Flyway applies schema changes from `src/main/resources/db/migration`. Hibernate validates the migrated schema at startup.

## Run locally without Docker

For local development without containers, install JDK 21, Maven 3.9+, and PostgreSQL 15+. Set `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `BOOTSTRAP_ADMIN_USERNAME`, and `BOOTSTRAP_ADMIN_PASSWORD`, then run:

```powershell
.\mvnw.cmd spring-boot:run
```

## MVP workflow

- Requesters create requests and can see or comment on their own requests.
- Agents and admins can search all requests, assign them, change their status, comment, and inspect audit history.
- Status transitions are validated centrally in the request service.
- All requests use one configurable SLA target, 24 hours by default (`SLA_TARGET_DURATION`, for example `48h` or `2d`), regardless of priority. Time in `WAITING_FOR_REQUESTER` pauses the SLA clock.
- SLA breaches are recorded once in request history and flagged for agent/admin attention. Priority is not automatically changed. Agents and admins can filter breached requests and see the breach count.

## Tests

Run `.\mvnw.cmd test`. Tests use an isolated in-memory database and do not require PostgreSQL or Docker.
