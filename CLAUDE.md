# CLAUDE.md

> This document is the English version of `CLAUDE-KOREA.md`. Update both files whenever the rules change.

## Project Overview

This project is a migration-support platform server that uses AI and data to provide
personalized job, policy, and regional information, helping young people successfully
settle in and relocate to local areas.

Backend-only Spring Boot API server. Java 17, Gradle wrapper.

## Critical Rules for AI Agents

Do not make codebase changes or perform work that the user has not explicitly requested.
Follow the user's instructions strictly; do not make proactive modifications or additions.

## Convention Files

| Scope | Rules file |
| --- | --- |
| Shared architecture, dependency direction, Contract, messaging | `.claude/skills/architecture-conventions/SKILL.md` |
| Naming conventions, shared foundations, DTOs, Controllers/Services | `.claude/skills/backend-conventions/SKILL.md` |
| Entities, Enums, Repository Ports/Adapters, QueryDSL, pagination/sorting, N+1 | `.claude/skills/persistence-conventions/SKILL.md` |
| Response envelope, `ErrorCode`, exception handling, Swagger | `.claude/skills/global-conventions/SKILL.md` |

Read the relevant rules file before working in its scope. For work spanning multiple
areas, also read `architecture-conventions`. This project follows Domain-Driven Design
(DDD): each domain has three layers; cross-domain communication is allowed only through
`{Domain}Contract` or `global/messaging`; there is no Business layer.

## Agent Routing

| Role | File | Authority |
| --- | --- | --- |
| backend-developer | `.claude/agents/backend-developer.md` | Sole writer for its assigned scope. Implements new features directly in the target DDD structure |
| ddd-refactorer | `.claude/agents/ddd-refactorer.md` | Sole writer for migrating existing layered code to the DDD hexagonal structure. Moves structure only, never changes behavior |

## General Rules

- Always respond in Korean.
- Do not modify files unrelated to the change requested by the user.
- Confirm before carrying out high-impact decisions, such as new permissions, external systems, or database schema changes.
- Do not delete files or run destructive Git commands without user approval.
- Run `git push` or `git commit` only when the user explicitly requests it.
- Idempotency, retry, and notification-failure policies have not yet been defined. Do not invent them.

## Verification

Run tests with `.\gradlew.bat test` on Windows or `./gradlew test` otherwise. Do not hide or
work around failures; report them clearly. Unit and controller tests run without a database.
Integration tests extend `IntegrationTestSupport` and run against a real MySQL instance started
by Testcontainers (**the Docker daemon must be running**). The `Dockerfile` is not involved;
Testcontainers pulls the official `mysql:8.0` image directly. For failure paths, assert the API
error code as well as the HTTP status.

Version caveats for this project (Spring Boot 3.5.7):

- **Do not add `testcontainers-bom`.** Spring Boot 3.5.7 manages Testcontainers 1.21.3, so do
  not specify versions and let Spring Boot manage them. Adding a separate Testcontainers 2.x BOM
  moves dependencies outside the managed version and requires separate migration compatibility checks.
- The managed Testcontainers 1.x artifact names are `mysql` and `junit-jupiter`. `MySQLContainer`
  is in the `org.testcontainers.containers` package and uses a generic parameter (for example,
  `MySQLContainer<?>`).
- **The managed Jackson version is Jackson 2 (`com.fasterxml.jackson`).** Inject
  `com.fasterxml.jackson.databind.ObjectMapper`.
