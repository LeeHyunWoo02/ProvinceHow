# 지방행정 인허가 데이터 기반 인프라 수집 (infra 컨텍스트)

작성 기준일 **2026-08-13**.
외부 스펙의 1차 근거는 `docs/external-api-spec.md` 2장이다. 여기서는 **이 프로젝트가 실제로 구현한 규칙**을 정리한다.

각 항목에 **[확인됨]** / **[미확인]** 라벨을 붙였다. 라벨 없는 문장은 이 저장소의 구현 사실이다.

---

## 0. 한 장 요약

| 항목 | 값 |
| --- | --- |
| 배치 | `industryJob`(업종 마스터) → `infraJob`(인프라 통계) |
| `infraJob` 구성 | **2-Step** — `infraCollectStep`(수집·적립) → `infraStep`(집계·반영). `API` 경로 전용이다(§9) |
| 트리거 | `DataRefreshScheduler.refreshLocaldata()` — `LOCALDATA_BATCH_ENABLED` / `LOCALDATA_BATCH_CRON` |
| JobParameter | `baseDate` (yyyy-MM-dd) |
| 기본 수집 경로 | 공식 data.go.kr 업종별 API |
| 업종 마스터 | `src/main/resources/infra/industry-master.yml` |
| 지역코드 매핑 | `src/main/resources/infra/localdata-region-mapping.yml` |
| 인증키 | `apis.datagokr.service-key` ← `DATA_GO_KR_SERVICE_KEY` **하나만** 쓴다 |
| `ratio` | 시군구 내 업종 구성비. 기본 **PERCENT(0~100)** |
| `score` | 업종별 **전국 백분위**. 구조적으로 `[0, 100]` |

---

## 1. 전제 — 구 LOCALDATA 는 없어졌다 **[확인됨]**

`localdata.go.kr` 은 **2026-04-16 폐쇄**됐다. 다음은 전부 죽은 스펙이다.

- 요청: `authKey`, `opnSvcId`, `localCode`, `resultType`, `pageIndex`, `pageSize`, `state`, `lastModTsBgn`
- 응답: `trdStateGbn`, `opnSfTeamCode`, `mgtNo`, `bplcNm`, `uptaeNm`, `siteWhlAddr`

데이터는 공공데이터포털(data.go.kr)의 **업종별 195종 API** 로 이관됐고,
**업종 구분이 파라미터에서 URL 경로 slug 로 승격**된 것이 가장 큰 구조 변화다.

`data/legacy/infra.csv` 의 `opnSvcId` 컬럼(14종)은 신 API 에서 쓸 수 없다.
그래서 이 컬럼은 업종 마스터의 `legacyServiceIds` 로 **명시 매핑**되며, 매핑이 없으면 제외된다(§4).

---

## 2. 확인된 API 스펙

### 2.1 엔드포인트 **[확인됨]**

```
GET https://apis.data.go.kr/1741000/{slug}/info
      ?serviceKey={KEY}
      &pageNo=1
      &numOfRows=100
      &returnType=json
      &cond%5BOPN_ATMY_GRP_CD%3A%3AEQ%5D=3000000
      &cond%5BSALS_STTS_CD%3A%3AEQ%5D=01
```

| 항목 | 값 | 라벨 |
| --- | --- | --- |
| 기관 경로 | `1741000` | [확인됨] |
| 오퍼레이션 | `/info`(현재) · `/history`(이력, `cond[BASE_DATE::EQ]` 필수) | [확인됨] |
| **`numOfRows` 상한** | **100** | [확인됨] Swagger 명시 |
| 파라미터 형식 | `cond[FIELD::OP]` — 대괄호·콜론 **URL 인코딩 필수** | [확인됨] |
| 일일 트래픽 | 개발계정 10,000회 | [확인됨] |
| 초당 호출 제한 | 에러코드 `23` 존재, 수치 비공개 | **[미확인]** |
| `returnType` 허용 문자열 | Swagger 에 열거되지 않음 (구현은 `json` 사용, 실동작 확인) | **[미확인]** |
| 데이터 갱신 | 매일, D-2 기준 현행화 | [확인됨] |

구현: `SDD.smash.domain.infra.infrastructure.external.LocalDataApiAdapter`

### 2.2 읽는 응답 필드

업종마다 필드 집합이 다르므로 **전 업종 공통으로 신뢰 가능한 세 개만** 읽는다 [확인됨].

| 필드 | 용도 |
| --- | --- |
| `MNG_NO` | 관리번호 — **중복 제거 키** |
| `SALS_STTS_CD` | 영업상태코드 — 집계 필터 |
| `OPN_ATMY_GRP_CD` | 개방자치단체코드 — 시군구 매핑 |

키 이름이 대소문자로 흔들리는 업종을 대비해 정확 일치 → 대소문자 무시 순으로 찾는다.

> **[확인됨] 좌표는 EPSG:5174(보정계수 없는 Bessel 중부원점TM)** 다. 이 프로젝트는 좌표를 쓰지 않는다.

### 2.3 게이트웨이 에러 **[확인됨]**

