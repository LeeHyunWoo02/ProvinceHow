---
name: global-conventions
description: smash(ProvinceHow)의 DDD 헥사고날 구조에서 통용되는 전역 규약 — 소문자 패키지 규칙과 계층별 클래스 접미사, 유비쿼터스 언어, DomainException/ErrorCode 기반 예외 설계(HttpStatus 분리), 도메인 모델·애플리케이션 DTO·응답 DTO 3분리 원칙, 계층별 로깅 기준을 정의한다. 새 클래스 이름을 정하거나, 예외를 던지거나, ErrorCode를 추가하거나, DTO를 만들거나, 로그를 남길 때 사용한다. 계층 배치는 architecture-conventions, 유스케이스·도메인 모델 작성은 backend-conventions, JPA 매핑은 persistence-conventions, 캐시는 redis-conventions를 따른다.
---

# global-conventions (DDD)

Java 17 · Spring Boot 3.5.7 · Gradle(Groovy) · Lombok · MySQL(Docker 컨테이너, 스키마 2개) · Redis(Docker 컨테이너).
아키텍처는 **DDD 헥사고날**이다 → architecture-conventions

---

## 1. 패키지 네이밍

**전부 소문자.** 최상위는 두 갈래다.

```
SDD.smash.domain.<context>.<layer>[.<sublayer>]     ← 바운디드 컨텍스트
SDD.smash.global.<area>[.<sublayer>]                ← 컨텍스트에 속하지 않는 공통 기반
```

| 갈래 | 값 |
|---|---|
| 컨텍스트 (`domain.` 아래) | `address`, `job`, `dwelling`, `infra`, `support`, `recommendation` |
| 계층 | `domain`(`.model` / `.service` / `.port`), `application`(`.port.out` / `.dto`), `infrastructure`(`.persistence[.projection]` / `.cache` / `.external[.dto]` / `.batch[.dto,.runner]` / `.scheduler`), `presentation`(`.dto`) |
| `global` 영역 | `domain.model`(공유 커널 값 객체), `exception`(`.handler`), `config`, `security`, `batch`, `metrics`, `util` |

예)
```
SDD.smash.domain.dwelling.domain.model.DwellingMarket
SDD.smash.domain.dwelling.infrastructure.persistence.DwellingJpaEntity
SDD.smash.global.domain.model.SigunguCode
SDD.smash.global.exception.handler.GlobalExceptionHandler
```

### 1.1 `domain`이 두 번 나오는 것에 주의

`SDD.smash.domain.dwelling.domain.model` 에서
- 앞의 `domain` = **컨텍스트 묶음 디렉터리**(아키텍처 의미 없음)
- 뒤의 `domain` = **헥사고날 domain 계층**(§규칙이 걸리는 쪽)

**패키지 이름이 곧 아키텍처 규칙이다.** 단, "domain에 JPA import 금지"는 **뒤쪽 `domain`** 을 가리킨다.
`SDD.smash.domain.` 접두어만 보고 계층을 판단하지 않는다 → architecture-conventions §3.1

---

## 2. 클래스 접미사 카탈로그

