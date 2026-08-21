---
name: architecture-conventions
description: smash(ProvinceHow)의 DDD 헥사고날(포트&어댑터) 아키텍처를 정의한다. 바운디드 컨텍스트 경계, domain/application/infrastructure/presentation 4계층과 의존 방향, Aggregate 경계와 ID 참조 규칙, 인바운드/아웃바운드 포트 설계, 컨텍스트 간 통신, 패키지 배치 지도와 구조 확장 규칙을 담는다. 새 클래스를 어느 계층에 둘지 정하거나, 포트를 만들거나, 도메인 경계를 판단하거나, 새 컨텍스트·어댑터를 추가할 때 사용한다. 명명·도메인 예외는 global-conventions, 유스케이스·도메인 모델 작성은 backend-conventions, JPA 엔티티 분리는 persistence-conventions, 캐시 포트는 redis-conventions를 따른다.
---

# architecture-conventions (DDD / 헥사고날)

이 프로젝트는 **DDD 헥사고날(포트 & 어댑터)** 구조다. 레이어드 구조에서의 전환은 완료됐다.
이 문서는 **현재 구조**를 정의한다. 새 코드는 예외 없이 이 구조를 따른다.

---

## 1. 핵심 원칙

```
                        ┌─────────────────────────────┐
      inbound           │                             │           outbound
      adapter           │        application          │           adapter
                        │   (유스케이스 오케스트레이션)   │
  HTTP Controller ──▶ ┌─┤                             ├─┐ ──▶ JPA RepositoryAdapter
  Batch Job       ──▶ │ │      ┌───────────────┐      │ │ ──▶ Redis CacheAdapter
  Scheduler       ──▶ └─┤      │    domain     │      ├─┘ ──▶ HTTP External Adapter
                        │      │ model/service │      │
                        │      │     port ◀────┼──────┼──── 구현(의존 역전)
                        │      └───────────────┘      │
                        └─────────────────────────────┘
```

1. **domain은 아무것도 의존하지 않는다.** Spring, JPA, Redis, Jackson, HTTP — 전부 금지. 순수 Java 17만.
2. **바깥은 안쪽만 안다.** 의존은 항상 `presentation/infrastructure → application → domain` 방향.
3. **역방향이 필요하면 포트로 뒤집는다.** domain이 저장소를 필요로 하면 `domain/port`에 인터페이스를 두고 `infrastructure`가 구현한다.
4. **Aggregate 밖은 ID로만 참조한다.** 다른 Aggregate를 객체로 물지 않는다.
5. **기술 용어가 domain에 새어들지 않는다.** `Entity`, `Repository`(구현), `RedisTemplate`, `ResponseEntity`가 domain 패키지 안에 있으면 위반.

---

## 2. 바운디드 컨텍스트

| 컨텍스트 | 책임 | 유형 | Aggregate Root |
|---|---|---|---|
| `address` | 행정구역 코드 체계, 인구. **다른 모든 컨텍스트가 참조하는 식별자의 원천** | Shared Kernel 제공자 | `Sido`, `Sigungu` |
| `job` | 지역별 일자리 수, 직종 분류, 일자리 적합도 | Core | `JobCount`, `JobCategory` |
| `dwelling` | 지역별 전월세 시세, 주거비 적합도 | Core | `DwellingMarket` |
| `infra` | 지역별 생활 인프라 규모, 인프라 충실도 | Core | `RegionInfra`, `Industry` |
| `support` | 청년 지원정책 (Redis가 정본, RDB 없음) | Supporting | `SupportPolicy` |
| `recommendation` | 지역 추천·상세 조회. 여러 컨텍스트를 조합하는 **공개 API 계약** | Core (조합) | 없음 (조합 전용) |

컨텍스트에 속하지 않는 공유 커널·기술 기반은 **`global`** 이다(§3). `global`은 컨텍스트가 아니다.

### 2.1 컨텍스트 맵

```
                       ┌──────────────────┐
                       │  recommendation  │   ← 공개 API (Customer)
                       └────────┬─────────┘
              ┌─────────┬───────┼────────┬─────────┐
              ▼         ▼       ▼        ▼         ▼
           ┌─────┐  ┌──────┐ ┌──────┐ ┌──────┐ ┌────────┐
           │ job │  │dwelling│ infra│ │support│ │address │
           └──┬──┘  └───┬──┘ └──┬───┘ └───┬──┘ └───┬────┘
              └─────────┴───────┴─────────┴────────┘
                          SigunguCode (Shared Kernel)
```

- **`recommendation` → 각 컨텍스트**: Customer/Supplier. `recommendation`은 각 컨텍스트의 **application `Service`** 만 호출한다(§3.3).
- **모든 컨텍스트 → 공유 커널**: `SigunguCode`/`SidoCode` **값 객체**만 공유한다(`global.domain.model`). `address`의 Aggregate(`Sigungu` 객체)나 Repository를 직접 쓰지 않는다.
- **컨텍스트 간 역방향 의존 금지.** `job`이 `dwelling`을 알면 안 된다.