`OpenAPI_ServiceResponse.cmmMsgHeader.errMsg` 가 있으면 실패로 본다. 자주 만나는 것:

| errMsg | 코드 | 의미 |
| --- | --- | --- |
| `SERVICE_KEY_IS_NOT_REGISTERED_ERROR` | 30 | 해당 **데이터셋에 활용신청이 안 된 키** |
| `LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR` | 22 | 일일 허용량 초과 |
| `...PER_SECOND_EXCEEDS_ERROR` | 23 | 초당 허용량 초과 |

HTTP 429 / 5xx 는 `Retry-After`(초) 를 읽어 대기 후 재시도한다. 그 밖의 4xx 는 즉시 실패다.
403 에는 "업종별 활용신청이 필요할 수 있다"는 안내를 메시지에 붙인다.

---

## 3. 인증키 발급·활용신청 절차 **[확인됨]**

1. `localdata.go.kr` 의 `authKey` 는 **소멸했다.** 별도 회원가입·발급이 필요 없다.
2. data.go.kr 계정의 `serviceKey` **하나**를 쓴다 → 환경변수 `DATA_GO_KR_SERVICE_KEY`.
3. **키 문자열은 계정 단위지만 권한은 데이터셋 단위다.**
   쓸 업종 수만큼 포털에서 활용신청 버튼을 눌러야 한다. **심의는 자동승인**이라 즉시 쓸 수 있다.
4. 신청할 데이터셋 ID 는 `industry-master.yml` 의 `datasetId` 필드에 적혀 있다.
   예) 일반음식점 `15154916` → `https://www.data.go.kr/data/15154916/openapi.do`

> 활용신청을 빠뜨린 업종은 HTTP 403 / `SERVICE_KEY_IS_NOT_REGISTERED_ERROR` 로 실패한다.
> 배치는 이 실패를 삼키지 않고 스냅샷 전체를 포기한다(§7).

### 비밀값 취급

- 로그에 남기는 URL 은 `serviceKey=****` 로 마스킹한다(`LocalDataApiAdapter.mask`).
- **응답 본문을 운영 로그에 찍지 않는다.** 인증키가 에코되는 응답이 있다.
- 키가 비어 있으면 **호출하지 않는다.** 배치를 건너뛰고 사유만 남긴다.

---

## 4. 업종 마스터

정본: `src/main/resources/infra/industry-master.yml`
위치 교체: `infra.industry-master.location=file:/etc/smash/industry-master.yml`

### 4.1 구조

```yaml
industries:
  - industryCode: RESTAURANT        # 내부 코드. industry.industry_code (varchar 10)
    name: 일반음식점                # 표시 이름
    major: FOOD                     # HEALTH | FOOD | CULTURE | LIFE | null
    majorReviewed: false            # 사람이 확인했는가
    slug: general_restaurants       # data.go.kr 엔드포인트 slug
    datasetId: "15154916"           # 활용신청용 데이터셋 ID
    enabled: true
    note: ...

legacyServiceIds:                   # 구 opnSvcId → 내부 코드
  "07_24_04_P": RESTAURANT
  "11_44_01_P": null                # 미확인 → LEGACY_CSV 경로에서 제외
```

### 4.2 규칙

- **외부 응답으로 `major` 를 추론하지 않는다.** 대분류 배정은 서비스 기획 판단이다.
- `major: null` → 미확정. **적재도 수집도 되지 않는다.**
- `majorReviewed: false` → 제안 상태. **적재는 되지만** 기동·배치 로그에 "확인 필요"로 계속 남는다.
  (전부 막으면 기능이 승인 전까지 죽으므로 이렇게 나눴다.)
- 알 수 없는 `major` 문자열은 **배치를 죽이지 않고** 미확정 취급이다.
  (As-Is 는 `Major.valueOf` 가 `IllegalArgumentException` 을 던져 Step 전체가 FAILED 였다.)
- `industryCode` 는 **10자 이하**여야 한다. 넘으면 로드 시점에 건너뛴다.

### 4.3 업종을 추가하는 방법

1. data.go.kr 에서 `행정안전부_<분야>_<업종> 조회서비스` 데이터셋을 찾아 **활용신청**한다(자동승인).
2. 데이터셋 URL 의 숫자(=`datasetId`)와 엔드포인트 slug 를 확인한다.
3. `industry-master.yml` 의 `industries:` 에 항목을 추가한다. `major` 는 제안으로 두고 `majorReviewed: false`.
4. 기획 확인 후 `majorReviewed: true` 로 바꾼다.
5. `industryJob` → `infraJob` 순으로 다시 돌린다(스케줄러가 이 순서를 지킨다).

> **[확인됨] 없는 업종**: 학원/교습소(교육청 소관), 어린이집/유치원(복지부·교육부), 편의점/소매업 전용 데이터셋.
> **[미확인]**: 195종 전 업종의 slug 전수표. 현재 18종만 확인됐다.

---

## 5. 지역코드 매핑 정책

정본: `src/main/resources/infra/localdata-region-mapping.yml`
위치 교체: `infra.region-mapping.location=file:/etc/smash/localdata-region-mapping.yml`