| 계층 | 종류 | 접미사 | 예시 |
|---|---|---|---|
| `domain.model` | Aggregate Root / Entity | **없음** (도메인 용어 그대로) | `DwellingMarket`, `JobCount`, `RegionInfra`, `SupportPolicy` |
| `domain.model` | 값 객체 | 없음 (`record`) | `SigunguCode`, `Score`, `Money`, `JobCode` |
| `domain.model` | 도메인 enum | 없음 | `DwellingType`, `SupportTag`, `Major` |
| `domain.model` | 캐시 키 값 객체 | `...Key` | `DwellingScoreKey`, `JobScoreKey`, `SupportScoreKey` |
| `domain.service` | 도메인 서비스 / 정책 | `...Policy` / `...Calculator` | `DwellingScorePolicy`, `JobScorePolicy`, `RentStatCalculator` |
| `domain.port` | out-port (저장) | `...Repository` | `DwellingMarketRepository` |
| `domain.port` | out-port (외부 공급) | `...Provider` | `RentRecordProvider`, `SupportPolicyProvider` |
| `domain.port` | out-port (캐시) | `...Cache` | `DwellingScoreCache` |
| `domain.port` | out-port (조회 전용) | `...Query` | `RegionCodeQuery` |
| `application.port.out` | out-port — **예외적으로만** (architecture-conventions §3.2) | `...Provider` | `RegionPickProvider`, `RegionSummaryProvider` |
| `application` | 유스케이스 (조회) — **컨텍스트의 공개 진입점** | `...QueryService` | `DwellingQueryService`, `RegionDetailService` |
| `application` | 유스케이스 (변경/갱신·계산) | `...Service` | `RefreshSupportPolicyService`, `DwellingScoreService` |
| `application.dto` | 유스케이스 입력 | `...Command` / `...Query` | `RecommendCommand` |
| `application.dto` | 유스케이스 출력 | `...Info` / `...View` | `DwellingInfo`, `RegionCodeView`, `SupportPolicyView` |
| `infrastructure.persistence` | JPA 매핑 클래스 | `...JpaEntity` | `DwellingJpaEntity`, `SigunguJpaEntity` |
| `infrastructure.persistence` | Spring Data 인터페이스 | `...JpaRepository` | `DwellingJpaRepository` |
| `infrastructure.persistence` | 포트 구현 | `...RepositoryAdapter` | `DwellingRepositoryAdapter` |
| `infrastructure.persistence` | 도메인↔JPA 변환 | `...JpaMapper` | `DwellingJpaMapper` |
| `infrastructure.persistence.projection` | JPQL 프로젝션 대상 | `...Row` | `RegionCodeRow`, `IndustryCountRow` |
| `infrastructure.cache` | 캐시/저장소 포트 구현 | `...RedisAdapter` | `DwellingScoreRedisAdapter`, `SupportPolicyRedisAdapter` |
| `infrastructure.external` | 외부 API 포트 구현 | `...ApiAdapter` | `MolitAptRentApiAdapter`, `YouthCenterApiAdapter` |
| `infrastructure.batch` | 배치 설정 / 실행기 | `...BatchConfig` / `...BatchRunner` | `DwellingBatchConfig`, `DwellingBatchRunner` |
| `infrastructure.batch.dto` | CSV 행 / Upsert 파라미터 | `...CsvRow` / `...UpsertRow` | `SidoCsvRow`, `DwellingUpsertRow` |
| `infrastructure.scheduler` | 스케줄러 | `...Scheduler` | `SupportPolicyRefreshScheduler` |
| `presentation` | 컨트롤러 | `...Controller` | `RecommendController`, `DetailController` |
| `presentation.dto` | 요청 / 응답 | `...Request` / `...Response` | `CodeResponse`, `DetailResponse` |
| `global.config` | Spring 설정 / 프로퍼티 | `...Config` / `...Properties` | `RedisConfig`, `YouthCenterProperties` |
| `global.security` | 서블릿 필터 / 지원 서비스 | `...Filter` / `...Service` | `ApiRateLimitFilter`, `ApiRateLimitService` |
| `global.metrics` | 계측기(Micrometer) | `...Metrics` | `CacheMetrics`, `ExternalApiMetrics`, `CallBudgetMetrics` |
| `global.util` | 무상태 기술 유틸 | `...Util` | `BatchTextUtil`, `MapperUtil` |

### 2.1 접미사가 알려주는 것

- **`Service`는 application에만 쓴다.** 도메인 규칙 객체는 `Policy`다. 이 구분이 "지금 도메인 규칙을 쓰는지, 오케스트레이션을 쓰는지"를 이름만으로 드러낸다. (`global.security`의 `ApiRateLimitService`는 컨텍스트 밖의 기술 컴포넌트라 예외다.)
- **`Repository`는 두 개가 있다.** `domain.port.XxxRepository`(인터페이스, 도메인 언어)와 `infrastructure.persistence.XxxJpaRepository`(Spring Data). 헷갈리면 import 경로를 본다.
- **out-port는 기본이 `domain.port`다.** `application.port.out`은 포트 시그니처가 `application/dto`를 요구할 때만 쓰는 예외다 → architecture-conventions §3.2
- **`...UseCase` 인터페이스와 `application.port.in` 패키지는 쓰지 않는다.** 컨텍스트의 공개 진입점은 application `Service` 클래스 자체다 → architecture-conventions §3.3. 폐기한 것은 in-port뿐이고 out-port는 그대로 인터페이스다.
- **`Entity`라는 이름을 도메인에 쓰지 않는다.** DDD의 Entity는 개념이지 접미사가 아니다. JPA 매핑 클래스만 `JpaEntity`를 붙인다.
- **`DTO` 접미사를 쓰지 않는다.** 역할에 따라 `Command`/`Info`/`View`/`Request`/`Response`/`Row`로 나눈다(§4).