### 2.2 새 컨텍스트를 만드는 기준

세 가지를 모두 만족할 때만 새 컨텍스트를 만든다. 아니면 기존 컨텍스트의 Aggregate로 넣는다.
1. 고유한 유비쿼터스 언어(용어 체계)를 갖는다
2. 독립적인 데이터 소스와 수명주기를 갖는다
3. 다른 컨텍스트 없이도 의미가 성립한다

---

## 3. 디렉터리 구조

패키지는 **전부 소문자**다. 최상위는 두 갈래다 — 컨텍스트는 전부 `domain/` 아래, 공통 기반은 `global/` 아래.

```
SDD/smash/
├── SmashApplication.java
│
├── global/                     ★ 컨텍스트에 속하지 않는 공통 기반
│   ├── domain/model/           SigunguCode, SidoCode, Score, Money   ← 공유 커널 값 객체
│   ├── exception/              DomainException, ErrorCode
│   │   └── handler/            GlobalExceptionHandler, ErrorCodeHttpMapper, ErrorResponse
│   ├── config/                 DataDBConfig, MetaDBConfig, RedisConfig, SecurityConfig,
│   │                           MapperConfig, SeedProperties, YouthCenter*
│   ├── security/               ApiRateLimitFilter, ApiRateLimitService
│   ├── batch/                  BatchGuard
│   ├── metrics/                CacheMetrics, ExternalApiMetrics, CallBudgetMetrics
│   └── util/                   BatchTextUtil, MapperUtil
│
└── domain/                     ★ 바운디드 컨텍스트는 전부 이 아래에 있다
    └── <context>/              address | job | dwelling | infra | support | recommendation
        ├── domain/             ★ 순수 Java. 프레임워크 의존 0
        │   ├── model/          Aggregate Root, Entity, 값 객체, 도메인 enum
        │   ├── service/        도메인 서비스 / 정책(Policy) — 여러 Aggregate에 걸친 규칙
        │   └── port/           ★ out-port 인터페이스 (Repository, Provider, Cache)
        │
        ├── application/        유스케이스. domain만 의존
        │   ├── <Xxx>QueryService  유스케이스 (@Service, @Transactional) ← 컨텍스트의 공개 진입점
        │   ├── port/out/       out-port 인터페이스 — ★ 예외적으로만 (§3.2)
        │   └── dto/            유스케이스 입출력 DTO
        │
        ├── infrastructure/     모든 기술 상세
        │   ├── persistence/    XxxJpaEntity, XxxJpaRepository, XxxRepositoryAdapter, XxxJpaMapper
        │   │   └── projection/ XxxRow (JPQL 생성자 프로젝션 대상 기술 DTO)
        │   ├── cache/          XxxRedisAdapter
        │   ├── external/       XxxApiAdapter (외부 HTTP) [+ dto/]
        │   ├── batch/          Spring Batch Job/Step/Reader/Processor/Writer [+ dto/, runner/]
        │   └── scheduler/      @Scheduled 컴포넌트
        │
        └── presentation/       HTTP inbound adapter
            ├── XxxController
            └── dto/            XxxRequest, XxxResponse
```

### 3.1 `domain`이라는 이름이 두 번 나온다

`SDD.smash.domain.dwelling.domain.model` 처럼 `domain`이 **두 번** 등장한다.
- 첫 번째 `domain`은 **컨텍스트 묶음 디렉터리**다. 아키텍처적 의미가 없다.
- 두 번째 `domain`은 **헥사고날의 domain 계층**이다. §4의 규칙이 걸리는 것은 이쪽이다.

“domain 패키지에 Spring import 금지” 같은 규칙은 **두 번째 `domain`(= 계층)** 을 가리킨다.
`SDD.smash.domain.` 접두어만 보고 계층을 판단하지 않는다.

**규칙**
- 필요 없는 디렉터리는 만들지 않는다(빈 패키지 금지). `support`는 RDB가 없어 `persistence/`가 없고 `cache/`가 그 역할을 한다.
- `presentation`은 컨트롤러가 있는 컨텍스트에만 둔다. 현재 `recommendation`뿐이다.
- `global/config`의 Spring 설정 클래스들은 컨텍스트에 속하지 않는 **애플리케이션 부트스트랩**이다.

### 3.2 out-port는 기본이 `domain/port`다 — `application/port/out`은 예외

**기본 규칙**: out-port는 `domain/port`에 둔다(§4.2). 도메인이 필요로 하는 것을 도메인 언어로
선언하고 `infrastructure`가 구현한다. 저장소·캐시·외부 공급 포트는 전부 여기다.

