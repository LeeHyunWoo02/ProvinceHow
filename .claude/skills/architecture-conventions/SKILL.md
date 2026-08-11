---
name: architecture-conventions
description: smash(ProvinceHow)의 DDD 헥사고날(포트&어댑터) 아키텍처를 정의한다. 바운디드 컨텍스트 경계, domain/application/infrastructure/presentation 4계층과 의존 방향, Aggregate 경계와 ID 참조 규칙, 인바운드/아웃바운드 포트 설계, 컨텍스트 간 통신, 기존 레이어드 구조에서의 마이그레이션 매핑과 전환 순서를 담는다. 새 클래스를 어느 계층에 둘지 정하거나, 포트를 만들거나, 도메인 경계를 판단하거나, 기존 코드를 DDD로 옮길 때 사용한다. 명명·도메인 예외는 global-conventions, 유스케이스·도메인 모델 작성은 backend-conventions, JPA 엔티티 분리는 persistence-conventions, 캐시 포트는 redis-conventions를 따른다.
---

# architecture-conventions (DDD / 헥사고날)

이 프로젝트는 **레이어드 → DDD 헥사고날(포트 & 어댑터)** 로 리팩토링 중이다.
이 문서는 **목표 구조(To-Be)** 를 정의하고, 각 절 끝에 **현재 코드(As-Is) → 목표 위치 매핑**을 함께 둔다.
새 코드는 예외 없이 목표 구조로 작성한다. 기존 코드는 §9의 전환 순서를 따른다.

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
| `common` | 공유 커널(값 객체), 공통 예외, 기술 설정 | Shared Kernel | — |

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

- **`recommendation` → 각 컨텍스트**: Customer/Supplier. `recommendation`은 각 컨텍스트가 공개한 **인바운드 포트(UseCase 인터페이스)** 만 호출한다.
- **모든 컨텍스트 → `address`**: Shared Kernel. `SigunguCode`/`SidoCode` **값 객체**만 공유한다. `address`의 Aggregate(`Sigungu` 객체)나 Repository를 직접 쓰지 않는다.
- **컨텍스트 간 역방향 의존 금지.** `job`이 `dwelling`을 알면 안 된다.

### 2.2 새 컨텍스트를 만드는 기준

세 가지를 모두 만족할 때만 새 컨텍스트를 만든다. 아니면 기존 컨텍스트의 Aggregate로 넣는다.
1. 고유한 유비쿼터스 언어(용어 체계)를 갖는다
2. 독립적인 데이터 소스와 수명주기를 갖는다
3. 다른 컨텍스트 없이도 의미가 성립한다

---

## 3. 디렉터리 구조

패키지는 **전부 소문자**다 (`SDD.smash.dwelling.domain.model`).

```
SDD/smash/
├── common/
│   ├── domain/model/          SigunguCode, SidoCode, Score, Money  ← 공유 커널 값 객체
│   ├── exception/             DomainException, ErrorCode
│   └── config/                DataDBConfig, MetaDBConfig, RedisConfig, SecurityConfig, ...
│
├── <context>/
│   ├── domain/                ★ 순수 Java. 프레임워크 의존 0
│   │   ├── model/             Aggregate Root, Entity, 값 객체, 도메인 enum
│   │   ├── service/           도메인 서비스 / 정책(Policy) — 여러 Aggregate에 걸친 규칙
│   │   └── port/              ★ out-port 인터페이스 (Repository, Provider, Cache)
│   │
│   ├── application/           유스케이스. domain만 의존
│   │   ├── port/in/           in-port 인터페이스 — 다른 컨텍스트에 공개할 때만
│   │   ├── <Xxx>QueryService  유스케이스 구현 (@Service, @Transactional)
│   │   └── dto/               유스케이스 입출력 DTO
│   │
│   ├── infrastructure/        모든 기술 상세
│   │   ├── persistence/       XxxJpaEntity, XxxJpaRepository, XxxRepositoryAdapter, XxxJpaMapper
│   │   ├── cache/             XxxRedisAdapter
│   │   ├── external/          XxxApiAdapter (외부 HTTP)
│   │   ├── batch/             Spring Batch Job/Step/Reader/Processor/Writer + Runner
│   │   └── scheduler/         @Scheduled 컴포넌트
│   │
│   └── presentation/          HTTP inbound adapter
│       ├── XxxController
│       └── dto/               XxxRequest, XxxResponse
│
└── SmashApplication.java
```

