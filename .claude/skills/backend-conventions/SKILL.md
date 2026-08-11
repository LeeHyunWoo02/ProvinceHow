---
name: backend-conventions
description: smash(ProvinceHow)의 DDD 헥사고날 구조에서 도메인 모델·정책(Policy)·유스케이스(application)·컨트롤러(presentation)를 만드는 워크플로우와, 빈약한 모델에서 도메인으로 로직을 옮기는 분리 기준, 계층별 JUnit5/Mockito 테스트 전략을 정의한다. 새 기능이나 API를 추가하거나, 기존 서비스를 DDD로 리팩토링하거나, 도메인/유스케이스/어댑터 테스트를 작성할 때 사용한다. 계층 배치는 architecture-conventions, 명명·예외·DTO는 global-conventions, JPA 매핑은 persistence-conventions, 캐시는 redis-conventions를 따른다.
---

# backend-conventions (DDD)

Java 17 · Spring Boot 3.5.7 · Lombok · JUnit5 + Mockito(`spring-boot-starter-test`).
`./gradlew test`, 단일 실행 `./gradlew test --tests "SDD.smash.dwelling.domain.*"`

---

## 1. 기능 추가 워크플로우 (안에서 바깥으로)

DDD에서는 **도메인부터 만들고 바깥으로 나온다.** 컨트롤러부터 시작하지 않는다.

```
1. 도메인 모델링   → domain/model      (Aggregate, 값 객체, 불변식)
2. 도메인 규칙     → domain/service    (Policy)          ← 여기까지 순수 테스트로 완성
3. 포트 정의       → domain/port       (필요한 것만)
4. 유스케이스      → application       (오케스트레이션)   ← 포트 목킹으로 테스트
5. 어댑터 구현     → infrastructure    (JPA/Redis/HTTP)
6. HTTP 노출       → presentation      (Request/Response)
```

각 단계가 끝날 때마다 테스트가 통과해야 한다. 1~2단계는 Spring 없이 실행되므로 가장 빠르게 반복할 수 있다.

---

## 2. 도메인 모델 작성

### 2.1 Aggregate Root

```java
package SDD.smash.dwelling.domain.model;

/** 지역의 전월세 시세 (Aggregate Root) */
public class DwellingMarket {

    private final SigunguCode sigunguCode;     // 다른 Aggregate는 ID로만
    private final RentStat monthly;            // 값 객체
    private final RentStat jeonse;

    private DwellingMarket(SigunguCode sigunguCode, RentStat monthly, RentStat jeonse) {
        if (sigunguCode == null) throw new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "시군구 코드는 필수입니다.");
        this.sigunguCode = sigunguCode;
        this.monthly = monthly;
        this.jeonse = jeonse;
    }

    /** 재구성용 — 저장소 어댑터가 사용한다. */
    public static DwellingMarket reconstitute(SigunguCode code, RentStat monthly, RentStat jeonse) {
        return new DwellingMarket(code, monthly, jeonse);
    }

    /** 해당 유형의 시세 중앙값. 실거래가 없으면 empty. */
    public Optional<Money> medianOf(DwellingType type) {
        RentStat stat = (type == DwellingType.MONTHLY) ? monthly : jeonse;
        return stat == null ? Optional.empty() : stat.median();
    }

    public SigunguCode sigunguCode() { return sigunguCode; }
}
```

**규칙**
- **생성자는 `private`, 진입점은 정적 팩토리.** 의미가 드러나는 이름을 쓴다: `of(...)`(신규 생성), `reconstitute(...)`(저장소에서 복원).
- **불변식을 생성자에서 강제한다.** 유효하지 않은 객체가 존재할 수 없게 한다.
- **가급적 불변(`final`)으로 만든다.** 이 프로젝트는 조회 중심이라 대부분의 Aggregate가 불변으로 충분하다.
- getter는 `get` 접두어 없이 **필드명 그대로**(`sigunguCode()`) 쓴다. 도메인 모델은 자바빈이 아니다.
- **JPA/Spring/Jackson 애너테이션을 붙이지 않는다.** 매핑은 `infrastructure/persistence`가 담당 → persistence-conventions §2

### 2.2 값 객체

```java
public record Money(int manwon) {                     // 만원 단위
    public Money {
        if (manwon < 0) throw new DomainException(ErrorCode.PRICE_AMOUNT_NOT_VALID, "금액은 0 이상이어야 합니다.");
    }
    public static Money of(int manwon) { return new Money(manwon); }
    public int diffTo(Money other)     { return Math.abs(manwon - other.manwon); }
    public boolean isAtLeast(Money other) { return manwon >= other.manwon; }
}
```

