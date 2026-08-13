# CLAUDE-KOREA.md

> 이 문서는 `CLAUDE.md`의 한글 버전이다. 규칙이 변경되면 두 파일을 함께 갱신한다.

## 프로젝트 개요

이 프로젝트는 청년들의 성공적인 지역 정착과 이주를 돕기 위해 AI와 데이터를 기반으로
일자리, 정책, 지역 정보를 맞춤형으로 제공하는 이주 도우미 플랫폼 서버이다. 

백엔드 전용 Spring Boot API 서버. Java 17, Gradle wrapper.

## 패키지 구조

```
SDD.smash.domain.<context>.<layer>   바운디드 컨텍스트: address, job, dwelling, infra,
                                     support, recommendation
                                     계층: domain / application / infrastructure / presentation
SDD.smash.global.<area>              컨텍스트가 아닌 공통 기반:
                                     domain.model(공유 커널 값 객체),
                                     exception[.handler], config, security, batch, util
```

`SDD.smash.domain.dwelling.domain.model` 처럼 `domain`이 두 번 나오는 점에 주의한다.
앞의 `domain`은 컨텍스트를 묶는 디렉터리일 뿐이고, 뒤의 `domain`이 헥사고날의 domain 계층이다.
"domain에 JPA import 금지" 같은 규칙은 뒤쪽을 가리킨다.

## AI 에이전트 중요 규칙

사용자가 명시적으로 요청하지 않은 코드베이스 변경이나 작업을 수행하지 않는다.
사용자의 지시를 엄격히 따르고, 선제적인 수정이나 추가를 하지 않는다.

## 컨벤션 파일

| 대상 | 규칙 파일 |
| --- | --- |
| 바운디드 컨텍스트, 4계층과 의존 방향, Aggregate, 포트 설계, 패키지 배치 | `.claude/skills/architecture-conventions/SKILL.md` |
| 도메인 모델, Policy, 유스케이스, 컨트롤러, 테스트 전략 | `.claude/skills/backend-conventions/SKILL.md` |
| JPA 엔티티, Repository 포트/어댑터, JPQL 프로젝션, 트랜잭션 경계, 배치 Upsert | `.claude/skills/persistence-conventions/SKILL.md` |
| 패키지·클래스 명명, 유비쿼터스 언어, `DomainException`/`ErrorCode`, DTO 분리, 로깅 | `.claude/skills/global-conventions/SKILL.md` |
| Redis 캐시·저장소 포트, 키 네이밍, TTL, 무효화 | `.claude/skills/redis-conventions/SKILL.md` |
| 시드 CSV, Spring Batch 시드 잡, `@Order`, `BatchGuard`, 재적재 절차 | `.claude/skills/seed-data/SKILL.md` |

위 대상의 작업을 할 때 해당 규칙 파일을 먼저 확인한다. 여러 영역에 걸친 작업은
`architecture-conventions`도 함께 확인한다.

이 프로젝트는 DDD 헥사고날(포트 & 어댑터) 구조다. 각 바운디드 컨텍스트는 최대 4계층
(`domain`, `application`, `infrastructure`, `presentation`)을 가지며 의존은 항상 안쪽으로만
흐른다(`presentation`/`infrastructure` → `application` → `domain`).

컨텍스트 간 호출은 대상 컨텍스트의 **application `Service` 클래스**를 통한다 —
이 프로젝트는 `application/port/in` 유스케이스 인터페이스를 **두지 않는다**.
이 완화는 `application` 계층 사이에만 적용되며, 다른 컨텍스트의 `domain`·`domain/port`·
`infrastructure` 직접 참조는 여전히 금지다. 컨텍스트가 직접 공유하는 것은
`SDD.smash.global.domain.model`의 값 객체뿐이다.
out-port(`domain/port`, `application/port/out`)는 의존 역전이 목적이므로 그대로 인터페이스다.

## 에이전트 라우팅

| 역할 | 파일 | 권한 |
| --- | --- | --- |
| backend-developer | `.claude/agents/backend-developer.md` | 할당된 범위의 유일한 작성자. 새 기능을 DDD 헥사고날 구조로 구현한다 |
| code-reviewer | `.claude/agents/code-reviewer.md` | 읽기 전용. 변경 파일을 DDD 아키텍처·프로젝트 규칙과 대조해 리뷰하며 코드를 수정하지 않는다 |


## 일반 규칙

- 항상 한국어로 답변한다.
- 사용자가 요청한 변경과 무관한 파일은 수정하지 않는다.
- 새 권한, 외부 시스템, DB 스키마 변경처럼 영향이 큰 결정은 실행 전에 확인한다.
- 사용자 승인 없이 파일을 삭제하거나 파괴적인 Git 명령을 실행하지 않는다.
- 사용자가 명시적으로 요청한 경우에만 `git push` 또는 `git commit`을 실행한다.
- 멱등성, 재시도, 알림 실패 정책은 아직 정의되지 않았다. 임의로 만들지 않는다.

## 검증

테스트는 `.\gradlew.bat test`(Windows) / `./gradlew test` 로 실행한다. 실패는 숨기거나
우회하지 말고 명확히 보고한다. 단위·컨트롤러 테스트는 DB 없이 돌고, 통합 테스트는
`IntegrationTestSupport` 를 상속해 Testcontainers 가 띄운 실제 MySQL 에서 실행한다
(**Docker 데몬이 떠 있어야 한다.** `Dockerfile` 은 관여하지 않으며 공식 `mysql:8.0`
이미지를 직접 받는다). 실패 경로는 HTTP 상태뿐 아니라 API 에러코드까지 단언한다.

이 프로젝트(Spring Boot 3.5.7)의 버전 함정:
- **`testcontainers-bom` 을 추가하지 않는다.** Spring Boot 3.5.7 이 Testcontainers 1.21.3 을
  관리하므로 버전을 적지 말고 Boot 가 관리하게 둔다. Testcontainers 2.x BOM 을 별도로 추가하면
  관리 버전에서 벗어나며 마이그레이션 호환성을 별도로 검증해야 한다.
- 관리되는 Testcontainers 1.x 아티팩트명은 `mysql` / `junit-jupiter` 이고,
  `MySQLContainer` 는 `org.testcontainers.containers` 패키지에 있으며
  제네릭 파라미터를 사용한다(예: `MySQLContainer<?>`).
- **관리되는 Jackson 은 Jackson 2(`com.fasterxml.jackson`)이다.**
  `com.fasterxml.jackson.databind.ObjectMapper` 를 주입한다.