**예외**: 포트 시그니처가 **`application/dto`를 입력으로 요구**하면 `application/port/out`에 둔다.
`domain/port`에 두면 `domain → application` **역방향 의존**이 생겨 §4 표를 더 크게 위반하기 때문이다.

이 예외는 아래 두 조건을 **모두** 만족할 때만 인정한다.
1. 포트의 입출력이 도메인 개념이 아니다 — 도메인 규칙이 관여하지 않는 부가 기능이다
2. 그 입력을 도메인 타입으로 새로 만드는 것이 **존재하지 않는 도메인 개념의 발명**이 된다

| 현재 적용 사례 | 위치 | 근거 |
|---|---|---|
| `RegionPickProvider`<br>`RegionSummaryProvider` | `domain/recommendation/application/port/out/` | 입력이 `RegionRecommendation`/`RegionDetailInfo`(여러 컨텍스트 조합 결과 = application DTO). AI 요약·추천은 도메인 규칙이 아니라 응답을 꾸미는 부가 기능이고, `recommendation`은 Aggregate가 없는 조합 전용 컨텍스트다(§2 표) |

- **이 예외를 늘리기 전에 먼저 "도메인 개념이 정말 없는가"를 따진다.** 도메인 규칙이 있다면
  값 객체·Aggregate를 만들고 포트를 `domain/port`로 되돌리는 것이 맞다.
- `application/port/out`의 포트는 **`presentation`이 직접 호출해도 된다.** 표현 계층의 선택적
  부가 기능(예: `aiUse=true`일 때만 AI 호출)을 유스케이스 계약에 억지로 밀어넣지 않기 위한 것이다.
  의존 방향은 `presentation → application`이므로 §4 표를 지킨다.

### 3.3 in-port를 두지 않는다 — 컨텍스트의 공개 진입점은 application `Service`다

**`application/port/in` 패키지와 `...UseCase` 인터페이스를 만들지 않는다.**
컨트롤러와 다른 컨텍스트의 application은 **대상 컨텍스트의 application `Service` 클래스를
직접 주입해 호출**한다.

```java
// ✅ recommendation 의 유스케이스가 다른 컨텍스트를 호출하는 방식
@Service
@RequiredArgsConstructor
public class RecommendRegionService {

    private final JobScoreService jobScoreService;          // job 컨텍스트의 application Service
    private final DwellingQueryService dwellingQueryService; // dwelling 컨텍스트의 application Service
    ...
}
```

**근거**: 구현이 하나뿐인 in-port 인터페이스는 실질적 다형성 없이 파일 수와 간접 참조만
늘린다. 이 프로젝트는 컨텍스트별 구현이 하나이고 교체 계획도 없어 그 비용을 받지 않는다.

**그래도 지키는 것** — 이 완화는 **application 계층 사이에만** 적용된다.
- 다른 컨텍스트의 **`domain` 모델 / `domain/port` / `infrastructure`** 직접 참조는 여전히 금지다.
- 즉 컨텍스트의 경계는 그대로이고, 그 경계를 넘는 **문(門)이 인터페이스에서 `Service` 클래스로
  바뀌었을 뿐**이다.
- `Service`의 `public` 메서드가 곧 컨텍스트의 공개 계약이다. 다른 컨텍스트가 쓸 일이 없는
  메서드는 `public`으로 열지 않는다.

> **out-port(`domain/port`, `application/port/out`)는 그대로 인터페이스다.** 폐기한 것은
> in-port뿐이다. out-port는 의존 역전(§1-3)이 목적이라 인터페이스가 반드시 필요하다.

---

## 4. 계층별 규칙과 의존 방향

| 계층 | 의존 가능 | 절대 금지 | 프레임워크 |
|---|---|---|---|
| `domain/model` | 같은 컨텍스트 domain, `global.domain.model` | 다른 컨텍스트, application, infrastructure, presentation | **없음** (Lombok `@Getter` 정도만 허용) |
| `domain/service` | 같은 컨텍스트 domain, `global.domain.model` | 위와 동일 + port 구현체 | 없음 |
| `domain/port` | 같은 컨텍스트 domain 모델 | 기술 타입(`Page`, `Optional`은 허용) | 없음 |
| `application` | 자기 domain 전체, **다른 컨텍스트의 application `Service`** | 다른 컨텍스트의 domain/infrastructure, `HttpServletRequest`, `RedisTemplate`, JPA 타입 | `@Service`, `@Transactional`만 |
| `infrastructure` | 자기 domain(port 구현), 자기 application(`port/out` 구현 포함) | 다른 컨텍스트의 infrastructure, **presentation** | 전부 허용 |
| `presentation` | 자기/타 컨텍스트의 `application` (`Service` · `port/out` 둘 다) | domain 모델 직접 노출, infrastructure, Repository | Spring Web |