- `record`로 만들고 compact 생성자에서 검증한다.
- **행위를 값 객체에 넣는다.** `Math.abs(a - b)`를 서비스에서 계산하지 말고 `diffTo`로 만든다.
- 원시 타입 집착(primitive obsession)을 피한다: `int price` → `Money budget`, `String code` → `SigunguCode`.

### 2.3 도메인 enum에 행위 부여

```java
public enum DwellingType {
    MONTHLY(Money.of(20), Money.of(110), Money.of(10)),
    JEONSE (Money.of(3_000), Money.of(21_000), Money.of(3_000));

    private final Money min, max, step;

    /** 사용자 입력 예산을 이 유형의 유효 구간으로 보정한다. */
    public Money normalize(Money budget) { ... }

    /** 이 유형의 감점 단위 */
    public Money step() { return step; }
}
```

기존 `DwellingScoreSerivce.validPrice()`의 `if (type == MONTHLY) ... else ...` 분기가 enum 안으로 들어가면 **분기 자체가 사라진다.** 타입별 분기를 서비스에서 발견하면 enum으로 옮길 신호다.

---

## 3. 도메인 정책(Policy) 작성

여러 Aggregate에 걸치거나 Aggregate 하나에 넣기 어색한 규칙을 담는다.

```java
package SDD.smash.dwelling.domain.service;

/** 예산과 시세 중앙값의 차이로 주거 적합도를 계산한다. */
public class DwellingScorePolicy {

    private static final int PENALTY_PER_STEP = 10;

    public Score score(DwellingType type, Money median, Money rawBudget) {
        Money budget = type.normalize(rawBudget);
        if (median == null) return Score.ZERO;
        if (budget.equals(type.upperBound()) && median.isAtLeast(budget)) return Score.MAX;

        int penalty = (median.diffTo(budget) / type.step().manwon()) * PENALTY_PER_STEP;
        return Score.of(Math.max(0, 100 - penalty));
    }
}
```

**규칙**
- **순수 함수여야 한다.** 저장소·캐시·현재시각·랜덤에 의존하면 그건 application 관심사다.
- Spring 빈으로 등록하지 않아도 된다(상태 없음). 주입이 편하면 `@Component`로 등록하되 **포트 외에는 의존하지 않는다.**
- 이름은 `...Policy` / `...Calculator`. `Service`를 쓰지 않는다 → global-conventions §2

---

## 4. 빈약한 모델(anemic) 탈피 기준

### 4.1 "이 코드는 어디로 가는가"

| 질문 | 예 → 위치 |
|---|---|
| 이 객체 **자신의 데이터만으로** 판단/계산되는가 | `domain/model` (Aggregate 또는 값 객체의 메서드) |
| 타입/상태에 따른 **분기**인가 | `domain/model`의 **enum 메서드** |
| **여러 Aggregate**의 데이터가 필요한 규칙인가 | `domain/service`의 Policy |
| **저장소·캐시·외부 API 호출 순서**인가 | `application` |
| **여러 컨텍스트**를 합치는가 | `recommendation/application` |
| **기술 변환**(JSON, SQL, 문자열 정제)인가 | `infrastructure` |
| HTTP 상태코드·JSON 필드명인가 | `presentation` |

### 4.2 실제 분해 예시 — `DwellingScoreSerivce` 3분할

현재 이 클래스 하나에 Redis·JPA·도메인 규칙이 섞여 있다. DDD 전환 시 셋으로 나뉜다.

```
As-Is: Dwelling/Service/DwellingScoreSerivce
       ├─ validPrice()            가격 보정 규칙
       ├─ calcMonthlyScore()      월세 점수 공식
       ├─ calcJeonseScore()       전세 점수 공식
       ├─ redisTemplate 캐시 조회/저장
       └─ dwellingRepository 조회

To-Be:
  dwelling/domain/model/DwellingType.normalize()          ← 가격 보정 (분기 소멸)
  dwelling/domain/service/DwellingScorePolicy.score()     ← 점수 공식 (순수)
  dwelling/domain/port/DwellingMarketRepository           ← 조회 포트
  dwelling/domain/port/DwellingScoreCache                 ← 캐시 포트
  dwelling/application/DwellingScoreService                ← 캐시 확인 → 조회 → Policy 적용 → 캐시 저장
  dwelling/infrastructure/cache/DwellingScoreRedisAdapter  ← Redis 상세
  dwelling/infrastructure/persistence/DwellingRepositoryAdapter
```

