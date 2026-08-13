# 배치·점수 계산 내부 분석 (Internal Spec)

> 후속 구현 에이전트용 내부 참고 문서. 조사 시점 브랜치 `feature/add_spring_batch_seed_data`, 커밋 `1270cc8`.
> **이 문서는 조사 결과만 담는다.** 코드는 한 줄도 수정하지 않았다.

> ## ⚠️ 조사 기준 시점 주의
>
> 이 문서는 **커밋 `1270cc8` 시점의 코드**를 기준으로 작성했다.
> 작성 도중(2026-08-13 10:51~10:58) **다른 세션이 같은 작업 트리를 동시에 수정**했다.
> 이 문서의 저자는 그 변경에 관여하지 않았으며, 아래 항목은 **이미 손이 닿은 상태**다.
>
> | 파일 | 상태 | 이 문서의 서술 |
> | --- | --- | --- |
> | `global/batch/BatchGuard.java` | **수정됨** (35줄 → 145줄). `JobExplorer` → `NamedParameterJdbcTemplate` 로 메타 테이블 직접 조회. `getJobInstances(jobName, 0, 20)` 한계 제거, `jobAlreadyCompleted(Map)` / `stepAlreadyCompleted(...)` 추가 | **B.7 과 C-2 는 구버전 기준이다.** C-2 가 지적한 20개 제한은 새 구현에서 해소된 것으로 보인다 |
> | `global/batch/SeedGroup.java` 외 6개 (`SeedStepSpec`, `SeedStepGate`, `SeedDataPrerequisiteInspector`, `SeedMasterJobListener`, `SeedReadiness`, `BatchLaunchResult`) | **신규 추가 (untracked)** | 이 문서에 없다 |
> | `src/test/resources/fixtures/work24/*.html` | 신규 추가 | 이 문서에 없다 |
> | `docs/work24-crawling-assessment.md` | 신규 추가 (다른 저자) | 이 문서와 무관 |
>
> **A절(infra ratio/score), B절 배치 지도, D절 환경, E절 은 위 변경의 영향을 받지 않았다** —
> 해당 파일들은 조사 중 변경되지 않았다. **B.7 과 C-2 만 재확인이 필요하다.**

>
> 표기 규칙
> - **[사실]** — 파일에서 직접 확인. `파일:라인` 인용을 붙였다.
> - **[추론]** — 코드 근거로부터의 해석. 실행 검증은 하지 않았다.
> - **[불명]** — 코드베이스만으로는 판정할 수 없다. 외부 정보나 담당자 확인이 필요하다.

---

## 목차

