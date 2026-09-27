# Service Desk

Java 21 internal IT service desk MVP built as a modular monolith with Spring Boot, Vaadin, PostgreSQL, and Flyway. Application screens and business behavior are implemented in Java.

## Prerequisites

- JDK 21
- Maven 3.9+
- PostgreSQL 15+

Create a database and role (or configure `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` for an existing database):

```sql
CREATE USER servicedesk WITH PASSWORD 'change-this-local-password';
CREATE DATABASE servicedesk OWNER servicedesk;
```

Set a distinct password for each configured application role before starting the app. For PowerShell:

```powershell
$env:SERVICEDESK_REQUESTER_PASSWORD = 'requester-local-password'
$env:SERVICEDESK_AGENT_PASSWORD = 'agent-local-password'
$env:SERVICEDESK_ADMIN_PASSWORD = 'admin-local-password'
$env:DATABASE_PASSWORD = 'change-this-local-password'
mvn spring-boot:run
```

The configured usernames default to `requester`, `agent`, and `admin`; override them with the matching `SERVICEDESK_*_USERNAME` variables. Startup fails when any configured user password is blank. These application accounts are in-memory and must be replaced with the organization's identity provider or persistent account provisioning before production use.

Flyway applies schema changes from `src/main/resources/db/migration`. Hibernate validates the migrated schema at startup.

## MVP workflow

- Requesters create requests and can see or comment on their own requests.
- Agents and admins can search all requests, assign them, change their status, comment, and inspect audit history.
- Status transitions are validated centrally in the request service.
- All requests use one configurable SLA target, 24 hours by default (`SLA_TARGET_DURATION`, for example `48h` or `2d`), regardless of priority. Time in `WAITING_FOR_REQUESTER` pauses the SLA clock.
- SLA breaches are recorded once in request history and flagged for agent/admin attention. Priority is not automatically changed. Agents and admins can filter breached requests and see the breach count.

## Tests

Run `mvn test`. Tests use an isolated in-memory database and do not require PostgreSQL.