`global.exception`(`DomainException`, `ErrorCode`)은 프레임워크 의존이 없으므로 **모든 계층에서 쓸 수 있다.**
`global.util`은 기술 유틸이므로 **domain에서 import하지 않는다** → global-conventions §6

> ⚠️ **`infrastructure → presentation` 금지가 실수하기 쉬운 지점이다.**
> 외부 API 어댑터가 응답 DTO(`presentation/dto`)를 직접 조립하면 이 방향이 생긴다.
> 어댑터는 **외부 호출 결과 자체**를 돌려주고, 응답 조립은 `presentation`이 한다.

### 4.1 domain — 무엇을 담는가

```java
package SDD.smash.domain.dwelling.domain.model;

/** 지역의 전월세 시세 (Aggregate Root) */
public class DwellingMarket {

    private final SigunguCode sigunguCode;   // 다른 Aggregate는 ID(값 객체)로만
    private final Money monthlyAverage;
    private final Money monthlyMedian;
    private final Money jeonseAverage;
    private final Money jeonseMedian;

    /** 사용자 예산과의 적합도를 0~100 점수로 계산한다. */
    public Score scoreFor(DwellingType type, Money budget) {
        Money median = (type == DwellingType.MONTHLY) ? monthlyMedian : jeonseMedian;
        if (median == null) return Score.ZERO;
        return type.scoringRule().apply(median, budget);
    }
}
```

- **불변식(invariant)을 생성자에서 강제**한다. 유효하지 않은 상태의 객체가 만들어지지 않게 한다.
- **비즈니스 규칙은 Aggregate 안에.** 게터만 있는 빈약한 모델(anemic)을 만들지 않는다 → backend-conventions §4
- `@Entity`, `@Column`, `@Id` 같은 JPA 애너테이션을 **domain 모델에 붙이지 않는다.** JPA 매핑은 `infrastructure/persistence`의 별도 클래스가 담당한다 → persistence-conventions §2
- 도메인 로그를 남기지 않는다(순수성 유지). 로깅은 application 이상에서.

### 4.2 domain/port — out-port

```java
package SDD.smash.domain.dwelling.domain.port;

public interface DwellingMarketRepository {
    Optional<DwellingMarket> findBy(SigunguCode code);
    List<DwellingMarket> findAll();
}

public interface DwellingScoreCache {                    // 캐시도 포트다
    Optional<Map<SigunguCode, Score>> find(DwellingScoreKey key);
    void put(DwellingScoreKey key, Map<SigunguCode, Score> scores);
}

public interface RentRecordProvider {                    // 외부 API도 포트다
    List<RentRecord> fetch(SigunguCode code, YearMonth month);
}
```

**규칙**
- 이름에 기술을 넣지 않는다. `DwellingJpaRepository`(❌ 이건 infrastructure의 이름), `RedisScoreCache`(❌) → `DwellingMarketRepository`, `DwellingScoreCache`(✅)
- 시그니처에 **도메인 타입만** 쓴다. `String sigunguCode`(❌) → `SigunguCode code`(✅)
- 반환 타입은 도메인 모델 또는 값 객체. JPA 엔티티·`JsonNode`·`Map<String,Object>` 금지.
- **포트는 도메인이 필요로 하는 만큼만** 정의한다. `JpaRepository`의 20개 메서드를 그대로 노출하지 않는다(Interface Segregation).

### 4.3 application — 유스케이스

```java
package SDD.smash.domain.dwelling.application;

@Service
@RequiredArgsConstructor
public class DwellingQueryService {                                   // 컨텍스트의 공개 진입점

    private final DwellingMarketRepository dwellingMarketRepository;  // out-port 주입
    private final DwellingScoreCache dwellingScoreCache;

    @Override
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public DwellingInfo getDwellingInfo(SigunguCode code) {
        return dwellingMarketRepository.findBy(code)
                .map(DwellingInfo::from)
                .orElse(null);
    }
}
```

- **오케스트레이션만** 한다: 포트 호출 → 도메인 호출 → 결과 DTO 변환. 비즈니스 규칙을 여기 쓰면 domain으로 내려야 한다는 신호다.
- 주입은 **항상 포트 인터페이스**다. 구현체(`XxxRepositoryAdapter`)를 직접 주입하지 않는다.
- 트랜잭션 경계는 **application의 public 메서드**다 → persistence-conventions §6
- `@Transactional`에는 반드시 `transactionManager = "dataTransactionManager"`를 지정한다(§7).

### 4.4 infrastructure — 어댑터

