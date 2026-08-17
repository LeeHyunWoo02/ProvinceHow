# 배치 데이터 파이프라인

이 문서는 **전체 흐름과 운영 절차**를 담는다. 개별 외부 API 의 상세 스펙은 각 문서를 본다.

| 문서 | 내용 |
| --- | --- |
| [external-api-spec.md](external-api-spec.md) | KOSIS / LOCALDATA / 국토부 실거래가 공식 스펙 검증 결과 |
| [worknet-job-api.md](worknet-job-api.md) | 워크넷 채용정보 API 스펙, 코드 매핑 정책 |
| [saramin-jobcount-batch-strategy.md](saramin-jobcount-batch-strategy.md) | 사람인 500회/일 제한 하 JobCount 배치 수집 전략, 호출 예산 가드레일 |
| [localdata-infra.md](localdata-infra.md) | 업종 마스터, 지역코드 매핑, ratio/score 계산식 |
| [work24-crawling-assessment.md](work24-crawling-assessment.md) | 고용24 공개검색 수집 **불가 판정** 근거 |
| [internal-batch-analysis.md](internal-batch-analysis.md) | 리팩토링 이전 구조 분석 (이력) |

---

## 1. 전체 흐름

```
[기동 시 1회]  SeedMasterJobLauncher  ──▶  seedMasterJob
                                             │
        ┌────────────────────────────────────┴─────────────────────────────────┐
        │  필수 기준 데이터 (ESSENTIAL) — 실패하면 준비 완료로 처리하지 않는다   │
        │    1. SidoStep       data/static/sido.csv                            │
        │    2. SigunguStep    data/static/sigungu.csv        (FK: sido)       │
        │    3. jcTopStep      data/static/level_top.csv                       │
        │    4. jcMiddleStep   data/static/level_middle.csv   (FK: job_code_top)│
        ├──────────────────────────────────────────────────────────────────────┤
        │  외부 갱신 데이터 (EXTERNAL) — 선행 데이터나 설정이 없으면 건너뛴다    │
        │    5. populationStep KOSIS API                      (FK: sigungu)    │
        │    6. industryStep   infra/industry-master.yml                       │
        │    7. infraStep      LOCALDATA API                  (FK: sigungu,industry)│
        │    8. jobCountStep   워크넷 채용정보 API            (FK: sigungu,job_code_middle)│
        │    9. dwellingStep   국토부 실거래가 API            (FK: sigungu)    │
        └──────────────────────────────────────────────────────────────────────┘

[정기]  DataRefreshScheduler (cron)  ──▶  PopulationJob / industryJob → infraJob
                                          jobCountJob / dwellingJob
```

**FK 순서는 `@Order` 가 아니라 Spring Batch Flow 가 보장한다.** 각 Step 앞에는
`SeedStepGate`(JobExecutionDecider)가 붙어 실행 여부를 판정한다.

### 두 그룹의 차이

| | 필수 기준 데이터 | 외부 갱신 데이터 |
| --- | --- | --- |
| Step | Sido, Sigungu, jcTop, jcMiddle | population, industry, infra, jobCount, dwelling |
| 출처 | 저장소에 커밋된 CSV | 외부 API / 번들 YAML |
| 재실행 기준 | `seedVersion` | `baseMonth`(yyyyMM) 또는 `baseDate`(yyyy-MM-dd) |
| 실패하면 | **준비 완료로 처리하지 않는다** (`ReadinessState.REFUSING_TRAFFIC`) | Job 은 계속 진행하고 사유를 기록한다 |
| 설정 없으면 | 실패 | **"미적재" 경고 후 건너뜀** — 애플리케이션은 정상 기동 |

필수 데이터가 실패해도 **프로세스를 죽이지 않는다.** compose 에 `restart: unless-stopped`
가 있어 죽이면 CSV 를 고칠 때까지 재시작 루프에 빠지고 원인 로그가 배너에 덮인다.

---

## 2. 배치별 데이터 출처와 주기