### 2.2 메서드 명명

| 계층 | 규칙 | 예시 |
|---|---|---|
| `domain.model` | 도메인 행위를 서술. getter 나열 금지 | `scoreFor(type, budget)`, `isAffordable(budget)`, `totalCount()` |
| `domain.port` | 저장소 언어 | `findBy(SigunguCode)`, `findAll()`, `save(...)`, `existsBy(...)` |
| `application` | 유스케이스 이름 | `getDwellingInfo(...)`, `recommend(...)`, `refreshAll()` |
| `infrastructure` | 기술 동작 | `toDomain(...)`, `toJpaEntity(...)`, `fetchMonth(...)` |

- 값 객체의 진입점은 정적 팩토리 `of(...)`, 저장소 복원은 `reconstitute(...)`.
- 검증 후 예외를 던지는 메서드는 `...OrThrow`. 다만 **대부분의 검증은 값 객체 생성자로 흡수**되므로 이런 메서드는 드문 것이 정상이다.
- 상수는 `private static final` + SCREAMING_SNAKE.

### 2.3 유비쿼터스 언어

코드에 쓰는 용어는 팀이 대화에서 쓰는 용어와 같아야 한다. 아래 표를 정본으로 삼는다.

| 도메인 용어 | 코드 | 쓰지 않을 표현 |
|---|---|---|
| 시군구 코드 | `SigunguCode` | `code`, `region`, `zipCd`(외부 API 용어) |
| 지역 추천 점수 | `Score` | `point`, `rate` |
| 전월세 시세 | `DwellingMarket` | `Dwelling`(모호), `Rent` |
| 지원정책 | `SupportPolicy` | `youthPolicy`, `plcy`(외부 API 용어) |
| 일자리 수 | `JobCount` | `employment` |
| 인프라 대분류 | `Major` | `category`, `level` |
| 사용자가 고른 인프라 대분류 | `infraChoice` (비트마스크 0~15) | `infraImportance`, `weight` |
| 사용자가 고른 지원정책 태그 | `supportChoice` (비트마스크 0~15) | `tagList` |

- **선택 항목은 비트마스크 정수**로 표현한다(`infraChoice`, `supportChoice`). 0~15 범위라 캐시 키가 유한하다 → redis-conventions §4
- **외부 API의 어휘(`plcyNm`, `aplyUrlAddr`, `LAWD_CD`, `zipCd`)는 `infrastructure/external` 안에서만 존재한다.** 어댑터 경계를 넘어오면 도메인 용어로 번역한다.

### 2.4 API 경로

- 공개 API는 전부 `/api` 하위. `SecurityConfig`가 `/api/**` permitAll, 나머지 authenticated.
- 경로·쿼리 파라미터는 camelCase (`/recommend`, `sigunguCode`, `midJobCode`, `infraChoice`).
- **현재 CORS는 GET/OPTIONS만 허용**한다. 다른 메서드를 열려면 `SecurityConfig`를 함께 바꾼다.
- `ApiRateLimitFilter`(`global/security`)는 **현재 비활성(미등록) 상태**다. `SecurityConfig`가 이 필터를 등록하지 않으므로 `/api/**` 요청에 레이트리밋이 걸리지 않는다. 클래스는 향후 재활성 가능성 때문에 남겨 둔다.

---

## 3. 예외 설계

### 3.1 구조

```
global/exception/
├── ErrorCode.java                  enum — 코드 문자열만. HttpStatus 없음 ★
├── DomainException.java            RuntimeException + ErrorCode
└── handler/
    ├── GlobalExceptionHandler.java @RestControllerAdvice
    ├── ErrorCodeHttpMapper.java    ErrorCode → HttpStatus 매핑 ★
    └── ErrorResponse.java          { code, message }
```