### 5.1 왜 표가 필요한가 **[확인됨]**

`OPN_ATMY_GRP_CD`(개방자치단체코드)는 **인허가기관의 7자리 독자 코드**이고 표준 시군구코드와
산술 관계가 **없다**. 응답에 법정동코드 필드도 없다.

| 자치단체 | 개방자치단체코드 | 시군구코드 |
| --- | --- | --- |
| 서울종로구 | `3000000` | 11110 |
| 서울강남구 | `3220000` | 11680 |
| 경기수원시 | `3740000` | 41110 |
| 강원춘천시 | `4181000` | 51110 |

### 5.2 정책

1. **공식 행정구역 코드가 주소 문자열보다 우선한다.** 사업장이 들고 있는 `OPN_ATMY_GRP_CD` 로 매핑한다
   (요청 코드가 `_ALL` 인 경우 요청 코드로 뭉뚱그리면 시군구가 뭉개지기 때문이다).
2. **매핑에 없는 코드는 추정하지 않고 제외**하며 `unmappedRegions` 로 계량해 로그에 남긴다.
   코드가 있는데 표가 비어 있는 상황을 주소 추정으로 덮으면 오류가 조용히 묻힌다.
3. **주소 문자열은 일반구 분해(§5.4)에서만 쓴다.** 개방자치단체코드 하나가 시군구코드 여러 개에
   대응하는 12개 시가 그 유일한 경우다. 그 외 지역은 코드로 시군구가 확정된다.
4. **이 파일은 수집 대상 목록이기도 하다.** 여기 없는 자치단체는 호출하지 않는다.

### 5.3 파일 현황 — 229건 **[확인됨]**

공식 코드표는 참고문서 `개방자치단체코드_영업상태코드.xlsx` 시트 `1. 개방자치단체코드`,
**총 261행**(시도 전체 17 + 시도 본청 16 + 시군구 228)이다 **[확인됨]**.

xlsx 를 실제로 파싱해 **시군구 228 + 세종 1 = 229건**을 채웠다(확인일 2026-08-13).
`RegionCodeMappingLoaderTest.loadsBundledMappingFile()` 이 이 개수를 단언한다.

**다시 채워야 할 때의 절차**

1. 코드표 xlsx 를 받는다
   `https://www.data.go.kr/cmm/cmm/fileDownload.do?atchFileId=FILE_000000003594039&fileDetailSn=1`
2. 시트 `1. 개방자치단체코드` 에서 시군구 228행을 뽑는다.
3. `data/static/sigungu.csv`(264행)와 **`시도 2자 약칭 + 시군구명(공백 제거)`** 규칙으로 조인한다.
   → **228건 전부 매칭된다 [확인됨]**.
4. `regions:` 아래에 `openOrgCode / sigunguCode / name` 형식으로 붙여 넣는다.
5. 세종특별자치시는 시군구 목록이 아니라 **시도 본청 코드 `5690000` → `36110`** 을 쓴다.

### 5.4 일반구 35개 — 주소 문자열로 재분배한다 **[정책 결정 2026-08-13]**

인허가 권한이 일반구가 아니라 **시**에 있어 데이터가 시 단위로만 나온다. 아래 12개 시가
개방자치단체코드 1개 ↔ 시군구코드 여러 개다.

```
수원시 장안/권선/팔달/영통구, 성남시 수정/중원/분당구, 안양시 만안/동안구,
부천시 원미/소사/오정구, 안산시 상록/단원구, 고양시 덕양/일산동/일산서구,
용인시 처인/기흥/수지구, 청주시 상당/서원/흥덕/청원구, 천안시 동남/서북구,
포항시 남/북구, 창원시 의창/성산/마산합포/마산회원/진해구, 전주시 완산/덕진구
```

**결정: 상위 시 값을 복제하지 않고, 사업장 주소 문자열의 구 이름으로 재분배한다.**
분해 규칙은 매핑 파일의 `districtSplits:` 블록에 있다.

```yaml
districtSplits:
  - parentSigunguCode: "41110"
    cityName: 수원시
    districts:
      - name: 장안구
        sigunguCode: "41111"
      ...
```

#### 주소 필드 — 지번주소가 먼저다

| 순위 | 필드 | CSV 컬럼 | 근거 |
| --- | --- | --- | --- |
| 1 | `LOTNO_ADDR` | `지번주소` | 결측이 거의 없다. `시도 시 구 동 번지` 라 나오는 "구"가 곧 행정구역이다 |
| 2 | `ROAD_NM_ADDR` | `도로명주소` | **결측률 42.8%**. 뒷부분이 도로명이라 `수정구길` 처럼 행정구역이 아닌 "구"를 품는다 |

앞 후보에서 구를 찾으면 뒤 후보는 보지 않는다. 우선순위는 `InfraFacility.addressCandidates()` 가 정한다.

#### 매칭 규칙

- **긴 이름 우선.** `DistrictSplit` 이 생성자에서 `districts` 를 이름 길이 내림차순으로 정렬해 보관한다.
  YAML 나열 순서에 기대지 않으므로 파일을 누가 재정렬해도 `마산합포구`/`마산회원구`,
  `일산동구`/`일산서구` 가 어긋나지 않는다.
