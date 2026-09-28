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

On the first startup, set the bootstrap administrator credentials and database password. The bootstrap admin is created only when the users table is empty; later restarts do not recreate or overwrite it. For PowerShell:

```powershell
$env:BOOTSTRAP_ADMIN_USERNAME = 'admin'
$env:BOOTSTRAP_ADMIN_PASSWORD = 'use-a-unique-long-password'
$env:DATABASE_PASSWORD = 'change-this-local-password'
.\mvnw.cmd spring-boot:run
```

The bootstrap password is required only when no account exists; startup fails with a clear message if the users table is empty and either bootstrap value is missing. Usernames are normalized to lowercase. Passwords must be at least 12 characters and no more than 72 UTF-8 bytes because BCrypt limits the input size. Secrets are read from environment variables and should never be committed to the repository.

There is no public registration. Sign in with the bootstrap admin, open **Manage users**, and create requester, agent, or admin accounts. Administrators can list accounts, enable/disable them, and reset passwords. Usernames are immutable and accounts are not deleted so historic request and audit references remain meaningful. The last active admin cannot be disabled.

Flyway applies schema changes from `src/main/resources/db/migration`. Hibernate validates the migrated schema at startup. Passwords are stored as BCrypt hashes; disabled accounts cannot authenticate or be assigned to new requests.

## MVP workflow

- Requesters create requests and can see or comment on their own requests.
- Agents and admins can search all requests, assign them, change their status, comment, and inspect audit history.
- Status transitions are validated centrally in the request service.
- All requests use one configurable SLA target, 24 hours by default (`SLA_TARGET_DURATION`, for example `48h` or `2d`), regardless of priority. Time in `WAITING_FOR_REQUESTER` pauses the SLA clock.
- SLA breaches are recorded once in request history and flagged for agent/admin attention. Priority is not automatically changed. Agents and admins can filter breached requests and see the breach count.

## Tests

Run `mvn test`. Tests use an isolated in-memory database and do not require PostgreSQL.