**판단 근거**: 점수 공식은 "주거비가 예산에 가까울수록 좋다"는 **도메인 지식**이므로 domain. 캐시는 **성능 최적화**이지 도메인 지식이 아니므로 application + infrastructure.

### 4.3 리팩토링 신호

아래를 발견하면 도메인으로 내릴 후보다.
- 서비스에 `if (type == X) ... else ...` 분기가 있다 → enum 메서드
- 서비스가 getter를 여러 번 호출해 계산한다 → Aggregate 메서드 (Tell, Don't Ask)
- 같은 검증 코드가 서비스 여러 곳에 반복된다 → 값 객체 생성자
- 상수(`110`, `3000`, `100`)가 서비스에 흩어져 있다 → 도메인 모델/enum의 상수

---

## 5. 유스케이스(application) 작성

### 5.1 in-port는 언제 만드는가

- **다른 컨텍스트가 호출하면** `application/port/in`에 `...UseCase` 인터페이스를 만든다.
- **자기 컨텍스트 안에서만 쓰면** 인터페이스 없이 구현 클래스를 직접 주입한다. **불필요한 인터페이스를 만들지 않는다.**

현재 구조상 `recommendation`이 다른 5개 컨텍스트를 호출하므로, **그 5개는 in-port가 필요**하다.

### 5.2 템플릿

```java
package SDD.smash.dwelling.application;

@Service
@RequiredArgsConstructor
public class DwellingScoreService implements DwellingScoreUseCase {

    private final DwellingMarketRepository dwellingMarketRepository;  // out-port
    private final DwellingScoreCache dwellingScoreCache;              // out-port
    private final DwellingScorePolicy policy = new DwellingScorePolicy();

    @Override
    public Map<SigunguCode, Score> scoresFor(DwellingType type, Money budget) {

        DwellingScoreKey key = DwellingScoreKey.of(type, type.normalize(budget));

        // 1) 캐시 확인 (기술 상세는 어댑터 뒤에 있다)
        Optional<Map<SigunguCode, Score>> cached = dwellingScoreCache.find(key);
        if (cached.isPresent()) return cached.get();

        // 2) 도메인 조회 + 규칙 적용
        Map<SigunguCode, Score> scores = loadMarkets().stream()
                .collect(toMap(DwellingMarket::sigunguCode,
                               m -> policy.score(type, m.medianOf(type).orElse(null), budget)));

        // 3) 캐시 저장 (TTL은 어댑터가 안다)
        if (!scores.isEmpty()) dwellingScoreCache.put(key, scores);
        return scores;
    }

    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    protected List<DwellingMarket> loadMarkets() {
        return dwellingMarketRepository.findAll();
    }
}
```

### 5.3 규칙

1. **주입은 포트 인터페이스만.** `XxxRepositoryAdapter`, `RedisTemplate`, `XxxJpaRepository`를 직접 주입하지 않는다.
2. **비즈니스 규칙을 쓰지 않는다.** `if`가 도메인 판단이면 모델로 내린다. 유스케이스에 남는 `if`는 캐시 히트/널 체크 정도다.
3. **트랜잭션 경계는 유스케이스 메서드**다. `@Transactional(transactionManager = "dataTransactionManager", readOnly = true)` — 매니저 지정 필수 → persistence-conventions §6
4. **트랜잭션 안에서 캐시·외부 API를 호출하지 않는다.** DB 조회 구간만 트랜잭션으로 감싼다.
5. **"코드 없음"과 "데이터 없음"을 구분한다.**
   - 형식 오류 → 값 객체 생성자에서 `DomainException`
   - 존재하지 않는 코드 → 포트 `existsBy` 확인 후 `DomainException`
   - 존재하지만 데이터가 없음 → `Optional.empty()` / `null` 반환 (API에서 필드가 비는 것이 정상)
6. 반환은 `application/dto`의 `...Info` / `...View` 또는 도메인 타입. **JPA 엔티티를 반환하지 않는다.**

---

## 6. 컨트롤러(presentation) 작성

```java
package SDD.smash.recommendation.presentation;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class RecommendController {

    private final RecommendRegionUseCase recommendRegionUseCase;   // in-port만

    @GetMapping("/recommend")
    public ResponseEntity<List<RecommendResponse>> recommend(@Valid RecommendRequest request) {
        List<RegionRecommendation> result = recommendRegionUseCase.recommend(request.toCommand());
        return ResponseEntity.ok(result.stream().map(RecommendResponse::from).toList());
    }
}
```

**규칙**
- 컨트롤러는 **in-port 1개만** 주입한다.
- 파라미터가 3개를 넘으면 `...Request` 레코드로 묶고 `@Valid`를 쓴다. 값 객체 변환은 `toCommand()`에서.
- **try/catch 금지.** 전역 핸들러가 처리한다 → global-conventions §3
- 로직·분기·조합 금지. 위임과 응답 변환만.
- 도메인 모델을 반환하지 않는다. `...Response`로 변환한다.

---

## 7. 테스트 전략

### 7.1 전제 (이 프로젝트의 제약)

- `src/main/resources`에 **`application.properties`가 없다.** `application-dev`/`application-prod`만 있고 값이 전부 `${ENV}`다.
- `src/test/resources`도 **없다.**
- 따라서 **`@SpringBootTest`는 프로필·환경변수 없이 뜨지 않는다.** 기존 `SmashApplicationTests`도 마찬가지다.

**DDD 전환은 이 문제를 크게 완화한다.** 도메인과 유스케이스가 프레임워크를 모르므로 **테스트의 대부분이 Spring 없이 실행**된다.

### 7.2 계층별 테스트 피라미드

```
        ▲  적음
        │  infrastructure 어댑터 테스트   — 필요 최소한. @DataJpaTest 등
        │  presentation 슬라이스 테스트   — @WebMvcTest
        │  application 유스케이스 테스트  — Mockito로 포트 목킹
        │  domain 모델·정책 테스트        — 순수 JUnit, 모킹 0    ★ 가장 많이
        ▼  많음
```

### 7.3 domain 테스트 — 모킹 없음 (가장 중요)

```java
package SDD.smash.dwelling.domain.service;

class DwellingScorePolicyTest {

    private final DwellingScorePolicy policy = new DwellingScorePolicy();   // Spring 불필요

    @Test
    @DisplayName("월세 중앙값이 예산과 같으면 100점")
    void scoresFullWhenMonthlyMedianEqualsBudget() {
        Score score = policy.score(DwellingType.MONTHLY, Money.of(60), Money.of(60));
        assertThat(score).isEqualTo(Score.of(100));
    }

    @Test
    @DisplayName("10만원 차이마다 10점 감점되고 0점 미만으로 내려가지 않는다")
    void deductsPerStepAndStopsAtZero() {
        assertThat(policy.score(DwellingType.MONTHLY, Money.of(80), Money.of(60))).isEqualTo(Score.of(80));
        assertThat(policy.score(DwellingType.MONTHLY, Money.of(500), Money.of(20))).isEqualTo(Score.ZERO);
    }

    @Test
    @DisplayName("실거래가 없으면 0점")
    void scoresZeroWhenNoMarketData() {
        assertThat(policy.score(DwellingType.MONTHLY, null, Money.of(60))).isEqualTo(Score.ZERO);
    }
}
```

**반드시 테스트할 것**
- 값 객체의 **불변식 위반 시 `DomainException`과 `ErrorCode`** (`Score.of(101)`, `new SigunguCode("111")`)
- 정책의 **경계값** (상·하한, 0점 클램프, null 입력)
- enum의 행위 (`DwellingType.normalize()`의 구간 보정)
- Aggregate 메서드의 정상/빈 데이터 경로

### 7.4 application 테스트 — 포트 목킹

```java
@ExtendWith(MockitoExtension.class)
class DwellingScoreServiceTest {

    @Mock DwellingMarketRepository dwellingMarketRepository;   // 포트를 목킹 (구현체 아님)
    @Mock DwellingScoreCache dwellingScoreCache;

    @InjectMocks DwellingScoreService service;

    @Test
    @DisplayName("캐시가 있으면 저장소를 조회하지 않는다")
    void returnsCachedScoresWithoutQueryingRepository() {
        given(dwellingScoreCache.find(any()))
                .willReturn(Optional.of(Map.of(new SigunguCode("11110"), Score.of(100))));

        Map<SigunguCode, Score> result = service.scoresFor(DwellingType.MONTHLY, Money.of(60));

        assertThat(result).containsEntry(new SigunguCode("11110"), Score.of(100));
        then(dwellingMarketRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("캐시 미스면 조회 후 계산 결과를 캐시에 저장한다")
    void storesComputedScoresOnCacheMiss() {
        given(dwellingScoreCache.find(any())).willReturn(Optional.empty());
        given(dwellingMarketRepository.findAll()).willReturn(List.of(market("11110", 60)));

        service.scoresFor(DwellingType.MONTHLY, Money.of(60));

        then(dwellingScoreCache).should().put(any(), any());
    }
}
```

- **포트가 인터페이스이므로 목킹이 자연스럽다.** `RedisTemplate`/`HashOperations`의 다단계 스텁이 사라진다 — DDD 전환의 가장 큰 테스트 이득이다.
- 목킹 대신 **인메모리 Fake 포트**를 써도 좋다. 같은 포트를 여러 테스트가 쓰면 Fake가 더 읽기 쉽다.
  ```java
  class InMemoryDwellingScoreCache implements DwellingScoreCache {
      private final Map<DwellingScoreKey, Map<SigunguCode, Score>> store = new HashMap<>();
      public Optional<Map<SigunguCode, Score>> find(DwellingScoreKey k) { return Optional.ofNullable(store.get(k)); }
      public void put(DwellingScoreKey k, Map<SigunguCode, Score> v)   { store.put(k, v); }
  }
  ```
- 스텁은 `BDDMockito`(`given/willReturn`), 검증은 `then(...).should()`, 단언은 **AssertJ**.
- `MockitoExtension`은 strict stubbing이다. 쓰지 않는 스텁은 `lenient()`로 덮지 말고 **삭제**한다.

### 7.5 infrastructure / presentation 테스트

- **어댑터**: 매핑(`XxxJpaMapper`)의 도메인↔JPA 왕복 변환은 순수 테스트로 검증한다. DB가 필요한 쿼리는 `@DataJpaTest` + H2를 쓰되, **`src/test/resources/application-test.properties`를 먼저 만들고** `@ActiveProfiles("test")`를 붙인다.
- **컨트롤러**: `@WebMvcTest(controllers = XxxController.class)` + in-port를 `@MockitoBean`으로 대체.
  `SecurityConfig`가 로드되면 `${front_url}` 때문에 실패하므로 `@TestPropertySource(properties = "front_url=http://localhost:5173")` 또는 `excludeAutoConfiguration = SecurityAutoConfiguration.class`를 함께 준다.
- **`@SpringBootTest`는 통합 환경이 갖춰지기 전까지 추가하지 않는다.**

### 7.6 테스트 위치와 이름

```
src/test/java/SDD/smash/<context>/<layer>/<대상>Test.java
예) src/test/java/SDD/smash/dwelling/domain/service/DwellingScorePolicyTest.java
    src/test/java/SDD/smash/dwelling/application/DwellingScoreServiceTest.java
```

- 클래스 `<대상>Test`, **메서드명은 영어 camelCase**(`returnsCachedScoresWithoutQueryingRepository`) + `@DisplayName`으로 한국어 문장 설명.
- given/when/then 주석으로 구간을 나눈다.

---

## 8. 체크리스트

**도메인**
- [ ] `domain` 패키지에 Spring/JPA/Redis import가 없는가
- [ ] 불변식이 생성자/compact 생성자에서 강제되는가
- [ ] 원시 타입 대신 값 객체(`SigunguCode`, `Money`, `Score`)를 쓰는가
- [ ] 타입 분기(`if type == ...`)가 enum 메서드로 들어갔는가
- [ ] Policy가 순수 함수인가 (저장소·시간·랜덤 의존 없음)

**애플리케이션**
- [ ] 포트 인터페이스만 주입받는가
- [ ] 비즈니스 규칙이 남아 있지 않은가
- [ ] `@Transactional(transactionManager = "dataTransactionManager", readOnly = true)`인가
- [ ] 트랜잭션 안에서 캐시/외부 API를 호출하지 않는가

**표현**
- [ ] 컨트롤러가 in-port 1개만 주입하고 로직이 없는가
- [ ] 도메인 모델이 아니라 `...Response`를 반환하는가
- [ ] `@Validated`/`@Valid`가 있고 값 객체 변환이 `toCommand()`에 있는가

**테스트**
- [ ] 도메인 모델·정책 테스트가 **모킹 없이** 도는가
- [ ] 값 객체 불변식 위반 테스트(`ErrorCode`까지)가 있는가
- [ ] 유스케이스 테스트가 **포트**를 목킹하는가 (`RedisTemplate` 목킹이 남아 있지 않은가)
- [ ] `./gradlew test` 통과