> ★ **`ErrorCode`는 도메인이 던지는 개념이므로 HTTP를 알면 안 된다.** `HttpStatus`를 필드로 갖지 않는다.
> 상태코드 매핑은 web adapter의 관심사이며 `ErrorCodeHttpMapper`에만 존재한다.

```java
// global/exception/ErrorCode.java — 프레임워크 의존 없음
public enum ErrorCode {
    // address
    ADDRESS_CODE_NOT_FOUND,
    // job
    JOB_CODE_NOT_FOUND,
    // infra
    INDUSTRY_CODE_NOT_FOUND,
    // dwelling
    PRICE_AMOUNT_NOT_VALID,
    NOT_FOUND_YEARMONTH,
    // global domain
    SCORE_OUT_OF_RANGE,
    // validation
    VALIDATION_FAILED, BIND_FAILED, MALFORMED_JSON,
    METHOD_NOT_ALLOWED, UNSUPPORTED_MEDIA_TYPE,
    // openai
    OPENAI_SERVER_ERROR, OPENAI_TOKEN_EXPIRED,
    ;
    public String code() { return name(); }   // 코드 문자열 == enum 이름
}

// global/exception/DomainException.java
@Getter
public class DomainException extends RuntimeException {
    private final ErrorCode errorCode;
    public DomainException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}

// global/exception/handler/ErrorCodeHttpMapper.java — HTTP는 여기서만
final class ErrorCodeHttpMapper {
    private static final Map<ErrorCode, HttpStatus> STATUS = new EnumMap<>(ErrorCode.class);
    static {
        STATUS.put(ErrorCode.ADDRESS_CODE_NOT_FOUND, HttpStatus.NOT_FOUND);
        // ...
    }
    static HttpStatus of(ErrorCode code) {
        return STATUS.getOrDefault(code, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
```

- `ErrorCode`는 **도메인 주석 블록(`// address`, `// job` …)** 으로 묶어 관리한다. 새 코드는 해당 블록 안에 넣는다.

### 3.2 규칙

1. **도메인 규칙 위반은 `DomainException`.** 다른 런타임 예외를 직접 던지지 않는다.
2. **검증은 가능한 한 값 객체 생성자에서.**
   ```java
   public record SigunguCode(String value) {
       public SigunguCode {
           if (value == null || value.length() != 5)
               throw new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "유효하지 않은 시군구 코드");
       }
   }
   ```
   → 형식은 값 객체가, **존재 여부는 포트 조회**가 검증한다(`repository.existsBy(code)`).
3. **어느 계층에서 던지는가**
   - `domain` — 불변식/규칙 위반. 대부분 여기.
   - `application` — 유스케이스 전제 위반(참조 대상 부재 등)
   - `infrastructure` — 기술 예외를 잡아 **도메인 예외로 번역**하거나, 흡수해서 빈 결과 반환
   - `presentation` — 던지지 않는다. 형식 검증은 Bean Validation에 맡긴다
4. **try/catch를 컨트롤러·유스케이스에 두지 않는다.** 전역 핸들러가 처리한다.
5. `ErrorCode` 추가 시 **`ErrorCodeHttpMapper`에도 반드시 추가**한다. 매핑을 빠뜨리면 500이 된다.
6. **클라이언트에 내부 예외 메시지를 노출하지 않는다.** fallback은 로그만 남기고 고정 문구를 반환한다.

### 3.3 응답 매핑표

| 상황 | ErrorCode | HTTP |
|---|---|---|
| 코드 형식 오류 / 미존재 | `ADDRESS_CODE_NOT_FOUND`, `JOB_CODE_NOT_FOUND`, `INDUSTRY_CODE_NOT_FOUND`, `NOT_FOUND_YEARMONTH` | 404 |
| 값 객체 불변식 위반 | `PRICE_AMOUNT_NOT_VALID`, `SCORE_OUT_OF_RANGE` | 400 |
| `@Valid` 실패 | `VALIDATION_FAILED` | 400 |
| 파라미터 누락/타입 불일치 | `BIND_FAILED` | 400 |
| JSON 파싱 실패 | `MALFORMED_JSON` | 400 |
| 지원하지 않는 메서드/타입 | `METHOD_NOT_ALLOWED` / `UNSUPPORTED_MEDIA_TYPE` | 405 / 415 |
| OpenAI 호출 실패 | `OPENAI_SERVER_ERROR` | 500 |
| OpenAI 쿼터/토큰 소진 | `OPENAI_TOKEN_EXPIRED` | 429 |
| 그 외 | (fallback) | 500 |

