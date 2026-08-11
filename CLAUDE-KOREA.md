# CLAUDE-KOREA.md

> 이 문서는 `CLAUDE.md`의 한글 버전이다. 규칙이 변경되면 두 파일을 함께 갱신한다.

## 프로젝트 개요

이 프로젝트는 청년들의 성공적인 지역 정착과 이주를 돕기 위해 AI와 데이터를 기반으로
일자리, 정책, 지역 정보를 맞춤형으로 제공하는 이주 도우미 플랫폼 서버이다. 

백엔드 전용 Spring Boot API 서버. Java 17, Gradle wrapper.

## AI 에이전트 중요 규칙

사용자가 명시적으로 요청하지 않은 코드베이스 변경이나 작업을 수행하지 않는다.
사용자의 지시를 엄격히 따르고, 선제적인 수정이나 추가를 하지 않는다.

## 컨벤션 파일

| 대상 | 규칙 파일 |
| --- | --- |
| 공통 아키텍처, 의존 방향, Contract, messaging | `.claude/skills/architecture-conventions/SKILL.md` |
| 명명 규칙, 공통 기반, DTO, Controller/Service | `.claude/skills/backend-conventions/SKILL.md` |
| Entity, Enum, Repository Port/Adapter, QueryDSL, 페이지네이션·정렬, N+1 | `.claude/skills/persistence-conventions/SKILL.md` |
| 응답 봉투, `ErrorCode`, 예외 처리, Swagger | `.claude/skills/global-conventions/SKILL.md` |

위 대상의 작업을 할 때 해당 규칙 파일을 먼저 확인한다. 여러 영역에 걸친 작업은
`architecture-conventions`도 함께 확인한다. 이 프로젝트는 Domain-Driven-Development(DDD)다
(각 도메인 내부는 3계층, 도메인 간은 `{Domain}Contract` 또는 `global/messaging`으로만,
Business 계층 없음).

## 에이전트 라우팅

| 역할 | 파일 | 권한 |
| --- | --- | --- |
| backend-developer | `.claude/agents/backend-developer.md` | 할당된 범위의 유일한 작성자. 새 기능을 목표 DDD 구조로 바로 구현한다 |
| ddd-refactorer | `.claude/agents/ddd-refactorer.md` | 기존 레이어드 코드를 DDD 헥사고날 구조로 이관하는 유일한 작성자. 구조만 옮기고 동작은 바꾸지 않는다 |


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