- **여러 개가 걸리면 가장 앞에 나온 것.** 주소는 큰 단위 우선 표기라 행정구역으로서의 구는 항상
  맨 앞이다. 뒤쪽의 "구"는 도로명·건물명이다.
  → `경기도 성남시 분당구 정자동 178-1 수정구빌딩` = **분당구**.

#### 구를 못 찾으면 — 버리고 계량한다

**상위 시 코드로 떨어뜨리지 않는다.** 상위 시(예: 수원시 `41110`)도 `sigungu` 테이블에 실재하므로
그대로 적재하면 `41111~41117` 과 **같은 사업장이 두 번** 집계되고, `ratio`(시군구 내 구성비)와
`score`(업종별 전국 백분위)가 둘 다 왜곡된다. 그래서 제외한 뒤 건수를 로그에 드러낸다.

```
districtResolved=<재분배 성공 건수>, districtUnresolved=<제외 건수>
```

`InfraSnapshot` 의 필드이자 `infraStep` 구조화 로그(§10)의 항목이다. `districtUnresolved` 가 0이
아니면 `[infraJob] 주소에서 일반구를 찾지 못해 N건을 제외했다` WARN 이 대상 시 이름과 함께 남는다.

#### 계산 순서

재분배는 **개수 집계 단계**에서 끝난다. `InfraSnapshotAssembler` 가 하위 구 코드로 적립한 맵
전체를 `InfraStatPolicy` 에 넘기므로, `ratio` 와 `score` 는 **일반구 단위**로 계산된다.
백분위의 모집단도 상위 시가 아니라 일반구다.

---

## 6. 영업상태 필터링 규칙 **[확인됨]**

공식 코드표(`개방자치단체코드_영업상태코드.xlsx` 시트 `2. 영업상태코드`) 기준이다.

| `SALS_STTS_CD` | 의미 | 집계 |
| --- | --- | --- |
| `01` | **영업/정상** | **포함** |
| `02` | 휴업 | 제외 |
| `03` | 폐업 | 제외 |
| `04` | 취소/말소/만료/정지/중지 | 제외 |
| `05` | 제외/삭제/전출 | 제외 |
| `06` | 기타 | 제외 |

- 요청에 `cond[SALS_STTS_CD::EQ]=01` 을 넣어 **서버에서 먼저 거른다** — 페이지 수(=호출 수)가 줄기 때문이다.
- 그래도 응답에 섞여 오는 경우를 대비해 **클라이언트에서 한 번 더** 거른다(`BusinessStatus.countsAsInfra`).
- **상세영업상태코드(`DTL_SALS_STTS_CD`)는 쓰지 않는다.** 업종마다 값 체계가 달라 통합 코드표가 없다 **[미확인]**.
- 코드표에 없는 값은 `null` 로 두고 집계에서 제외한다.

중복 제거는 **관리번호(`MNG_NO`)** 기준이며 먼저 만난 건을 남긴다.
offset 페이지네이션이라 수집 중 데이터가 갱신되면 같은 사업장이 두 페이지에 걸칠 수 있다.

---

## 7. 수집 경로 — API vs 벌크 CSV

`infra.collect.source` = `API`(기본) / `BULK_CSV` / `LEGACY_CSV`

### 7.1 왜 API 가 기본인가 — 그리고 왜 전국 수집이 안 되는가 **[확인됨]**

| | 공식 API | 벌크 CSV |
| --- | --- | --- |
| URL | `apis.data.go.kr/1741000/{slug}/info` | `file.localdata.go.kr/file/download/{slug}/info?orgCode=` |
| 인증 | `serviceKey` + **데이터셋별 활용신청** | 불필요. 단 **`Referer: https://www.data.go.kr/` 필수** |
| 한 번에 | 100건 | 전량 (종로구 일반음식점 실측 20,558행 / 6MB) |
| 제한 | **10,000회/일** | 없음(측정 안 됨) |
| 인코딩 | JSON UTF-8 | **CP949**, 한글 헤더 |
| 공식성 | 공식 문서·SLA 있음 | **유지 기간/SLA [미확인]** — 동작만 실측 |

일반음식점 전국 **2,129,830건**을 API 로 받으려면 **21,299회** 호출이 필요하다.
일일 10,000회로는 **하루에 불가능하고 2~3일 걸린다.** 업종 16종을 곱하면 아예 성립하지 않는다.

**판단**
- **기본은 API 로 둔다.** 공식 경로이고 유지 보장이 있는 유일한 채널이다. SLA 미확인 경로를
  기본값으로 삼으면 어느 날 조용히 멈춘다.
- **벌크 CSV 를 명시적 옵션으로 지원한다.** 시드 구축처럼 "한 번에 전국을 채워야 하는" 작업은
  이것 말고 방법이 없다. 자치단체 228 × 업종 16 = 3,648 요청이면 전국이 완결된다.