- `GlobalExceptionHandler`는 `ResponseEntityExceptionHandler`를 상속하고, Spring MVC 표준 예외는 **새 핸들러를 만들지 말고 부모 메서드를 `@Override`** 한다.
- **fallback도 `ErrorResponse`를 반환한다.**

---

## 4. DTO 3분리

같은 데이터라도 계층마다 다른 타입을 쓴다. **한 클래스를 3계층이 공유하지 않는다.**

| 종류 | 위치 | 목적 | Lombok |
|---|---|---|---|
| **도메인 모델** | `domain/model` | 비즈니스 규칙과 불변식 | `@Getter`만 (또는 `record`) |
| **애플리케이션 DTO** | `application/dto` | 유스케이스 입출력. 도메인 타입을 담아도 됨 | `record` 우선 |
| **표현 DTO** | `presentation/dto` | HTTP 요청/응답 계약(JSON 필드명) | `record` + Bean Validation |
| **기술 DTO** | `infrastructure/**` | CSV 행, 외부 API 응답, Upsert 파라미터, JPQL 프로젝션 Row | 필요에 따라 |

```java
// application/dto — 유스케이스 입력. 경계에서 값 객체로 승격해 채운다
public record RecommendCommand(Integer supportChoice, JobCode jobCode,
                               DwellingType dwellingType, Money budget,
                               Integer infraChoice) {}

// presentation — 원시 타입으로 받고 즉시 값 객체/Command로 승격
@GetMapping("/recommend")
public ResponseEntity<RecommendAggregateResponse> recommend(
        @RequestParam @NotNull @Min(0) @Max(15) Integer supportChoice,
        @RequestParam(required = false) String midJobCode,
        @RequestParam @NotNull Integer price, ...) {

    JobCode jobCode = (midJobCode == null) ? null : JobCode.of(midJobCode);
    RecommendCommand command = new RecommendCommand(supportChoice, jobCode, dwellingType,
                                                    Money.of(price), infraChoice);
    ...
}
```

### 4.1 규칙

1. **원시 타입은 경계에서만.** `presentation`이 `String`/`Integer`로 받고 즉시 값 객체로 승격한다. 그 안쪽은 전부 값 객체다.
2. **도메인 모델을 JSON으로 직렬화하지 않는다.** 응답은 `presentation/dto`로 만든다.
3. **`record`를 기본으로 쓴다.** 가변이 꼭 필요할 때만 클래스 + `@Getter @Setter`.
   - 예외: **Redis/Jackson 역직렬화 대상**은 기본 생성자 + setter가 필요하다. 이 타입들은 `infrastructure/cache` 안에 두고 도메인으로 새어나가지 않게 한다 → redis-conventions §3
4. **`@Data` 금지.** 도메인 모델에는 `@EqualsAndHashCode`/`@ToString`도 붙이지 않는다(값 객체는 `record`가 처리).
5. JSON 필드명은 camelCase. `presentation/dto`의 필드명이 곧 API 계약이므로 **변경 시 프론트와 합의**한다.
6. **성공 응답에 공통 봉투(`ApiResponse<T>`)를 쓰지 않는다.** 컨트롤러는 `ResponseEntity<XxxResponse>`를 그대로 반환한다. 오류만 `ErrorResponse` 형식이다.

> ⚠️ **알려진 예외**: `RecommendAggregateResponse.items`의 타입이 `presentation/dto`가 아니라
> `application/dto`(`RegionRecommendation`)다. 프론트 계약(JSON 필드명)을 그대로 유지하기 위한
> 의도적 타협이며 클래스 주석에 근거가 적혀 있다. **새 응답에서 이 방식을 따라하지 않는다.**

---

## 5. 로깅 기준

### 5.1 계층별 정책

