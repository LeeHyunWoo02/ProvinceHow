# 워크넷 채용정보 OPEN-API 연동 명세

- 조사·작성일: **2026-08-13**
- 대상 API
  - 한국고용정보원_워크넷 채용정보 **채용목록 및 상세정보** — https://www.data.go.kr/data/3038225/openapi.do (자동승인)
  - 한국고용정보원_워크넷 **공통코드**(지역, 직종, 자격면허 등) — https://www.data.go.kr/data/15037287/openapi.do
- 이 문서는 `docs/work24-crawling-assessment.md` 의 결론(공개 검색화면 스크래핑 불가)에 따라
  **공식 OPEN-API 로 전환**하기 위한 연동 명세다.
- 조사 시점에 **인증키가 없다.** 따라서 실제 데이터 응답은 한 번도 받아보지 못했다.
  아래 "확인됨"은 (a) 공식 문서·공개 예제 2개 이상의 교차검증 또는
  (b) **인증키 없이 실제 엔드포인트를 찔러 받은 오류 응답**으로 확정된 것만 가리킨다.

---

## 0. 먼저 읽을 것 — 두 가지 경고

### 0-1. 워크넷 OPEN-API 사이트는 2025-03-13 종료됐다

`https://openapi.work.go.kr/opiMain.do` 는 현재 아래 안내만 띄우고 고용24로 리다이렉트한다(실측).

> 워크넷 OPEN-API서비스가 종료되었습니다.
> 워크넷 OPEN-API서비스가 고용24 OPEN-API로 통합되었습니다.
> 신규 인증키 신청을 원하면 고용24 OPEN-API 서비스에 회원가입 후 인증키를 신청해주세요.

**그러나 API 엔드포인트 자체는 살아 있다.** `https://openapi.work.go.kr/opi/opi/opia/wantedApi.do`
는 2026-08-13 현재 HTTP 200 과 정상 오류 XML 을 돌려준다(§2). 문서 사이트만 닫힌 상태다.
공공데이터포털의 두 API 도 계속 게시돼 있다.

> **따라서 인증키를 어디서 받아야 하는지가 갈린다.** §7 참조.

### 0-2. 라이선스가 크롤링을 막았던 것과 같다 — 확인 필요

공공데이터포털의 두 API 모두 이용허락범위가
**"공공저작물 : 출처표시, 상업적 이용금지, 변경금지 (제4유형)"** 로 표기돼 있다.

이는 `work24-crawling-assessment.md` §3 에서 스크래핑을 불가로 만든 것과 **동일한 조항**이다.
API 로 바꿨다고 저작권 조건이 달라지지 않는다.

| 항목 | 우리 사용 | 제4유형 조항 |
| --- | --- | --- |
| `sigungu_code, job_code, count` 집계 생성 | 한다 | **변경금지(2차적 저작물 작성 금지)** 에 저촉될 소지 |
| 서비스에서 통계로 제공 | 한다 | 상업성이 있으면 **상업적 이용금지** 에 저촉 |

**조치**: 인증키 활용신청서의 활용목적란에
"시군구×직종별 채용공고 **건수 집계** 및 이주지원 서비스 내 **통계 제공**" 을 명시하고,
제공기관(한국고용정보원)과 **집계·재가공·재배포 범위를 사전 협의**할 것.
이 협의가 없으면 데이터를 받아도 쓸 수 없다. 크롤링 조사 때와 같은 결론이다.

---

## 1. 엔드포인트

| 항목 | 값 | 상태 |
| --- | --- | --- |
| Base URL | `https://openapi.work.go.kr` | **확인됨** (HTTPS 로 정상 응답) |
| 채용목록 경로 | `/opi/opi/opia/wantedApi.do` | **확인됨** (200 + 정상 오류 XML) |
| 채용상세 경로 | 같은 경로에 `callTp=D` | **확인됨** (공개 예제 2건) |
| 공통코드 API 경로 | — | **미확인** (`comCodeApi.do` 등 추정 경로는 전부 404) |
| 응답 포맷 | **XML 전용** | **확인됨** (§2-2) |