- API 경로에는 **일일 호출 예산**(`apis.localdata.daily-call-budget`, 기본 9,000)이 있다.
  예산이 소진되면 **실패가 아니라 그날 수집을 정상 종료**하고, 지금까지 받은 몫은 staging 에
  남겨 다음 실행이 이어받는다. 부분 수집분이 서비스 테이블로 나가는 경로는 여전히 없다(§9).

**운영 권장**: 최초 시드와 대규모 재적재는 `BULK_CSV`, 이후 증분·검증은 `API`.

### 7.2 레거시 CSV **[명시적 옵션]**

`infra.collect.source=LEGACY_CSV` 일 때만 `data/legacy/infra.csv`(`sigungu_code,opnSvcId,num`)를 읽는다.
**기본 경로가 아니다** — 데이터가 언제 만들어졌는지 알 수 없다.

`opnSvcId` 는 마스터의 `legacyServiceIds` 에 **등록된 것만** 변환되고, `null` 이면
"어느 업종인지 확인되지 않았다"는 뜻이라 **임의 분류 없이 제외**하고 로그에 남긴다.
현재 14종 중 `07_24_04_P`(일반음식점) 1종만 확인됐다 — 나머지 13종은 **[미확인]** 이다
(구 개방서비스 목록 페이지가 폐쇄되어 확인 경로가 없다).

---

## 8. ratio / score 계산식과 근거

`InfraStatPolicy`(순수 함수, `domain/service`). 반올림은 전 구간 **`setScale(2, RoundingMode.HALF_UP)`** 이며,
나눗셈 중간 단계만 `MathContext.DECIMAL64` 로 계산한 뒤 마지막에 한 번 자른다.

### 8.1 `ratio` — 시군구 내 업종 구성비

```
fraction(g, i) = count(g, i) / Σ_j count(g, j)     # 같은 시군구의 모든 업종 합
ratio(g, i)    = RatioBasis.apply(fraction)         # PERCENT(기본) → × 100
```

시군구 전체 합이 0이면 `0` 이다.

#### ⚠️ 단위 해석은 코드로 판정할 수 없었다

`ratio` 는 `GET /api/detail` 로 pass-through 될 뿐 **산술·비교·포맷팅하는 코드가 저장소에 한 곳도 없다.**
그래서 "0~1 비율"인지 "0~100 퍼센트"인지 판정이 불가능하다.

| 신호 | 판정력 |
| --- | --- |
| `scale = 2` | 0~1 해석이면 유효 단계가 101개뿐이라 지나치게 거칠다 → 퍼센트에 **약간** 유리 |
| `setScale(2, HALF_UP)` | 0~1 해석이면 정보가 잘린다 → 퍼센트에 유리 |
| `precision = 18` | 판정력 없음 |
| 필드명 `ratio` | 이름만으로는 근거 부족 |
| 소비 코드 | **없음. 판정 불가** |

**선택: 기본값 `PERCENT`(0~100).** 위 두 신호가 퍼센트 쪽에 유리하다는 것이 전부이며 **결정적 근거가 아니다.**
그래서 해석을 고정하지 않고 **프로퍼티로 전환 가능하게** 만들었다.

```properties
infra.ratio.basis=PERCENT    # 기본. 0.00 ~ 100.00
infra.ratio.basis=FRACTION   # 0.00 ~ 1.00
```

> **확인해야 할 것**: 프런트가 값에 `%` 를 붙여 표시하는지. 붙인다면 `PERCENT` 가 맞고,
> 붙이면서 다시 ×100 을 한다면 `FRACTION` 으로 바꿔야 한다. 확정되면 이 문서와
> `RatioBasis.DEFAULT` 를 함께 고친다.

### 8.2 `score` — 업종별 전국 백분위

```
같은 업종 i 를 가진 시군구 집합 G_i,  N = |G_i|
below = |{ h ∈ G_i : count(h,i) < count(g,i) }|
ties  = |{ h ∈ G_i : count(h,i) = count(g,i), h ≠ g }|

score(g, i) = 100 × (below + 0.5 × ties) / (N - 1)     (N ≥ 2)
score(g, i) = 50                                        (N = 1)
```

#### 왜 백분위인가

1. **구조적으로 `[0, 100]` 이다.** `below + 0.5 × ties ≤ N - 1` 이므로 100 을 넘을 수 없다.
   `infra.score` 의 모든 행이 `[0,100]` 이어야 한다는 제약(§8.3)을 **계산식 자체가 보장**한다.
   `count / 임의의 최대치 × 100` 같은 방식은 최대치 추정이 틀리면 조용히 100 을 넘는다.
2. **지역 규모 보정이 자연히 들어간다.** 절대 개수는 대도시가 항상 압도하지만, 백분위는
   "같은 업종에서 다른 시군구 대비 몇 번째인가"라 점수가 분포 전체에 고르게 퍼진다.
3. **인구 대비(1인당 시설 수)보다 의존이 적다.** 인구 정규화는 `population` 테이블이 같은 기준일로
   채워져 있어야 하고 인구 결측 지역에서 0으로 나누기가 생긴다. 게다가 그 값도 결국 `[0,100]` 으로
   정규화해야 하는데 그 정규화가 다시 백분위다.
