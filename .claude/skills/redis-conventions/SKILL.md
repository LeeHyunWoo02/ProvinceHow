---
name: redis-conventions
description: smash(ProvinceHow)의 DDD 헥사고날 구조에서 Redis를 다루는 규칙 — 캐시/저장소를 domain/port 뒤로 숨기는 포트 설계, infrastructure/cache 어댑터 구현, 키 네이밍과 네임스페이스, TTL 기준, 파생 캐시 무효화와 정합성 유지, Redis가 정본인 support 컨텍스트의 Repository 포트화, 캐시 관련 테스트 전략을 정의한다. 캐시를 도입하거나, Redis 키/TTL을 정하거나, 캐시 무효화를 다루거나, RedisTemplate을 쓰는 코드를 작성·리팩토링할 때 사용한다. 계층 배치는 architecture-conventions, 명명·DTO는 global-conventions, 유스케이스·테스트는 backend-conventions, RDB는 persistence-conventions를 따른다.
---

# redis-conventions (DDD)

## 0. 제1원칙 — `RedisTemplate`은 `infrastructure/cache` 밖으로 나가지 않는다

```
❌ As-Is                                  ✅ To-Be
JobScoreService                           job/domain/port/JobScoreCache          (인터페이스)
 ├─ RedisTemplate 주입                     job/application/JobScoreService        (포트만 주입)
 ├─ 키 문자열 조립                          job/infrastructure/cache/
 ├─ TTL 상수                                 JobScoreRedisAdapter                 (키·TTL·직렬화)
 └─ 점수 계산
```

`domain`과 `application`은 **캐시가 Redis인지, 인메모리인지, 아예 없는지 몰라야 한다.**
키 문자열·TTL·직렬화는 전부 어댑터 내부의 구현 상세다.

---

## 1. Redis의 두 가지 역할

| 역할 | 컨텍스트 | 포트 이름 | 의미 |
|---|---|---|---|
| **정본 저장소** | `support` | `SupportPolicyRepository` | 청년 지원정책. RDB 테이블이 없고 Redis가 원본이다 |
| **파생 캐시** | `job`, `dwelling`, `infra`, `support` | `...ScoreCache` | 계산 결과의 성능 최적화. 없어도 기능은 동작해야 한다 |

**이 구분이 포트 이름과 장애 정책을 가른다.**
- 정본 저장소 포트는 `Repository`다. 없으면 데이터가 없는 것이다.
- 캐시 포트는 `Cache`다. **없으면 다시 계산하면 된다.** 캐시 실패가 기능 실패가 되면 안 된다.

---

## 2. 포트 설계

### 2.1 캐시 포트

```java
package SDD.smash.dwelling.domain.port;

public interface DwellingScoreCache {
    Optional<Map<SigunguCode, Score>> find(DwellingScoreKey key);
    void put(DwellingScoreKey key, Map<SigunguCode, Score> scores);
}
```

```java
package SDD.smash.dwelling.domain.model;

/** 주거 점수 캐시의 도메인 식별자. 결과를 결정하는 모든 입력을 담는다. */
public record DwellingScoreKey(DwellingType type, Money normalizedBudget) {

    public static DwellingScoreKey of(DwellingType type, Money budget) {
        return new DwellingScoreKey(type, type.normalize(budget));   // 구간화된 예산만 허용
    }
}
```

**규칙**
- **포트 이름·시그니처에 기술 용어 금지.** `RedisScoreCache`(❌), `getFromRedis`(❌), `String key`(❌)
- **캐시 키를 값 객체로 만든다.** 이것이 "무엇이 결과를 결정하는가"를 도메인 언어로 못 박고, 문자열 조립 실수를 없앤다.
- 키 값 객체의 팩토리에서 **구간화(normalize)를 강제**한다 → 카디널리티 폭발 방지(§4.2)
- 조회는 `Optional`. `null`을 반환하지 않는다.

### 2.2 정본 저장소 포트 (support)

```java
package SDD.smash.support.domain.port;

public interface SupportPolicyRepository {
    List<SupportPolicy> findBy(SigunguCode code, SupportTag tag);
    int countBy(SigunguCode code, SupportTag tag);
    void saveAll(SigunguCode code, SupportTag tag, List<SupportPolicy> policies);
}
```