교차검증 출처
- 공공데이터포털 API 상세: https://www.data.go.kr/data/3038225/openapi.do
- 공개 예제(파이썬 강의): https://wikidocs.net/67233 , https://wikidocs.net/67231
- 공개 예제(블로그): https://informationsystem2team.blogspot.com/2014/06/api_5.html
- **실측**: 인증키 없이 엔드포인트 호출 (2026-08-13)

> 공통코드 API 의 실제 호출 경로는 확정하지 못했다. data.go.kr 상세페이지의 엔드포인트 표시가
> 로그인 뒤에만 렌더링되고, 추정 경로(`/opi/opi/opia/comCodeApi.do`,
> `/opi/opi/opia/wantedComCdApi.do`, `/opi/opi/opia/comCdApi.do`, `/opi/opi/opic/comCodeApi.do`)는
> 전부 404 였다. **추측으로 구현하지 않았다.** 코드 매핑은 §5 의 설정 파일로 대신한다.

---

## 2. 요청 파라미터

### 2-1. 실측 확인된 것

```
GET https://openapi.work.go.kr/opi/opi/opia/wantedApi.do
    ?authKey=<인증키>&callTp=L&returnType=XML&startPage=1&display=100
```

| 파라미터 | 의미 | 값 | 상태 |
| --- | --- | --- | --- |
| `authKey` | 인증키 | — | **확인됨**. `serviceKey` 가 **아니다** |
| `callTp` | 호출유형 | `L`(목록) / `D`(상세) | **확인됨**. 누락 시 `messageCd=008` |
| `returnType` | 반환형식 | `XML` | **확인됨**. `JSON` 을 주면 `messageCd=004` (§2-2) |
| `startPage` | 시작페이지 | 1 ~ 1000 | 이름 확인됨 / 상한 1000 은 **미확인**(공개 예제 근거) |
| `display` | 출력건수 | 1 ~ 100 | 이름 확인됨 / 상한 100 은 **미확인**(공개 예제 근거) |
| `region` | 근무지역 | — | 이름만 확인, **코드 체계 미확인** (§4) |
| `occupation` | 직종 | — | 이름만 확인, **코드 체계 미확인** (§4) |
| `keyword` | 검색어 | — | **확인됨**(공개 예제) |

그 밖에 data.go.kr 문서가 나열하는 조건(임금형태, 최소/최대급여, 학력, 경력, 우대조건, 고용형태,
근무기간, 근무형태, 기업형태, 사업자등록번호, 강소기업여부, 채용여부, 구인시작/종료일, 정렬방식)은
**이름 철자를 확정하지 못했다.** 구현에서 쓰지 않는다.

### 2-2. 실측 응답 (인증키 없이, 2026-08-13)

```
$ curl "https://openapi.work.go.kr/opi/opi/opia/wantedApi.do?authKey=TEST&callTp=L&returnType=XML&startPage=1&display=10"
HTTP/1.1 200 OK
Content-Type: application/xml
<?xml version="1.0" encoding="UTF-8"?><wantedRoot><message>유효하지 않은 인증키 입니다.</message><messageCd>002</messageCd></wantedRoot>
```

| 실험 | 응답 |
| --- | --- |
| `callTp` 누락 | `messageCd=008` "호출타입 값이(callTp)이 바르지 않습니다." |
| `returnType=JSON` | `messageCd=004` "리턴 타입이 올바르지 않습니다.**(xml)**" → **XML 전용 확정** |
| `region=99999` 등 이상값 | 인증키 오류(`002`)가 먼저 난다 → 값 검증 여부 확인 불가 |

- **파라미터 검증이 인증키 검증보다 먼저 일어난다**(`callTp` 누락이 `002` 보다 앞선다).
  덕분에 키 없이도 파라미터명 일부를 검증할 수 있었다.
- 저장 fixture: `src/test/resources/fixtures/worknet/invalid-auth-key.xml` (실제 응답 원문)

### 2-3. 호출 제한(트래픽)

**미확인.** data.go.kr 은 "기관 정책에 따라 상이"라고만 적고 있고, 응답에
`RateLimit-*` / `Retry-After` 계열 헤더는 없었다. 구현은 보수적으로 잡았다 — §6.