**규칙**
- 필요 없는 디렉터리는 만들지 않는다(빈 패키지 금지). `support`는 RDB가 없어 `persistence/`가 없고 `cache/`가 그 역할을 한다.
- `presentation`은 컨트롤러가 있는 컨텍스트에만 둔다. 현재 실질적으로 `recommendation`뿐이다.
- `common/config`의 Spring 설정 클래스들은 컨텍스트에 속하지 않는 **애플리케이션 부트스트랩**이다.

---

## 4. 계층별 규칙과 의존 방향

| 계층 | 의존 가능 | 절대 금지 | 프레임워크 |
|---|---|---|---|
| `domain/model` | 같은 컨텍스트 domain, `common.domain.model` | 다른 컨텍스트, application, infrastructure, presentation | **없음** (Lombok `@Getter` 정도만 허용) |
| `domain/service` | 같은 컨텍스트 domain, `common.domain.model` | 위와 동일 + port 구현체 | 없음 |
| `domain/port` | 같은 컨텍스트 domain 모델 | 기술 타입(`Page`, `Optional`은 허용) | 없음 |
| `application` | 자기 domain 전체, **다른 컨텍스트의 `application/port/in`** | 다른 컨텍스트의 domain/infrastructure, `HttpServletRequest`, `RedisTemplate`, JPA 타입 | `@Service`, `@Transactional`만 |
| `infrastructure` | 자기 domain(port 구현), 자기 application | 다른 컨텍스트의 infrastructure, presentation | 전부 허용 |
| `presentation` | 자기/타 컨텍스트의 `application` | domain 모델 직접 노출, infrastructure, Repository | Spring Web |

### 4.1 domain — 무엇을 담는가

```java
package SDD.smash.dwelling.domain.model;

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
package SDD.smash.dwelling.domain.port;

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
package SDD.smash.dwelling.application;

@Service
@RequiredArgsConstructor
public class DwellingQueryService implements DwellingQueryUseCase {   // in-port 구현

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
package SDD.smash.dwelling.infrastructure.persistence;

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
package SDD.smash.recommendation.presentation;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class RecommendController {

    private final RecommendRegionUseCase recommendRegionUseCase;   // in-port만 주입

    @GetMapping("/recommend")
    public ResponseEntity<List<RecommendResponse>> recommend(@Valid RecommendRequest request) {
        return ResponseEntity.ok(
                recommendRegionUseCase.recommend(request.toCommand()).stream()
                        .map(RecommendResponse::from).toList());
    }
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
   // ❌ As-Is — 다른 Aggregate를 객체로 참조
   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "sigungu_code")
   private Sigungu sigungu;

   // ✅ To-Be — 도메인 모델
   private final SigunguCode sigunguCode;

   // ✅ To-Be — JPA 엔티티(infrastructure)
   @Column(name = "sigungu_code", length = 5, nullable = false)
   private String sigunguCode;
   ```

   > 이 변경은 **점수 테이블의 `@MapsId` 패턴(`DwellingScore`, `InfraScore`)도 함께 해체**한다. 파생 점수는 Aggregate의 계산 결과이므로 별도 테이블이 아니라 도메인 정책(`Policy`)의 산출물로 다룬다 → §5.4
3. **Aggregate는 작게.** 조회 편의를 위해 Aggregate를 키우지 않는다. 여러 Aggregate가 필요한 조회는 application에서 조합하거나 전용 조회 모델(§5.5)을 쓴다.
4. **Aggregate Root를 통해서만 내부에 접근한다.** `RegionInfra.industryCounts()`로 꺼내고, `IndustryCount`를 별도 Repository로 조회하지 않는다.