```java
package SDD.smash.domain.dwelling.infrastructure.persistence;

@Repository
@RequiredArgsConstructor
public class DwellingRepositoryAdapter implements DwellingMarketRepository {   // out-port 구현

    private final DwellingJpaRepository jpaRepository;   // Spring Data 인터페이스
    private final DwellingJpaMapper mapper;

    @Override
    public Optional<DwellingMarket> findBy(SigunguCode code) {
        return jpaRepository.findBySigunguCode(code.value()).map(mapper::toDomain);
    }
}
```

- 어댑터는 **포트 인터페이스를 구현**하고, 도메인 타입 ↔ 기술 타입 변환을 책임진다.
- 기술 예외를 도메인/애플리케이션으로 그대로 흘리지 않는다. 필요하면 도메인 예외로 번역한다.
- 인바운드 어댑터(batch/scheduler)는 **application의 유스케이스를 호출**한다. Repository를 직접 부르지 않는다 — 단, 대량 적재 배치는 성능상 예외를 허용한다(§6.2).

### 4.5 presentation

```java
package SDD.smash.domain.recommendation.presentation;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class RecommendController {

    private final RecommendRegionService recommendRegionService;   // 자기 컨텍스트의 application Service
    private final RegionPickProvider regionPickProvider;           // application/port/out (§3.2)

    @GetMapping("/recommend")
    public ResponseEntity<RecommendAggregateResponse> recommend(...) { ... }
}
```

- **도메인 모델을 응답으로 노출하지 않는다.** `presentation/dto`의 Response DTO로 변환한다.
- HTTP 관심사(상태코드, 헤더, 검증 메시지)는 여기서만 다룬다.

---

## 5. Aggregate 설계

### 5.1 Aggregate 목록과 경계

| 컨텍스트 | Aggregate Root | 포함 | 식별자 |
|---|---|---|---|
| `address` | `Sido` | — | `SidoCode` |
| `address` | `Sigungu` | `Population` | `SigunguCode` |
| `job` | `JobCategory` | 대분류-중분류 계층 | `JobCode` |
| `job` | `JobCount` | — | `SigunguCode` + `JobCode` |
| `dwelling` | `DwellingMarket` | 월세/전세 시세 값 객체 | `SigunguCode` |
| `infra` | `Industry` | — | `IndustryCode` |
| `infra` | `RegionInfra` | 업종별 개수 목록 | `SigunguCode` |
| `support` | `SupportPolicy` | — | `SigunguCode` + `SupportTag` |

### 5.2 규칙

1. **하나의 트랜잭션에서 하나의 Aggregate만 변경한다.**
2. **Aggregate 밖은 ID(값 객체)로만 참조한다.** JPA 연관관계로 다른 Aggregate를 물지 않는다.

   ```java
   // ❌ 다른 Aggregate를 객체로 참조
   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "sigungu_code")
   private Sigungu sigungu;

   // ✅ 도메인 모델
   private final SigunguCode sigunguCode;

   // ✅ JPA 엔티티(infrastructure)
   @Column(name = "sigungu_code", length = 5, nullable = false)
   private String sigunguCode;
   ```

   > 파생 점수는 Aggregate의 계산 결과이므로 **별도 테이블이 아니라 도메인 정책(`Policy`)의 산출물**로 다룬다. `@MapsId`로 다른 Aggregate와 PK를 공유하는 점수 테이블을 새로 만들지 않는다 → §5.4
3. **Aggregate는 작게.** 조회 편의를 위해 Aggregate를 키우지 않는다. 여러 Aggregate가 필요한 조회는 application에서 조합하거나 전용 조회 모델(§5.5)을 쓴다.
4. **Aggregate Root를 통해서만 내부에 접근한다.** `RegionInfra.industryCounts()`로 꺼내고, `IndustryCount`를 별도 Repository로 조회하지 않는다.

### 5.3 값 객체 (Value Object)

공유 커널(`global.domain.model`)과 컨텍스트 로컬로 나뉜다.

```java
// global/domain/model — 모든 컨텍스트가 공유
public record SigunguCode(String value) {
    public SigunguCode {
        if (value == null || value.length() != 5)
            throw new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "유효하지 않은 시군구 코드");
    }
}

public record Score(int value) {
    public static final Score ZERO = new Score(0);
    public Score {
        if (value < 0 || value > 100)
            throw new DomainException(ErrorCode.SCORE_OUT_OF_RANGE, "점수는 0~100 범위여야 합니다.");
    }
    public Score plus(Score other) { return new Score(Math.min(100, value + other.value)); }
}
```

**규칙**
- `record`로 만든다(Java 17). 불변이며 `equals`/`hashCode`가 값 기반이다.
- **compact 생성자에서 불변식을 검증**한다. 유효성 검사를 서비스에 흩뿌리지 않는다.
- `String sigunguCode`를 계층 사이로 넘기지 않는다. 경계(presentation/adapter)에서 값 객체로 바꾼다.
- 공유 커널에 넣는 기준: **2개 이상 컨텍스트가 같은 의미로 쓰고, 정의가 바뀔 일이 거의 없는 것**. 그 외는 컨텍스트 로컬.