---

## 3. 응답 구조

```xml
<wantedRoot>
    <total>1419</total>
    <startPage>1</startPage>
    <display>100</display>
    <wanted>
        <wantedAuthNo>KJAU002608130001</wantedAuthNo>
        <company>...</company><title>...</title>
        <sal>...</sal><salTpNm>...</salTpNm>
        <region>...</region><holidayTpNm>...</holidayTpNm>
        <career>...</career><regDt>2026-08-13</regDt><closeDt>2026-10-12</closeDt>
        <basicAddr>...</basicAddr>
    </wanted>
    ...
</wantedRoot>
```

| 요소 | 의미 | 상태 |
| --- | --- | --- |
| `wantedRoot` | 루트 | **확인됨** (오류 응답 실측) |
| `message` / `messageCd` | 오류 메시지 / 코드 | **확인됨** (실측) |
| `total` | 전체 건수 | **확인됨** (공개 예제 2건) |
| `startPage` / `display` | 요청 에코 | **확인됨** (공개 예제) |
| `wanted` | 공고 1건 | **확인됨** (공개 예제 2건) |
| `wantedAuthNo` | 구인인증번호 = 공고 고유키 | **확인됨** (공개 예제 2건) |
| `company` `title` `sal` `region` `career` `regDt` `closeDt` `basicAddr` | 회사명/제목/급여/근무지역/경력/등록일/마감일/주소 | **확인됨** (공개 예제) |
| **근무지역 코드 필드명** | — | **미확인** |
| **직종 코드 필드명** | — | **미확인** |

> `<region>` 이 코드인지 명칭인지 확정하지 못했다. 공개 예제는 값을 그대로 출력만 한다.
> 고용24 화면의 근무지역 텍스트가 도로명/법정동이 뒤섞인 비정형이라
> (`work24-crawling-assessment.md` §8-4) **명칭 파싱에 의존하지 않는 설계**로 갔다.

### 페이지네이션

- `startPage` 를 1부터 올리며 `display` 건씩 받는다. **확인됨**
- 종료 조건은 `total` 기준으로 계산한다. `total` 을 못 읽으면 **빈 페이지가 나올 때까지** 돈다.
- 도달 가능한 최대 건수는 `maxStartPage × maxDisplay = 1000 × 100 = 100,000` 건이다.
  전국 공고가 이를 넘으면 **잘린다.** 그때는 `request.extraParams` 로 지역/직종 조건을 걸어
  질의를 쪼개야 한다(설정만 바꾸면 된다).

---

## 4. 코드 체계 결론

| 코드 | 프로젝트 체계 | 워크넷 OPEN-API | 판정 |
| --- | --- | --- | --- |
| 지역 | 행정표준코드 시군구 5자리 (`11110`) — `data/static/sigungu.csv` 264건 | 고용24 **화면**의 `region` 은 5자리 행정표준코드로 동작함이 확인됨(`work24-crawling-assessment.md` §5-2). **OPEN-API 도 같은지는 미확인** | **동일하다고 가정하되 설정으로 뒤집을 수 있게 함** |
| 직종 | KECO 중분류 3자리 (`011`, `01A`) — `data/static/level_middle.csv` 114건 | 고용24 화면의 `occupation=011` 이 `level_middle.csv` 의 `011,행정·경영·금융·보험 관리직` 과 **코드·명칭 모두 일치**함이 확인됨(§7-1). **OPEN-API 도 같은지는 미확인** | 같음이 유력. **동일 가정 + 설정으로 뒤집기 가능** |

가정은 **코드에 상수로 박지 않고** `src/main/resources/worknet/worknet-job-api.json` 에 뒀다.
인증키를 받은 뒤 실제 응답을 보고 이 파일만 고치면 된다(§5).

---

## 5. 매핑 정책

설정 파일: **`src/main/resources/worknet/worknet-job-api.json`**
(경로는 `worknet.job.spec-path` 로 바꿀 수 있다. 파일이 없거나 깨져 있어도 기동을 막지 않고 기본값으로 떨어진다.)