- `support`는 RDB가 없으므로 이 포트의 구현이 `infrastructure/cache/SupportPolicyRedisAdapter`다.
- **포트 이름은 `Repository`** 다. 구현이 Redis라는 사실은 포트 이름에 드러나지 않는다.

---

## 3. 어댑터 구현 (infrastructure/cache)

```java
package SDD.smash.dwelling.infrastructure.cache;

@Component
@RequiredArgsConstructor
@Slf4j
public class DwellingScoreRedisAdapter implements DwellingScoreCache {

    private static final String KEY_PREFIX = "dwelling:score:";
    private static final Duration TTL = Duration.ofDays(30);

    private final RedisTemplate<String, Object> redisTemplate;   // 여기서만 등장한다

    @Override
    public Optional<Map<SigunguCode, Score>> find(DwellingScoreKey key) {
        try {
            Map<Object, Object> cached = redisTemplate.opsForHash().entries(redisKey(key));
            if (cached == null || cached.isEmpty()) return Optional.empty();
            return Optional.of(toDomain(cached));
        } catch (RuntimeException e) {
            log.warn("[cache] 조회 실패 key={} — 미스로 처리", redisKey(key), e);
            return Optional.empty();               // ★ 캐시 장애를 미스로 흡수
        }
    }

    @Override
    public void put(DwellingScoreKey key, Map<SigunguCode, Score> scores) {
        if (scores.isEmpty()) return;              // 빈 결과는 캐싱하지 않는다
        try {
            String k = redisKey(key);
            redisTemplate.opsForHash().putAll(k, toRaw(scores));
            redisTemplate.expire(k, TTL);          // ★ putAll 뒤 expire는 한 쌍
        } catch (RuntimeException e) {
            log.warn("[cache] 저장 실패 key={}", redisKey(key), e);   // 저장 실패는 삼킨다
        }
    }

    private String redisKey(DwellingScoreKey key) {
        return KEY_PREFIX + key.type().name() + ":" + key.normalizedBudget().manwon();
    }
}
```

**규칙**
1. **키 조립·TTL·직렬화는 어댑터 안에서만.** 상수는 `private static final`.
2. **Hash는 `putAll` 직후 `expire`를 반드시 호출한다.** Hash의 `putAll`에는 TTL 인자가 없어 빠뜨리면 **영구 키**가 된다. String은 `ops.set(k, v, ttl)` 한 줄로 되므로 그쪽을 우선한다.
3. **캐시 조회 실패를 미스로 흡수한다.** Redis 장애가 API 500이 되면 안 된다. (As-Is는 예외를 그대로 흘려 500이 된다 — 전환 시 고친다.)
4. **빈 결과를 캐싱하지 않는다.** 히트 판정이 "비어있지 않음"이므로 대칭을 맞춘다.
5. 정본 저장소(`support`)는 다르다. **조회 실패를 삼키지 않고** 도메인 예외로 번역하거나 빈 결과를 명시적으로 반환한다(§6.3).
6. 도메인 타입 ↔ Redis 저장 타입 변환은 어댑터가 한다. **Jackson 역직렬화용 DTO(기본 생성자 + setter)는 `infrastructure/cache` 안에 두고 도메인으로 새어나가지 않게 한다.**

### 3.1 RedisTemplate 빈 (common/config/RedisConfig)

| 빈 | 타입 | 직렬화 |
|---|---|---|
| `redisTemplate` (`@Primary`) | `RedisTemplate<String, Object>` | key `StringRedisSerializer` / value `GenericJackson2JsonRedisSerializer` |
| `supportListRedisTemplate` | `RedisTemplate<String, SupportPolicyListPayload>` | key `StringRedisSerializer` / value `Jackson2JsonRedisSerializer` |