| 계층 | 로깅 |
|---|---|
| `domain` | **금지.** 순수성 유지. 규칙 위반은 예외로 표현한다 |
| `application` | 유스케이스 단위 결과·건수. 실패는 예외로 올린다 |
| `infrastructure` | 기술 실패의 1차 기록(외부 API, 배치, 캐시). **여기가 로그의 주 무대** |
| `presentation` | 전역 핸들러의 오류 기록만 |

### 5.2 작성 규칙

```java
@Slf4j                    // Lombok. 수동 LoggerFactory 선언 금지
public class MolitAptRentApiAdapter { ... }
```

| 레벨 | 언제 | 예시 |
|---|---|---|
| `error` | 작업이 실패로 끝남. 예외 객체를 마지막 인자로 | `log.error("[MOLIT] fetch 실패 sigungu={}, ym={}", code, ym, e)` |
| `warn` | 진행은 하지만 데이터가 누락/비정상 | `log.warn("인프라 데이터 없음 sigungu={}", code)` |
| `info` | 배치·스케줄러의 시작/완료/건수 | `log.info("[SupportRefresh] 완료 count={}, elapsed={}ms", n, ms)` |
| `debug` | 상세 추적 | 루프 내부 진행 상황 |

1. **문자열 연결(`+`) 금지, `{}` 플레이스홀더 사용.**
2. 스케줄러/배치/캐시 로그에는 `[식별자]` 접두어 + 처리 건수/소요시간.
3. **비밀값 금지** — serviceKey, apiKey, DB 접속정보. 외부 API URL 전체를 로그로 찍지 않는다(쿼리스트링에 키가 들어 있다).
4. `System.out.println` / `printStackTrace()` 금지.
5. 루프 안 `info`는 건수가 크면 `debug`로 낮춘다.

---

## 6. 공통 유틸 (`global`)

`global/util`은 **도메인 지식이 없는 기술 유틸**만 담는다. 계산 규칙이 도메인 지식이면 `domain/service`의 Policy로 옮긴다.

| 클래스 | 성격 | 위치 |
|---|---|---|
| `BatchTextUtil` (`normalize`, `isBlank`, `digitsOnly`, `addLeadingZero`) | 외부 데이터 정제 = 기술 | `global/util` |
| `MapperUtil` (`text`, `num` on `JsonNode`) | JSON 파싱 = 기술 | `global/util` |
| `BatchGuard` | Spring Batch 재실행 제어 | `global/batch` |

- 유틸은 `public static` 메서드만 갖는 무상태 클래스(Spring 빈 아님).
- **domain 계층에서 `global/util`을 import하지 않는다.** 도메인이 필요로 하는 계산은 도메인 안에 둔다. (`global/domain/model`의 값 객체는 예외 — 공유 커널이다.)
- 평균·중앙값 같은 **도메인 계산은 `domain/service`** 에 둔다(예: `dwelling`의 `RentStatCalculator`). 유틸 클래스로 빼지 않는다.

---

## 7. 체크리스트

- [ ] 패키지가 `SDD.smash.domain.<context>.<layer>` 또는 `SDD.smash.global.<area>`인가 (전부 소문자)
- [ ] 클래스 접미사가 §2 카탈로그와 일치하는가 (`Service`는 application에만, 도메인 규칙은 `Policy`)
- [ ] 외부 API 어휘(`plcyNm`, `LAWD_CD`)가 어댑터 밖으로 새지 않았는가
- [ ] 예외가 `DomainException` + `ErrorCode`인가, `ErrorCode`에 `HttpStatus`가 없는가
- [ ] 새 `ErrorCode`를 `ErrorCodeHttpMapper`에도 추가했는가
- [ ] 형식 검증이 값 객체 생성자에 있는가 (서비스에 흩어져 있지 않은가)
- [ ] DTO가 계층별로 분리됐는가 (도메인 모델을 JSON으로 노출하지 않았는가)
- [ ] 원시 타입이 presentation 경계 안쪽으로 넘어가지 않았는가
- [ ] `domain`에 로그가 없는가, 나머지는 `@Slf4j` + `{}` + 비밀값 미노출인가
- [ ] `.\gradlew.bat test` 통과