### 5-1. 원칙

1. **코드 대 코드로만 옮긴다. 명칭 유사도 자동 매핑은 하지 않는다.**
   "중구"는 서울에도 부산에도 대구에도 있다. 이름으로 맞추면 틀린 지역에 공고가 쌓인다.
2. `mapping.regionCodes` / `mapping.jobCodes` 의 **명시 매핑이 passthrough 보다 우선**한다.
3. **매핑되지 않은 코드는 임의 분류하지 않고 버린다.** 버린 건수는 집계해 로그로 남긴다.
4. 매핑을 통과해도 **`sigungu` / `job_code_middle` 마스터에 없는 코드는 배치 Processor 가 다시 버린다.**
   즉 적재되는 코드는 항상 기존 테이블에 존재하는 코드뿐이다(FK 보장).

### 5-2. 시도 단위 / 지역 무관 공고

| 입력 | 처리 |
| --- | --- |
| 시군구 코드 (`11110`) | 그대로 집계 |
| **시도 대표코드 (`NN000`, 예: `11000` 서울 전체)** | **버린다.** 어느 구인지 알 수 없어 임의 배분하면 데이터가 왜곡된다 |
| 전국·지역무관 공고 | 위와 같다. 시군구가 확정되지 않으면 집계하지 않는다 |
| `mapping.ignoredRegionCodes` 에 등록한 코드 | 조용히 버린다(매핑 실패로 세지 않는다) |

`NN000` 규칙이 안전한 근거: `data/static/sigungu.csv` 264건 중 **`000` 으로 끝나는 코드는 하나도 없다.**
따라서 시군구 코드와 충돌하지 않는다.

> **대가**: 시도 단위로만 등록된 공고는 `JobCount` 에 반영되지 않아 실제보다 적게 잡힌다.
> 임의 배분으로 숫자를 부풀리는 것보다 낫다는 판단이다.

### 5-3. 다중 지역 / 다중 직종 공고

**한 공고가 N개 지역 × M개 직종에 걸리면, N×M 개 칸에 각각 1건씩 센다.**

- 근거: `JobCount` 는 "그 시군구에서 그 직종으로 **지원할 수 있는** 공고 수"다.
  서울 중구·용산구 동시 모집 공고는 두 구 모두에서 실제로 지원 가능하므로 두 곳 모두 1건이 맞다.
- 안 하는 선택지와 이유
  - `1/N` 안분 → 정수가 아니고, `count` 컬럼이 `Integer` 다
  - 첫 번째 지역에만 몰아주기 → **임의 배분**이다
- **대가**: 전 지역 `count` 합계는 전국 공고 수보다 크다.
  이 값을 "전국 공고 수"로 해석하면 안 된다.

### 5-4. 중복 제거

`wantedAuthNo` 로 중복을 제거한다. 페이지를 넘겨 받는 동안 목록이 밀려
같은 공고가 두 페이지에 걸쳐 나올 수 있기 때문이다.
`worknet.job.dedupe-enabled=false` 로 끌 수 있다(메모리를 아껴야 할 때).

### 5-5. 매핑 실패 집계

배치 종료 로그에 다음이 남는다(§8).
`unresolvedRegion`(지역 미매핑으로 버린 공고 수) / `unresolvedJob`(직종 미매핑으로 버린 공고 수).
미매핑 코드는 **최대 10개까지 샘플만** WARN 으로 남긴다 — 전량을 찍으면 로그가 터진다.

---

## 6. 스냅샷 교체 정책 (이번 회차에 사라진 집계)

**정책: 사라진 조합의 행을 지우지 않고 `count = 0` 으로 내린다.**

동작
1. API 를 끝까지 당겨 `(시군구, 직종) → 건수` 집계를 만든다.
2. `JobCount` 에 **이미 있는 키 전량**을 조회한다(`findAllKeys()`).
3. 새 집계에 없는 키를 `count = 0` 행으로 추가해 함께 Upsert 한다.

선택 근거