| 값 객체 | 위치 |
|---|---|
| `SigunguCode`, `SidoCode` | `global/domain/model` |
| `Score` | `global/domain/model` |
| `Money` (만원 단위) | `global/domain/model` |
| `JobCode` | `domain/job/domain/model` |
| `IndustryCode`, `Major` | `domain/infra/domain/model` |
| `SupportTag` | `domain/support/domain/model` |
| `DwellingType` | `domain/dwelling/domain/model` |

> 인프라·지원정책 선택은 `Major`/`SupportTag`의 **비트마스크 정수(`infraChoice`, `supportChoice`, 0~15)** 로 표현한다. 과거의 `InfraImportance` 등급 개념은 삭제됐으니 되살리지 않는다.

### 5.4 도메인 서비스 / 정책 (Policy)

**한 Aggregate에 담기 애매한 규칙**은 `domain/service`의 정책 객체로 만든다.

```java
package SDD.smash.domain.dwelling.domain.service;

/** 예산과 시세 중앙값의 차이로 주거 적합도를 계산하는 정책 */
public class DwellingScorePolicy {

    private static final Money MONTHLY_STEP = Money.of(10);
    private static final Money JEONSE_STEP  = Money.of(3_000);

    public Score score(DwellingType type, Money median, Money budget) { ... }
}
```

- **Spring 빈이 아니어도 된다.** 상태가 없으면 정적 메서드 또는 `new`로 쓴다. 주입이 필요하면 `@Component`로 등록하되 **포트만 의존**한다.
- 이름은 `<대상>Policy` 또는 `<대상>Calculator`. `Service` 접미사는 application 계층에만 쓴다 → global-conventions §2
- **정책은 순수 함수여야 한다.** 저장소·캐시·시간·랜덤에 의존하면 그건 application 관심사다.

### 5.5 조회 전용 모델 (CQRS-lite)

이 프로젝트는 조회 API가 전부다. 화면용 조합 조회에 Aggregate를 억지로 쓰지 않는다.

- **명령/규칙 경로**: Aggregate + Policy를 통과한다.
- **조회 경로**: `application/dto`의 조회 모델을 **프로젝션으로 직접** 채워도 된다. 이때도 Repository 포트를 거치며, 포트가 조회 모델을 반환한다.
  ```java
  public interface RegionCodeQuery {                      // domain/port
      List<RegionCodeView> findAllRegionCodes();
      Optional<RegionCodeView> findBy(SigunguCode code);
  }
  ```
- 조회 모델에는 **비즈니스 규칙을 넣지 않는다.** 규칙이 필요하면 Aggregate 경로로 돌린다.

---

## 6. 인바운드 어댑터

### 6.1 HTTP (presentation)

- 모든 공개 API는 `/api` 하위. `SecurityConfig`는 `/api/**` permitAll + **CORS GET/OPTIONS만 허용**이다. 다른 메서드를 열려면 설정도 함께 바꾼다.
- 컨트롤러는 **application `Service`** 를 주입한다(§3.3). AI 요약처럼 표현 계층의 선택 기능만 `application/port/out`을 직접 호출한다(§3.2).

### 6.2 배치 (infrastructure/batch)

Seed 배치는 **외부 파일/API → 저장소**로 데이터를 밀어넣는 인바운드 어댑터다.

```
domain/<context>/infrastructure/batch/
├── DwellingBatchConfig.java     Job/Step/Reader/Processor/Writer 빈
├── runner/DwellingBatchRunner   @EventListener(ApplicationReadyEvent) + @Order
└── dto/                         CSV 읽기 DTO, Upsert DTO (기술 DTO)
```

- `spring.batch.job.enabled=false`이며 Runner의 `@Order`가 FK 선후관계를 통제한다.

  | Order | Job | 컨텍스트 | 선행 의존 |
  |---|---|---|---|
  | 1 | Sido | address | — |
  | 2 | Sigungu | address | Sido |
  | 3 | JobCodeTop | job | — |
  | 4 | JobCodeMiddle | job | JobCodeTop |
  | 5 | Population | address | Sigungu |
  | 6 | Industry | infra | — |
  | 7 | Infra | infra | Sigungu, Industry |
  | 8 | JobCount | job | Sigungu, JobCodeMiddle |
  | 9 | Dwelling | dwelling | Sigungu + 외부 API |

  > **코드를 표에 맞추지 말고 표를 코드에 맞춘다** — `@Order` 변경은 실행 순서 변경이다.