- **키 직렬화는 항상 String.** `setKeySerializer` + `setHashKeySerializer` 둘 다 지정한다. 생략하면 JDK 직렬화로 키가 깨져 `redis-cli`에서 읽을 수 없다.
- 전용 템플릿을 함부로 늘리지 않는다. 제네릭 컬렉션 페이로드일 때만 추가한다.
- 의존성은 `data-redis-reactive`지만 **코드는 동기 `RedisTemplate`을 쓴다.** 리액티브와 혼용하지 않는다.
- **Spring Cache 추상화(`@Cacheable`)를 쓰지 않는다.** 어노테이션이 application 계층에 캐시 관심사를 다시 끌어들이기 때문이다. 포트 + 어댑터로 명시한다.

---

## 4. 키 네이밍

### 4.1 형식

```
<context>:<용도>:<식별자...>
```

| 키 패턴 | 구조 | 포트 | TTL |
|---|---|---|---|
| `job:score:{jobCode\|default}` | Hash `{sigunguCode: score}` | `JobScoreCache` | 12h |
| `infra:score:{LOW\|MID\|HIGH}` | Hash | `InfraScoreCache` | 24h |
| `dwelling:score:{MONTHLY\|JEONSE}:{예산}` | Hash | `DwellingScoreCache` | 30d |
| `support:score:{tag\|default}` | Hash | `SupportScoreCache` | 4d |
| `support:policy:{sigunguCode}:{tag}` | String(JSON) | `SupportPolicyRepository` | 4d |
| `support:policy:{sigunguCode}:{tag}:count` | String(Integer) | `SupportPolicyRepository` | 4d |

> ⚠️ **As-Is에서 반드시 고칠 것**
> 현재 정책 원본 키는 `11110:주거지원` / `11110:주거지원:NUM`으로 **네임스페이스가 없다.** 시군구 코드로 시작해 충돌 위험이 있고 패턴 정리도 어렵다.
> 전환 시 **`support:policy:` 접두어를 붙인다.** 생산자(스케줄러)와 소비자(조회) 양쪽을 동시에 바꿔야 하므로 `support` 컨텍스트 전환 단계에서 한 번에 처리한다.
>
> 또한 As-Is의 `dwelling:score`만 접두어 끝에 콜론이 없어 사용처에서 `+ ":"`를 덧붙인다. **접두어에 콜론을 포함**하는 쪽으로 통일한다.

### 4.2 새 키를 만들 때

1. 네임스페이스 `<context>:<용도>:` 를 정한다.
2. **결과를 결정하는 모든 입력**을 키 값 객체(§2.1)에 담는다. 빠지면 다른 조건의 결과를 잘못 돌려준다.
3. `null` 입력은 **`"default"` 리터럴**로 치환한다.
4. **연속값을 그대로 키에 넣지 않는다.** 반드시 구간화한다.
   - `dwelling:score`는 예산을 `DwellingType.normalize()`로 구간화(월세 20~110 / 10단위, 전세 3000~21000 / 3000단위)해 키 수가 유한하다. 이 보정을 빼면 키가 무한 증식한다.
   - 구간화 규칙은 **도메인 지식**이므로 enum/값 객체에 둔다. 어댑터가 임의로 자르지 않는다.
5. TTL을 §5 기준으로 정한다.
6. 위 표에 행을 추가한다.

---

## 5. TTL 기준

TTL은 **원본 데이터의 갱신 주기보다 짧거나 같게** 잡는다.

| 데이터 성격 | 갱신 주기 | TTL 기준 | 현재 값 |
|---|---|---|---|
| 외부 API 원본(정책) | 스케줄러 3일 | 갱신 주기 + 1일(실패 유예) | 4일 |
| 정책 기반 파생 점수 | 원본과 동일 | 원본 TTL + **명시적 무효화** | 4일 |
| 일자리 점수(RDB 원본, 배치 1회) | 재배포 시 | 반나절 | 12시간 |
| 인프라 점수(RDB 원본, 정적) | 재배포 시 | 하루 | 24시간 |
| 주거 점수(RDB 원본, 월 단위 실거래) | 배치 재실행 시 | 한 달 | 30일 |