| 후보 | 채택 | 이유 |
| --- | --- | --- |
| **0 으로 내린다** | ✅ | 기존 Upsert SQL(`ON DUPLICATE KEY UPDATE count = VALUES(count)`)을 **한 글자도 안 바꾸고** 스냅샷 교체가 된다. `DELETE` 가 없어 Step 이 중간에 죽어도 데이터가 사라지지 않는다. 조회 경로(`SUM(count)`)에서 0 은 없는 것과 같다 |
| 배치 시작 시 전체 `UPDATE ... SET count = 0` | ✗ | Step 이 중간에 실패하면 **전 지역 일자리 수가 0인 상태로 서비스가 뜬다.** 별도 Step 이 필요한데 `seedMasterJob` Flow(다른 에이전트 소유)를 건드려야 한다 |
| 없는 조합 `DELETE` | ✗ | "0건"과 "미수집"을 구분할 수 없게 된다. 되돌리기도 어렵다 |
| 그냥 둔다(기존 동작) | ✗ | 지난 회차 값이 유령처럼 남는다. 지금 고치려는 문제다 |

끄려면 `worknet.job.reset-missing-to-zero=false`.

> **인증키가 없을 때는 0 리셋도 하지 않는다.** 아무 행도 내보내지 않으므로 기존 데이터가 그대로 남는다.
> 키를 안 넣었다는 이유로 서비스 데이터를 지우면 안 된다.

---

## 7. 인증키 발급 절차

**프로젝트는 인증키를 `DATA_GO_KR_SERVICE_KEY` 하나로 통합한다.** 별도 키 이름을 만들지 않는다.

### 7-1. 공공데이터포털 경로 (권장, 자동승인)

1. https://www.data.go.kr 회원가입 / 로그인
2. https://www.data.go.kr/data/3038225/openapi.do → **활용신청**
   - 심의: **자동승인**
   - 활용목적에 §0-2 의 문구를 명시하고 재가공·재배포 범위를 적을 것
3. 같은 절차로 https://www.data.go.kr/data/15037287/openapi.do (공통코드) 도 신청
4. 마이페이지 > 오픈API > 개발계정에서 **일반 인증키(Decoding)** 를 복사
5. `DATA_GO_KR_SERVICE_KEY` 환경변수에 넣는다

> ⚠️ **미확인**: data.go.kr 이 발급한 인증키를 `openapi.work.go.kr` 의 `authKey` 파라미터에
> 그대로 넣어 통과하는지는 **키가 없어 검증하지 못했다.**
> 통과하지 않으면 §7-2 로 간다. 구현은 파라미터명을 설정으로 바꿀 수 있게 해 뒀다
> (`request.authKeyParam`).

### 7-2. 고용24 경로 (워크넷 사이트 종료에 따른 승계)

1. 고용24 **기업회원** 가입 (OPEN-API 는 기업회원 전용)
2. https://www.work24.go.kr/cm/e/a/0110/selectOpenApiIntro.do → `OPEN-API > 서비스 소개 및 신청`
3. 담당자 심사 → 인증키 발급 (**리드타임 있음**)
4. 같은 `DATA_GO_KR_SERVICE_KEY` 환경변수에 넣는다 (키 이름을 늘리지 않는다)

### 7-3. 키가 없는 동안의 동작

- 애플리케이션은 **정상 기동한다.**
- `jobCountStep` 은 API 를 **호출하지 않고** 아무 행도 내보내지 않으며, 다음 경고를 남긴다.
  ```
  [jobCountStep] baseDate=... 채용정보 API 인증키(apis.datagokr.service-key / DATA_GO_KR_SERVICE_KEY)가
  비어 있어 일자리 수를 적재하지 않는다. 이 데이터는 '미적재' 상태로 남는다.
  ```
- 기존 `JobCount` 데이터는 그대로 남는다.

---

## 8. 구현 구조