4. **동점은 midrank(0.5 × ties)** 다. 전 지역이 같은 값이면 0점이 아니라 50점이 된다 —
   "모두 같으면 아무도 특별하지 않다"가 0점보다 타당하다.

> **주의 — 모집단은 "이번 스냅샷에 그 업종 행이 있는 시군구"** 다. 수집 범위가 좁으면 백분위의
> 의미가 달라진다. 그래서 부분 수집 스냅샷을 반영하지 않는 것이 배치의 규칙이다(§9).

### 8.3 왜 `[0, 100]` 을 강제하는가

추천 경로는 `AVG(infra.score)` 를 `(시군구, 대분류)` 단위로 집계한 뒤 사용자가 고른 대분류 개수 N 으로
나누고, 그 결과를 `Score.of(int)` 에 넣는다. `Score` 는 0~100 을 벗어나면 `DomainException` 이다.

- 산술평균은 언제나 구성 요소의 최댓값 이하다 → **모든 행이 `[0,100]` 이면 N 과 무관하게 안전**하고,
  **N = 1 이 가장 빡빡한 경우**다.
- DB 컬럼은 `decimal(6,2)` 라 **100 초과도 적재는 성공한다.** 그러면 실패가 적재 시점이 아니라
  **추천 API 호출 시점**으로 미뤄져 HTTP 400 으로 나간다. 데이터 적재 오류인데 사용자 입력 오류처럼 보인다.

그래서 **값 객체 `InfraScore`** 가 `[0,100]` 을 생성자에서 강제하고, 배치 Processor 가 한 번 더 확인해
어긋나는 행을 `null`(skip) 로 버린다. **100 초과 값은 DB 에 도달하지 못한다.**

---

## 9. 스냅샷 교체 정책

**부분 반영을 하지 않는다.** 이 원칙은 그대로이고, 달라진 것은 "완성될 때까지 어디에 모아 두는가"다.

### 9.1 API 경로 — 2-Step + staging 체크포인트

전국 대상 3,664개(229지역 × 16업종)에 대상당 평균 5.4회를 호출하면 약 19,800회가 든다.
일일 예산은 9,000회라 **하루에 끝나지 않는다.** 그래서 수집과 반영을 나누고, 며칠에 걸쳐 모은다.

```
infraCollectStep  아직 안 받은 대상만 호출 → staging 에 대상 단위로 적립(청크 크기 1 = 대상 1개)
                  예산이 소진되면 COMPLETED 로 정상 종료한다. 다음 실행이 이어받는다
   ↓
infraStep         기대 대상이 전부 채워진 회차에서만 ratio/score 를 내고 infra 에 upsert
                  미완성이면 진척 로그만 남기고 건너뛴다(기존 스냅샷 유지)
                  반영에 성공하면 그 회차 staging 을 지운다
```

| 테이블 | 내용 |
| --- | --- |
| `infra_collection_target` | 회차별 "이 (기관, 업종) 대상은 수집을 마쳤다" 진행 행 |
| `infra_staging_count` | 회차별 (시군구, 업종) 개수. 여러 대상이 기여하므로 **합산 upsert** |

- 두 테이블 쓰기는 **한 청크 트랜잭션**에 묶인다. 카운트만 커밋되고 진행 행이 없으면 다음 실행이
  같은 대상을 다시 받아 **이중 합산**이 된다.
- **`run_key` = 회차 시작일(yyyy-MM-dd)** 이다. `baseDate` 를 쓰지 않는 이유는 날짜가 바뀌는
  순간 이어달리기가 끊기기 때문이다. staging 에 회차가 남아 있으면 **가장 오래된 것**을 이어받고,
  없으면 오늘 날짜로 새 회차를 연다. 반영에 성공하면 지워지므로 **정상 상태에서 회차는 최대 하나**다.
- 회차가 둘 이상 보이면 정리 실패나 수동 개입의 흔적이다. 배치는 가장 오래된 것만 쓰고 나머지는
  `log.warn` 으로 드러낸다 — 무엇을 지울지는 사람이 판단한다.
- 회차가 `infra.collect.stall-threshold-days`(기본 7일)를 넘도록 완성되지 않으면 `log.error` 로
  stall 을 알린다. 영구 실패 대상(존재하지 않는 (기관, 업종) 조합, 매핑 오타)이 하나만 있어도
  회차는 영영 완성되지 않는데 Job 은 매일 COMPLETED 를 내기 때문이다. **배치가 죽지는 않는다.**
- `infraCollectStep` 이 FAILED 로 끝나도 `infraStep` 은 돈다(`.on("*")`). 반영은 "이 회차가
  완성됐는가"만 보므로, 오늘 수집이 죽었다는 이유로 **어제 완성해 둔 회차의 반영을 막지 않는다.**

### 9.2 BULK_CSV / LEGACY_CSV 경로 — 한 번에 전량