### 5.3 값 객체 (Value Object)

공유 커널(`common.domain.model`)과 컨텍스트 로컬로 나뉜다.

```java
// common/domain/model — 모든 컨텍스트가 공유
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

| 값 객체 | 위치 | 대체 대상 |
|---|---|---|
| `SigunguCode`, `SidoCode` | `common` | 전 도메인의 `String sigunguCode` |
| `Score` | `common` | `Integer score`, `@Min(0) @Max(100)` |
| `Money` (만원 단위) | `common` | `Integer price`, `monthMid`, `jeonseMid` |
| `JobCode` | `job` | `String topCode` / `midJobCode` |
| `IndustryCode`, `Major` | `infra` | `String code`, `Major` enum |
| `SupportTag` | `support` | 기존 `SupportTag` (유지) |
| `DwellingType` | `dwelling/domain/model` | 기존 enum (이동) |

> `InfraImportance`는 머지 충돌 정리(2026-08-11)로 `infraChoice` 비트마스크(`Major`, 위 행) 방식에
> 통합되며 삭제됐다. 코드에 없는 개념이니 되살리지 않는다.

### 5.4 도메인 서비스 / 정책 (Policy)

**한 Aggregate에 담기 애매한 규칙**은 `domain/service`의 정책 객체로 만든다.

```java
package SDD.smash.dwelling.domain.service;

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

- 모든 공개 API는 `/api` 하위. 현재 `SecurityConfig`는 `/api/**` permitAll + **CORS GET/OPTIONS만 허용**이다. 다른 메서드를 열려면 설정도 함께 바꾼다.
- 컨트롤러는 **in-port(UseCase 인터페이스)만** 주입한다.

### 6.2 배치 (infrastructure/batch)

Seed 배치는 **외부 파일/API → 저장소**로 데이터를 밀어넣는 인바운드 어댑터다.

```
<context>/infrastructure/batch/
├── DwellingBatchConfig.java     Job/Step/Reader/Processor/Writer 빈
├── DwellingBatchRunner.java     @EventListener(ApplicationReadyEvent) + @Order
└── dto/                         CSV 읽기 DTO, Upsert DTO (기술 DTO)
```

- `spring.batch.job.enabled=false`이며 Runner의 `@Order`가 FK 선후관계를 통제한다. **이 순서 규칙은 DDD 전환 후에도 그대로 유지**한다.

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

  > 위 값은 2026-08-11 실제 코드에서 실측한 것이다. 이전 표에는 머지 충돌 정리 때 삭제된
  > `InfraScore` 배치가 8번으로 남아 있어 `JobCount`·`Dwelling`이 한 칸씩 밀려 적혀 있었다.
  > 코드를 표에 맞추지 말고 표를 코드에 맞춘다 — `@Order` 변경은 실행 순서 변경이다.

- 재실행 방지는 `BatchGuard.alreadyDone(jobName, seedVersion)` 유지. 위치는 `common/batch` 또는 각 컨텍스트 infrastructure.
- **대량 적재 배치는 Aggregate를 거치지 않아도 된다.** `JdbcBatchItemWriter` + Upsert SQL로 직접 쓰는 현재 방식을 유지한다. 도메인 불변식은 Processor에서 값 객체 생성으로 검증한다.
  ```java
  // Processor에서 값 객체로 검증 → 실패 시 null 반환(skip)
  try { new SigunguCode(raw); } catch (DomainException e) { return null; }
  ```

### 6.3 스케줄러 (infrastructure/scheduler)

- `@Scheduled` 컴포넌트는 **application 유스케이스를 호출**한다. Redis/외부 API를 직접 다루지 않는다.
  ```java
  @Component
  @RequiredArgsConstructor
  public class SupportPolicyRefreshScheduler {
      private final RefreshSupportPolicyUseCase refreshUseCase;

      @Scheduled(initialDelay = 0, fixedDelayString = "#{T(java.time.Duration).ofDays(3).toMillis()}")
      public void refresh() { refreshUseCase.refreshAll(); }
  }
  ```