```
domain/job/
├── domain/model/     JobPosting, JobPostingId, JobPostingPage, JobCountKey   ← 순수 자바
├── domain/port/      JobPostingProvider                                       ← out-port
├── infrastructure/external/
│   ├── WorknetJobPostingApiAdapter   HTTP 호출 · 인증키 주입 · 재시도 · URL 마스킹
│   ├── WorknetJobPostingParser       XML → 원문 DTO
│   ├── WorknetCodeMapper             워크넷 코드 → SigunguCode/JobCode
│   ├── WorknetApiSpecLoader          worknet-job-api.json 로드
│   └── dto/                          WorknetApiSpecFile, WorknetJobPostingRaw   ← 밖으로 안 나간다
└── infrastructure/batch/
    ├── WorknetJobCountItemReader     API 페이지 수집 → 집계 → JobCountCsvRow 방출
    └── JobCountBatchConfig           Reader 만 교체 (Processor·Writer·SQL 그대로)
```

- **CSV 중간산출물이 없다.** API → 집계 → Processor → DB Upsert.
- 공고 원문은 **페이지 단위로만** 메모리에 있다가 즉시 집계에 접혀 버려진다.
  상주 메모리는 집계 맵(최대 264×114 = 3만) + 중복제거 ID 집합(최대 `maxPages × pageSize`)뿐이다.
- **레거시 CSV 경로는 남아 있다.** `worknet.job.batch.enabled=false` 로 되돌린다.
- 기존 `jobCountJob` / `jobCountStep` / Processor / Writer / Upsert SQL 은 그대로다.

### 8-1. 멱등성

- 같은 기준일로 다시 돌려도 `count = VALUES(count)` 라 같은 상태로 수렴한다.
- `baseDate` 는 `JobParameters` 로 들어오며(`DataRefreshScheduler` / `seedMasterJob` 이 넣는다)
  `BatchGuard` 가 같은 기준일 재실행을 막는다.
- Step 중간 재시작은 지원하지 않는다 — 부분 집계를 이어 붙이면 건수가 틀린다. 열리면 처음부터 다시 모은다.

### 8-2. 구조화 로그

Step 종료 시 한 줄로 남는다.

```
[jobCountStep] baseDate=2026-08-13 status=COMPLETED apiCalls=57 readPostings=5432 duplicates=12
               unresolvedRegion=88 unresolvedJob=140 emittedRows=3120 zeroedRows=44
               written=3164 elapsed=61234ms
```

실패하면 `status` 와 함께 **실패 원인 예외**를 ERROR 로 남긴다.

### 8-3. 비밀값 취급

- 인증키가 비면 **호출하지 않는다.**
- 로그의 URL 은 항상 마스킹한다. 파라미터명이 아니라 **키 값 자체**를 `****` 로 바꾸므로
  `authKeyParam` 을 설정으로 바꿔도 계속 가려진다. URL 인코딩된 형태도 함께 지운다.
- **API 응답 전문을 로그로 찍지 않는다.** 기업명·주소가 들어 있고 양도 크다.

---

## 9. 설정 항목

`application-dev.properties` / `application-prod.properties` 에 추가해야 한다.
(이 문서 작성 시점에 두 파일은 다른 작업자가 잡고 있어 직접 넣지 않았다.
**코드에는 전부 기본값이 있어 넣지 않아도 기동한다.**)

```properties
# 채용정보 API (공공데이터포털 / 한국고용정보원 워크넷)
apis.datagokr.service-key=${DATA_GO_KR_SERVICE_KEY:}
worknet.api.base-url=${WORKNET_API_BASE_URL:https://openapi.work.go.kr}
worknet.api.path=${WORKNET_API_PATH:/opi/opi/opia/wantedApi.do}
worknet.api.max-attempts=${WORKNET_API_MAX_ATTEMPTS:3}
worknet.api.retry-delay-ms=${WORKNET_API_RETRY_DELAY_MS:1000}

# 일자리 수 배치 - 적재 소스와 수집 범위
worknet.job.batch.enabled=${WORKNET_JOB_BATCH_ENABLED:true}
worknet.job.page-size=${WORKNET_JOB_PAGE_SIZE:100}
worknet.job.max-pages=${WORKNET_JOB_MAX_PAGES:1000}
worknet.job.dedupe-enabled=${WORKNET_JOB_DEDUPE_ENABLED:true}
worknet.job.reset-missing-to-zero=${WORKNET_JOB_RESET_MISSING_TO_ZERO:true}
worknet.job.spec-path=${WORKNET_JOB_SPEC_PATH:classpath:worknet/worknet-job-api.json}
```