- 재실행 방지는 `BatchGuard.alreadyDone(jobName, seedVersion)`(`global/batch`).
- **대량 적재 배치는 Aggregate를 거치지 않아도 된다.** `JdbcBatchItemWriter` + Upsert SQL로 직접 쓴다. 도메인 불변식은 Processor에서 값 객체 생성으로 검증한다.
  ```java
  // Processor에서 값 객체로 검증 → 실패 시 null 반환(skip)
  try { SigunguCode.of(raw); } catch (DomainException e) { return null; }
  ```

### 6.3 스케줄러 (infrastructure/scheduler)

- `@Scheduled` 컴포넌트는 **application 유스케이스를 호출**한다. Redis/외부 API를 직접 다루지 않는다.
  ```java
  @Component
  @RequiredArgsConstructor
  public class SupportPolicyRefreshScheduler {
      private final RefreshSupportPolicyService refreshSupportPolicyService;

      @Scheduled(initialDelay = 0, fixedDelayString = "#{T(java.time.Duration).ofDays(3).toMillis()}")
      public void refresh() { refreshSupportPolicyService.refreshAll(); }
  }
  ```
- 갱신 후 파생 캐시 무효화 책임은 **유스케이스**가 진다 → redis-conventions §5

---

## 7. 기술 인프라 (global/config)

DataSource 2개 구성을 쓴다.
DB는 **Docker 컨테이너 MySQL 1개에 스키마 2개**다(RDS 아님) — 드라이버 `com.mysql.cj.jdbc.Driver`, 호스트는 compose 서비스명 `mysql`.

| DataSource | 스키마 | 용도 | 트랜잭션 매니저 |
|---|---|---|---|
| `dataDBSource` (`@Primary` DataSource) | `smash_data` | 업무 데이터 — 모든 JPA 엔티티 | `dataTransactionManager` (**`@Primary` PlatformTransactionManager**, `JpaTransactionManager`) |
| `batchDataSource` (`@BatchDataSource`) | `smash_meta` | Spring Batch 메타 | `batchTransactionManager` (`DataSourceTransactionManager`, `@Primary` **아님**) |

> ⚠️ **트랜잭션 매니저 이름을 항상 명시한다.** `@Primary`가 지금은 `dataTransactionManager`(JPA)라
> 무수식 `@Transactional`도 우연히 JPA 트랜잭션이 열리지만, **그 우연에 기대지 않는다.**
> `@Primary`가 어디 붙어 있는지는 `DataDBConfig`/`MetaDBConfig`를 고치면 바뀌는 값이고,
> 바뀌는 순간 무수식 `@Transactional`은 조용히 다른 DB의 트랜잭션이 된다(원자성도 `readOnly`도 잃는다).
> **반드시 `@Transactional(transactionManager = "dataTransactionManager", readOnly = true)`** → persistence-conventions §6
>
> 같은 이유로 **배치 Step의 청크 트랜잭션 매니저도 타입 주입이 아니라 `@Qualifier`로 못 박는다**
> (`InfraBatchConfig`가 그 예다). Step이 업무 데이터를 쓰는데 매니저가 바뀌면 원자성이 깨진다.

`DataDBConfig`의 `@EnableJpaRepositories(basePackages = ...)`는 **`SDD.smash.domain.<context>.infrastructure.persistence` 를 하나씩 열거**한다. 도메인 패키지에 Spring Data 인터페이스가 생기는 실수를 부팅 시점에 잡기 위해 범위를 좁혀 둔 것이다.

> ⚠️ 이 목록은 **손으로 유지하는 문자열**이다. 패키지를 옮기거나 컨텍스트를 추가하면 여기도 같이 고쳐야 한다.
> 빠뜨리면 리포지토리 빈이 등록되지 않아 **부팅이 실패**한다. 같은 이유로 JPQL 생성자 프로젝션의 **FQCN 문자열**(`SELECT new SDD.smash.domain....Row(...)`)도 컴파일러가 잡아주지 않으니 함께 확인한다 → persistence-conventions §4.3

---

## 8. 패키지 배치 지도

컨텍스트마다 실제로 존재하는 하위 패키지다. **없는 것은 필요가 없어서 없는 것이다** — 관례를 맞추려고 빈 패키지를 만들지 않는다.

| 컨텍스트 | `domain` | `application` | `infrastructure` | `presentation` |
|---|---|---|---|---|
| `address` | model, port | dto | batch(dto, runner), persistence(projection) | — |
| `job` | model, port, service | dto | batch(dto, runner), cache, external, persistence(projection) | — |
| `dwelling` | model, port, service | dto | batch(dto), cache, external, persistence | — |
| `infra` | model, port, service | dto | batch(dto, runner), cache, persistence(projection) | — |
| `support` | model, port, service | dto | cache, external, scheduler | — |
| `recommendation` | model, service | **port/out**, dto | external(dto) | dto |