**규칙**
- **TTL 없는 키를 만들지 않는다.**
- TTL은 어댑터의 `private static final Duration` 상수로 둔다. 매직 넘버(초 단위 long) 금지.
- **도메인 규칙이 바뀌면 TTL을 기다리지 말고 키 버전을 올린다**: `dwelling:score:v2:...`. 점수 공식을 수정한 배포에서 특히 중요하다(30일 TTL이 옛 결과를 계속 반환한다).
- 값이 §4.1 표와 달라지면 표를 갱신한다.

---

## 6. 정합성 유지

### 6.1 무효화 책임은 유스케이스에 있다

원본을 갱신하는 **유스케이스**가 파생 캐시 무효화까지 책임진다. 어댑터나 스케줄러가 아니다.

```java
package SDD.smash.support.application;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshSupportPolicyService implements RefreshSupportPolicyUseCase {

    private final SigunguCodeQuery sigunguCodeQuery;
    private final SupportPolicyProvider provider;        // 외부 API 포트
    private final SupportPolicyRepository repository;    // 정본 저장 포트
    private final SupportScoreCache scoreCache;          // 파생 캐시 포트

    @Override
    public void refreshAll() {
        long started = System.nanoTime();
        int saved = 0;
        for (SigunguCode code : sigunguCodeQuery.findAllCodes()) {
            for (SupportTag tag : SupportTag.values()) {
                try {
                    repository.saveAll(code, tag, provider.fetch(code, tag));   // 항목 단위 즉시 저장
                    saved++;
                } catch (RuntimeException e) {
                    log.warn("[SupportRefresh] 실패 sigungu={}, tag={}", code.value(), tag, e);
                }
            }
        }
        scoreCache.evictAll();          // ★ 원본이 바뀌었으므로 파생 캐시를 버린다
        log.info("[SupportRefresh] 완료 saved={}, elapsed={}ms", saved, elapsedMs(started));
    }
}
```

**규칙**
- **파생 캐시를 새로 추가하면 그 원본을 갱신하는 유스케이스의 무효화 목록에도 추가한다.** 이 구조에서 가장 흔한 정합성 버그가 이걸 빠뜨리는 것이다.
- 파생 관계를 포트 이름과 주석으로 드러낸다.
- **무효화도 포트 메서드로 표현한다** (`evictAll()`, `evict(key)`). 유스케이스가 키 패턴 문자열을 알면 안 된다.

### 6.2 `evictAll` 구현 — `keys()` 금지

```java
@Override
public void evictAll() {
    // ✅ 삭제 대상을 열거할 수 있으면 직접 만든다
    List<String> keys = new ArrayList<>();
    keys.add(KEY_PREFIX + "default");
    for (SupportTag tag : SupportTag.values()) keys.add(KEY_PREFIX + tag.name());
    redisTemplate.delete(keys);
}
```

> ⚠️ **As-Is 문제**: `redisTemplate.delete(Objects.requireNonNull(redisTemplate.keys("support:score:*")))`
> - `KEYS`는 Redis를 블로킹하는 O(N) 명령이다
> - 매칭 키가 없으면 `keys()`가 빈 셋/`null`을 반환해 **`requireNonNull`에서 NPE로 스케줄러가 죽는다**
>
> **신규 코드에서 `keys()`를 쓰지 않는다.** 열거 가능하면 직접 열거하고, 정말 패턴 스캔이 필요하면 `ScanOptions` 기반 `scan()`을 쓴다.

### 6.3 장애 정책

| 대상 | 실패 시 |
|---|---|
| 파생 캐시 조회 | **미스로 흡수** → 재계산. `log.warn` |
| 파생 캐시 저장 | **삼킨다** → 다음 요청에 재시도. `log.warn` |
| 정본 저장소(`support`) 조회 | 빈 결과를 명시적으로 반환. API 응답에서 해당 필드가 비는 것이 정상 |
| 정본 저장소 저장(스케줄러) | 항목 단위 `catch` 후 continue. `log.warn`. 전체 실패는 `log.error` |

- **캐시 때문에 기능이 죽지 않게 한다.** 이것이 캐시 포트와 저장소 포트를 나눈 이유다.
- 로깅은 어댑터/유스케이스에서 한다. **domain에는 로그를 쓰지 않는다** → global-conventions §5

---

## 7. 테스트

### 7.1 유스케이스 — 포트를 목킹하거나 Fake로