### 9-1. 아직 남은 두 가지 (다른 작업자 소유 파일)

1. **`global/batch/SeedMasterJobConfig`** — `jobCountStep` 의 `requiredConfigs` 가 아직
   `jobCount.filePath` 다. API 모드에서는 이 값이 비어 있어도 되므로,
   `SeedStepGate` 가 엉뚱하게 Step 을 건너뛴다. 아래로 바꿔야 한다.
   ```java
   specs.add(new SeedStepSpec("jobCountStep", SeedGroup.EXTERNAL, jobCountEnabled, BASE_DATE,
           configs("apis.datagokr.service-key", dataGoKrServiceKey),   // ← filePath 대신
           List.of(SIGUNGU, JOB_CODE_MIDDLE), null));
   ```
   `SeedStepSpec.requiredConfigs` 는 **키만 로그에 남기고 값은 남기지 않으므로**
   인증키를 넣어도 안전하다. 키가 비면 Step 을 건너뛰고 사유를 기록한다 — 우리가 원하는 동작이다.

2. **`global/batch/DataRefreshScheduler`** — `jobCountJob` 정기 실행 스위치가 아직
   `work24.crawler.enabled` / `work24.crawler.cron` 이다. 크롤링을 폐기했으므로
   `worknet.job.batch.*`(`WORKNET_JOB_BATCH_ENABLED` / `WORKNET_JOB_BATCH_CRON`, 기본 `0 0 3 * * *`)
   로 바꿔야 한다. **중복 실행을 만들지 않으려고 별도 스케줄러를 만들지 않았다.**

---

## 10. 인증키 없이 검증하지 못한 것

| 항목 | 왜 못 했나 | 어떻게 대비했나 |
| --- | --- | --- |
| 정상 데이터 응답의 실제 XML | 키가 없어 `messageCd=002` 만 받음 | 응답 요소명을 설정 파일로 분리 + 후보 필드 다중 지정 |
| `<wanted>` 안의 지역/직종 **코드 필드명** | 위와 같음 | `response.regionFields` / `jobCodeFields` 에 후보를 두고 앞에서부터 훑음 |
| 워크넷 **지역코드 체계** | 위와 같음 + 공통코드 API 경로 미확정 | `mapping.regionCodePassthrough` + `mapping.regionCodes` 로 뒤집을 수 있게 함 |
| 워크넷 **직종코드 체계** | 위와 같음 | `mapping.jobCodePassthrough` + `mapping.jobCodes` |
| 다중 지역/직종의 **실제 표현 방식** | 위와 같음 | `response.multiValueDelimiters` 후보(`,` `|`) |
| `display` / `startPage` **실제 상한** | 위와 같음 | `request.maxDisplay` / `maxStartPage` 로 설정화 (기본 100 / 1000) |
| **호출 제한(트래픽)** 정책 | 문서에 "기관 정책에 따라 상이" | 재시도 3회 + 1초 간격, 페이지 상한으로 총 호출 수를 묶음 |
| data.go.kr 키가 `authKey` 로 통하는지 | 키가 없음 | `request.authKeyParam` 을 설정화 |
| **공통코드 API 호출 경로** | 추정 경로 전부 404 | 구현하지 않음. 매핑표는 설정 파일로 사람이 채운다 |

**인증키를 받은 직후 해야 할 일**
1. `callTp=L&display=1&startPage=1` 로 한 건만 받아 **응답 XML 원문을 확인**한다
2. 지역/직종 코드 필드명을 확인해 `worknet-job-api.json` 의 후보를 **하나로 좁힌다**
3. 지역/직종 코드 샘플을 `data/static/sigungu.csv` / `level_middle.csv` 와 대조해
   passthrough 가정이 맞는지 확인한다. 틀리면 매핑표를 채운다
4. 소량(`max-pages=2`)으로 배치를 돌려 `unresolvedRegion` / `unresolvedJob` 이 0에 가까운지 본다
5. 그 뒤에 전량 수집으로 올린다