읽는 법
- `address`에 `domain/service`가 없다 — 코드 체계에는 계산 규칙이 없다.
- `recommendation`에 `domain/port`가 없다 — 조합 전용이라 자기 저장소가 없고, out-port가 §3.2의 예외 케이스뿐이다.
- `support`에 `persistence`가 없다 — Redis가 정본이라 `cache`가 저장소 역할을 한다 → redis-conventions §2.2
- 어느 컨텍스트에도 `application/port/in`이 없다 — in-port를 두지 않는 것이 이 프로젝트의 규칙이다(§3.3). `recommendation`의 `port/out`만 §3.2의 예외로 남는다.
- `presentation`이 `recommendation`에만 있다 — 나머지 컨텍스트는 application `Service`로만 노출된다.

---

## 9. 구조를 확장할 때

### 9.1 새 컨텍스트를 추가한다

1. §2.2의 세 기준을 통과하는지 먼저 확인한다. 아니면 기존 컨텍스트에 넣는다.
2. `SDD/smash/domain/<context>/` 아래에 **필요한 계층만** 만든다(§8).
3. 공개 진입점은 `application`의 `...Service`다. **in-port 인터페이스를 만들지 않는다**(§3.3). 다른 컨텍스트가 쓸 메서드만 `public`으로 연다 → backend-conventions §5.1
4. JPA를 쓴다면 `DataDBConfig`의 `@EnableJpaRepositories` basePackages에 **새 패키지를 추가**한다(§7).
5. Seed 배치가 있다면 `@Order`를 선행 의존보다 큰 값으로 정하고 §6.2 표에 행을 추가한다.
6. §8 지도와 §2 표를 갱신한다.

### 9.2 기존 컨텍스트에 어댑터를 추가한다

- 먼저 **포트를 정의**한다(`domain/port`). 어댑터부터 만들면 기술 타입이 포트로 새어든다.
- 캐시 어댑터는 redis-conventions §3, JPA 어댑터는 persistence-conventions §4를 따른다.
- 새 파생 캐시를 만들면 **원본 갱신 유스케이스의 무효화 목록에도 추가**한다 → redis-conventions §6.1

### 9.3 패키지를 옮긴다

문자열로만 패키지를 참조하는 곳이 있어 **컴파일러가 잡아주지 않는다.** 이동 후 반드시 확인한다.

- `DataDBConfig`의 `@EnableJpaRepositories(basePackages = ...)`
- JPQL 생성자 프로젝션의 FQCN (`SELECT new SDD.smash.domain....Row(...)`)
- `LocalContainerEntityManagerFactoryBean.setPackagesToScan(...)`
- 테스트의 패키지 선언 — 테스트는 **main 구조를 그대로 미러링**한다 → backend-conventions §7.6
- 확인 방법은 `.\gradlew.bat test`가 아니라 **애플리케이션 부팅**까지다. 위 세 가지는 컴파일이 아니라 부팅/쿼리 시점에 터진다.

---

## 10. 아키텍처 리뷰 체크리스트

**계층**
- [ ] 계층 `domain` 패키지(`domain/<context>/domain/**`)에 Spring/JPA/Redis/Jackson import가 하나도 없는가
- [ ] `application`이 포트 인터페이스만 주입받는가 (구현체 직접 주입 없음)
- [ ] `presentation`이 도메인 모델을 그대로 응답하지 않는가
- [ ] `infrastructure`가 다른 컨텍스트의 `infrastructure`를 참조하지 않는가

**컨텍스트**
- [ ] 컨텍스트 간 호출이 대상 컨텍스트의 application `Service`를 통해서만 일어나는가
- [ ] `...UseCase` 인터페이스나 `application/port/in` 패키지를 새로 만들지 않았는가
- [ ] 다른 컨텍스트의 domain 모델/Repository를 직접 쓰지 않는가
- [ ] 공유하는 것이 `global.domain.model`의 값 객체뿐인가

**Aggregate**
- [ ] 다른 Aggregate를 객체가 아니라 **ID 값 객체**로 참조하는가
- [ ] 불변식이 생성자에서 강제되는가
- [ ] 한 트랜잭션이 하나의 Aggregate만 변경하는가
- [ ] 비즈니스 규칙이 application이 아니라 domain에 있는가

**포트**
- [ ] 포트 이름과 시그니처에 기술 용어가 없는가 (`Jpa`, `Redis`, `String code`)
- [ ] 포트가 도메인이 필요로 하는 메서드만 갖는가

**공통**
- [ ] `@Transactional`에 `transactionManager = "dataTransactionManager"`가 있는가
- [ ] 새 배치의 `@Order`가 선행 배치보다 큰가
- [ ] 패키지를 옮겼다면 §9.3의 문자열 참조를 전부 고쳤는가
- [ ] `.\gradlew.bat test` 통과