| Step | Job | 출처 | 인증키 | 주기 | 기준 파라미터 |
| --- | --- | --- | --- | --- | --- |
| SidoStep | SidoJob | `data/static/sido.csv` | — | 버전당 1회 | `seedVersion` |
| SigunguStep | SigunguJob | `data/static/sigungu.csv` | — | 버전당 1회 | `seedVersion` |
| jcTopStep | jcTopJob | `data/static/level_top.csv` | — | 버전당 1회 | `seedVersion` |
| jcMiddleStep | jcMiddleJob | `data/static/level_middle.csv` | — | 버전당 1회 | `seedVersion` |
| populationStep | PopulationJob | [KOSIS 통계 API](https://kosis.kr/openapi/) | `KOSIS_API_KEY` | 월 1회 | `baseMonth` |
| industryStep | industryJob | `classpath:infra/industry-master.yml` | — | 일 1회 | `baseDate` |
| infraStep | infraJob | [LOCALDATA 업종별 API](https://www.data.go.kr/) | `DATA_GO_KR_SERVICE_KEY` | 일 1회 | `baseDate` |
| jobCountStep | jobCountJob | [워크넷 채용정보](https://www.data.go.kr/data/3038225/openapi.do) | `DATA_GO_KR_SERVICE_KEY` | 일 1회 | `baseDate` |
| dwellingStep | dwellingJob | [국토부 아파트 전월세](https://www.data.go.kr/data/15126474/openapi.do) | `MOLIT_SERVICE_KEY` | 월 1회 | `baseMonth` |

`support policy` 는 별도 스케줄러(`SupportPolicyRefreshScheduler`)가 3일 주기로
Redis 정본을 갱신한다. 위 RDB 배치와 대상·경로가 겹치지 않는다.

### 인증키 발급 절차

| 키 | 발급처 | 심사 |
| --- | --- | --- |
| `DATA_GO_KR_SERVICE_KEY` | [data.go.kr](https://www.data.go.kr) 회원가입 → API 마다 "활용신청" | 자동승인 |
| `MOLIT_SERVICE_KEY` | 위와 같음 (같은 키를 써도 된다) | 자동승인 |
| `KOSIS_API_KEY` | [kosis.kr/openapi](https://kosis.kr/openapi/) 회원가입 | 심사 없음, 즉시 |

**활용신청은 API 마다 따로 한다.** 계정 키는 하나지만 신청하지 않은 API 를 부르면
`SERVICE_KEY_IS_NOT_REGISTERED_ERROR` 가 온다. LOCALDATA 는 업종별로 API 가 나뉘어
있어 수집할 업종마다 신청해야 한다.

Encoding / Decoding 두 종류가 발급되면 **URL 인코딩되지 않은 Decoding 키**를 넣는다.

---

## 3. 새 서버 최초 구동

```bash
cp backend.env.example backend.env
# backend.env 에서 DB 접속 정보와 SEED_VERSION 을 채운다.
# 인증키는 나중에 채워도 된다 — 없으면 해당 배치만 건너뛴다.

docker compose build backend
docker compose up -d
```

배치 메타 스키마가 없으면 **최초 1회만** `BATCH_SCHEMA_INIT=always` 로 올렸다가
`never` 로 되돌린다.

기동 로그에서 확인할 것:

```
[batch] step=SidoStep job=seedMasterJob seedVersion=v1 baseDate=... baseMonth=...
        read=17 filter=0 write=17 skip=0 commit=2 elapsedMs=239 status=COMPLETED exit=COMPLETED
[batch] step=dwellingStep group=EXTERNAL 건너뜀 - 미적재 데이터
        reason=필수 설정 누락 keys=[apis.molit.service-key]
```

인증키를 채운 뒤에는 **재기동만 하면** 건너뛰었던 Step 이 그 기준일/기준월로 실행된다.

---

## 4. 수동 실행 · 재실행 · 실패 복구

### 특정 Job 만 수동 실행

정기 스케줄과 같은 경로다. 환경변수로 켜고 재기동하거나, 운영 중이라면 cron 시각을
앞당긴다. 배치 메타의 유니크 제약이 중복 실행을 막으므로 스케줄러와 겹쳐도 안전하다.

```bash
# 예: 전월세만 특정 기준월로 다시 돌린다
DWELLING_BATCH_ENABLED=true DWELLING_DEAL_YMD_OVERRIDE=202509 docker compose up -d backend
```

### 재실행 규칙

| 대상 | 다시 돌리는 법 |
| --- | --- |
| 필수 기준 데이터 | `SEED_VERSION` 을 올린다 (`v1` → `v2`) |
| 필수 기준 데이터 (테이블이 비었을 때) | **자동으로 다시 돈다.** 이력이 있어도 적재 테이블이 비면 재실행한다 |
| 인구 / 전월세 | 다음 달이 되면 자동. 즉시 하려면 `DWELLING_DEAL_YMD_OVERRIDE` 로 기준월을 바꾼다 |
| 인프라 / 일자리 수 | 다음 날이 되면 자동 |

**`SEED_VERSION` 은 외부 데이터의 월별·일별 갱신을 막지 않는다.** 외부 Step 은
`seedVersion` 이 아니라 `baseMonth`/`baseDate` 로 완료 여부를 판정하기 때문이다.

### 멱등성

모든 외부 데이터 Writer 는 `ON DUPLICATE KEY UPDATE` upsert 다. 같은 기준일/기준월로
몇 번을 돌려도 행 수가 늘지 않는다. 필수 기준 데이터는 PK 가 외부 부여 문자열이라
`save` 가 `merge` 로 동작해 역시 멱등하다.

### 실패 복구

| 증상 | 원인과 조치 |
| --- | --- |
| 준비 완료 아님 + `필수 기준 데이터 적재 실패` | `data/static/*.csv` 를 확인한다. 열 개수가 어긋난 행이 있으면 배치가 **실패**한다(조용히 건너뛰지 않는다) |
| `건너뜀 - 미적재 데이터 keys=[...]` | 그 프로퍼티가 비어 있다. 대개 인증키다 |
| `건너뜀 - 이미 완료됨` | 같은 기준으로 이미 성공했다. 위 재실행 규칙을 본다 |
| `건너뜀 - 이전 실행이 아직 진행 중` | 다른 인스턴스가 돌고 있다. 끝나면 다음 주기에 실행된다 |
| 일부 지역만 실패 | Step 을 성공으로 표시하지 않는다. 부분 집계는 저장되지 않는다 |

---

## 5. 중복 실행 제어

JVM 메모리 락을 쓰지 않는다. **DB 가 막는다.**

1. `jobExplorer.findRunningJobExecutions` — 진행 중이면 건너뛰고 사유를 기록한다
2. **결정적 JobParameters** — `triggerTime` 같은 매번 달라지는 값을 넣지 않아, 같은
   기준일의 실행이 같은 JobInstance 로 수렴한다
3. 배치 메타 `JOB_INSTANCE(JOB_NAME, JOB_KEY)` **유니크 제약** — 2 덕분에 두 인스턴스가
   동시에 기동하면 늦은 쪽이 DB 레벨에서 막히고 예외로 흡수된다

`BatchGuard.stepAlreadyCompleted` 는 **Job 이름을 조건에 넣지 않는다.** seedMasterJob 이
돌린 `dwellingStep` 과 스케줄러가 `dwellingJob` 으로 돌린 `dwellingStep` 이 서로의
실행을 인정해 같은 기준월을 두 경로가 중복 적재하지 않는다.

> **남은 구멍**: 서로 다른 JobParameters 로 같은 테이블을 동시 적재하는 경우는 막지
> 못한다. 전용 락 테이블이나 MySQL `GET_LOCK` 이 필요하며 스키마 변경 사항이다.

---

## 6. 코드 매핑 정책

**모든 매핑은 코드 대 코드다. 명칭 유사도로 자동 매핑하지 않는다.**
매핑되지 않은 값은 임의 추정 없이 제외하고 **건수를 집계해 로그로 남긴다.**

| 대상 | 규칙 |
| --- | --- |
| 인구 (KOSIS) | 분류값 ID 가 곧 행정표준코드. 5자리 + `sigungu` 테이블 대조로 전국·시도 합계와 읍면동을 거른다 |
| 인프라 (LOCALDATA) | 개방자치단체코드(7자리) → 시군구(5자리) 명시 매핑표 229건. 산술 변환이 불가능해 표가 필요하다 |
| 인프라 — 일반구 35개 | LOCALDATA 는 시 단위로만 준다. **사업장 주소의 구 이름**으로 하위 구에 재분배한다. 구를 못 찾으면 상위 시로 떨어뜨리지 않고 실패로 계량한다 — 상위 시도 `sigungu` 에 있어 이중 집계가 된다 |
| 일자리 (워크넷) | 시도 대표코드(`NN000`)는 임의 배분 없이 제외. 다지역×다직종 공고는 조합마다 1건 |

업종 ↔ `Major`(HEALTH/FOOD/CULTURE/LIFE) 매핑은 **외부 응답에서 추론하지 않는다.**
`src/main/resources/infra/industry-master.yml` 에 명시하며, `majorReviewed: false` 인
항목은 로그에 "확인 필요"로 계속 남는다.

---

## 7. ratio 와 score

```
ratio(g,i) = count(g,i) / Σ_j count(g,j)      시군구 g 안에서 업종 i 가 차지하는 비율
score(g,i) = 100 × (below + 0.5 × ties) / (N − 1)      업종 i 의 전국 백분위 (midrank)
```

- `INFRA_RATIO_BASIS=PERCENT`(기본, 0~100) 또는 `FRACTION`(0~1).
  **실제 단위 계약은 코드에서 판정 불가다** — `GET /api/detail` 로 pass-through 될 뿐
  산술·비교·포맷팅하는 코드가 없다. 프런트가 `%` 를 붙이는지 확인이 필요하다.
- score 를 백분위로 고른 이유: `below + 0.5×ties ≤ N−1` 이라 **구조적으로 [0,100]** 이다.
  `InfraScorePolicy` 가 평균을 `Score.of()` 에 넣는데 `Score` 는 0~100 을 벗어나면
  예외를 던진다. DB 는 `decimal(6,2)` 라 100 초과도 **적재는 성공하고 추천 API 호출
  시점에 HTTP 400** 으로 터진다. 계산식 자체가 범위를 보장하게 만들었다.
- 반올림은 전 구간 `setScale(2, RoundingMode.HALF_UP)`, 중간 나눗셈만 `DECIMAL64`.

자세한 유도는 [localdata-infra.md](localdata-infra.md) 를 본다.

---

## 8. LOCALDATA 업종 추가

1. `src/main/resources/infra/industry-master.yml` 에 항목을 추가한다
   (`industryCode`, `serviceId`, `major`, `enabled`, `majorReviewed`)
2. `major` 는 사람이 정한다. 확신이 없으면 `major: null` + `enabled: false` 로 둔다
3. data.go.kr 에서 그 업종 API 에 **활용신청**한다 (자동승인)
4. 재기동하면 `industryStep` 이 `industry` 테이블을 갱신하고 `infraStep` 이 수집한다

`INFRA_COLLECT_SOURCE` 는 `API`(기본) / `BULK_CSV`(최초 시드·대규모 재적재) /
`LEGACY_CSV`(레거시 파일) 중 고른다. API 는 100건/요청 × 10,000회/일 제한이라
전국 수집에는 벌크 CSV 가 현실적이다.

---

## 9. 워크넷 API 응답이 바뀌었을 때

`src/main/resources/worknet/worknet-job-api.json` 만 고친다. 자바 코드를 고칠 필요가 없다.

워크넷 OPEN-API **문서 사이트가 2025-03-13 종료**되어 응답의 지역·직종 코드 필드명과
코드 체계가 **미확인 상태**다(엔드포인트는 살아 있다). 그래서 필드명을 후보 배열로 두고
앞에서부터 훑는다. 인증키 수령 직후 실제 응답 1건을 확인해 이 파일을 확정해야 한다.
절차는 [worknet-job-api.md](worknet-job-api.md) §10 에 있다.

---

## 10. 데이터 스냅샷 교체 정책

| 대상 | 정책 |
| --- | --- |
| 일자리 수 | 이번 스냅샷에서 사라진 조합은 **삭제가 아니라 `count=0` 으로 갱신**. Step 이 중간에 죽어도 데이터가 사라지지 않는다 |
| 인프라 | 새 스냅샷이 **완성된 뒤** 원자적으로 반영. 일부 API 실패 시 기존 스냅샷을 보존한다 |
| 인구 | 기준월별 upsert. 수집 실패 시 기존 데이터 보존 |
| 전월세 | 12개월 구간 중 일부 월이 실패하면 그 시군구는 성공으로 표시하지 않는다. **부분 집계를 저장하지 않는다** — 값이 있는데 틀린 값이 되어 이후 어떤 검증에도 걸리지 않기 때문이다 |

---

## 11. 관측

전 Step 에 `SeedStepExecutionListener` 가 공통으로 붙어 구조화 로그를 남긴다.

```
[batch] step=<이름> job=<Job> <기준 파라미터> read=N filter=N write=N skip=N
        commit=N elapsedMs=N status=<상태> exit=<종료코드>
```

배치별로 API 호출 수, 매핑 실패 건수, 수집 실패 지역을 추가로 남긴다
(`InfraStepLogger`, `districtResolved` / `districtUnresolved` 등).

**인증키는 어떤 레벨에서도 로그에 나가지 않는다.** URL 의 `serviceKey`/`authKey`/`apiKey`
는 값 자체를 마스킹하며 URL 인코딩 형태도 함께 지운다. API 응답 전문도 남기지 않는다.

건너뛴 Step 의 사유는 `StepExecution` 이 아니라 **JobExecution 의 ExecutionContext** 에
있다. 건너뛴 Step 은 StepExecution 자체가 생기지 않아 쓸 자리가 없기 때문이다.

---

## 12. 아직 정해지지 않은 것

| 항목 | 상태 |
| --- | --- |
| `ratio` 실제 단위 (0~1 vs 0~100) | 코드에서 판정 불가. 프런트 확인 필요 |
| 워크넷 응답의 지역·직종 필드명 | 인증키 수령 후 실측 필요 |
| `districtUnresolved` 실제 규모 | 첫 실제 수집에서 확인 필요. 크면 일반구 인프라가 과소 집계된다 |
| 상위 시 12개의 전월세 결측 | MOLIT 은 구 단위로만 준다. 하위 구 합산 여부는 기획 결정 |
| 멀티 인스턴스 전용 락 | 스키마 변경 사항이라 미구현 |
| `population.statistics_month` / `collected_at` | 컬럼이 없어 미적재. 스키마 변경 사항 |