- 갱신 후 파생 캐시 무효화 책임은 **유스케이스**가 진다 → redis-conventions §5

---

## 7. 기술 인프라 (common/config)

DataSource 2개 구성은 DDD 전환과 무관하게 유지된다.
DB는 **Docker 컨테이너 MySQL 1개에 스키마 2개**다(RDS 아님) — 드라이버 `com.mysql.cj.jdbc.Driver`, 호스트는 compose 서비스명 `mysql`.

| DataSource | 스키마 | 용도 | 트랜잭션 매니저 |
|---|---|---|---|
| `dataDBSource` (`@Primary` DataSource) | `smash_data` | 업무 데이터 — 모든 JPA 엔티티 | `dataTransactionManager` |
| `batchDataSource` (`@BatchDataSource`) | `smash_meta` | Spring Batch 메타 | `batchTransactionManager` (**`@Primary` PlatformTransactionManager**) |

> ⚠️ `@Primary` **트랜잭션 매니저는 배치용**이다. application 계층에서 무수식 `@Transactional`을 쓰면 JPA 트랜잭션이 열리지 않는다.
> **반드시 `@Transactional(transactionManager = "dataTransactionManager", readOnly = true)`** → persistence-conventions §6

`@EnableJpaRepositories(basePackages = ...)`의 대상은 **`infrastructure.persistence` 하위로 좁힌다.** 그래야 도메인 패키지에 Spring Data 인터페이스가 생기는 실수를 컴파일 시점이 아니라 부팅 시점에라도 잡을 수 있다.

---

## 8. As-Is → To-Be 매핑

| 현재 | 목표 | 비고 |
|---|---|---|
| `Apis/Controller/*` | `recommendation/presentation/*` | Request/Response DTO 신설 |
| `Apis/Service/RecommendService` | `recommendation/application/RecommendRegionService` | 각 컨텍스트 in-port만 호출 |
| `Apis/Service/DetailService` | `recommendation/application/RegionDetailService` | |
| `Apis/Service/CodeService` | `recommendation/application/RegionCodeService` | 코드 조회는 각 컨텍스트 in-port로 위임 |
| `Apis/Dto/*` | `recommendation/presentation/dto/*` (응답) + `application/dto/*` (내부) | 2분리 |
| `<D>/Entity/<E>.java` | `<c>/domain/model/<E>.java` (POJO) + `<c>/infrastructure/persistence/<E>JpaEntity.java` | **분리** |
| `<D>/Entity/<Enum>.java` | `<c>/domain/model/<Enum>.java` | 이동만 |
| `<D>/Repository/<R>.java` | `<c>/domain/port/<R>.java` (인터페이스) + `<c>/infrastructure/persistence/<R>JpaRepository.java` + `<R>Adapter.java` | **분리** |
| `<D>/Service/<X>Service.java` (조회) | `<c>/application/<X>QueryService.java` | 규칙은 domain으로 내림 |
| `<D>/Service/<X>ScoreService.java` | 계산 → `<c>/domain/service/<X>ScorePolicy`<br>캐시/조합 → `<c>/application/<X>ScoreService`<br>Redis → `<c>/infrastructure/cache/<X>ScoreRedisAdapter` | **3분할** |
| `Address/Service/AddressVerifyService` | `common/domain/model/SigunguCode` 생성자 검증 + `address/domain/port/SigunguRepository.exists(...)` | 대부분 값 객체로 흡수 |
| `<D>/Adapter/MolitAptRentAdapter` | `dwelling/infrastructure/external/MolitAptRentAdapter` (+ `domain/port/RentRecordProvider`) | 포트 신설 |
| `Support/service/YouthCenterClient` | `support/infrastructure/external/YouthCenterApiAdapter` (+ `domain/port/SupportPolicyProvider`) | 이름 정정 |
| `Support/service/SupportService` | `support/application/SupportQueryService` + `support/infrastructure/cache/SupportPolicyRedisAdapter` | Redis 접근 분리 |
| `Support/scheduler/YouthSupportScheduler` | `support/infrastructure/scheduler/*` + `support/application/RefreshSupportPolicyService` | 로직을 유스케이스로 |
| `<D>/Converter/*` | `<c>/infrastructure/persistence/<E>JpaMapper` 또는 배치 DTO 매퍼 | 도메인 밖 |
| `<D>/Batch/*` | `<c>/infrastructure/batch/*` | 이동 |
| `Exception/*` | `common/exception/*` | `ErrorCode`에서 `HttpStatus` 분리 → global-conventions §3 |
| `Config/*` | `common/config/*` | 이동 |
| `Util/BatchTextUtil`, `MapperUtil` | `common/util/` 또는 각 infrastructure | 기술 유틸 |
| `Util/CalculateUtil` | `<c>/domain/service/` 또는 `common/domain/` | 도메인 계산이면 domain으로 |

