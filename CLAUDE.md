# CLAUDE.md

> This document is the English version of `CLAUDE-KOREA.md`. Update both files whenever the rules change.

## Project Overview

This project is a migration-support platform server that uses AI and data to provide
personalized job, policy, and regional information, helping young people successfully
settle in and relocate to local areas.

Backend-only Spring Boot API server. Java 17, Gradle wrapper.

## Package Structure

```
SDD.smash.domain.<context>.<layer>   Bounded contexts: address, job, dwelling, infra,
                                     support, recommendation
                                     Layers: domain / application / infrastructure / presentation
SDD.smash.global.<area>              Shared foundation, not a context:
                                     domain.model (shared-kernel value objects),
                                     exception[.handler], config, security, batch, util
```

Note that `domain` appears twice in a package such as `SDD.smash.domain.dwelling.domain.model`.
The first is only a grouping directory for contexts; the second is the hexagonal domain layer.
Rules like "no JPA imports in domain" refer to the second one.

## Critical Rules for AI Agents

Do not make codebase changes or perform work that the user has not explicitly requested.
Follow the user's instructions strictly; do not make proactive modifications or additions.

## Convention Files

| Scope | Rules file |
| --- | --- |
| Bounded contexts, the four layers and dependency direction, Aggregates, port design, package layout | `.claude/skills/architecture-conventions/SKILL.md` |
| Domain models, Policies, use cases, controllers, test strategy | `.claude/skills/backend-conventions/SKILL.md` |
| JPA entities, Repository ports/adapters, JPQL projections, transaction boundaries, batch upserts | `.claude/skills/persistence-conventions/SKILL.md` |
| Package/class naming, ubiquitous language, `DomainException`/`ErrorCode`, DTO separation, logging | `.claude/skills/global-conventions/SKILL.md` |
| Cache and repository ports over Redis, key naming, TTL, invalidation | `.claude/skills/redis-conventions/SKILL.md` |
| Seed CSV files, Spring Batch seed jobs, `@Order`, `BatchGuard`, reload procedure | `.claude/skills/seed-data/SKILL.md` |

Read the relevant rules file before working in its scope. For work spanning multiple
areas, also read `architecture-conventions`.

This project follows Domain-Driven Design with a hexagonal (ports & adapters) structure.
Each bounded context has up to four layers — `domain`, `application`, `infrastructure`,
`presentation` — and dependencies only ever point inward
(`presentation`/`infrastructure` → `application` → `domain`).

Cross-context calls go through the target context's **application `Service` class** —
this project deliberately does **not** define `application/port/in` use-case interfaces.
The relaxation applies between `application` layers only: reaching into another context's
`domain`, `domain/port`, or `infrastructure` is still forbidden, and the only thing
contexts share directly is the value objects in `SDD.smash.global.domain.model`.
Out-ports (`domain/port`, `application/port/out`) remain interfaces — dependency
inversion needs them.

## Agent Routing

| Role | File | Authority |
| --- | --- | --- |
| backend-developer | `.claude/agents/backend-developer.md` | Sole writer for its assigned scope. Implements new features in the DDD hexagonal structure |
| code-reviewer | `.claude/agents/code-reviewer.md` | Read-only. Reviews changed files against the DDD architecture and project conventions; never edits code |

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