- [A. infra 컨텍스트의 ratio / score 소비 로직](#a-infra-컨텍스트의-ratio--score-소비-로직)
- [B. 배치 9개 전체 지도](#b-배치-9개-전체-지도)
- [C. 발견된 문제점](#c-발견된-문제점)
- [D. 인프라 / 테스트 환경](#d-인프라--테스트-환경)
- [E. backend.env 변수 이름 목록](#e-backendenv-변수-이름-목록)

---

# A. infra 컨텍스트의 ratio / score 소비 로직

## A.1 데이터 모델 한눈에 보기

infra 컨텍스트는 테이블 2개 위에 서 있다.

| 테이블 | 엔티티 | 성격 | 적재 배치 |
| --- | --- | --- | --- |
| `industry` | `IndustryJpaEntity` | 업종 마스터 (코드 → 이름 → 대분류) | `industryJob` (@Order 6) |
| `infra` | `InfraJpaEntity` | 시군구 × 업종 팩트 (count/ratio/score) | `infraJob` (@Order 7) |

`major`(대분류)는 **`industry` 테이블에만** 있다. `infra` 에는 없다.
그래서 조회 3개 모두 `JOIN IndustryJpaEntity ind ON ind.code = i.industryCode` 로 명시 조인한다
(`InfraJpaRepository.java:15-19` 주석 — FK 객체 참조를 없앴으므로 `i.industry.major` 같은 암시적 경로를 쓸 수 없다).

### 스키마 제약 [사실]

`InfraJpaEntity.java:33-69`

| 컬럼 | 선언 | 라인 | DB 타입 | 허용 범위 |
| --- | --- | --- | --- | --- |
| `id` | `@Id @GeneratedValue(IDENTITY) Long` | `:51-53` | BIGINT AUTO_INCREMENT | — |
| `sigungu_code` | `length = 5, nullable = false` | `:55-56` | `varchar(5) NOT NULL` | — |
| `industry_code` | `length = 10, nullable = false` | `:58-59` | `varchar(10) NOT NULL` | — |
| `count` | `@Column(name = "`​`count`​`") Integer, nullable = false` | `:61-62` | `int NOT NULL` | INT 전 범위 |
| `ratio` | `precision = 18, scale = 2, nullable = false` | `:64-65` | `decimal(18,2) NOT NULL` | ±9,999,999,999,999,999.99 |
| `score` | `precision = 6, scale = 2, nullable = false` | `:67-68` | `decimal(6,2) NOT NULL` | **±9999.99** |

인덱스·제약 (`InfraJpaEntity.java:34-44`) — 이름이 명시돼 있어 바꾸면 중복 인덱스가 새로 생긴다:

| 종류 | 이름 | 컬럼 |
| --- | --- | --- |
| UNIQUE | `uk_infra_sigungu_industry` | `(sigungu_code, industry_code)` |
| INDEX | `idx_infra_sigungu` | `(sigungu_code)` |
| INDEX | `idx_infra_industry` | `(industry_code)` |

`IndustryJpaEntity.java:31-43` — `@Id` 는 **자연키** `industry_code varchar(10) NOT NULL` (`:33-35`),
`major` 는 `@Enumerated(EnumType.STRING)` (`:40-42`) 이므로 DB 에 `"HEALTH"` 같은 **문자열**로 저장된다.

> **[사실] `count` 컬럼 선언이 두 엔티티에서 다르다.**
> `InfraJpaEntity.java:61` 은 백틱 포함 `@Column(name = "`​`count`​`")`,
> `JobCountJpaEntity.java:69` 은 백틱 없이 `@Column(name = "count")`.
> 반면 `InfraBatchConfig.java:154-156` 의 upsert SQL 은 백틱 없이 `count` 를 쓴다.
> MySQL 에서 `COUNT` 는 예약어가 아닌 키워드(non-reserved)라 양쪽 다 파싱되지만, 선언이 갈려 있는 것 자체는 정리 대상이다.

---

## A.2 `Major` enum 4개의 의미

`Major.java:12-35` — 값과 비트만 정의돼 있고 **한글 라벨도, 설명 주석도, 매핑 표도 없다.**

| 상수 | 비트 | 10진 | 선언 |
| --- | --- | --- | --- |
| `HEALTH` | `1 << 3` | 8 | `Major.java:13` |
| `FOOD` | `1 << 2` | 4 | `Major.java:14` |
| `CULTURE` | `1 << 1` | 2 | `Major.java:15` |
| `LIFE` | `1` | 1 | `Major.java:16` |

`fromChoiceMask(int)` (`Major.java:28-34`) 가 비트마스크를 `EnumSet<Major>` 로 푼다.
유효 마스크는 0~15 로 유한하고, 이 유한함이 캐시 키 카디널리티를 16개로 묶는다
(`InfraScoreRedisAdapter.java:43-44, 88-100`).

### 의미를 코드에서 확정할 수 있는가 — **아니다** [사실]

`Major` 값을 실제 업종에 이어 붙이는 유일한 지점은 `industry.csv` 의 `major` 컬럼이다.

```java
// InfraCsvMapper.java:17-23
public static IndustryJpaEntity toIndustryJpaEntity(IndustryCsvRow row) {
    return IndustryJpaEntity.builder()
            .code(normalize(row.code()))
            .name(normalize(row.name()))
            .major(Major.valueOf(normalize(row.major())))   // ← 여기가 유일한 결정 지점
            .build();
}
```

그런데 **`data/industry.csv` 는 저장소에 존재하지 않는다** [사실].
`data/` 에는 `infra.csv`, `level_middle.csv`, `level_top.csv`, `sido.csv`, `sigungu.csv` 5개뿐이다.
`industry.filePath` 의 기본값도 빈 문자열이다 (`application-dev.properties:60`, `application-prod.properties:58`).

→ **결론: 4개 대분류의 실제 의미는 이 코드베이스만으로 확정 불가다. [불명]**

### 확보 가능한 간접 근거

**1) `data/infra.csv` 의 `opnSvcId` 14종** [사실]

파일 헤더는 `sigungu_code,opnSvcId,num` 이고, 264개 시군구 × 14개 업종 = 3,696 데이터 행이다.
(`data/infra.csv:1`, 총 3,697줄, UTF-8 BOM `EF BB BF` 있음)

| opnSvcId | 행 수 | `num` 합계 | 시군구 평균 |
| --- | ---: | ---: | ---: |
| `07_24_04_P` | 264 | 1,037,553 | 3,930.1 |
| `07_24_05_P` | 264 | 312,866 | 1,185.1 |
| `05_18_01_P` | 264 | 310,597 | 1,176.5 |
| `01_01_02_P` | 264 | 116,530 | 441.4 |
| `03_09_01_P` | 264 | 44,582 | 168.9 |
| `01_01_06_P` | 264 | 40,488 | 153.4 |
| `06_20_01_P` | 264 | 29,732 | 112.6 |
| `03_05_05_P` | 264 | 28,088 | 106.4 |
| `10_42_01_P` | 264 | 26,571 | 100.6 |
| `11_44_01_P` | 264 | 8,321 | 31.5 |
| `01_01_01_P` | 264 | 6,528 | 24.7 |
| `03_13_02_P` | 264 | 5,794 | 21.9 |
| `03_05_04_P` | 264 | 3,777 | 14.3 |
| `10_37_01_P` | 264 | 485 | 1.8 |

**2) 데이터 출처** [사실] — `README.md:65, 83` 이 데이터 소스로 **LOCALDATA**(지방행정인허가데이터)를 명시한다.
`opnSvcId` 는 LOCALDATA 의 "개방서비스ID" 필드명과 일치한다.

**3) `IndustryJpaEntity.code` 가 `length = 10`** [사실] — `opnSvcId` 값 `11_44_01_P` 가 정확히 10자다
(`.claude/skills/seed-data/SKILL.md:295` 도 같은 점을 지적한다). `opnSvcId` → `industry_code` 로 그대로 쓸 의도로 보인다 [추론].

### 대분류 의미 추정 — **전부 [추론], 검증 필요**

> 아래는 enum 이름의 통상적 의미와 LOCALDATA 분류 관행에서 끌어낸 것이다.
> **코드·데이터 어디에도 근거가 없으므로 그대로 신뢰하지 말 것.**

| 상수 | 추정 의미 | 확신도 |
| --- | --- | --- |
| `HEALTH` | 보건·의료 (병원, 의원, 약국 등) | 이름 기반 추정만 |
| `FOOD` | 음식점 (일반음식점, 휴게음식점 등) | 이름 기반 추정만 |
| `CULTURE` | 문화·여가 (체육시설, 공연장, 노래연습장 등) | 이름 기반 추정만 |
| `LIFE` | 생활편의 (이·미용, 세탁, 목욕 등 위생업) | 이름 기반 추정만 |

`opnSvcId` 개별 코드가 어느 대분류에 속하는지는 **판정하지 않는다**.
LOCALDATA 코드표를 외부에서 받아와 대조하는 것이 유일한 확정 경로다.

> **후속 작업 지침**
> `industry.csv` 를 만들 때 `major` 값이 `HEALTH|FOOD|CULTURE|LIFE` 중 하나가 아니면
> `Major.valueOf()` (`InfraCsvMapper.java:21`) 가 `IllegalArgumentException` 을 던져 **배치 전체가 실패**한다.
> Processor 에 예외 흡수가 없으므로(`IndustryBatchConfig.java:80-82`) 한 행만 틀려도 Step 이 FAILED 다.

---

## A.3 3개 JPQL 이 `count` / `ratio` / `score` 를 쓰는 방식

`InfraJpaRepository.java` 전체가 이 3개 쿼리다.

### ① `findMajorSummary` — `InfraJpaRepository.java:22-35`

```jpql
SELECT new ...projection.MajorInfraSummaryRow(
    ind.major,
    SUM(i.count),
    AVG(i.score)
)
FROM InfraJpaEntity i
JOIN IndustryJpaEntity ind ON ind.code = i.industryCode
WHERE i.sigunguCode = :sigunguCode
  AND ind.major = :major
GROUP BY ind.major
```

| 컬럼 | 사용 | 결과 타입 |
| --- | --- | --- |
| `count` | **`SUM`** — 해당 시군구·대분류에 속한 모든 업종 개수의 총합 | `Long` (`MajorInfraSummaryRow.java:6`, 주석: "SUM(int) 은 Long") |
| `ratio` | **미사용** | — |
| `score` | **`AVG`** — 대분류 안 업종들의 점수 평균 | `Double` |

반환은 `Optional` — 시군구·대분류 조합에 행이 하나도 없으면 비어 있다.
`InfraQueryService.getMajorInfraSummaries` (`InfraQueryService.java:36-51`) 가 `Major.values()` 4개를 순회하며
비어 있으면 **예외 없이 로그만 남기고 목록에서 뺀다** (`:44-49`).

### ② `findIndustryCounts` — `InfraJpaRepository.java:37-48`

```jpql
SELECT new ...projection.IndustryCountRow(
    ind.major,
    ind.name,
    i.count,
    i.ratio
)
FROM InfraJpaEntity i
JOIN IndustryJpaEntity ind ON ind.code = i.industryCode
WHERE i.sigunguCode = :sigunguCode
```

| 컬럼 | 사용 | 결과 타입 |
| --- | --- | --- |
| `count` | **집계 없이 행 값 그대로** | `Integer` |
| `ratio` | **집계 없이 행 값 그대로** | `BigDecimal` |
| `score` | **미사용** | — |

`GROUP BY` 가 없다. 한 시군구의 업종 행을 전부 그대로 돌려준다.
**`ratio` 가 등장하는 유일한 조회 쿼리다.**

### ③ `findRegionMajorScores` — `InfraJpaRepository.java:50-61`

```jpql
SELECT new ...projection.RegionMajorScoreRow(
    i.sigunguCode,
    ind.major,
    AVG(i.score)
)
FROM InfraJpaEntity i
JOIN IndustryJpaEntity ind ON ind.code = i.industryCode
WHERE ind.major IN :majors
GROUP BY i.sigunguCode, ind.major
```

| 컬럼 | 사용 | 결과 타입 |
| --- | --- | --- |
| `count` | **미사용** | — |
| `ratio` | **미사용** | — |
| `score` | **`AVG`**, `(시군구, 대분류)` 단위 그룹 | `Double` |

시군구 조건이 없다 — **전 시군구를 한 번에** 가져온다. 추천 점수 계산의 원천이다.
`GROUP BY i.sigunguCode, ind.major` 이므로 **(시군구, 대분류) 쌍당 행은 최대 1개**다. 이 사실이 A.4 계산의 전제다.

### 요약표

| 쿼리 | `count` | `ratio` | `score` | 그룹 단위 | 소비처 |
| --- | --- | --- | --- | --- | --- |
| `findMajorSummary` | `SUM` | — | `AVG` | (시군구 고정) 대분류 | 화면 통계 |
| `findIndustryCounts` | 행 그대로 | 행 그대로 | — | 없음 (행 단위) | 화면 상세 |
| `findRegionMajorScores` | — | — | `AVG` | 시군구 × 대분류 | **추천 점수** |

> **[사실] `AVG(i.score)` 가 두 경로로 갈라지고, 두 경로의 계약이 다르다.**
> - `findMajorSummary` → `MajorInfraSummary.averageScore` (`Double` 원시값, `MajorInfraSummary.java:9`)
>   → API 로 **범위 검증 없이 그대로** 나간다. 클래스 주석(`:6-8`)이 "추천 점수가 아니므로 범위를 강제하지 않는다"고 명시.
> - `findRegionMajorScores` → `InfraScorePolicy` → **`Score.of()` 로 0~100 강제**.
>
> 같은 컬럼의 같은 집계값에 한쪽은 제약이 없고 한쪽은 예외를 던진다.

---

## A.4 `infra.score` 의 허용 범위 역산 — **핵심 결론**

### 계산 경로

```
infra.score  (decimal(6,2))
  └ AVG(i.score) GROUP BY sigungu, major      InfraJpaRepository.java:50-61
      └ RegionMajorScoreRow.averageScore (Double)   RegionMajorScoreRow.java:6
          └ RegionMajorScore.averageScore            InfraJpaMapper.java:26-28
              └ InfraScorePolicy.scores(...)         InfraScorePolicy.java:25-44
                  └ Score.of((int) Math.round(sum / divisor))   InfraScorePolicy.java:41
                      └ Score 생성자 검증 → DomainException      Score.java:19-23
```

### 정책 코드 (`InfraScorePolicy.java:25-44`)

```java
if (selectedMajors.isEmpty()) return Map.of();          // :27-29

Map<SigunguCode, Double> sumBySigungu = new HashMap<>();
for (RegionMajorScore row : regionScores) {
    double toAdd = row.averageScore() == null ? 0.0 : row.averageScore();
    sumBySigungu.merge(row.sigunguCode(), toAdd, Double::sum);   // :35  시군구별 합산
}

int divisor = selectedMajors.size();                     // :38  선택한 대분류 개수 N
for (...) {
    result.put(key, Score.of((int) Math.round(value / divisor)));  // :41
}
```

### 기호 정의

- `N` = 사용자가 고른 대분류 개수, `N ∈ {1, 2, 3, 4}` (0이면 `:27-29` 에서 빈 맵으로 조기 반환)
- `A(g, m)` = 시군구 `g`, 대분류 `m` 의 `AVG(infra.score)`
- 해당 `(g, m)` 행이 아예 없으면 합계에 **아무 기여도 없다**(0을 더하는 것과 동일)
- 결과 `v(g) = Math.round( Σ_{m∈S} A(g,m) / N )`
- 요구 조건: `0 ≤ v(g) ≤ 100` — 벗어나면 `Score.java:21` 이 `DomainException(SCORE_OUT_OF_RANGE)`

### 상한 도출

`Σ_{m∈S} A(g,m) / N` 은 **선택한 N개 대분류 평균값들의 산술평균**이다(결측은 0으로 취급).
산술평균은 언제나 구성 요소의 최댓값 이하다.

```
Σ A(g,m) / N  ≤  max_{m∈S} A(g,m)
```

**분모가 N이므로 대분류를 여러 개 골라도 값이 커지지 않는다.**
분자의 상한이 `N × max(A)` 이고 분모가 `N` 이라 상한은 `max(A)` 로 고정된다.
따라서 **`N = 1` 이 가장 빡빡한 경우**이고, 그때 조건은 `Math.round(A) ≤ 100` → `A < 100.5`.

### 하한 도출

결측 대분류는 0을 기여하므로 값을 **낮추는 방향으로만** 작동한다.
하한이 깨지는 건 `A` 자체가 음수일 때뿐이다. 역시 `N = 1` 이 최악이고,
`Math.round(A) ≥ 0` → `A ≥ -0.5`.

### 결론

> ### 집계 기준 (필요충분조건) [사실]
> 모든 `(시군구, 대분류)` 조합에 대해
> **`AVG(infra.score) ∈ [-0.5, 100.5)`** 여야 `SCORE_OUT_OF_RANGE` 가 발생하지 않는다.

> ### 행 기준 (운영에서 강제해야 할 규칙) [사실]
> **`infra.score` 의 모든 행 값이 `[0, 100]` 이면 언제나 안전하다.**
>
> 근거: `AVG` 는 항상 `[min, max]` 안에 있으므로 모든 행이 `[0,100]` → 모든 `A(g,m)` 도 `[0,100]`
> → 평균도 `[0,100]` → `Math.round` 결과도 `[0,100]`. `A = 100.0` 이어도 `round(100.0) = 100` 으로 정확히 상한에 걸린다.
>
> 이는 **충분조건이지 필요조건은 아니다.** 100 초과 행이 있어도 같은 그룹의 낮은 값들이 섞여
> `AVG` 가 100 이하로 희석되면 우연히 통과한다. 그런 우연에 기대면 안 되므로 행 단위 `[0, 100]` 을 규칙으로 삼는다.

### 질문에 대한 직답

> "여러 대분류를 선택하면 합이 커지는데 N으로 나누므로…"

**커지지 않는다.** `N` 으로 나누기 때문에 상한은 대분류 개수와 무관하게 `max(A)` 로 고정된다.
오히려 선택한 대분류 중 데이터가 없는 것이 있으면 0이 더해진 뒤 `N` 으로 나뉘어
**점수가 부당하게 낮아진다** — 이쪽이 실제 위험이다 (C-11 참고).

> "각 대분류 평균 score 가 0~100 이면 안전한가?"

**안전하다.** 위 상한 도출대로 `N ≥ 2` 는 `N = 1` 보다 항상 느슨하다.

### 스키마 제약과 도메인 제약의 간극 [사실]

| 계층 | 허용 범위 | 근거 |
| --- | --- | --- |
| DB 컬럼 | `-9999.99 ~ 9999.99` | `InfraJpaEntity.java:67` `precision=6, scale=2` |
| CSV 파싱 | 무제한 (`BigDecimal`) | `InfraBatchConfig.java:123-124` |
| 배치 Processor | **검증 없음** | `InfraBatchConfig.java:132-149` — sigungu_code / industry_code 존재만 확인 |
| 도메인 (`Score`) | `0 ~ 100` | `Score.java:16-23` |

**CHECK 제약도, 배치 검증도 없다.** 100 초과 `score` 가 CSV 에 있으면:

1. `infraJob` 은 **성공(COMPLETED)** 한다.
2. `GET /api/detail` 의 `infraMajors[].score` 도 **정상 응답**한다 (범위 검증 없는 경로).
3. `POST /api/recommend` 에서 해당 대분류를 선택한 순간 `DomainException(SCORE_OUT_OF_RANGE)` 이 터진다.
4. `ErrorCodeHttpMapper.java:25` 매핑에 따라 **HTTP 400 Bad Request** 로 나간다.

> **[추론] 이건 사용자 입력 오류가 아니라 데이터 적재 오류인데 400 으로 나간다.**
> 원인 판별이 어렵고, 실패 지점이 적재 시점에서 조회 시점으로 미뤄진다.
> 후속 구현에서 `InfraBatchConfig` Processor 에 범위 검증을 넣는 것을 검토할 만하다
> (persistence-conventions §7.2 규칙 5 — "도메인 불변식은 Processor 에서 값 객체 생성으로 검증, `catch (DomainException e) { return null; }` 로 skip").

---

## A.5 `ratio` 는 어디서 소비되는가

### 전체 추적 [사실]

**쓰기 경로**

| 단계 | 위치 | 처리 |
| --- | --- | --- |
| CSV 4번째 컬럼 읽기 | `InfraBatchConfig.java:121-122` | `new BigDecimal(normalize(...)).setScale(2, RoundingMode.HALF_UP)` |
| CSV 행 DTO | `InfraCsvRow.java:12-13` | `BigDecimal ratio` |
| Processor 통과 | `InfraBatchConfig.java:145` | `.ratio(row.ratio())` — 가공 없음 |
| Upsert DTO | `InfraUpsertRow.java:25` | `BigDecimal ratio` |
| INSERT | `InfraBatchConfig.java:154-155` | `:ratio` 바인딩 |

**읽기 경로**

| 단계 | 위치 | 처리 |
| --- | --- | --- |
| JPQL 프로젝션 | `InfraJpaRepository.java:42` | `i.ratio` — 집계 없음 |
| 기술 DTO | `IndustryCountRow.java:8` | `BigDecimal ratio` |
| 도메인 매핑 | `InfraJpaMapper.java:21-24` | `row.ratio()` 그대로 — `count` 만 null 방어, `ratio` 는 방어 없음 |
| 도메인 모델 | `IndustryCount.java:11` | `BigDecimal ratio` (`RegionInfra` Aggregate 내부) |
| Aggregate | `RegionInfra.java:15-32` | `industryCounts()` 로만 노출 |
| 유스케이스 | `InfraQueryService.java:53-60` | `getInfraDetails(sigunguCode)` |
| application DTO | `IndustryCountView.java:9, 12` | `ratio` 그대로 |
| recommendation DTO | `IndustryDetailItem.java:9, 13` | `ratio` 그대로 (필드명 `ratio` 유지) |
| presentation DTO | `DetailResponse.java:43` | `List<IndustryDetailItem> infraDetails` |
| API | `DetailController.java:34-49` | **`GET /api/detail?sigunguCode=...`** 응답 JSON |

### 어느 API 로 나가는가 [사실]

**`GET /api/detail` 단 하나다.** (`DetailController.java:34`)

응답 JSON 형태:
```json
{
  "infraDetails": [
    { "major": "FOOD", "name": "...", "num": 123, "ratio": 45.67 }
  ]
}
```

`ratio` 는 `RegionDetailService.details()` (`RegionDetailService.java:68-70`) 경로로만 채워진다.

> **[사실] `POST /api/recommend` 에는 `ratio` 가 없다.**
> `RecommendRegionService` 는 `infraQueryService.getMajorInfraSummaries()` 만 호출하고
> (`RecommendRegionService.java:115-117`), 이 경로에는 `ratio` 가 등장하지 않는다(`findMajorSummary` 는 `ratio` 미사용).

### 값의 의미와 범위 — **[불명]**

**`ratio` 로 산술 연산·비교·포맷팅·정규화를 하는 코드가 코드베이스에 단 한 곳도 없다.**
(`ratio` 전수 grep 결과 — 위 표의 pass-through 지점이 전부다.)

판정 근거로 쓸 수 있는 신호와 그 한계:

| 신호 | 0~1 비율 해석 | 0~100 퍼센트 해석 | 판정력 |
| --- | --- | --- | --- |
| `scale = 2` (`InfraJpaEntity.java:64`) | 0.00/0.01/…/1.00 → **101단계뿐**, 지나치게 거칠다 | 0.00~100.00 → 자연스럽다 | 퍼센트 쪽에 **약간** 유리. 결정적이지 않음 |
| `precision = 18` | 과도 | 과도 | **판정력 없음.** As-Is 컬럼 정의를 그대로 옮긴 결과로 보임 [추론] |
| `setScale(2, HALF_UP)` (`InfraBatchConfig.java:122`) | 0~1 이면 유효숫자가 2자리로 잘려 정보 손실 | 문제 없음 | 퍼센트 쪽에 유리 |
| `nullable = false` | — | — | 빈 값이면 `NumberFormatException` |
| 실측 데이터 | — | — | **불가.** 현재 `data/infra.csv` 에 `ratio` 컬럼 자체가 없다 |
| 소비 코드 | — | — | **없음.** 판정 불가 |
| 필드명 `ratio` | "비율"이면 0~1 | — | 이름만으로는 근거 부족 |

> ### 결론: **불명확** [불명]
> 코드베이스만으로는 `ratio` 가 0~1 비율인지 0~100 퍼센트인지 **판별할 수 없다.**
> `scale = 2` 와 `setScale(2, HALF_UP)` 이 퍼센트 해석에 약간 유리하지만 결정적 근거가 아니다.
>
> **후속 구현 지침**
> - `ratio` 값의 의미는 **원본 데이터 산출 담당자에게 확인해야 한다.**
> - 확정되면 `IndustryCount.java` 의 클래스 주석과 `InfraJpaEntity.java:64` 에 단위를 명시할 것.
> - 프런트가 `%` 를 붙여 표시하는지 확인하면 실질적 계약을 알 수 있다(백엔드 저장소 범위 밖).

> ### 추가 [사실] — `ratio` 는 재적재해도 갱신되지 않는다
> `InfraBatchConfig.java:156`
> ```sql
> ON DUPLICATE KEY UPDATE count = VALUES(count)
> ```
> UPDATE 절에 **`count` 만 있다.** `ratio` 와 `score` 는 빠져 있다.
> `SEED_VERSION` 을 올려 `infraJob` 을 다시 돌려도 기존 행의 `ratio`/`score` 는 **최초 INSERT 값 그대로 남는다.**
> `uk_infra_sigungu_industry` 가 걸려 있으므로 INSERT 도 안 되고 UPDATE 도 두 컬럼을 건드리지 않는다. → C-3 참고.

---

## A.6 recommendation 최종 합산에서 infra 점수의 위치

### 흐름 [사실]

```
InfraScoreService.scoresFor(infraChoice)          InfraScoreService.java:45-69
  → Map<SigunguCode, Score>
RecommendRegionService.recommend(command)          RecommendRegionService.java:58-121
  → RegionScorePolicy.combine(...)                 RegionScorePolicy.java:40-51
  → RegionScorePolicy.selectTopTenRenormalized()   RegionScorePolicy.java:60-75
```

### `InfraScoreService` 의 순서 [사실] — `InfraScoreService.java:45-69`

As-Is 순서를 의도적으로 유지한다(주석 `:23-29`).

| 단계 | 라인 | 내용 |
| --- | --- | --- |
| 1 | `:50-53` | **캐시 먼저 확인.** 선택 항목 판정보다 앞이다 |
| 2 | `:56` | `Major.fromChoiceMask(infraChoice == null ? 0 : infraChoice)` |
| 3 | `:57-59` | 선택 대분류가 없으면 **빈 맵 반환. 캐시에 쓰지 않는다** |
| 4 | `:62-66` | 원천 조회 → 정책 적용 → 캐시 저장 |

`@Transactional` 을 붙이지 않는다 (`:31-33` 주석 — 캐시 접근을 트랜잭션으로 감싸면 커넥션을 쥔 채 네트워크 대기).

캐시: `InfraScoreRedisAdapter` — 키 `infra:score:{infraChoice}`, Hash `{시군구코드: 점수(Integer)}`, **TTL 24시간** (`InfraScoreRedisAdapter.java:33, 36`).

### `RegionScorePolicy.combine` — **비대칭 나눗셈** [사실]

`RegionScorePolicy.java:40-51`

```java
int div = 4;
if (!supportSelected) div--;
if (!infraSelected)   div--;
int sum = jobScore.value() + dwellingScore.value() + supportScore.value() + infraScore.value();
return Score.of(sum / div);       // 정수 나눗셈 — 소수점 버림
```

| 축 | 분모에 항상 포함? | 근거 |
| --- | --- | --- |
| job | **예** | `RegionScorePolicy.java:42` |
| dwelling | **예** | 동일 |
| support | 선택했을 때만 | `:43-45` |
| infra | **선택했을 때만** | `:46-48` |

`infraSelected` 판정은 `RecommendRegionService.java:123-125`:
```java
private boolean isSelected(Integer choice) { return choice != null && choice != 0; }
```

> **[사실] `infraChoice` 판정이 두 곳에서 각각 이뤄지고 기준이 다르다.**
> - `RecommendRegionService.isSelected()` — `choice != null && choice != 0`
> - `InfraScorePolicy` — `Major.fromChoiceMask(choice).isEmpty()`
>
> **[추론] 두 판정이 갈리는 입력이 존재한다.** 예: `infraChoice = 16`
> → `isSelected` 는 `true`(0이 아님) → 분모 `div` 에 포함
> → `fromChoiceMask(16)` 은 상위 비트를 무시해 **빈 집합** → `scoresFor` 는 빈 맵
> → `infraScores.getOrDefault(code, Score.ZERO)` (`RecommendRegionService.java:81`) 로 전 지역 0점
> → **분모는 4인데 분자에 infra 기여가 0** → 모든 지역 점수가 부당하게 낮아진다.
>
> `InfraScoreKey.java:6-9` 주석이 15 초과 마스크의 캐시 키 문제를 별도로 언급하고 있으나,
> 분모 오염 쪽은 언급이 없다. UI 선택지가 4개뿐이라 실제로는 발생하지 않는다는 전제다(같은 주석).

### `selectTopTenRenormalized` [사실] — `RegionScorePolicy.java:60-75`

1. 점수 내림차순 정렬 (`:62`)
2. 상위 10개 (`:64`, `MAX_RESULTS = 26`번 줄 상수 `10`)
3. **1위 점수를 100으로 재정규화**: `round(candidate / maxScore * 100)` (`:71`)

전원 0점이면 `0/0 = NaN` → `Math.round(NaN) = 0` → 전원 0점 (`:56-58` 주석이 As-Is 재현임을 명시).
빈 목록은 방어하지 않는다 (`:66` 주석).

제외 시도 [사실] — `RegionScorePolicy.java:23-24`: **서울(11) / 경기(41) / 인천(28)**.

### infra 점수가 최종에 미치는 영향 요약

| 상황 | infra 기여 | 분모 |
| --- | --- | --- |
| `infraChoice` = null 또는 0 | 없음 | 3 (support 도 미선택이면 2) |
| `infraChoice` ∈ 1~15 | `InfraScorePolicy` 결과 | 4 (support 미선택이면 3) |
| `infraChoice` > 15 [추론] | **0점 (버그)** | 4 — 분모만 늘어난다 |
| `infra` 테이블이 빈 경우 | 전 지역 0점 | 선택했다면 4 |

> **[사실] 현재 `infra` 테이블은 비어 있을 가능성이 높다.**
> `seed.jobs.infra.enabled=false` (`application-prod.properties:66`) 이고,
> `.claude/skills/seed-data/SKILL.md:287` 도 "infra 가 비면 `RegionMajorScoreRepository` 결과가 없어
> 지역추천 인프라 점수가 항상 0" 이라고 같은 결론을 적어 두었다.

---

# B. 배치 9개 전체 지도

## B.1 마스터 표

시드 배치 8개 + dwelling 1개 = **총 9개**.

| # | Job 이름 | Step 이름 | Runner 클래스 | `@Order` | 활성화 프로퍼티 (dev/prod 기본값) | 입력 | 출력 테이블 | Writer 방식 | JobParameters |
| ---: | --- | --- | --- | ---: | --- | --- | --- | --- | --- |
| 1 | `SidoJob` | `SidoStep` (chunk 10) | `address...runner.SidoBatchRunner` | 1 | `seed.jobs.sido.enabled` (**true**) | `${sido.filePath}` → `data/sido.csv`, UTF-8 | `sido` | **JPA** `RepositoryItemWriter.save` | `seedVersion` |
| 2 | `SigunguJob` | `SigunguStep` (50) | `address...runner.SigunguBatchRunner` | 2 | `seed.jobs.sigungu.enabled` (**true**) | `${sigungu.filePath}` → `data/sigungu.csv`, UTF-8 | `sigungu` | **JPA** `save` | `seedVersion` |
| 3 | `jcTopJob` | `jcTopStep` (10) | `job...runner.JobCodeTopBatchRunner` | 3 | `seed.jobs.job-code-top.enabled` (**true**) | `${jobCodeTop.filePath}` → `data/level_top.csv`, UTF-8 | `job_code_top` | **JPA** `save` | `seedVersion` |
| 4 | `jcMiddleJob` | `jcMiddleStep` (100) | `job...runner.JobCodeMiddleBatchRunner` | 4 | `seed.jobs.job-code-middle.enabled` (**true**) | `${jobCodeMiddle.filePath}` → `data/level_middle.csv`, UTF-8 | `job_code_middle` | **JPA** `save` | `seedVersion` |
| 5 | `PopulationJob` | `populationStep` (100) | `address...runner.PopulationBatchRunner` | 5 | `seed.jobs.population.enabled` (false) | `${population.filePath}` → **빈 값**, MS949 | `Population` (SQL) / `population` (엔티티) | **JDBC upsert** | `seedVersion` |
| 6 | `industryJob` | `industryStep` (20) | `infra...runner.IndustryBatchRunner` | 6 | `seed.jobs.industry.enabled` (false) | `${industry.filePath}` → **빈 값**, UTF-8 | `industry` | **JPA** `save` | `seedVersion` |
| 7 | `infraJob` | `infraStep` (**500**) | `infra...runner.InfraBatchRunner` | 7 | `seed.jobs.infra.enabled` (false) | `${infra.filePath}` → `data/infra.csv`, **MS949** | `infra` | **JDBC upsert** | `seedVersion` |
| 8 | `jobCountJob` | `jobCountStep` (**1000**) | `job...runner.JobCountBatchRunner` | 8 | `seed.jobs.job-count.enabled` (false) | `${jobCount.filePath}` → **빈 값**, MS949 | `JobCount` | **JDBC upsert** | `seedVersion` |
| 9 | `dwellingJob` | `dwellingStep` (10) | `dwelling.infrastructure.batch.DwellingBatchRunner` | 9 | **없음 — 항상 실행** | **국토부 전월세 API** | `Dwelling` (SQL) / `dwelling` (엔티티) | **JDBC upsert** | `dealYmd`, `months`, `seedVersion`, `triggerTime` |

정의 위치: `SidoBatchConfig.java:49-64` / `SigunguBatchConfig.java:73-88` / `JobCodeTopBatchConfig.java:57-71` /
`JobCodeMiddleBatchConfig.java:83-97` / `PopulationBatchConfig.java:68-83` / `IndustryBatchConfig.java:41-56` /
`InfraBatchConfig.java:86-101` / `JobCountBatchConfig.java:85-101` / `DwellingBatchConfig.java:63-85`

## B.2 Runner 공통 사항 [사실]

- **9개 모두 `ApplicationRunner`/`CommandLineRunner` 를 구현하지 않는다.** 전부 `@EventListener(ApplicationReadyEvent.class)` 메서드다.
- `spring.batch.job.enabled=false` (`application-dev.properties:4`, `application-prod.properties:4`, `src/test/resources/application.properties:10`) 로 Boot 자동 실행을 끄고 수동 `jobLauncher.run()` 한다.
- **`@Async` 는 `SidoBatchRunner.java:42` 한 곳뿐이다.** (C-1 참고)
- `DwellingBatchRunner` 만 `...batch.runner` 가 아니라 `...infrastructure.batch` 바로 아래 있다.
- `DwellingBatchRunner` 만 전체를 `try/catch(Exception)` 로 감싸 예외를 삼킨다 (`DwellingBatchRunner.java:53, 71-73`). 나머지 8개는 `throws Exception` 으로 전파.
- `JobCountBatchRunner.java:46-49` 만 `jobExplorer.findRunningJobExecutions()` 로 중복 실행을 추가 방어한다.

## B.3 Reader 상세 [사실]

| Job | 인코딩 | `linesToSkip` | `quoteCharacter` | `names(...)` 선언 컬럼 | `@StepScope` | 컬럼 수 검증 |
| --- | --- | ---: | --- | --- | :---: | :---: |
| SidoJob | UTF-8 | 1 | `'\0'` | `sido_code, name` | **O** (`:67`) | X |
| SigunguJob | UTF-8 | 1 | `'\0'` | `sigungu_code, sido_code, name` | **O** (`:91`) | X |
| jcTopJob | UTF-8 (`:79`) | 1 | 기본값 `"` | `code, name` | **X** | **O** (`:87-91`) |
| jcMiddleJob | UTF-8 (`:105`) | 1 | 기본값 `"` | `code, name, upstreamCode` | **X** | **O** (`:113-117`) |
| PopulationJob | **MS949** | 1 | `'\0'` | `sigungu_code, population` | **O** (`:86`) | X |
| industryJob | UTF-8 (`:65`) | 1 | `'\0'` | `code, name, major` | **O** (`:59`) | X |
| infraJob | **MS949** (`:110`) | 1 | `'\0'` | `sigungu_code, industry_code, count, ratio, score` (`:116`) | **O** (`:104`) | X |
| jobCountJob | **MS949** | 1 | `'\0'` | `sigungu_code, job_code, count` | **O** (`:104`) | X |
| dwellingJob | — (CSV 아님) | — | — | — | **O** (`:91`) | — |

CSV Reader 8개 모두 `FileSystemResource` + `.strict(true)` + `.delimiter(",")`.

### 실제 CSV 파일 인코딩 실측 [사실]

| 파일 | 실측 | 리더 선언 | 일치? |
| --- | --- | --- | :---: |
| `data/sido.csv` | UTF-8 (BOM 없음) | UTF-8 | ✅ |
| `data/sigungu.csv` | UTF-8 (BOM 없음) | UTF-8 | ✅ |
| `data/level_top.csv` | UTF-8 (BOM 없음, CRLF) | UTF-8 | ✅ |
| `data/level_middle.csv` | UTF-8 (BOM 없음) | UTF-8 | ✅ |
| `data/infra.csv` | **UTF-8 + BOM (`EF BB BF`)** | **MS949** | ⚠️ C-4 참고 |

## B.4 Writer 상세

### RepositoryItemWriter (5개) [사실]

| Job | Writer 빈 | repository | methodName | 라인 |
| --- | --- | --- | --- | --- |
| SidoJob | `SidoWriter()` | `SidoJpaRepository` | `"save"` | `SidoBatchConfig.java:92-98` |
| SigunguJob | `SigunguWriter()` | `SigunguJpaRepository` | `"save"` | `SigunguBatchConfig.java:125-131` |
| jcTopJob | `jcTopWriter()` | `JobCodeTopJpaRepository` | `"save"` | `JobCodeTopBatchConfig.java:105-110` |
| jcMiddleJob | `jcMiddleWriter()` | `JobCodeMiddleJpaRepository` | `"save"` | `JobCodeMiddleBatchConfig.java:139-144` |
| industryJob | `industryWriter()` | `IndustryJpaRepository` | `"save"` | `IndustryBatchConfig.java:85-91` |

### JdbcBatchItemWriter (4개) [사실]

4개 모두 `@Qualifier("dataDBSource") DataSource` + `BeanPropertyItemSqlParameterSourceProvider` + `.assertUpdates(false)`.

| Job | SQL | 라인 |
| --- | --- | --- |
| PopulationJob | `INSERT INTO Population (sigungu_code, population_count) VALUES (:sigunguCode, :population) ON DUPLICATE KEY UPDATE population_count = VALUES(population_count)` | `PopulationBatchConfig.java:122-126` |
| infraJob | `INSERT INTO infra (sigungu_code, industry_code, count, ratio, score) VALUES (:sigunguCode, :industryCode, :count, :ratio, :score) ON DUPLICATE KEY UPDATE count = VALUES(count)` | `InfraBatchConfig.java:153-157` |
| jobCountJob | `INSERT INTO JobCount (sigungu_code, job_code_middle_code, count) VALUES (:sigunguCode, :middleCode, :count) ON DUPLICATE KEY UPDATE count = VALUES(count)` | `JobCountBatchConfig.java:148-152` |
| dwellingJob | `INSERT INTO Dwelling (...) VALUES (...) ON DUPLICATE KEY UPDATE month_avg=VALUES(month_avg), month_mid=..., jeonse_avg=..., jeonse_mid=...` | `DwellingBatchConfig.java:156-164` |

### Upsert DTO ↔ 네임드 파라미터 [사실]

4개 모두 `@Getter @Builder` **클래스**(record 아님) — `BeanPropertyItemSqlParameterSourceProvider` 가 getter 를 요구한다.

| DTO | 필드 | 라인 |
| --- | --- | --- |
| `PopulationUpsertRow` | `sigunguCode(String)`, `population(String)` | `:17-18` |
| `InfraUpsertRow` | `sigunguCode(String)`, `industryCode(String)`, **`count(String)`**, `ratio(BigDecimal)`, `score(BigDecimal)` | `:22-26` |
| `JobCountUpsertRow` | `sigunguCode(String)`, `middleCode(String)`, `count(Integer)` | `:17-19` |
| `DwellingUpsertRow` | `sigunguCode`, `monthAvg(Double)`, `monthMid(Integer)`, `jeonseAvg(Double)`, `jeonseMid(Integer)` | `:17-23` |

## B.5 Job 레벨 listener / fault-tolerance [사실]

| Job | listener | fault-tolerant |
| --- | --- | --- |
| `jobCountJob` | `JobScoreCacheCleaner` (`JobCountBatchConfig.java:87`) — **`beforeJob`** | 없음 |
| `dwellingJob` | `DwellingScoreCacheCleaner` (`DwellingBatchConfig.java:65`) — `afterJob` | **있음** (`:79-83`) |
| 나머지 7개 | **없음** | 없음 |

`dwellingStep` 재시도 정책 (`DwellingBatchConfig.java:79-83`):
`.faultTolerant().retry(ResourceAccessException).retry(SocketTimeoutException).retryLimit(3)` + `FixedBackOffPolicy(1000ms)`

### CacheCleaner 3종

| 클래스 | 시점 | 연결된 Job |
| --- | --- | --- |
| `JobScoreCacheCleaner` | `beforeJob` (`:28-30`) | `jobCountJob` |
| `DwellingScoreCacheCleaner` | `afterJob` (`:25-27`) | `dwellingJob` |
| **`InfraScoreCacheCleaner`** | `afterJob` (`:27-30`) | **없음 — 고아 컴포넌트** → C-8 |

## B.6 dwelling 배치 상세 [사실]

CSV 가 아니라 **국토교통부 아파트 전월세 실거래가 API** 를 쓴다.

**Reader** — `DwellingBatchConfig.java:90-107`, `IteratorItemReader<WorkItem>`
- `@Value("#{jobParameters['dealYmd']}")`, `@Value("#{jobParameters['months']}")` 로 **late binding** (`@StepScope` 필수)
- `dealYmd` 또는 `months` 가 null 이면 `DomainException(NOT_FOUND_YEARMONTH)` (`:95-96`)
- `to = YearMonth.parse(dealYmd, "yyyyMM")`, `from = to.minusMonths(months - 1)`
- 아이템 = `addressQueryService.getAllSigunguCodes()` → **DB 의 전체 시군구 수**
- `WorkItem` = `record WorkItem(SigunguCode sigunguCode, YearMonth from, YearMonth to)` (`WorkItem.java:8`)

**Processor** — `DwellingBatchConfig.java:116-152`
- 시군구 1건당 `from`~`to` 를 1개월씩 순회하며 `rentRecordProvider.fetch(...)` → **아이템당 API 12회 호출**
- 월세: `RentRecord::isMonthly` (`monthlyRent > 0`) 필터 후 `monthlyRent` 수집 (`:129-132`)
- 전세: `RentRecord::isJeonse` (`monthlyRent == 0`) 필터 후 `deposit` 수집 (`:134-137`)
- 양쪽 다 비면 `null` 반환 → Writer 로 안 넘김 (`:139-142`)
- 평균/중앙값은 `RentStatCalculator.mean()` / `median()`

**API 어댑터** — `MolitAptRentApiAdapter.java`
```java
private static final int PAGE_NO = 1;    // :36
private static final int ROWS   = 1000;  // :37   "페이지네이션은 쓰지 않는다"
```
- URL: `{base-url}/{path}?LAWD_CD={sigunguCode}&DEAL_YMD={yyyyMM}&pageNo=1&numOfRows=1000&_type=json&serviceKey={...}` (`:66-86`)
- `serviceKey` 가 이미 인코딩돼 보이면(`%2B`/`%2F`/`%3D`) 재인코딩 회피 (`:77-82, 133-135`)
- 응답 파싱: JSON 우선, 실패 시 **XmlMapper 폴백** (`:100-110`)
- 추출: `root.at("/response/body/items/item")` (`:113`), 단일 객체/배열 양쪽 처리 (`:117-123`)
- 예외는 로그 후 **rethrow** (`:96`) → Step retry 가 작동

**JobParameters** — `DwellingBatchRunner.java:62-67`

| 키 | 타입 | 출처 |
| --- | --- | --- |
| `dealYmd` | String | `@Value("${dwelling.dealYmd}")` ← `${DEALYMD}` |
| `months` | Long | **하드코딩 `12L`** (`:60`) |
| `seedVersion` | String | `SeedProperties.getVersion()` |
| `triggerTime` | Long | `System.currentTimeMillis()` — 매 기동마다 새 `JobInstance` |

## B.7 BatchGuard [사실]

`global/batch/BatchGuard.java` 전문 35줄.

```java
public boolean alreadyDone(String jobName, String seedVersion) {
    List<JobInstance> instances = jobExplorer.getJobInstances(jobName, 0, 20);   // :22
    for (JobInstance instance : instances) {
        List<JobExecution> execs = jobExplorer.getJobExecutions(instance);        // :24
        for (JobExecution exec : execs) {
            JobParameters param = exec.getJobParameters();
            String v = param.getString("seedVersion");                           // :27
            if (seedVersion.equals(v) && exec.getStatus() == BatchStatus.COMPLETED) {  // :28
                return true;
            }
        }
    }
    return false;
}
```

| 항목 | 값 |
| --- | --- |
| 조회 범위 | `getJobInstances(jobName, start=0, count=20)` — **최근 20개 인스턴스만** (`:22`) |
| 파라미터 키 | 고정 문자열 `"seedVersion"` (`:27`) |
| 완료 판정 | **`BatchStatus.COMPLETED` 만** (`:28`). **`ExitStatus` 는 전혀 보지 않는다** |
| 미완료로 보는 상태 | `FAILED`, `STOPPED`, `ABANDONED` → 재실행됨 |

호출부는 9개 Runner 모두 동일 패턴이며, 첫 인자 문자열이 `JobBuilder` 이름과 **전부 일치**함을 대조 확인했다.

---

# C. 발견된 문제점

## 요청받은 항목

### C-1. `SidoBatchRunner` 의 `@Async` 와 `@Order` — **현재는 무해, 잠재적 함정** [사실+추론]

**[사실]** `@Async` 는 `SidoBatchRunner.java:42` 단 한 곳이다.
그런데 **`@EnableAsync` 가 코드베이스 어디에도 없고, `TaskExecutor` 빈 정의도 없다.**
(`src/main/java` 전수 grep — `Async` 문자열은 `SidoBatchRunner.java:14`(import)와 `:42` 뿐)

**[추론]** `@EnableAsync` 가 없으면 Spring 이 async 프록시를 만들지 않는다.
따라서 **`@Async` 는 현재 아무 효과가 없고** `runSidoJobAfterStartup()` 은 동기로 돈다.
결과적으로 `@Order(1)` 기반 FK 선후관계는 **지금은 지켜진다.**

**[추론] 위험 시나리오** — 누군가 `@EnableAsync` 를 추가하는 순간:
1. `SidoJob` 만 별도 스레드로 빠져나간다
2. `@Order(2)` `SigunguJob` 이 Sido 적재 완료 전에 시작된다
3. `SigunguBatchConfig.resolveSidoCode()` (`:62-70`) 가 빈 캐시를 잡아 `sidoCode = null` 을 만든다
4. `SigunguJpaEntity.sidoCode` 가 `nullable = false` (`SigunguJpaEntity.java:51`) 라 flush 에서 **전량 실패**

> **후속 지침**: `@Async` 를 제거하는 것이 가장 안전하다.
> `@EnableAsync` 를 추가할 일이 생기면 이 줄을 먼저 지워야 한다.

### C-2. `BatchGuard.alreadyDone()` 의 `getJobInstances(jobName, 0, 20)` 함의 [사실+추론]

**[사실]** `BatchGuard.java:22` 가 최근 20개 `JobInstance` 만 조회한다.
Spring Batch 의 `getJobInstances` 는 `JOB_INSTANCE_ID DESC` 순이므로 "가장 최근 20개"다.

**[추론] 배치별 영향이 다르다.**

| 배치 | `JobInstance` 증가 방식 | 20개 제한의 영향 |
| --- | --- | --- |
| 시드 8개 | 파라미터가 `seedVersion` 하나뿐 → **`SEED_VERSION` 값 개수만큼만** 인스턴스가 생긴다 | `SEED_VERSION` 을 21번 이상 바꿔야 문제. 실질적으로 무제한 |
| `dwellingJob` | `triggerTime` 이 매번 달라 **기동마다 인스턴스 1개씩 증가** | **실질적 위험.** 21번째 기동부터 옛 성공 이력이 조회 창 밖으로 밀린다 |

**[추론] 구체적 실패 시나리오**
같은 `SEED_VERSION` 으로 21번 기동했고 그 사이 20번이 실패했다면,
성공 기록이 조회 범위(최근 20개)를 벗어나 `alreadyDone` 이 `false` 를 반환하고 **이미 성공한 작업을 다시 돈다.**
`dwellingJob` 은 전 시군구 × 12개월 외부 API 호출이라 재실행 비용이 크다.

**[사실] 부가 문제 — `ExitStatus` 미확인.**
`BatchGuard.java:28` 은 `BatchStatus.COMPLETED` 만 본다.
Step 이 전부 skip 돼 아무것도 적재하지 못해도 `BatchStatus` 는 `COMPLETED` 다.
→ **"COMPLETED 인데 테이블이 빈" 상태가 영구 고착된다.**
`SEED_VERSION` 을 올리기 전에는 절대 재시도하지 않는다.
(`.claude/skills/seed-data/SKILL.md` §3-D 가 같은 증상을 별도 항목으로 다룬다.)

### C-3. `SEED_VERSION` 단일 파라미터 — 외부 데이터 갱신 영구 차단 [사실+추론]

**[사실]** 시드 8개의 JobParameters 는 `seedVersion` **하나뿐**이다 (`SidoBatchRunner.java:52-54` 외 7곳 동일).

**[추론] 두 겹의 차단이 걸린다.**

| 겹 | 메커니즘 | 결과 |
| --- | --- | --- |
| 1 | `BatchGuard.alreadyDone` | 같은 `seedVersion` 의 COMPLETED 이력이 있으면 launch 자체를 안 한다 |
| 2 | Spring Batch `JobInstance` 동일성 | 파라미터가 같으면 같은 `JobInstance` → 이미 COMPLETED 면 `JobInstanceAlreadyCompleteException` |

**[추론] 월별/일별 갱신이 불가능한 구조다.**
`jobCount`(고용24 채용 통계)나 `population`(인구) 처럼 주기적으로 바뀌는 외부 데이터를
매월 갱신하려면 **매번 사람이 `SEED_VERSION` 환경변수를 손으로 올려야 한다.**
그리고 `SEED_VERSION` 은 **9개 배치가 공유**하므로 값을 하나 올리면 **9개가 전부 다시 돈다**
(`.claude/skills/seed-data/SKILL.md` §4 도 같은 점을 경고한다).

**[사실] `dwellingJob` 만 이 문제를 우회한다.** `triggerTime` (`DwellingBatchRunner.java:66`) 덕에
`JobInstance` 는 매번 새로 생긴다. 다만 `BatchGuard`(1번 겹)는 여전히 막으므로
결국 `SEED_VERSION` 을 올리지 않으면 실행되지 않는다.

> **후속 지침**: 데이터 성격별로 파라미터를 분리하는 것이 정공법이다.
> 예 — 시드성 데이터는 `seedVersion`, 주기 갱신 데이터는 `dataYearMonth` 같은 별도 키.
> `BatchGuard` 도 키 이름을 하드코딩(`"seedVersion"`, `:27`)하고 있어 함께 손봐야 한다.

### C-4. `InfraBatchConfig` CSV 헤더 vs 실제 `data/infra.csv` — **치명적 불일치** [사실]

| 항목 | 배치가 요구 | 실제 파일 |
| --- | --- | --- |
| 컬럼 수 | **5** | **3** |
| 헤더 | `sigungu_code,industry_code,count,ratio,score` (`InfraBatchConfig.java:116`) | `sigungu_code,opnSvcId,num` (`data/infra.csv:1`) |
| 인코딩 | **MS949** (`InfraBatchConfig.java:110`) | **UTF-8 + BOM** (`EF BB BF` 실측) |

**[사실] 데이터 행 전수 확인**: `awk -F, '{print NF}'` 결과 모든 행이 **3 컬럼**이다. 예외 없음.

**[추론] 실행하면 어떻게 되는가**
1. `linesToSkip(1)` 로 헤더는 건너뛴다 → BOM 오염은 헤더 줄에만 있어 무해
2. 첫 데이터 행에서 `DelimitedLineTokenizer` 가 3개 토큰을 얻는다
3. `names(...)` 는 5개 → **`IncorrectTokenCountException`** → `FlatFileParseException`
4. `infraStep` 에 skip 정책이 없으므로 **Step 즉시 FAILED**
5. `BatchGuard` 는 COMPLETED 가 아니므로 다음 기동에 또 시도한다(무한 실패 루프)

**[사실] 컬럼 이름 대응 관계** (`.claude/skills/seed-data/SKILL.md:297-298` 이 명시)
- `opnSvcId` → `industry_code`
- `num` → `count`
- `ratio`, `score` — **원본에 없다. 새로 산출해야 한다.**

**[사실] 선행 의존도 함께 막혀 있다.**
`infraJob` Processor 는 `isKnownIndustryCode()` (`InfraBatchConfig.java:72-80`) 로
`industry` 테이블에 있는 코드만 통과시킨다.
그런데 `data/industry.csv` 가 **존재하지 않는다.**
`industry` 가 비어 있으면 infra 의 모든 행이 **조용히 skip** 되고 Step 은 COMPLETED 로 끝난다 → C-2 의 "COMPLETED 인데 빈 테이블".

> **후속 작업 순서 (강제)**
> 1. `industry.csv` 작성 — 헤더 `code,name,major`, **UTF-8**, `major ∈ {HEALTH, FOOD, CULTURE, LIFE}`, `code` 10자 이하
> 2. `infra.csv` 헤더 정합 — 5컬럼으로 변환 + **UTF-8 BOM 제거** (`quoteCharacter('\0')` 라 BOM 이 첫 컬럼에 붙으면 전 행 skip)
> 3. `score` 산출 시 **A.4 의 `[0, 100]` 규칙 준수**
> 4. `ratio` 의 단위는 A.5 대로 **담당자 확인 필요**
> 5. `seed.jobs.industry.enabled` / `seed.jobs.infra.enabled` 를 `true` 로

### C-5. `InfraUpsertRow.count` 가 `String` [사실+추론]

**[사실]** `InfraUpsertRow.java:24` — `private String count;`
클래스 주석(`:14-16`)이 스스로 "As-Is `InfraUpsertDTO` 그대로다. DB 컬럼은 Integer 이지만
**JDBC 가 숫자 문자열을 암묵 변환하는 데 의존한다**. 고쳐야 할 As-Is 특이사항으로 보고하되 이관에서는 손대지 않는다"고 적어 두었다.

**[사실]** 소스도 String 이다 — `InfraCsvRow.countRaw` (`InfraCsvRow.java:12`), `InfraBatchConfig.java:120` 에서 `normalize(...)` 로 읽는다.

**[사실] 같은 문제가 `PopulationUpsertRow.population` 에도 있다** (`PopulationUpsertRow.java:18`, String).
반면 `JobCountUpsertRow.count` 는 **`Integer`** 다 (`JobCountUpsertRow.java:19`). 세 배치의 처리가 일관되지 않는다.

**[추론] 실질 위험**
- 숫자가 아닌 값(`"N/A"`, `"-"`, 천단위 콤마 `"1,234"`)이 CSV 에 있으면 **파싱 시점이 아니라 INSERT 시점에** 터진다
- MySQL strict mode 가 아니면 `"1,234"` 가 **`1` 로 조용히 잘려 들어간다** — 데이터 오염이 무음으로 발생
- `InfraBatchConfig.java:120` 의 `normalize()` 는 trim 계열이지 숫자 검증이 아니다
  (`BatchTextUtil` 에는 `digitsOnly()` 도 있고 `PopulationBatchConfig.java:101` 이 쓰지만, infra 는 안 쓴다)

> **후속 지침**: `Integer` 로 바꾸고 Processor 에서 파싱 실패를 `return null` 로 skip 처리하는 것이 정공법이다
> (persistence-conventions §7.2 규칙 5 와 일치).

### C-6. `RepositoryItemWriter` + `methodName("save")` 의 멱등성 — **멱등하다** [사실+추론]

**판정 기준**: `SimpleJpaRepository.save(entity)` 는 `entityInformation.isNew(entity)` 로 분기한다.
`Persistable` 을 구현하지 않은 엔티티는 **`@Id` 필드가 null 인지**로 판단한다.
- `null` → `persist()` (INSERT)
- `null 아님` → `merge()` → **SELECT 후 존재하면 UPDATE, 없으면 INSERT**

**[사실] `save` 를 쓰는 5개 엔티티의 `@Id` 전략**

| 엔티티 | `@Id` | 값 할당 시점 | 판정 |
| --- | --- | --- | --- |
| `SidoJpaEntity` | `@Id String sido_code` (`:30-32`) | CSV 에서 매핑 시 | **자연키, 항상 non-null → merge → 멱등** ✅ |
| `SigunguJpaEntity` | `@Id String sigungu_code` (`:43-45`) | CSV | **멱등** ✅ |
| `JobCodeTopJpaEntity` | `@Id String code` (`:31-33`) | CSV | **멱등** ✅ |
| `JobCodeMiddleJpaEntity` | `@Id String code` (`:40-42`) | CSV | **멱등** ✅ |
| `IndustryJpaEntity` | `@Id String industry_code` (`:33-35`) | CSV | **멱등** ✅ |

> **결론: 5개 모두 PK 가 코드 문자열(자연키)이므로 `save` 가 `merge` 로 동작한다. 재실행해도 중복 행이 쌓이지 않는다.** [추론]

**[추론] 다만 대가가 있다.**
- `merge` 는 행마다 **SELECT 를 한 번씩** 날린다 → chunk 100 이면 SELECT 100 + UPDATE 100
- `JdbcBatchItemWriter` + `ON DUPLICATE KEY UPDATE` (왕복 1회) 대비 확연히 느리다
- 현재 데이터량(sido 17, sigungu 264, level_top 13, level_middle 114, industry 14)에서는 문제 없다 [추론]
- persistence-conventions §7.1 도 `RepositoryItemWriter` 를 "단순 신규·소량"용으로 규정한다

**[사실] `@Id` length 미지정 2건** — `JobCodeTopJpaEntity.java:31-33` 과 `JobCodeMiddleJpaEntity.java:40-42` 의
`@Column(name = "code")` 에 `length` 가 없어 `varchar(255)` 로 만들어진다. 다른 코드 엔티티들은 명시돼 있다.

### C-7. `ON DUPLICATE KEY UPDATE` 에 필요한 UNIQUE 제약 — **4개 모두 있다** [사실]

| 테이블 | 필요한 UNIQUE | 엔티티 선언 | 라인 | 판정 |
| --- | --- | --- | --- | :---: |
| `Population` | `(sigungu_code)` | `@Column(..., unique = true)` | `PopulationJpaEntity.java:37` | ✅ |
| `JobCount` | `(sigungu_code, job_code_middle_code)` | `@UniqueConstraint(columnNames = {...})` — **이름 없음** | `JobCountJpaEntity.java:47` | ✅ |
| `Dwelling` | `(sigungu_code)` | `@Column(..., unique = true)` — **`nullable` 미지정** | `DwellingJpaEntity.java:39` | ✅ |
| `infra` | `(sigungu_code, industry_code)` | `@UniqueConstraint(name = "uk_infra_sigungu_industry", ...)` | `InfraJpaEntity.java:37-38` | ✅ |

각 엔티티 Javadoc 이 "제약을 지우면 배치 재실행 시 중복 행이 쌓인다"고 명시해 두었다
(`PopulationJpaEntity.java:21-23`, `JobCountJpaEntity.java:27-29`, `DwellingJpaEntity.java:22-24`).

**[사실] 주의할 편차 3건**

| 편차 | 위치 | 함의 |
| --- | --- | --- |
| `JobCount` 의 UNIQUE 만 **이름이 없다** | `JobCountJpaEntity.java:47` | Hibernate 해시 이름이 붙는다. As-Is 수렴 목적이라고 Javadoc(`:27-29`)에 명시. `docker/mysql/ddl/2026-08-11-rename-fk-index.sql:79-83` 이 "이 제약은 건드리면 안 된다"고 경고 |
| `Dwelling.sigungu_code` 만 **`nullable` 미지정** | `DwellingJpaEntity.java:39` | 기본값 `nullable = true`. MySQL UNIQUE 는 NULL 을 여러 개 허용하므로, `sigunguCode` 가 null 인 행이 들어오면 **중복 방지가 무력화**된다 [추론] |
| `hbm2ddl.auto=update` 는 제약을 **추가만** 한다 | `DataDBConfig.java:47` | 기존 DB 에 제약이 없으면 update 로 생기지만, 이미 중복 행이 있으면 인덱스 생성이 실패한다 [추론] |

### C-8. `InfraScoreCacheCleaner` 가 어느 Job 에도 연결되지 않았다 [사실] — **요청 목록 외 발견**

**[사실]** `src/main` 전수 grep 결과 `InfraScoreCacheCleaner` 라는 이름이 등장하는 곳은
**자기 자신의 클래스 선언부(`InfraScoreCacheCleaner.java:23`) 하나뿐**이다.
`InfraBatchConfig` / `IndustryBatchConfig` 어디에도 `.listener(...)` 호출이 없다.

클래스 Javadoc(`:12-19`)이 이 문제를 스스로 기록해 두었다 —
"As-Is `InfraBatch`/`IndustryBatch` 는 `JobBuilder...listener(...)` 를 호출하지 않아
이 리스너가 실제로는 한 번도 실행되지 않는 고아 컴포넌트였다.
dwelling/job 의 대응 클래스는 각자의 Job 에 연결돼 있었다 — 여기만 빠져 있다."

**[추론] 결과**: `infraJob` 이 infra 데이터를 갱신해도 `infra:score:*` Redis 캐시가 무효화되지 않는다.
`InfraScoreRedisAdapter.java:36` 의 **TTL 24시간**이 지나야 새 데이터가 반영된다.
`jobScoreCache`(TTL 12h) / `dwellingScoreCache`(TTL 30d) 는 리스너가 붙어 있어 즉시 무효화된다.

### C-9. `@Value` 필드 주입 + `@Bean` 메서드 + `@StepScope` 없는 Reader [사실+추론]

**[사실] `@StepScope` 가 없는 Reader 는 2개다.**

| Reader | `@StepScope` | 라인 |
| --- | :---: | --- |
| `jcTopCsvReader()` | **없음** | `JobCodeTopBatchConfig.java:74` |
| `jcMiddleCsvReader()` | **없음** | `JobCodeMiddleBatchConfig.java:100` |
| 나머지 6개 CSV Reader + `dwellingReader` | 있음 | — |

**[추론] `@Value` 필드 주입 자체는 문제되지 않는다.**
`@Configuration` 클래스의 `@Value` 필드는 그 설정 클래스 자신의 빈 생성 단계(`populateBean`)에서 주입된다.
`@Bean` 메서드는 그 이후에 호출되므로 `filePath` 는 이미 채워져 있다.
`@StepScope` 유무와 무관하게 **프로퍼티 플레이스홀더는 정상 해석된다.**
(실제로 `jcTopJob` / `jcMiddleJob` 은 `enabled=true` 로 현재 운영에서 돌고 있다.)

**[추론] 실제 문제는 두 가지다.**

1. **`#{jobParameters[...]}` 를 쓸 수 없다.**
   `@StepScope` 없이는 late binding 이 안 된다. `dwellingReader` (`DwellingBatchConfig.java:91-94`) 처럼
   JobParameters 기반 입력이 필요해지면 **반드시 `@StepScope` 를 붙여야 한다.**
   지금 `@Value("${...}")` 정적 경로만 쓰고 있어 드러나지 않을 뿐이다.

2. **싱글턴 Reader 는 Step 실행 간 상태가 공유된다.**
   `FlatFileItemReader` 는 `ItemStream` 상태(현재 라인 번호, 열린 파일 핸들)를 갖는다.
   현 구조(JVM 당 1회, 단일 스레드)에서는 Step 이 `open()`/`close()` 를 감싸므로 동작한다 [추론].
   그러나 재시작·병렬 Step·같은 Reader 재사용 상황에서는 안전하지 않다.

> **후속 지침**: 동작 버그는 아니지만 **일관성 결함**이다. 두 Reader 에 `@StepScope` 를 붙이면 다른 6개와 맞춰진다.

### C-10. `spring.batch.jdbc.initialize-schema=never` 기본값과 메타테이블 생성 [사실]

| 환경 | 값 | 위치 |
| --- | --- | --- |
| dev | `${BATCH_SCHEMA_INIT:never}` | `application-dev.properties:5` |
| prod | `${BATCH_SCHEMA_INIT:never}` | `application-prod.properties:5` |
| test | **`always`** | `src/test/resources/application.properties:11` |

스키마 스크립트: `classpath:org/springframework/batch/core/schema-mysql.sql` (3개 환경 동일)

**[사실] 어느 DataSource 에 만들어지는가**
`MetaDBConfig.java:16` 의 **`@BatchDataSource`** 어노테이션이
Boot 의 `BatchAutoConfiguration`/`JobRepository` 에게 "이 DataSource 를 배치 메타용으로 써라"고 지정한다.
→ `BATCH_JOB_INSTANCE` 등은 **`spring.datasource-meta.*` → `smash_meta` 스키마**에 생성된다.
빈 이름은 `batchDataSource` 이며 메서드명(`metaDBSource`)과 다르다 (`MetaDBConfig.java:19`).

**[추론] `never` 인 채로 기동하면**
1. `smash_meta` 에 `BATCH_*` 테이블이 없다
2. `ApplicationReadyEvent` → Runner → `guard.alreadyDone()` → `jobExplorer.getJobInstances()` 호출
3. `Table 'smash_meta.BATCH_JOB_INSTANCE' doesn't exist` → **9개 배치가 전부 기동과 동시에 실패**

`.claude/skills/seed-data/SKILL.md` §0.1 과 §3-J 가 같은 증상을 다루며,
`scripts/verify-seed.sh` 도 `BATCH_SCHEMA_INIT` 이 `always` 가 아니면 WARN 을 낸다.

**메타테이블 생성 방법 3가지**

| 방법 | 절차 | 비고 |
| --- | --- | --- |
| **권장** | `backend.env` 에 `BATCH_SCHEMA_INIT=always` 로 1회 기동 → 생성 후 `never` 로 낮춤 | seed-data SKILL §0.1 이 권장 |
| 수동 | `schema-mysql.sql` 을 `smash_meta` 에 직접 실행 | jar 내부에서 꺼내야 함 |
| 초기화 | `docker compose down -v` → 볼륨 삭제 → `docker/mysql/init/01-init-meta-db.sh` 재실행 | `smash_meta` **DB 는 만들지만 BATCH_* 테이블은 안 만든다** (`01-init-meta-db.sh:9-10` 주석) |

**[사실]** `backend.env` 에 `BATCH_SCHEMA_INIT` 변수 자체는 정의돼 있다(E절 참고). 값은 확인하지 않았다.

---

## 추가로 발견한 문제

### C-11. `InfraScorePolicy` 의 결측 대분류가 0점으로 희석된다 [사실+추론]

**[사실]** `InfraScorePolicy.java:38, 41` — `divisor = selectedMajors.size()` 이고,
`findRegionMajorScores` 결과에 없는 `(시군구, 대분류)` 조합은 합계에 기여하지 않는다.

**[추론]** 사용자가 4개를 전부 골랐는데 어떤 시군구에 `HEALTH` 업종 데이터만 있다면,
그 시군구 점수 = `A(g, HEALTH) / 4` 가 되어 **실제 인프라 수준의 1/4 로 평가된다.**
"데이터가 없다"와 "인프라가 없다"를 구분하지 않는다.
`InfraQueryService.getMajorInfraSummaries` 는 같은 상황을 `log.warn` 후 목록에서 빼는데(`:44-49`),
점수 경로는 조용히 0으로 처리한다 — **같은 결측을 두 경로가 다르게 다룬다.**

### C-12. `infraChoice > 15` 일 때 분모만 늘어난다 [추론]

A.6 에 상술. `RecommendRegionService.isSelected()` 와 `Major.fromChoiceMask()` 의 판정 기준이 달라
분모에는 포함되고 분자에는 0이 들어간다.

### C-13. 테이블명 대소문자 불일치 2건 [사실+추론]

| Job | SQL 이 쓰는 이름 | 엔티티 `@Table(name=...)` | 일치 |
| --- | --- | --- | :---: |
| `PopulationJob` | **`Population`** (`PopulationBatchConfig.java:123`) | **`population`** (`PopulationJpaEntity.java:26`) | ❌ |
| `dwellingJob` | **`Dwelling`** (`DwellingBatchConfig.java:157`) | **`dwelling`** (`DwellingJpaEntity.java:27`) | ❌ |
| `infraJob` | `infra` | `infra` | ✅ |
| `jobCountJob` | `JobCount` | `JobCount` | ✅ |

**[추론]** MySQL 의 `lower_case_table_names` 값에 따라 결과가 갈린다.
- `0` (**리눅스 컨테이너 기본**) → 테이블명 대소문자 구분 → `INSERT INTO Population` 이
  `Table 'smash_data.Population' doesn't exist` 로 **실패**
- `1` (Windows) → 무해

`docker-compose.yaml:24-27` 의 mysql `command` 에 `lower_case_table_names` 설정이 없어 **기본값 0** 이 적용된다 [추론].
현재는 두 배치 모두 실행 조건이 제한적이라(`population` 은 `enabled=false`) 드러나지 않는다.

> **[추론] `dwellingJob` 은 `@ConditionalOnProperty` 가 없어 매 기동마다 실행된다.**
> 즉 이 오류는 이미 실운영에서 발생하고 있을 가능성이 있으나,
> `DwellingBatchRunner.java:71-73` 이 예외를 삼키고 `log.error` 만 남겨 **조용히 실패**한다.

### C-14. MOLIT API 페이지네이션 미구현 [사실+추론]

**[사실]** `MolitAptRentApiAdapter.java:36-37` — `pageNo = 1`, `numOfRows = 1000` 고정.
다음 페이지를 읽는 코드가 없고 `totalCount` 도 파싱하지 않는다(`:71-72`).

**[추론]** 특정 시군구·특정 월 거래가 1,000건을 넘으면 **초과분이 조용히 유실**된다.
오류도 경고도 없다. 수도권·대도시 시군구에서 발생 가능하나,
`RegionScorePolicy` 가 서울/경기/인천을 제외하므로(`:23-24`) 추천 결과 영향은 제한적일 수 있다.

### C-15. 외부 API 오류가 "거래 0건"으로 위장된다 [추론]

**[사실]** `MolitAptRentApiAdapter.java:100-110` 의 `parseJsonWithXmlFallback` 이
파싱 예외를 삼키고 **빈 `ObjectNode` 를 반환**한다(`:106-109`). `log.warn` 만 남는다.

**[추론]** API 가 인증 오류를 HTTP 200 + 에러 XML 로 반환하면 "거래 없음"과 구분되지 않는다.
`dwellingJob` 은 COMPLETED 로 끝나고 `Dwelling` 테이블은 비거나 옛 값이 남는다.

### C-16. `@Configuration` 인스턴스 필드 캐시가 리셋되지 않는다 [추론]

**[사실]** `InfraBatchConfig.java:59-60`
```java
private Set<String> sigunguCodeCache = null;
private Set<String> industryCodeCache = null;
```
`SigunguBatchConfig` / `JobCountBatchConfig` / `PopulationBatchConfig` 에도 같은 패턴이 있다.

**[추론]** `@Configuration` 은 싱글턴이므로 이 캐시는 **JVM 수명 내내 유지된다.**
`@Order` 순서가 깨져 부모 테이블 적재 전에 자식 배치가 먼저 캐시를 채우면
그 프로세스 내내 **빈 캐시가 고착**되고, 모든 행이 Processor 에서 `null` 로 skip 된다
→ Step 은 COMPLETED, 테이블은 빈 상태 (C-2 와 같은 증상).
C-1 의 `@EnableAsync` 시나리오와 결합하면 정확히 이 결과가 나온다.

### C-17. `verify-seed.sh` 의 스펙 테이블이 실제 코드와 어긋난다 [사실]

| 항목 | `verify-seed.sh` SPECS | 실제 코드 |
| --- | --- | --- |
| `level_top.csv` 인코딩 | **MS949** (`:57`) | **UTF-8** (`JobCodeTopBatchConfig.java:79`) |
| `level_middle.csv` 인코딩 | **MS949** (`:58`) | **UTF-8** (`JobCodeMiddleBatchConfig.java:105`) |
| 실제 파일 인코딩 | — | **UTF-8** (실측) |

**[추론]** 두 파일은 한글을 포함하므로 `detect_encoding` 이 UTF-8 로 판정하고
MS949 스펙과 불일치해 **거짓 FAIL** 을 낸다. 스크립트 쪽 스펙이 잘못됐다.

**[사실] 부수적 불일치 2건**
- `verify-seed.sh:5` 주석은 "Seed Job **9개**", `SPECS` 는 **8행** (dwelling 제외 후 갱신 누락)
- `-h` 도움말(`:8`)은 "**dev** 점검"이라 하지만 `PROFILE="prod"` / `PROPS=application-prod.properties` 로 **고정**(`:21-24`)

### C-18. 문서(SKILL.md)와 코드의 모순 [사실]

**① `@Primary` TransactionManager**

`.claude/skills/persistence-conventions/SKILL.md:319` 는
"`@Primary` `PlatformTransactionManager` 는 `batchTransactionManager`(meta DB) 다" 라고 서술한다.

**실제 코드는 반대다** —
- `DataDBConfig.java:54` — `@Primary @Bean("dataTransactionManager")` `JpaTransactionManager` ✅ `@Primary`
- `MetaDBConfig.java:24-28` — `@Bean("batchTransactionManager")` `DataSourceTransactionManager` ❌ `@Primary` 없음

`src/main/java` 전수 grep 결과 `@Primary` 는 `DataDBConfig:30`, `DataDBConfig:54`, `RedisConfig:31` 세 곳뿐이다.

**[추론] 파급**: 배치 Config 8개가 생성자로 **무수식 `PlatformTransactionManager`** 를 주입받는다
(`SidoBatchConfig.java:35`, `InfraBatchConfig.java:54`, `JobCountBatchConfig.java:52`, `DwellingBatchConfig.java:56` 등).
`@Primary` 가 data 쪽인 현재 상태에서는 이 값이 **`dataTransactionManager`(JPA / `smash_data`)** 로 해석된다.
즉 **청크 트랜잭션이 meta 가 아니라 data DB 에 걸린다.**
`JdbcBatchItemWriter` 들이 `@Qualifier("dataDBSource")` 로 같은 data DB 를 잡는 것과는 일관되지만,
JobRepository 메타 기록만 `@BatchDataSource` 로 meta 에 가므로 **의도 검증이 필요한 지점이다.**

**② 존재하지 않는 파일 참조**

`.claude/skills/seed-data/SKILL.md` §5 가 참조하는 다음 파일들이 **저장소에 없다** [사실]:
`backend.env.example`, `backend.prod.env.example`, `docker-compose.prod.yaml`, `dev.Dockerfile`,
그리고 `verify-seed.sh --prod` 옵션(스크립트 옵션은 `--data-dir` / `--emit-env` / `-h` 3개뿐, `:26-33`).

**③ `@Order` 표기**

`architecture-conventions` §6.2 표는 Dwelling 을 **10** 으로 적지만 실제 코드는 **9** 다
(`DwellingBatchRunner.java:50`, 클래스 주석 `:21-24` 이 이 불일치를 스스로 기록).

### C-19. `application.properties`(프로파일 없는 기본 파일)가 없다 [사실]

`src/main/resources` 에는 `application-dev.properties` 와 `application-prod.properties` 두 개뿐이다.
프로파일을 지정하지 않고 기동하면 모든 `${ENV}` 플레이스홀더가 미해결로 남아 **컨텍스트 로딩이 실패**한다.
`Dockerfile:24` 가 `ENV SPRING_PROFILES_ACTIVE=prod` 로 고정하므로 컨테이너 기동은 문제 없다.

### C-20. RDS 시절 JDBC 드라이버 의존성이 남아 있다 [사실]

`build.gradle` — `implementation 'software.amazon.jdbc:aws-advanced-jdbc-wrapper:2.5.0'`
`.claude/skills/seed-data/SKILL.md` §0.1 과 `scripts/verify-seed.sh:266` 은
`DRIVER` 가 이 드라이버면 **FAIL** 로 판정하는데, 의존성 자체는 빌드에 남아 있다.

### C-21. `data/` 에 3개 시드 CSV 가 없다 [사실]

| 필요 파일 | 존재 | `*.filePath` 기본값 | `enabled` |
| --- | :---: | --- | :---: |
| `sido.csv` | ✅ | `data/sido.csv` | true |
| `sigungu.csv` | ✅ | `data/sigungu.csv` | true |
| `level_top.csv` | ✅ | `data/level_top.csv` | true |
| `level_middle.csv` | ✅ | `data/level_middle.csv` | true |
| `population.csv` | ❌ | **빈 문자열** | false |
| `industry.csv` | ❌ | **빈 문자열** | false |
| `infra.csv` | ⚠️ 있으나 스펙 불일치 (C-4) | `data/infra.csv` | false |
| `job_count.csv` | ❌ | **빈 문자열** | false |

**[추론]** 기본값이 `data/...`(호스트 상대경로)인데 컨테이너 마운트는 `/app/data` (`docker-compose.yaml:14`) 다.
`*_FILEPATH` 환경변수를 주지 않으면 컨테이너에서 파일을 찾지 못한다.
`backend.env` 에는 컨테이너 경로가 정의돼 있다(E절).

---

# D. 인프라 / 테스트 환경

## D.1 `IntegrationTestSupport` [사실]

`src/test/java/SDD/smash/IntegrationTestSupport.java` (59줄)

| 항목 | 내용 |
| --- | --- |
| 클래스 어노테이션 | **`@SpringBootTest` 단 하나** (`:19`), `abstract class` (`:20`) |
| **없는 것** | `@Testcontainers`, `@Container`, `@ActiveProfiles`, `@AutoConfigureMockMvc` |
| 컨테이너 | `static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")` (`:22`) |
| 기동 방식 | **static 초기화 블록에서 `MYSQL.start()`** (`:31-33`) — JUnit 확장이 아니라 **싱글턴 컨테이너 패턴** (`:15` 주석). JVM 당 1회 |
| `withReuse` | **없음** (Testcontainers reuse 미사용) |
| DB 이름 | `withDatabaseName("smash_data")` (`:23`) |
| command | `mysqld --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci --default-time-zone=+09:00` (`:24-29`) — `docker-compose.yaml:24-27` 과 동일 |
| 프로파일 | `@ActiveProfiles` 없음 → **활성 프로파일 없음** → `application-dev/prod.properties` 는 로드되지 않고 `src/test/resources/application.properties` 만 읽힌다 |

### `@DynamicPropertySource` — 덮어쓰는 건 접속정보 6개뿐 (`:49-58`)

```
spring.datasource-data.jdbc-url / username / password   (:51-53)
spring.datasource-meta.jdbc-url / username / password   (:55-57)
```

**[사실] data / meta 두 DataSource 가 같은 컨테이너·같은 스키마(`smash_data`)를 가리킨다.**
주석(`:16-17`)이 "운영은 두 스키마지만 분리 검증 테스트가 아니면 하나로 충분"이라고 밝힌다.

### 대역으로 바꾸는 빈 — 2개, 모두 `@MockitoBean` [사실]

`@MockBean`(deprecated)이 아니라 Spring Boot 3.4+ 의 `@MockitoBean` 을 쓴다.

| 빈 | 라인 | 이유 (주석) |
| --- | --- | --- |
| `DwellingBatchRunner` | `:39-40` | `ApplicationReadyEvent` 로 국토부 API 를 호출하는 배치 러너. **시드 배치들과 달리 `@ConditionalOnProperty` 가 없어** 테스트에서 대역이 필요하다 |
| `SupportPolicyRefreshScheduler` | `:46-47` | `@Scheduled(initialDelay = 0)` 이라 컨텍스트 기동 즉시 청년정책 API 를 전 시군구 호출한다 |

> **[사실] 나머지 시드 배치 Runner 8개는 mock 이 아니다.**
> `src/test/resources/application.properties:59-66` 의 `seed.jobs.*.enabled=false` 로 **빈 등록 자체를 막아** 끈다.
> `DwellingBatchRunner` 만 `@ConditionalOnProperty` 가 없어 유일하게 mock 이 필요하다.

## D.2 `src/test/resources/application.properties` [사실]

테스트 리소스는 이 파일 **하나뿐**이다 (83줄).

| 라인 | 값 | 비고 |
| --- | --- | --- |
| `:10` | `spring.batch.job.enabled=false` | |
| `:11` | `spring.batch.jdbc.initialize-schema=`**`always`** | **main 의 `never` 와 다름.** 빈 컨테이너에 `BATCH_*` 생성 |
| `:12` | `schema=classpath:.../schema-mysql.sql` | |
| `:17-18` | `driver-class-name=com.mysql.cj.jdbc.Driver` 만 명시 | url/user/pw 는 Testcontainers 가 주입 |
| `:22` | `server.port=0` | 랜덤 포트 |
| `:25-28` | Redis `localhost:6379`, ssl false, repositories false | Lettuce 지연접속이라 컨텍스트 로딩 시 접속 안 함 |
| `:36-43, :75-82` | molit / youthcenter / openai / worknet 전부 `http://localhost` + 더미 키 | |
| `:45-46` | `seed.version=test`, `dwelling.dealYmd=202601` | |
| `:49-56` | **`*.filePath` 8개 전부 빈 값** | 배치를 끄므로 안 읽음 |
| `:59-66` | **`seed.jobs.*.enabled` 8개 전부 false** | |

## D.3 테스트 목록 [사실]

| 파일 | 종류 | 테스트 수 |
| --- | --- | ---: |
| `SmashApplicationTests` | **유일한 통합 테스트** (`extends IntegrationTestSupport`) | 1 (`contextLoads`) |
| `IntegrationTestSupport` | abstract 베이스 | — |
| `global/domain/model/MoneyTest` | 순수 단위 | 5 |
| `global/domain/model/ScoreTest` | 순수 단위 | 5 |
| `global/domain/model/SidoCodeTest` | 순수 단위 | 2 |
| `global/domain/model/SigunguCodeTest` | 순수 단위 | 4 |
| `domain/recommendation/domain/service/RegionScorePolicyParityTest` | 순수 단위 (패리티) | 4 |

**[사실] Docker 가 필요한 테스트는 `SmashApplicationTests.contextLoads()` 하나뿐이다.**
나머지는 Spring 컨텍스트 없이 도는 POJO 테스트다.

**[사실] infra 컨텍스트 전용 테스트가 없다.** `InfraScorePolicy` 의 A.4 경계값(0, 100, 100 초과)을
검증하는 테스트도 없다. `ScoreTest` 가 `Score` 자체의 범위만 검증한다.

**[사실]** `src/test/java/SDD/smash/recommendation/domain/service/` 는 **빈 디렉터리**(구 패키지 잔재).

## D.4 `docker/` [사실]

### `docker/mysql/init/01-init-meta-db.sh` (24줄) — **자동 실행**

`docker-compose.yaml:32` 가 `./docker/mysql/init:/docker-entrypoint-initdb.d:ro` 로 마운트한다.
MySQL 컨테이너 **최초 기동 시 1회**만 실행(볼륨이 비어 있을 때).

- `META_DB="${MYSQL_META_DB:-smash_meta}"` (`:14`)
- `CREATE DATABASE IF NOT EXISTS smash_meta CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`
  + `GRANT ALL ON meta.* TO '${MYSQL_USER}'@'%'` + `FLUSH PRIVILEGES` (`:16-22`)
- **`smash_data` 는 만들지 않는다** — mysql 이미지의 `MYSQL_DATABASE` 가 생성 (`:6` 주석)
- **`BATCH_*` 테이블은 만들지 않는다** — 앱이 `BATCH_SCHEMA_INIT=always` 로 생성 (`:9-10` 주석)

### `docker/mysql/ddl/*.sql` (2개) — **마운트되지 않는다. 수동 실행 전용** [사실]

**`2026-08-11-drop-legacy-fk.sql`** (156줄)
- 목적: DDD 전환으로 `@ManyToOne` 을 제거했으나 **DB 에 옛 물리 FK 가 남아 있음**
  (`hbm2ddl update` 는 제약을 삭제하지 않음) → 수동 삭제
- 구조: ① dry-run SELECT(`:49-65`) → ② `drop_legacy_aggregate_fk()` 프로시저가 `information_schema` 커서로
  동적 `ALTER TABLE ... DROP FOREIGN KEY`(`:84-124`) → ③ 사후 0행 확인(`:128-136`) → ④ 인덱스 안내(`:139-155`)
- 대상 9개: `sido, sigungu, population, dwelling, industry, infra, job_code_top, job_code_middle, JobCount` (`:62-64`)
- 제약명 하드코딩 안 하는 이유: Hibernate 해시 이름이라 환경마다 다름 (`:70-74`). 멱등 (`:44`)
- 실행: `docker compose exec -T mysql mysql -u... smash_data < ...sql` (`:39-41`)

**`2026-08-11-rename-fk-index.sql`** (189줄)
- 목적: 해시 이름 인덱스를 **엔티티 선언 이름으로 RENAME** 해서 `hbm2ddl update` 가 중복 인덱스를 안 만들게 함 (`:12-18`)
- 대상 3개 (`:168-171`): `sigungu.sido_code → idx_sigungu_sido`,
  `job_code_middle.top_code → idx_job_code_middle_top`, `JobCount.job_code_middle_code → idx_jobcount_job_code_middle`
- RENAME 인 근거(`:21-34`): `DROP INDEX` 는 FK 가 살아있으면 **ERROR 1553** 으로 거부되지만
  `RENAME INDEX` 는 성공하고 FK 가 개명된 인덱스를 계속 쓴다 → drop-fk 스크립트와 **순서 무관**
- 권장 순서: rename → drop-fk → 앱 재기동 (`:40`)
- **`JobCount` 복합 유니크는 자동 제외** — `JobCountBatch` 의 `ON DUPLICATE KEY UPDATE` 가 의존하므로 건드리면 안 됨 (`:79-83`)

## D.5 `docker-compose.yaml` / `Dockerfile` [사실]

### docker-compose.yaml (54줄) — `name: smash`, 서비스 3개

| 서비스 | 이미지/빌드 | 포트 | env | 볼륨 | healthcheck |
| --- | --- | --- | --- | --- | --- |
| `backend` | `build: .` (`:5-7`) | `8080:8080` (`:10`) | `env_file: ./backend.env` (`:11-12`) | **`./data:/app/data:ro`** (`:14`) | 없음. `depends_on: service_healthy` (`:15-19`) |
| `mysql` | `mysql:8.0` (`:22`) | **호스트 노출 없음** | `env_file: ./backend.env` (`:28-29`) | `mysql-data:/var/lib/mysql`, `./docker/mysql/init:/docker-entrypoint-initdb.d:ro` (`:31-32`) | `mysqladmin ping`, 10s/5s/30회/start_period 60s (`:33-38`) |
| `my-redis` | `redis:7` (`:41`) | 노출 없음 | 없음 | `redis-data:/data` (`:44-45`) | `redis-cli ping` 10s/5s/10회 (`:46-50`) |

- mysql command: utf8mb4 + `--default-time-zone=+09:00` (`:24-27`). **`lower_case_table_names` 설정 없음** → C-13
- redis command: `--appendonly yes` (`:43`)
- **`backend.env` 단일 파일을 backend·mysql 두 서비스가 공유**한다 (`:12, :29`)
- **CSV 경로**: 호스트 `./data` → 컨테이너 `/app/data` **읽기전용**

### Dockerfile (30줄) — 멀티스테이지

- **builder**: `eclipse-temurin:17-jdk-jammy`, `/workspace` (`:3-4`).
  gradle 메타파일 먼저 COPY 후 `./gradlew -q help` 로 의존성 캐시 워밍 (`:8-12`),
  `--mount=type=cache,target=/root/.gradle` BuildKit 캐시 (`:11, :15`).
  **`clean bootJar -x test`** (`:16-19`) — **테스트는 이미지 빌드에서 제외**
- **runtime**: `eclipse-temurin:17-jre-jammy`, `/app` (`:21-22`)
- `ENV SPRING_PROFILES_ACTIVE=prod` (`:24`)
- `JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=40 -XX:+UseStringDeduplication -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Seoul -Dfile.encoding=UTF-8"` (`:25`)
- `ENTRYPOINT ["java","-jar","/app/app.jar"]` (`:30`)

## D.6 `scripts/verify-seed.sh` (355줄) [사실]

읽기전용 사전 진단 스크립트. `set -uo pipefail` (`:14`).
옵션 3개: `--data-dir` / `--emit-env` / `-h` (`:26-33`).
`DATA_DIR=./data`, `CONTAINER_DATA_DIR=/app/data`, `PROFILE="prod"` 하드코딩 (`:16-24`).
exit 0 = 적재 가능, exit 1 = FAIL 존재.

### 스펙 테이블 `SPECS` (`:54-63`) — 8행

형식: `order|propKey|envVar|파일명|인코딩|컬럼수|헤더정규식|seed.jobs키`

| @Order | key | 파일 | 인코딩 | 컬럼 | 헤더 |
| ---: | --- | --- | --- | ---: | --- |
| 1 | sido | `sido.csv` | UTF-8 | 2 | `sido_code,name` |
| 2 | sigungu | `sigungu.csv` | UTF-8 | 3 | `sigungu_code,sido_code,name` |
| 3 | jobCodeTop | `level_top.csv` | **MS949** ⚠️ C-17 | 2 | `code,name` |
| 4 | jobCodeMiddle | `level_middle.csv` | **MS949** ⚠️ C-17 | 3 | `code,name,upstream_code` |
| 5 | population | `population.csv` | MS949 | 2 | `sigungu_code,population` |
| 6 | industry | `industry.csv` | UTF-8 | 3 | `code,name,major` |
| 7 | infra | `infra.csv` | MS949 | 5 | `sigungu_code,industry_code,count,ratio,score` |
| 8 | jobCount | `job_count.csv` | MS949 | 3 | `sigungu_code,job_code,count` |

### 6개 섹션

| 섹션 | 라인 | 검사 내용 |
| --- | --- | --- |
| 1. 파일 스펙 대조 (@Order 순) | `:142-181` | `job_enabled()` (`:66-74`) 확인 → false 면 `SKIP` / true 면 파일존재 → 컬럼수 → 헤더정규식 → 인코딩(`detect_encoding` `:98-113`) → 데이터행>0 |
| 2. 참조 무결성 | `:184-213` | `FKS` 7행(`:80-88`) awk 조인. 컬럼은 1-based, **음수는 뒤에서부터**(`-1`=마지막) |
| 3. 배치 소스 대조 | `:216-231` | `grep -rhoE '\$\{[A-Za-z]+\.filePath\}'` 로 실제 배치 키를 뽑아 `SPECS` 와 `comm` 대조 → **스펙 드리프트 자동 검출** |
| 4. `data/` 미사용 파일 | `:234-242` | SPECS 에 없는 `*.csv` 를 WARN |
| 5. 실행 환경 | `:245-334` | `SEED_VERSION` 없으면 FAIL / `DRIVER` 가 aws-wrapper 면 FAIL / URL 이 localhost 면 FAIL / `BATCH_SCHEMA_INIT≠always` 면 WARN / `REDIS_HOST≠my-redis` 면 FAIL / 비번에 `CHANGE_ME` 있으면 FAIL / compose 4항목 |
| 6. env 출력 | `:337-345` | `--emit-env` 일 때만. `SIDO_FILEPATH=/app/data/sido.csv` 형태 8줄 |

FK 검사 7쌍: `sigungu→sido`, `level_middle(-1)→level_top`, `population→sigungu`,
`infra→sigungu`, `infra→industry`, `job_count→sigungu`, `job_count→level_middle`

## D.7 이중 DataSource 구성 [사실]

### `DataDBConfig.java` (64줄) — 업무 데이터

| 항목 | 값 | 라인 |
| --- | --- | --- |
| `@EnableJpaRepositories` basePackages | `address` / `dwelling` / `job` / `infra` 의 `.infrastructure.persistence` **4개** | `:19-24` |
| `entityManagerFactoryRef` | `"dataEntityManager"` | `:25` |
| `transactionManagerRef` | `"dataTransactionManager"` | `:26` |
| DataSource | **`@Primary`** `@Bean("dataDBSource")` + `@ConfigurationProperties("spring.datasource-data")` | `:30-35` |
| EMF | `dataEntityManager()` — **빈 이름은 메서드명**, `@Primary` **없음** | `:37-52` |
| `setPackagesToScan` | **`"SDD.smash"`** (엔티티는 전체 스캔 — Repository basePackages 보다 넓다) | `:43` |
| **ddl-auto** | `properties.put("hibernate.hbm2ddl.auto", "`**`update`**`")` | `:47` |
| `show_sql` | `"false"` | `:48` |
| TxManager | **`@Primary`** `@Bean("dataTransactionManager")` → `JpaTransactionManager` | `:54-63` |

### `MetaDBConfig.java` (29줄) — 배치 메타

| 항목 | 값 | 라인 |
| --- | --- | --- |
| `@EnableJpaRepositories` | **없음** (JPA 미사용, 순수 JDBC) | — |
| DataSource | **`@BatchDataSource`** + `@Bean("batchDataSource")` + `@ConfigurationProperties("spring.datasource-meta")` | `:16-22` |
| 메서드명 | `metaDBSource()` — **빈 이름(`batchDataSource`)과 다르다** | `:19` |
| `@Primary` | **없음** | — |
| TxManager | `@Bean("batchTransactionManager")` → `DataSourceTransactionManager`. `@Primary` **없음** | `:24-28` |
| EMF | **없음** | — |

### 정리

| 대상 | DataSource | 생성 경로 |
| --- | --- | --- |
| 업무 테이블 (`sido`, `infra`, …) | `dataDBSource` → `smash_data` | `hibernate.hbm2ddl.auto=update` (`DataDBConfig.java:47`) |
| 배치 메타 (`BATCH_*`) | `batchDataSource` (`@BatchDataSource`) → `smash_meta` | `spring.batch.jdbc.initialize-schema` |

**`@Primary` 는 DataSource·TransactionManager 둘 다 data 쪽에 붙어 있다.** meta 는 `@BatchDataSource` 로만 식별된다.
→ C-18 (문서와 모순, 배치 청크 트랜잭션이 data DB 에 걸림)

## D.8 `application-dev` / `application-prod` 차이 [사실]

두 파일은 거의 동일하고 차이는 4곳뿐이다.

| 항목 | dev | prod |
| --- | --- | --- |
| Redis SSL | `ssl.enabled=false` (하드코딩, dev`:29`) | `=${REDIS_SSL:false}` (prod`:29`) |
| Swagger | **활성** (dev`:37-39`) | **비활성** (prod`:36-37`) |
| 로깅 | 없음 | `logging.level.root=INFO` 등 (prod`:89-91`) |
| 그 외 | 동일 | 동일 |

**[사실]** `spring.jpa.hibernate.ddl-auto` 프로퍼티는 **두 파일 어디에도 없다.**
DDL 전략은 `DataDBConfig.java:47` 의 코드로만 지정된다.
`LocalContainerEntityManagerFactoryBean` 을 직접 만들었으므로 Boot 의 `spring.jpa.*` 자동설정이 이 EMF 에 적용되지 않는다 [추론].

## D.9 배치 관련 프로젝트 규칙 요약

### `.claude/skills/seed-data/SKILL.md` (333줄)

| 절 | 핵심 |
| --- | --- |
| §0 | 적재는 애플리케이션이 한다. 별도 도구 없음. `docker compose up --build` → healthcheck → `ApplicationReadyEvent` → `@Order` 순차 → `@ConditionalOnProperty` false 면 **빈 등록 자체가 안 됨** → `BatchGuard` skip → 실행. `data/` 는 `.gitignore` 대상이라 기동 전 검증 필수. 경로는 컨테이너 기준 `/app/data/...` |
| §0.1 | **반드시 지킬 3가지** — ① `DRIVER=com.mysql.cj.jdbc.Driver` ② JDBC 호스트는 compose 서비스명 `mysql` ③ `BATCH_SCHEMA_INIT=always`(never 면 **모든 배치가 기동과 동시에 실패**, 생성 후 never 로 낮춰도 됨) |
| §0.2 | 현재 on/off 상태. "파일만 넣고 플래그를 안 켜는 실수가 잦다" |
| §1 | `@Order` 순서표 8개. `infraScoreJob` 은 `infraChoice` 비트마스크 전환으로 **삭제됨**. `@Order(9)` 는 Dwelling(CSV 아님) |
| §1.1 | FK 선후: `sido → sigungu → {population, infra ← industry, job_count ← level_middle ← level_top}`. **Processor 가 부모에 없는 코드를 만나면 `null` 반환으로 조용히 skip → "성공했는데 데이터가 빈" 상태** |
| §1.2 | sido/sigungu/industry 는 UTF-8, 나머지는 MS949 (리더 하드코딩) |
| §1.3 | `JobCodeMiddleBatch` 는 `line.split(",")` 직접 사용, 이름에 콤마 허용, **`upstream_code` 는 반드시 마지막 컬럼** |
| §2 | 기동 전 5단계 — env 복사 → `data/` 배치 + `enabled=true` → `verify-seed.sh` → `docker compose up --build` → 로그 `grep -iE "Job:\|COMPLETED\|FAILED\|Already\|Skip"` |
| §3 | 실패 대응 13개(A~M). 특히 **D: COMPLETED인데 빈 테이블 → FK 미매칭 전량 skip**, **G: `@Order` 는 순서만 보장하므로 가장 작은 @Order 실패부터 해결**, **M: 로그에조차 안 나옴 → `enabled=false` 라 빈 미등록** |
| §4 | 재적재 = `SEED_VERSION` 을 날짜 기반으로 올림. 대부분 upsert 라 비울 필요 없음. **버전 올리면 9개가 전부 다시 돔**. **`BatchGuard` 는 최근 20개만 조회**해서 버전을 자주 올리면 이미 돌린 버전을 다시 돌 수 있음. 완전 초기화는 `docker compose down -v` |
| §5 | 새 배치 추가 8단계 — 구현 → **dev/prod 프로퍼티 양쪽**에 추가 → Runner(`@Component`+`@ConditionalOnProperty`+`@EventListener`+참조 테이블보다 큰 `@Order`) → BatchGuard → `verify-seed.sh` SPECS 행 → 필요시 FKS → env example → 3번 섹션 PASS 확인 |
| §6 | 현재 상태 — 활성 4개 OK. Population/JobCount/Industry 소스 없음. **Infra 는 컬럼 3 vs 5 불일치** → infra 가 비면 **지역추천 인프라 점수가 항상 0** |

### `.claude/skills/persistence-conventions/SKILL.md`

**§7 배치 영속성** (`:350-398`)
- **§7.1 Writer 선택**: `JdbcBatchItemWriter` + `ON DUPLICATE KEY UPDATE` = **기본 선택**(Upsert·재실행 안전·대량).
  `RepositoryItemWriter` = 단순 신규·소량
- **§7.2 표준형**: `.dataSource(dataDataSource)` + `BeanPropertyItemSqlParameterSourceProvider` + `.assertUpdates(false)`
- **§7.2 규칙 7개**:
  1. **`@Qualifier("dataDBSource") DataSource` 주입 필수 — 아니면 meta DB 에 쓴다**
  2. 네임드 파라미터명 == Upsert Row 필드명
  3. 대상 테이블에 **유니크 제약**이 있는지 먼저 확인
  4. 대량 적재는 Aggregate 를 거치지 않아도 됨 (성능상 허용 예외)
  5. 단 **도메인 불변식은 Processor 에서 값 객체 생성으로 검증** — `catch (DomainException e) { return null; }` 로 skip
  6. 배치 DTO 는 전부 기술 DTO(`infrastructure/batch/dto`)
  7. `normalize`/`addLeadingZero` 등 정제는 배치 안에서 끝냄
- **§7.3 실행 순서**: **물리 FK 가 아니라 적재 순서 + Processor 검증**이 참조 무결성을 보장 → `@Order` 임의 변경 금지

**§6 이중 DataSource 트랜잭션 경계** (`:315-346`)
- **§6.1**: 무수식 `@Transactional` 금지. 반드시 `@Transactional(transactionManager = "dataTransactionManager", readOnly = true)`
  (문서는 `batchTransactionManager` 가 `@Primary` 라고 하지만 **실제 코드는 반대** → C-18)
- **§6.2 계층별**: presentation ❌ / **application public 메서드 = 트랜잭션 경계 ✅** / domain ❌ /
  persistence 어댑터 ❌ / **batch ❌ (`StepBuilder.chunk(size, txManager)` 가 경계)** / scheduler ❌
- **§6.3 규칙 5개**: ① 조회는 `readOnly=true` ② `transactionManager` 명시 필수
  ③ **트랜잭션 안에서 캐시·외부 API 호출 금지** ④ 한 트랜잭션에 Aggregate 하나 ⑤ self-invocation 주의

**§8 스키마 변경 주의** (`:402-416`) — `hbm2ddl.auto=update` 는 **추가만** 반영

| 하려는 것 | update | 대응 |
| --- | :---: | --- |
| 컬럼 추가 | ✅ | 그대로. `nullable` 기본값 주의 |
| 인덱스/유니크 추가 | ✅ | **이름이 다르면 중복 생성** |
| 컬럼 삭제 / 타입 축소 | ❌ | `docker/mysql/ddl/` 에 DDL 작성 |
| FK 제약 삭제 | ❌ | 같음 |
| 테이블명·컬럼명 변경 | ❌ | **새 테이블/컬럼이 생긴다.** 원칙적으로 안 함 |

**제1원칙: 테이블명·컬럼명을 바꾸지 않는다.**

## D.10 `build.gradle` 의존성 [사실]

플러그인: `java`, `org.springframework.boot 3.5.7`, `io.spring.dependency-management 1.1.7`. **Java toolchain 17**.

| 스코프 | 의존성 |
| --- | --- |
| implementation | `spring-boot-starter-batch`, `spring-boot-starter-data-redis-`**`reactive`**, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `spring-boot-starter-web`, `spring-boot-starter-webflux`, `jackson-dataformat-xml`, `springdoc-openapi-starter-webmvc-ui:2.8.13`, **`software.amazon.jdbc:aws-advanced-jdbc-wrapper:2.5.0`** (C-20) |
| compileOnly / annotationProcessor | `lombok` |
| runtimeOnly | `com.mysql:mysql-connector-j` |
| testImplementation | `spring-boot-starter-test`, `reactor-test`, `spring-batch-test`, `spring-security-test`, `org.testcontainers:mysql` **(버전 미지정)**, `org.testcontainers:junit-jupiter` |

**[사실] test 태스크 워크어라운드**
```gradle
systemProperty 'api.version', (System.getenv('API_VERSION') ?: '1.40')
```
Testcontainers 1.21.3 의 docker-java 가 Docker API 1.32 로 붙는데 Docker Engine 29 는 최소 1.40 을 요구해
`/info` 가 400 을 반환한다. 이 워크어라운드 없이는 로컬에서 통합 테스트가 아예 뜨지 않는다.

---

# E. `backend.env` 변수 이름 목록

> **값은 비밀값이므로 이 문서에 옮기지 않는다.** 이름만 나열한다.

```
DRIVER=
BATCH_SCHEMA_INIT=
MYSQL_ROOT_PASSWORD=
MYSQL_DATABASE=
MYSQL_META_DB=
MYSQL_USER=
MYSQL_PASSWORD=
MYSQL_DATA_URL=
MYSQL_DATA_USERNAME=
MYSQL_DATA_PASSWORD=
MYSQL_META_URL=
MYSQL_META_USERNAME=
MYSQL_META_PASSWORD=
SEED_VERSION=
FRONT_URL=
SERVER_PORT=
REDIS_HOST=
REDIS_PORT=
DEALYMD=
MOLIT_BASE_URL=
MOLIT_PATH=
MOLIT_SERVICE_KEY=
YOUTH_BASE_URL=
YOUTH_BASE_PATH=
YOUTH_API_KEY=
SIDO_FILEPATH=
SIGUNGU_FILEPATH=
JOBCODETOP_FILEPATH=
JOBCODEMIDDLE_FILEPATH=
INFRA_FILEPATH=
POPULATION_FILEPATH=
INDUSTRY_FILEPATH=
JOBCOUNT_FILEPATH=
RATE_LIMIT_WINDOW_SECONDS=
RATE_LIMIT_ALLOWED_COUNT=
RATE_LIMIT_LOCK_TTL_MS=
RATE_LIMIT_SECRET=
OPENAI_API_KEY=
OPENAI_URL=
OPENAI_MODEL=
OPENAI_TEMPERATURE=
WORKNET_BASE_URL=
WORKNET_PATH=
```

총 **43개**.

## 이름만으로 알 수 있는 사항 [사실]

| 관찰 | 함의 |
| --- | --- |
| **`REDIS_SSL` 이 없다** | prod 는 `${REDIS_SSL:false}` 기본값으로 false 처리된다 |
| **`SEED_JOBS_*_ENABLED` 오버라이드가 없다** | `verify-seed.sh:68` 이 찾는 형식. 없으므로 **프로퍼티 파일 값이 그대로 쓰인다** — 즉 `industry`/`infra`/`population`/`job-count` 는 여전히 false |
| prod 필수 플레이스홀더는 전부 존재 | `SEED_VERSION`, `DEALYMD`, `FRONT_URL`, `SERVER_PORT` 등 |
| `MYSQL_META_DB` | `docker/mysql/init/01-init-meta-db.sh:14` 가 소비 |
| `*_FILEPATH` 8개 모두 정의됨 | `docker-compose.yaml:14` 마운트에 맞춰 컨테이너 경로여야 한다 |
| **`backend.env.example` 이 저장소에 없다** | seed-data SKILL §2-1 과 `verify-seed.sh:247` 이 참조하는 파일 → C-18 |
| `backend.env` 는 `.gitignore:4` 로 커밋 제외 | 팀원마다 내용이 다를 수 있다 |

---

## 부록: 후속 구현 시 우선순위 제안

| 순위 | 항목 | 근거 |
| ---: | --- | --- |
| 1 | `industry.csv` 작성 + `infra.csv` 5컬럼 정합 (C-4) | infra 컨텍스트 전체가 이것 하나로 막혀 있다. 추천 인프라 점수가 항상 0 |
| 2 | `infra.score` 를 `[0, 100]` 으로 산출 (A.4) | 벗어나면 추천 API 가 HTTP 400 으로 터진다 |
| 3 | `ratio` 단위 확정 (A.5) | 담당자 확인 필요. 코드로는 판정 불가 |
| 4 | `InfraScoreCacheCleaner` 를 `infraJob` 에 연결 (C-8) | 데이터 갱신이 최대 24시간 반영되지 않는다 |
| 5 | `infraJob` upsert 의 UPDATE 절에 `ratio`, `score` 추가 (C-3) | 재적재해도 값이 안 바뀐다 |
| 6 | 테이블명 대소문자 정합 (C-13) | `dwellingJob` 이 매 기동마다 조용히 실패 중일 수 있다 |
| 7 | `SidoBatchRunner` 의 `@Async` 제거 (C-1) | 지금은 무해하나 `@EnableAsync` 추가 시 즉시 폭발 |
| 8 | `BatchGuard` 재실행 정책 재설계 (C-2, C-3) | 주기 갱신 데이터를 다룰 수 없는 구조 |
| 9 | `InfraUpsertRow.count` → `Integer` (C-5) | 무음 데이터 오염 가능 |
| 10 | `verify-seed.sh` SPECS 인코딩 수정 (C-17) | 거짓 FAIL |