---

## 9. 전환 순서 (Strangler)

한 번에 전부 옮기지 않는다. **컨텍스트 하나씩, 아래 순서로** 진행한다.

1. **`common` 골격** — `SigunguCode`/`SidoCode`/`Score`/`Money` 값 객체, `DomainException` + `ErrorCode`(HttpStatus 분리), `config` 이동
2. **`address`** — 다른 모든 컨텍스트가 의존하므로 먼저. `Sigungu`/`Sido` 도메인 모델 + JPA 엔티티 분리 + 포트/어댑터
3. **`dwelling`** — 도메인 로직(점수 계산)이 가장 뚜렷해 헥사고날 이득이 큼. 여기서 패턴을 확립한다
4. **`job` → `infra`** — `dwelling`에서 만든 패턴을 복제
5. **`support`** — Redis 포트화(§redis-conventions §2)
6. **`recommendation`** — 각 컨텍스트의 in-port가 다 갖춰진 뒤 마지막에 조합 계층을 옮긴다
7. **정리** — 옛 패키지 삭제, `@EnableJpaRepositories` 범위 축소, ArchUnit 규칙 도입(선택)

**전환 중 지켜야 할 것**
- 한 컨텍스트를 옮기는 동안 **기존 패키지와 새 패키지가 공존**한다. 새 패키지가 옛 패키지를 참조해도 되지만, **옛 패키지가 새 패키지를 참조하게 만들지 않는다**(되돌리기 불가).
- 각 단계 끝에 `./gradlew test`가 통과해야 한다. 도메인 모델과 정책의 단위 테스트를 먼저 쓰고 옮긴다 → backend-conventions §6
- DB 스키마는 `hbm2ddl.auto=update`다. **FK 제거(§5.2)는 기존 컬럼을 그대로 두고 JPA 매핑만 바꾸는 방식**으로 하면 스키마 변경 없이 진행할 수 있다. 실제 FK 제약 삭제는 마지막에 별도 DDL로 한다.

---

## 10. 아키텍처 리뷰 체크리스트

**계층**
- [ ] `domain` 패키지에 Spring/JPA/Redis/Jackson import가 하나도 없는가
- [ ] `application`이 포트 인터페이스만 주입받는가 (구현체 직접 주입 없음)
- [ ] `presentation`이 도메인 모델을 그대로 응답하지 않는가
- [ ] `infrastructure`가 다른 컨텍스트의 `infrastructure`를 참조하지 않는가

**컨텍스트**
- [ ] 컨텍스트 간 호출이 `application/port/in`을 통해서만 일어나는가
- [ ] 다른 컨텍스트의 domain 모델/Repository를 직접 쓰지 않는가
- [ ] 공유하는 것이 값 객체(`SigunguCode` 등)뿐인가

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
- [ ] `./gradlew test` 통과