`RedisTemplate`/`HashOperations` 다단계 스텁이 **필요 없어지는 것**이 포트화의 가장 큰 이득이다.

```java
@ExtendWith(MockitoExtension.class)
class DwellingScoreServiceTest {

    @Mock DwellingScoreCache cache;                    // 포트를 목킹
    @Mock DwellingMarketRepository repository;
    @InjectMocks DwellingScoreService service;

    @Test
    @DisplayName("캐시 히트 시 저장소를 조회하지 않는다")
    void 캐시히트시_저장소_미조회() {
        given(cache.find(any())).willReturn(Optional.of(Map.of(new SigunguCode("11110"), Score.of(100))));

        service.scoresFor(DwellingType.MONTHLY, Money.of(60));

        then(repository).shouldHaveNoInteractions();
    }
}
```

여러 테스트가 같은 포트를 쓰면 **인메모리 Fake**가 더 읽기 쉽다.

```java
class InMemoryDwellingScoreCache implements DwellingScoreCache {
    final Map<DwellingScoreKey, Map<SigunguCode, Score>> store = new HashMap<>();
    public Optional<Map<SigunguCode, Score>> find(DwellingScoreKey k) { return Optional.ofNullable(store.get(k)); }
    public void put(DwellingScoreKey k, Map<SigunguCode, Score> v)   { store.put(k, v); }
}
```

### 7.2 어댑터 테스트

- **임베디드 Redis를 도입하지 않는다.** 어댑터의 순수 로직(키 조립, 도메인↔raw 변환)을 분리해 단위 테스트한다.
- `RedisTemplate`을 목킹해야 한다면 `opsForHash()`/`opsForValue()`는 **매번 새 객체를 반환**하므로 반드시 스텁을 건다(안 걸면 NPE).
- 검증 포인트: `putAll` **직후 `expire`가 호출되는지**, 빈 결과일 때 저장하지 않는지, 조회 예외가 `Optional.empty()`로 흡수되는지.

### 7.3 키 값 객체 테스트

```java
@Test
@DisplayName("예산이 구간으로 보정되어 키 카디널리티가 제한된다")
void 예산이_구간화된다() {
    assertThat(DwellingScoreKey.of(DwellingType.MONTHLY, Money.of(63)))
            .isEqualTo(DwellingScoreKey.of(DwellingType.MONTHLY, Money.of(57)));   // 둘 다 60으로 보정
}
```

---

## 8. 체크리스트

**포트**
- [ ] `RedisTemplate`이 `infrastructure/cache` 밖에 등장하지 않는가
- [ ] 포트 이름·시그니처에 기술 용어(`Redis`, `String key`)가 없는가
- [ ] 정본 저장소는 `...Repository`, 파생 캐시는 `...Cache`로 구분됐는가
- [ ] 캐시 키가 **값 객체**로 표현됐는가

**어댑터**
- [ ] 키 조립·TTL·직렬화가 어댑터 안에만 있는가
- [ ] 키에 네임스페이스(`<context>:<용도>:`)가 있고 접두어가 `:`로 끝나는가
- [ ] Hash에서 `putAll` 뒤 `expire`가 한 쌍으로 있는가
- [ ] **TTL을 설정했는가**, §4.1 표를 갱신했는가
- [ ] 연속값을 구간화해 카디널리티를 제한했는가
- [ ] 빈 결과를 캐싱하지 않는가
- [ ] 캐시 조회 실패를 미스로 흡수하는가 (500으로 흘리지 않는가)
- [ ] `keys()`를 쓰지 않았는가

**정합성**
- [ ] 새 파생 캐시를 **원본 갱신 유스케이스의 무효화 목록**에 추가했는가
- [ ] 무효화가 포트 메서드(`evictAll`)로 표현됐는가
- [ ] 캐시 접근이 `@Transactional` 밖에 있는가
- [ ] 점수 공식을 바꿨다면 키 버전을 올렸는가

**테스트**
- [ ] 유스케이스 테스트가 `RedisTemplate`이 아니라 **포트**를 목킹하는가
- [ ] 캐시 히트/미스/저장+TTL 3케이스가 있는가