체크포인트를 쓰지 않는다. `infraCollectStep` 은 빈 스트림으로 즉시 끝나고, 조립은 `infraStep` 의
**Reader 안에서 통째로** 일어난다. Reader 가 예외를 던지면 청크가 한 번도 돌지 않으므로 `infra`
테이블에 **한 행도 쓰이지 않고 기존 정상 스냅샷이 그대로 남는다.**

### 9.3 두 경로의 공통 불변식

- `InfraStatPolicy` 에는 **완전한 counts** 만 들어간다. 부분 수집분이 서비스 테이블로 나가는
  경로가 없다.
- 왜 부분 반영이 위험한가: `ratio` 는 시군구 합계를, `score` 는 업종별 전국 분포를 기준으로 한다.
  일부 업종만 갱신되면 **두 값이 서로 다른 기준으로 섞인다.**
- 수집 경로가 준비되지 않았으면(인증키 없음 등) 예외 대신 **빈 Reader** 를 돌려 Step 을 건너뛴다.
  이때도 기존 적재분은 건드리지 않는다.

### 멱등성

- Upsert 는 `ON DUPLICATE KEY UPDATE` 로 **`count` / `ratio` / `score` 셋 다** 갱신한다.
  (As-Is 는 `count` 만 갱신해 `ratio`/`score` 가 최초 INSERT 값에 고정돼 있었다.)
- 같은 `baseDate` 로 다시 돌려도 같은 입력이면 같은 결과다.
- 재실행 자체를 막는 것은 `BatchGuard.stepAlreadyCompleted`(같은 `baseDate` 로 이미 완료된 Step)와
  `BatchLaunchGuard`(진행 중이면 건너뜀)다.

### 파생 캐시 무효화

`InfraScoreCacheCleaner` 를 **`infraJob` 에 리스너로 연결**했다. As-Is 는 어느 Job 에도 연결돼 있지 않아
인프라를 갱신해도 `infra:score:*` 캐시 TTL(24시간)이 만료될 때까지 옛 점수가 계속 나갔다.

---

## 10. 설정

### 10.1 환경변수

```bash
DATA_GO_KR_SERVICE_KEY=              # data.go.kr 인증키 (하나만 쓴다)
LOCALDATA_API_BASE_URL=https://apis.data.go.kr
LOCALDATA_BATCH_ENABLED=true
LOCALDATA_BATCH_CRON=0 0 4 * * *
```

### 10.2 프로퍼티

| 키 | 기본값 | 의미 |
| --- | --- | --- |
| `apis.datagokr.service-key` | (빈 값) | `DATA_GO_KR_SERVICE_KEY`. 비면 배치를 건너뛴다 |
| `apis.localdata.base-url` | `https://apis.data.go.kr` | `LOCALDATA_API_BASE_URL` |
| `apis.localdata.page-size` | `100` | **상한 100 으로 잘린다** |
| `apis.localdata.max-pages` | `500` | 대상당 페이지 상한(안전핀) |
| `apis.localdata.request-interval-ms` | `120` | 최소 호출 간격 |
| `apis.localdata.daily-call-budget` | `9000` | 하루 호출 상한. 소진되면 **실패가 아니라 그날 수집 종료** |
| `apis.localdata.max-attempts` | `3` | 429/5xx 재시도 횟수 |
| `apis.localdata.retry-delay-ms` | `1000` | `Retry-After` 가 없을 때의 대기 |
| `apis.localdata.max-retry-after-ms` | `60000` | `Retry-After` 상한 |
| `apis.localdata.bulk-base-url` | `https://file.localdata.go.kr/file/download` | 벌크 CSV |
| `apis.localdata.bulk-referer` | `https://www.data.go.kr/` | **없으면 302** |
| `infra.collect.source` | `API` | `API` / `BULK_CSV` / `LEGACY_CSV`. **`API` 만 2-Step + staging** |
| `infra.collect.stall-threshold-days` | `7` | 회차가 이 일수를 넘도록 미완성이면 `log.error`(관측 전용) |
| `infra.ratio.basis` | `PERCENT` | `PERCENT` / `FRACTION` |
| `infra.industry-master.location` | `classpath:infra/industry-master.yml` | 업종 마스터 |
| `infra.region-mapping.location` | `classpath:infra/localdata-region-mapping.yml` | 지역코드 매핑 |
| `infra.legacy-csv.path` | `data/legacy/infra.csv` | `LEGACY_CSV` 경로일 때만 |
| `infra.legacy-csv.encoding` | `UTF-8` | 레거시 CSV 인코딩 |

**호환 프로퍼티** — `SeedMasterJobConfig` 의 관문이 아직 이 키를 보고 있어 남겨 뒀다.

| 키 | 해석 |
| --- | --- |
| `industry.filePath` | 비어 있지 않으면 **업종 마스터 파일 위치**로 쓴다(`classpath:` / `file:` 가능) |
| `infra.filePath` | 비어 있지 않으면 **레거시 CSV 경로**로 쓴다 |

### 10.3 구조화 로그

#### 수집 Step (`infraCollectStep`)

```
[infraJob] 수집 시작 baseDate=2026-08-13, runKey=2026-08-11, 진척=1674/3664, 이번에 시도할 대상=1990
[infraJob] 호출 예산 소진으로 수집을 멈춘다(실패 아님). collected=1652/1990, reason=일일 호출 예산 소진
[infraJob] step=infraCollectStep, baseDate=2026-08-13,
  planned=1990, collected=1652, empty=41, unresolved=0, retried=3, budgetExhausted=true,
  apiCalls=8998, read=402113, filteredOut=198220, duplicates=12,
  unmappedFacilities=0, unmappedRegions=0, districtResolved=50120, districtUnresolved=61,
  staged=1652, elapsed=18304120ms, status=COMPLETED
```

**읽는 법**

| 줄 | 의미 |
| --- | --- |
| `runKey=2026-08-11` | 이 회차는 8/11 에 시작했다. 8/13 실행이 그것을 이어받았다 |
| `진척=1674/3664` | 회차 전체 대상 3,664개 중 1,674개가 이미 끝났다. **이 값이 매일 오르면 정상** |
| `budgetExhausted=true` | 오늘 몫을 다 썼다. **실패가 아니다.** status 는 COMPLETED 다 |
| `unresolved=N` (N>0) | 2차 패스에서도 실패한 대상. 회차가 그만큼 안 채워진다 |
| `회차가 N일째 완성되지 않았다` (ERROR) | stall. 진척이 멈춘 것이므로 `unresolved` 표본을 보고 원인을 찾는다 |

> **"왜 실패로 안 죽지"는 장애가 아니다.** 예산 소진과 대상 단위 실패는 설계상 COMPLETED 로
> 끝난다. 관측해야 할 것은 status 가 아니라 **진척이 오르는가**와 **stall ERROR 가 있는가**다.

#### 반영 Step (`infraStep`)

미완성 회차에서는 반영을 건너뛰고 진척만 남긴다.

```
[infraJob] 수집 진행 중 1674/3664 (runKey=2026-08-11) - 반영을 건너뛴다. 기존 스냅샷을 유지한다.
```

회차가 완성된 날에만 아래가 나온다(`source=API(staging)` 로 경로가 드러난다).

```
[infraJob] 회차 완성 - 반영 시작 baseDate=2026-08-15, runKey=2026-08-11, targets=3664, countRows=41230, rows=41230
[infraJob] step=infraStep, baseDate=2026-08-15,
  source=API(staging), ratioBasis=PERCENT, runKey=2026-08-11, targets=3664,
  countRows=41230, rows=41230, aggregateElapsed=812ms,
  saved=41230, filteredByProcessor=0, elapsed=9120ms, status=COMPLETED
[infraJob] staging 정리 runKey=2026-08-11, targets=3664, counts=41230
```

`BULK_CSV` / `LEGACY_CSV` 경로는 한 번에 전량을 조립하므로 형식이 다르다.

```
[infraJob] step=infraStep, baseDate=2026-08-13,
  source=BULK_CSV, ratioBasis=PERCENT, targets=3648, apiCalls=8721, read=412330,
  filteredOut=201004, duplicates=17, unmappedRegions=2, unmappedIndustries=0,
  districtResolved=51230, districtUnresolved=87,
  rows=3648, collectElapsed=1830412ms,
  saved=3648, filteredByProcessor=0, elapsed=1834900ms, status=COMPLETED
```

실패하면 같은 형식에 `status=FAILED, reason=<예외 클래스>: <메시지 첫 줄>` 이 붙는다.

`districtUnresolved` 는 **일반구 시인데 주소에서 구를 찾지 못해 제외한 사업장 수**다(§5.4).
0이 아니면 그만큼 인프라 개수가 과소 집계된 것이므로, 값이 커지면 주소 필드 결측률을 다시 본다.

---

## 11. 남은 [미확인] 목록

| # | 항목 | 왜 |
| --- | --- | --- |
| 1 | ~~개방자치단체코드 261행 전수표~~ | **해소됨(2026-08-13)**. xlsx 파싱으로 229건(시군구 228 + 세종 1) 확보 |
| 2 | 구 `opnSvcId` 13종의 업종 | 구 개방서비스 목록 페이지 폐쇄 |
| 3 | 195종 전 업종 slug | 18종만 확인됨 |
| 4 | `file.localdata.go.kr` 의 공식 SLA·유지 기간 | 공식 문서 없이 동작만 실측 |
| 5 | 초당 호출 제한 수치 | 에러코드 23 만 공개 |
| 6 | `returnType` 허용 문자열 | Swagger 미열거 |
| 7 | `DTL_SALS_STTS_CD` 전 업종 통합 코드표 | 배포되지 않음. 그래서 쓰지 않는다 |
| 8 | `ratio` 의 실제 단위 계약 | 소비 코드가 없어 코드로 판정 불가. 프런트 확인 필요 |
| 9 | ~~일반구 35개 처리 정책~~ | **결정됨(2026-08-13)**: 주소 문자열 재분배(§5.4). 구를 못 찾으면 제외 |
| 10 | 각 업종의 `Major` 배정 | 서비스 기획 판단. 마스터에 제안만 올려 뒀다 |
