# 외부 공공데이터 API 구현 스펙

> 조사일: **2026-08-13**
> 이 문서는 조사 결과만 담는다. 코드는 변경하지 않았다.
>
> 각 항목에는 **[확인됨]** / **[미확인]** 라벨을 붙였다.
> - **[확인됨]** = 공식 페이지 fetch, 공식 배포 파일 다운로드, 또는 실제 API 호출로 직접 검증한 것
> - **[미확인]** = 확인하지 못한 것. 추측으로 채우지 않았다.
>
> 요청 예시 URL의 인증키는 모두 `{SERVICE_KEY}` / `{KOSIS_API_KEY}` 플레이스홀더로 적었다.
> 실제 키 값은 이 문서에 포함하지 않는다.

---

## 목차

1. [시군구별 인구 API — 후보 비교와 추천](#1-시군구별-인구-api--후보-비교와-추천)
2. [지방행정 인허가 데이터 API (구 LOCALDATA)](#2-지방행정-인허가-데이터-api-구-localdata)
3. [국토교통부 아파트 전월세 실거래가 API](#3-국토교통부-아파트-전월세-실거래가-api)
4. [환경변수 종합](#4-환경변수-종합)
5. [프로젝트 적용 시 확인된 이슈](#5-프로젝트-적용-시-확인된-이슈)

---

## 요약 (먼저 읽을 것)

| 항목 | 결론 |
| --- | --- |
| **인구** | **KOSIS `orgId=101` / `tblId=DT_1B040A3` (행정구역(시군구)별, 성별 인구수)** 채택 권장. KOSIS 분류값 ID가 행정표준코드와 일치하는 유일한 후보다. |
| **인허가** | **LOCALDATA(localdata.go.kr)는 2026-04-16 폐쇄**되었다. `authKey`/`opnSvcId` 체계 전체가 폐기됐고 data.go.kr 업종별 API로 이관됐다. 기존 `infra.csv`의 `opnSvcId` 컬럼은 죽은 값이다. |
| **실거래가** | 프로젝트의 현재 설정(`MOLIT_BASE_URL`/`MOLIT_PATH`)은 **지금도 유효**하다. 실호출로 검증 완료. 다만 어댑터에 **페이지네이션 누락 버그**가 있다. |
| **인증키 단일화** | data.go.kr 계열은 `DATA_GO_KR_SERVICE_KEY` **하나로 통일 가능**. 단 데이터셋마다 활용신청이 별도로 필요하다(자동승인). KOSIS는 **별도 키**가 필요하다. |

---

# 1. 시군구별 인구 API — 후보 비교와 추천

## 1.0 결론

**후보 A(KOSIS)를 채택한다.**

판단 기준은 과제에서 지정한 대로 **"행정구역 코드를 주는가, 명칭만 주는가"** 였다.

| 기준 | A. KOSIS | B. 행안부 도로명별 인구 | C. SGIS |
| --- | --- | --- | --- |
| 시군구 단위 직접 제공 | **O** | **X** (도로명 단위) | O |
| 행정구역 코드 제공 | **O — 행정표준코드와 일치** | [미확인] (설명문은 명칭 위주) | O — **자체 센서스 코드, 불일치** |
| 전국 1회 수집 | **O** (`objL1=ALL`) | X (약 250회 + 합산) | 시도별 분할 필요 |
| 갱신 주기 | 월 | 월 | 연 (센서스) |
| 인증 | 단일 키 | 단일 키 | 키 → 토큰(만료 관리) |

- **후보 B는 탈락 확정**이다. 공식 명칭 자체가 "행정안전부_**도로명별** 주민등록 인구 및 세대현황"이다.
- **후보 C(SGIS)는 코드 체계가 다르다.** 공식 예제에서 종로구 `adm_cd`가 `11010`인데, 법정동코드 기준 종로구는 `11110`이다. 별도 매핑 테이블이 필요해진다.

---

## 1.1 후보 A — KOSIS 공유서비스 OpenAPI **[채택]**

### 확인에 사용한 공식 URL

| URL | 확인 결과 |
| --- | --- |
| `https://kosis.kr/serviceInfo/openAPIGuide.do` | **[확인됨]** 200 |
| `https://kosis.kr/openapi/devGuide/devGuide_0201List.do` | **[확인됨]** 통계자료 API 전체 명세 |
| `https://kosis.kr/openapi/devGuide/devGuide_0101List.do` | **[확인됨]** 통계목록 API |
| `https://kosis.kr/openapi/Param/statisticsParameterData.do` | **[확인됨]** 실호출로 응답 수신 |
| `https://kosis.kr/openapi/statisticsData.do` | **[확인됨]** 실호출로 응답 수신 |
| `https://kosis.kr/statHtml/statHtmlContent.do?orgId=101&tblId=DT_1B040A3&conn_path=I2` | **[확인됨]** 통계표 메타데이터 JSON 직접 수신 |
| `https://kosis.kr/openapi/file/openApi_manual_v1.0.pdf` | 다운로드는 성공(13MB), **텍스트 추출 실패 → 내용 [미확인]** |

### 요청 URL — **[확인됨]**

KOSIS는 통계자료 조회 방식이 둘이다.

| 방식 | URL | 비고 |
| --- | --- | --- |
| ① 통계표 **지정** | `https://kosis.kr/openapi/Param/statisticsParameterData.do?method=getList` | orgId/tblId를 코드로 직접 지정. **배치에 적합** |
| ② 사용자 **등록** | `https://kosis.kr/openapi/statisticsData.do?method=getList` | KOSIS 웹에서 미리 통계표를 등록해 받은 `userStatsId` 사용 |

→ **①번을 쓴다.** ②는 사람이 웹 UI에서 사전 등록해야 해서 서버 배치 자동화에 맞지 않는다.

> **[확인됨]** HTTP 프로토콜 제공은 종료되었다(2026-02-05 공지). **HTTPS 필수.**

### 요청 파라미터 (①번 방식) — **[확인됨]**

| 이름 | 필수 | 설명 | 예시 |
| --- | --- | --- | --- |
| `apiKey` | 필수 | 발급받은 인증키 | `{KOSIS_API_KEY}` |
| `orgId` | 필수 | 기관 ID | `101` (통계청) |
| `tblId` | 필수 | 통계표 ID | `DT_1B040A3` |
| `objL1` | 필수 | 분류1 코드 | `ALL` |
| `objL2`~`objL8` | 선택 | 분류2~8 코드 | `0` |
| `itmId` | 필수 | 항목 | `T20` |
| `prdSe` | 필수 | 수록주기 | `M` |
| `startPrdDe` | 선택 | 시작 수록시점 | `202601` |
| `endPrdDe` | 선택 | 종료 수록시점 | `202606` |
| `newEstPrdCnt` | 선택 | 최근 수록시점 개수 | `1` |
| `prdInterval` | 선택 | 수록시점 간격 | `1` |
| `format` | 필수 | 결과유형 | `json` |
| `outputFields` | 선택 | 응답 필드 선택 | — |
| `smblChk` | 선택 | 통계부호 표시 | `Y` |
| `jsonVD` | — | **개발가이드 파라미터 표에는 없다.** 공식 R/Python 예제 코드에는 `jsonVD=Y`로 등장한다. **의미 [미확인]** | `Y` |

주기 코드 — **[확인됨]**: `D`(YYYYMMDD), `M`(YYYYMM), `Q`(YYYYQQ), `S`(YYYYHH), `Y`(YYYY), `F`(2·3·4년), `IR`(부정기)

주의 — **[확인됨]**:
- `startPrdDe`/`endPrdDe`(시점기준)와 `newEstPrdCnt`(최신자료기준)는 **택일**이다.
- 시점을 지정하지 않으면 기본으로 최근 1개 시점만 반환한다.

### 응답 필드 — **[확인됨]**

| 필드명 | 설명 |
| --- | --- |
| `ORG_ID` | 기관코드 |
| `TBL_ID` | 통계표 ID |
| `TBL_NM` | 통계표명 |
| `C1`~`C8` | **분류값 ID 1~8** |
| `C1_OBJ_NM`~`C8_OBJ_NM` | 분류명 (예: "행정구역별") |
| `C1_NM`~`C8_NM` | 분류값 명 (예: "종로구") |
| `ITM_ID` / `ITM_NM` | 항목 ID / 항목명 |
| `UNIT_ID` / `UNIT_NM` | 단위 ID / 단위명 |
| `PRD_SE` | 수록주기 |
| `PRD_DE` | 수록시점 |
| `DT` | **수치값** |
| `LST_CHN_DE` | 최종수정일 |

영문 필드(`C1_NM_ENG`, `ITM_NM_ENG`, `UNIT_NM_ENG` 등)도 함께 제공된다.

### 핵심 검증 — C1은 행정구역 코드인가?

**[확인됨 — 시도 레벨]** `kosis.kr/statHtml/statHtmlContent.do` 가 반환하는 `DT_1B040A3` 메타데이터 JSON에서 분류 `A`(행정구역별)의 1레벨 값을 직접 추출했다.

```
00 전국 / 11 서울특별시 / 12 전남광주통합특별시 / 26 부산광역시 / 27 대구광역시 /
28 인천광역시 / 29 광주광역시 / 30 대전광역시 / 31 울산광역시 / 36 세종특별자치시 /
41 경기도 / 51 강원특별자치도 / 43 충청북도 / 44 충청남도 / 52 전북특별자치도 /
46 전라남도 / 47 경상북도 / 48 경상남도 / 50 제주특별자치도
```

이 값들은 프로젝트의 `data/static/sido.csv` 의 `sido_code` 17개와 **정확히 일치**한다(51 강원특별자치도, 52 전북특별자치도 포함). 즉 KOSIS 분류값 ID는 내부 코드가 아니라 **행정표준코드**다.

**[확인됨 — 시군구 레벨]** 키 발급 후 실호출로 확인했다(2026-08-13). `C1=11110, C1_NM=종로구, DT=136139`(202607 기준)처럼 시군구 5자리 코드가 그대로 내려온다. `objL1=ALL` 로 요청하면 전국(`00`) → 시도(`11` 2자리) → 시군구(`11110` 5자리)가 한 응답에 섞여 나오므로, 적재 시 자릿수 5로 필터링해 시군구만 남겨야 한다.

시도 레벨이 5자리(`11000`)가 아니라 **2자리(`11`)** 라는 점에 유의한다. 자릿수 5로 필터링하면 시군구만 남는다.

> **[확인됨] 주의**: KOSIS에는 `12 전남광주통합특별시` 가 존재하는데 프로젝트 `sido.csv` 에는 없다. `29 광주광역시` / `46 전라남도` 와 동시에 존재한다. 인구 합계 중복 여부는 **[미확인]** 이며, 적재 전 확인이 필요하다.

### 통계표 ID 검증 — **[확인됨]**

| orgId | tblId | 통계표명 | 주기 |
| --- | --- | --- | --- |
| 101 | `DT_1B04005N` | 행정구역(읍면동)별/5세별 주민등록인구(2011년~) | M#Y |
| 101 | **`DT_1B040A3`** | **행정구역(시군구)별, 성별 인구수** | M#Y |

- 과제에서 지목한 `DT_1B04005N`은 **실재한다.** 다만 읍면동 + 5세별 구간 표라, 시군구 총인구를 얻으려면 `objL1=ALL`로 받아 코드 5자리만 필터링하고 연령 분류를 "계"로 지정해야 한다.
- **`DT_1B040A3`이 목적에 더 정확하다.** 애초에 시군구 단위로 집계된 표다. 두 표 모두 같은 통계(`statId=2008001` 주민등록인구현황)에서 나온다.

### `DT_1B040A3` 항목코드 — **[확인됨]**

KOSIS 통계표 메타데이터 JSON에서 직접 추출했다.

| `itmId` | 항목명 (`scrKor`) | 영문 (`scrEng`) |
| --- | --- | --- |
| **`T20`** | **총인구수** | Koreans (Total) |
| `T21` | 남자인구수 | Koreans (Male) |
| `T22` | 여자인구수 | Koreans (Female) |

`sigungu_code,population` 시드를 만들 때 쓸 항목은 **`itmId=T20`** 이다.

### 실제 요청 예시 URL

```
https://kosis.kr/openapi/Param/statisticsParameterData.do?method=getList
  &apiKey={KOSIS_API_KEY}
  &orgId=101
  &tblId=DT_1B040A3
  &objL1=ALL
  &itmId=T20
  &prdSe=M
  &newEstPrdCnt=1
  &format=json
  &jsonVD=Y
```

### 페이지네이션 — **[확인됨] 없음**

개발가이드 요청변수 표에 페이지/행수 파라미터가 없다. 조회량은 시점 범위와 분류 지정으로만 조절한다. 시군구 약 250건 규모는 1회 호출로 처리된다.

### 오류 응답 — **[확인됨 — 형식] / [미확인 — 전체 코드 목록]**

실호출로 확인한 형식:

```json
{"err":"10","errMsg":"인증 KEY값이 누락되었습니다."}
{"err":"11","errMsg":"유효하지 않은 인증KEY입니다."}
```

**HTTP 상태는 200으로 내려온다.** 따라서 본문의 `err` 필드 존재 여부로 실패를 판정해야 한다.
전체 오류코드 목록은 개발가이드 HTML에 없고 PDF 추출도 실패해 **[미확인]** 이다.

### 인증키 / 호출 제한

- 발급: `https://kosis.kr/openapi/` → "OPEN API 인증키 신청" (통계정보활용약관 동의) — **[확인됨]**
- **분당 호출건수 제한이 존재한다** — 공지 2건 확인(2026-02-05, 2026-07-09). **구체적 수치 [미확인]** (공지 상세 페이지가 사이트 개편으로 404)
- 일일 쿼터 — **[미확인]**

---

## 1.2 후보 B — 행정안전부 도로명별 주민등록 인구 (15108092) **[부적합 확정]**

### 확인에 사용한 공식 URL
- `https://www.data.go.kr/data/15108092/openapi.do`
- `https://www.data.go.kr/tcs/dss/selectApiDataDetailView.do?publicDataPk=15108092` (curl로 HTML 수신 성공)

| 항목 | 값 | 라벨 |
| --- | --- | --- |
| 공식 명칭 | **행정안전부_도로명별 주민등록 인구 및 세대현황** | **[확인됨]** |
| Base URL | `https://apis.data.go.kr/1741000/rnPpltnHhStus` | **[확인됨]** |
| 제공기관 | 행정안전부 주민과 | **[확인됨]** |
| 데이터 포맷 | JSON + XML, REST | **[확인됨]** |
| 심의 | 개발/운영 모두 자동승인 | **[확인됨]** |
| 트래픽 | 개발계정 10,000건/일 | **[확인됨]** |
| 등록/수정일 | 2022-11-16 / 2025-05-26 | **[확인됨]** |
| operation 경로 | — | **[미확인]** |
| 요청 파라미터명 | — | **[미확인]** |
| 응답 필드명 | — | **[미확인]** |

> 상세기능 탭이 AJAX 로딩이라 파라미터/필드를 추출하지 못했다. 다만 아래 사유로 **부적합이 확정**되어 추가 조사를 하지 않았다.

### 부적합 판정 근거 — 공식 설명문 **[확인됨]**

> "**시군구 행정기관과 통계년월을 기준으로** 시도명, 시군구명, **도로명**, 총인구수, 세대수, 세대당 인구, 남자인구수, 여자인구수, 남녀비율을 조회하기 위한 서비스입니다."

1. **조회 단위가 도로명이다.** 시군구 총인구를 얻으려면 한 시군구의 모든 도로명 행을 합산해야 하는데, 거주불명자·재외국민 등 도로명에 귀속되지 않는 인구 처리 탓에 공식 시군구 인구와 불일치할 위험이 크다.
2. **시군구를 입력으로 요구한다.** 전국을 얻으려면 약 250회 호출이 필요하다.
3. **출력이 명칭 위주다.** 시도**명**/시군구**명**/도로**명**. 행정구역 코드 제공 여부는 **[미확인]**.

### 같은 계열 (동일하게 부적합)
- `15108071` 행정안전부_법정동별(행정동 통반단위) 주민등록 인구 및 세대현황
- `15108065` 행정안전부_행정동별(통반단위) 주민등록 인구 및 세대현황

---

## 1.3 후보 C — 통계청(국가데이터처) SGIS OpenAPI **[대안이나 코드 불일치]**

### 확인에 사용한 공식 URL
- `https://sgis.mods.go.kr/developer/html/newOpenApi/api/dataApi/census.html` — **[확인됨]** 200
- `https://sgis.mods.go.kr/developer/html/openApi/api/data.html` — **[확인됨]** 200

### 도메인 이전 — **[확인됨]**

- 문서: `sgis.kostat.go.kr` → **`sgis.mods.go.kr`** (302 리다이렉트)
- API: `sgisapi.kostat.go.kr` → **`sgisapi.mods.go.kr`** (302 리다이렉트)
- 기관 표기가 "통계청" → "**국가데이터처**"로 변경되었다.

### 인증 — 2단계 **[확인됨]**

```
https://sgisapi.mods.go.kr/OpenAPI3/auth/authentication.json?consumer_key={KEY}&consumer_secret={SECRET}
```

응답: `accessToken`, `accessTimeout`(만료 시각, epoch 초). **토큰 만료 관리가 필요하다.**

### 총조사 주요지표 API **[확인됨]**

```
https://sgisapi.mods.go.kr/OpenAPI3/stats/population.json
```

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `accessToken` | 필수 | 액세스 토큰 |
| `year` | 필수 | 기준연도 (인구/주택 2015~2024) |
| `adm_cd` | 선택 | 행정구역코드. 미지정=전국 시도 / 2자리=시도 / **5자리=시군구** / 8자리=읍면동 |
| `low_search` | 선택 | 하위 통계 유무. `0`/`1`(기본)/`2` |

주요 응답 필드: `adm_cd`, `adm_nm`, **`tot_ppltn`(총인구)**, `avg_age`, `ppltn_dnsty`, `aged_child_idx`, `oldage_suprt_per`, `juv_suprt_per`, `tot_family`, `avg_fmember_cnt`, `tot_house` 등

### 결정적 약점 — **[확인됨]**

공식 문서 예제에서 종로구의 `adm_cd`는 **`11010`** 이다. 법정동코드 기준 종로구는 **`11110`** 이다.

즉 SGIS는 **센서스 전용 행정구역코드**를 쓰며 행정표준코드와 직접 조인되지 않는다. 별도 매핑 테이블이 필요하다.

추가 약점:
- 데이터가 **인구주택총조사(센서스) 기반 연 단위**다. 주민등록인구(월 단위)와 정의·수치가 다르다.
- 읍면동 자릿수 표기가 신·구 문서에서 8자리/7자리로 엇갈린다(시군구 5자리는 양쪽 일치).

### 오류 응답 — **[확인됨]**

```json
{"errCd":-401,"errMsg":"인증 정보가 존재하지 않습니다","id":"API_0312","trId":"..."}
```

---

# 2. 지방행정 인허가 데이터 API (구 LOCALDATA)

## 2.0 최우선 결론 — 전제가 무효화되었다

> ### **[확인됨] LOCALDATA(localdata.go.kr)는 2026년 4월 16일 0시부로 폐쇄되었다.**
>
> 데이터는 공공데이터포털(data.go.kr)로 이관되었고, **업종별 195종 API로 분리 개방**되었다.

실측 근거:

| 대상 | 결과 |
| --- | --- |
| `https://www.localdata.go.kr/` | **연결 실패** (curl exit 7 / HTTP 000) |
| `http://www.localdata.go.kr/platform/rest/TO0/openDataApi?authKey=...` | **연결 실패**. 인증 오류조차 받지 못한다 — 엔드포인트 소멸 |
| `https://sample.localdata.go.kr/...` | 302 → `/error.html` (403) |

따라서 과제에 나열된 다음 항목들은 **전부 폐기된 구 스펙**이다:

- 요청 파라미터: `authKey`, `resultType`, `pageIndex`, `pageSize`, `opnSvcId`, `localCode`, `lastModTsBgn`, `lastModTsEnd`, `bgnYmd`, `endYmd`, `state`
- 응답 필드: `opnSfTeamCode`, `mgtNo`, `trdStateGbn`, `trdStateNm`, `dtlStateGbn`, `bplcNm`, `uptaeNm`, `apvPermYmd`, `dcbYmd`, `siteWhlAddr`, `rdnWhlAddr`

구 스펙 자체의 정확한 철자는 원본 문서가 사라져 **[미확인]** 이다. 아래는 **현재 살아있는 신 스펙**이다.

### 참고: data.go.kr `15154963` 은 통합 API가 아니다 — **[확인됨]**

과제가 지목한 `https://www.data.go.kr/data/15154963/openapi.do` 는 "지방행정 인허가 통합 API"가 아니라 **"행정안전부_생활_통신판매업 조회서비스"** 단일 업종 데이터셋이다.

---

## 2.1 확인에 사용한 공식 URL

| URL | 상태 |
| --- | --- |
| `https://www.data.go.kr/data/15154916/openapi.do` | **[확인됨]** 200 — 행정안전부_식품_일반음식점 조회서비스 |
| `https://www.data.go.kr/data/15154963/openapi.do` | **[확인됨]** 200 — 행정안전부_생활_통신판매업 조회서비스 |
| `https://apis.data.go.kr/1741000/general_restaurants/info` | **[확인됨]** 실호출 403(인증) — 엔드포인트 존재 확인 |
| `https://www.data.go.kr/cmm/cmm/fileDownload.do?atchFileId=FILE_000000003594039&fileDetailSn=1` | **[확인됨]** 공식 코드표 xlsx 20,377 bytes 다운로드 및 파싱 성공 |
| `https://file.localdata.go.kr/file/download/general_restaurants/info?orgCode=3000000` | **[확인됨]** 무인증 벌크 CSV 6,057,833 bytes 다운로드 성공 |
| `https://www.localdata.go.kr/*` | **[확인됨]** 접속 불가 (폐쇄) |

---

## 2.2 요청 URL과 파라미터 — **[확인됨]**

데이터셋 상세 페이지에 임베드된 **공식 Swagger 스펙**에서 추출했다.

```
https://apis.data.go.kr/1741000/{업종slug}/info
  ?serviceKey={SERVICE_KEY}
  &pageNo=1
  &numOfRows=100
  &returnType=json
  &cond%5BOPN_ATMY_GRP_CD%3A%3AEQ%5D=3000000
  &cond%5BSALS_STTS_CD%3A%3AEQ%5D=01
```

오퍼레이션은 업종마다 **`/info`(현재 데이터)** 와 **`/history`(과거 시점 이력)** 두 개다.

| 파라미터 | 필수 | 의미 / 형식 |
| --- | --- | --- |
| `serviceKey` | **필수** | 공공데이터포털에서 받은 인증키 |
| `pageNo` | **필수** | 페이지번호 |
| `numOfRows` | **필수** | 한 페이지 결과 수 (**최대 100**) |
| `returnType` | 선택 | 응답 데이터 타입. 허용값 문자열은 Swagger에 열거되지 않음 — **[미확인]** (데이터포맷 `JSON+XML` 지원은 확인됨) |
| `cond[OPN_ATMY_GRP_CD::EQ]` | 선택 * | 개방자치단체코드 (지역 필터) |
| `cond[SALS_STTS_CD::EQ]` | 선택 | 영업상태코드 일치 |
| `cond[LCPMT_YMD::GTE]` / `cond[LCPMT_YMD::LT]` | 선택 | 인허가일자 범위 (YYYYMMDD) |
| `cond[DAT_UPDT_PNT::GTE]` / `cond[DAT_UPDT_PNT::LT]` | 선택 | 데이터갱신시점 범위 (YYYYMMDDHHMMSS) — **증분 수집용**, 구 `lastModTsBgn` 대체 |
| `cond[BPLC_NM::LIKE]` | 선택 | 사업장명 포함 |
| `cond[ROAD_NM_ADDR::LIKE]` | 선택 | 도로명주소 포함 (업종에 따라 유무 상이) |
| `cond[BASE_DATE::EQ]` | `/history`만 **필수** | 데이터기준일자. 조회 범위 2026-01-01 ~ 조회일 전일 |

\* `/history` 에서는 `cond[OPN_ATMY_GRP_CD::EQ]` 가 **필수**다.

> **주의**: 파라미터명에 대괄호와 콜론이 들어간다. URL 인코딩이 필수다.
> `cond[OPN_ATMY_GRP_CD::EQ]` → `cond%5BOPN_ATMY_GRP_CD%3A%3AEQ%5D`

### 구 스펙 ↔ 신 스펙 대조 — **[확인됨]**

| 구 LOCALDATA | 신 data.go.kr |
| --- | --- |
| `opnSvcId=07_24_04_P` | URL 경로 slug (`general_restaurants`) |
| `authKey` | `serviceKey` |
| `pageIndex` / `pageSize` | `pageNo` / `numOfRows` |
| `resultType` | `returnType` |
| `localCode` / `opnSfTeamCode` | `cond[OPN_ATMY_GRP_CD::EQ]` (7자리, **동일 체계 승계**) |
| `lastModTsBgn` / `lastModTsEnd` | `cond[DAT_UPDT_PNT::GTE]` / `[::LT]` |
| `bgnYmd` / `endYmd` | `cond[LCPMT_YMD::GTE]` / `[::LT]` |
| `state` | `cond[SALS_STTS_CD::EQ]` |

**업종 구분이 파라미터에서 URL 경로로 승격**된 것이 가장 큰 구조 변화다. 업종당 별도 데이터셋 + 별도 활용신청이다.

---

## 2.3 응답 필드 — **[확인됨]** (일반음식점 기준)

공통 래퍼: `response.header.{resultCode, resultMsg}` / `response.body.{dataType, numOfRows, pageNo, totalCount, items.item[]}`

| 필드명 | 설명 | 구 스펙 대응 |
| --- | --- | --- |
| `OPN_ATMY_GRP_CD` | 개방자치단체코드 (7자리) | `opnSfTeamCode` |
| `MNG_NO` | 관리번호 (예: `3000000-101-2016-00350`) | `mgtNo` |
| `BPLC_NM` | 사업장명 | `bplcNm` |
| `SALS_STTS_CD` | **영업상태코드** | `trdStateGbn` |
| `SALS_STTS_NM` | 영업상태명 | `trdStateNm` |
| `DTL_SALS_STTS_CD` | 상세영업상태코드 | `dtlStateGbn` |
| `DTL_SALS_STTS_NM` | 상세영업상태명 | `dtlStateNm` |
| `LCPMT_YMD` | 인허가일자 (YYYYMMDD) | `apvPermYmd` |
| `CLSBIZ_YMD` | 폐업일자 (YYYYMMDD) | `dcbYmd` |
| `LOTNO_ADDR` | 지번주소 | `siteWhlAddr` |
| `ROAD_NM_ADDR` | 도로명주소 | `rdnWhlAddr` |
| `LCTN_ZIP` / `ROAD_NM_ZIP` | 소재지 / 도로명 우편번호 | — |
| `BZSTAT_SE_NM` | 업태구분명 (한식, 중식 등) | `uptaeNm` |
| `SNTTN_BZSTAT_NM` | 위생업태명 | — |
| `CRD_INFO_X` / `CRD_INFO_Y` | 좌표 X/Y | `x` / `y` |
| `TELNO` / `HPG` | 전화번호 / 홈페이지 | — |
| `LCTN_AREA` / `FCLT_TOTAL_SCL` | 소재지면적 / 시설총규모 | — |
| `MLT_UTZTN_BSNSSP_YN` | 다중이용업소여부 | — |
| `DAT_UPDT_SE` | 데이터갱신구분 (실측값 `I`, `U`. 전체 값 정의는 **[미확인]**) | — |
| `DAT_UPDT_PNT` | 데이터갱신시점 (YYYYMMDDHHMMSS) | `lastModTs` |
| `LAST_MDFCN_PNT` | 최종수정시점 | — |

**좌표계 — [확인됨]**: `보정계수 안 들어간 Bessel 중부원점TM (EPSG:5174)`. WGS84 변환이 필요하다.

> **[확인됨] 필드 집합은 업종마다 다르다.** 통신판매업에는 `NTSL_MTH_NM`(판매방식명), `ROBIZ_YMD`(재개업일자), `TCBIZ_BGNG_YMD`/`TCBIZ_END_YMD`(휴업 시작/종료일자), `LCPMT_RTRCN_YMD`(인허가취소일자)가 있으나 일반음식점에는 없다.
> 공통으로 신뢰할 수 있는 골격은 **개방자치단체코드 / 관리번호 / 영업상태 / 인허가일자 / 주소 / 갱신시점** 뿐이다.

---

## 2.4 영업상태 코드표 — **[확인됨]**

공식 참고문서 **`개방자치단체코드_영업상태코드.xlsx`** 의 시트 `2. 영업상태코드` 를 직접 다운로드해 파싱했다.

| `SALS_STTS_CD` (구 `trdStateGbn`) | 영업상태코드명 |
| --- | --- |
| `01` | **영업/정상** |
| `02` | 휴업 |
| `03` | **폐업** |
| `04` | 취소/말소/만료/정지/중지 |
| `05` | 제외/삭제/전출 |
| `06` | 기타 |

**실데이터 교차검증 [확인됨]** — 서울종로구 일반음식점 20,557건: `01`=6,647건, `03`=13,910건.

> `DTL_SALS_STTS_CD`(상세영업상태코드)는 **업종별로 값 체계가 다르다.** 일반음식점 실측은 `01`=영업, `02`=폐업.
> **전 업종 통합 코드표는 배포되지 않는다 — [미확인].** 상세코드는 쓰지 말고 `SALS_STTS_CD` 만 쓰는 것을 권한다.

---

## 2.5 행정구역 코드 — 시군구 5자리 정규화 **[확인됨]**

### 결론: `OPN_ATMY_GRP_CD` 는 시군구 코드가 아니다. 매핑 테이블이 필수다.

`OPN_ATMY_GRP_CD`(개방자치단체코드, 구 `opnSfTeamCode`)는 **인허가기관(자치단체) 7자리 독자 코드**다. 법정동/행정표준코드와 산술적 관계가 **없다**.

| 자치단체명 | 개방자치단체코드 | 표준 시군구코드 |
| --- | --- | --- |
| 서울종로구 | `3000000` | 11110 |
| 서울중구 | `3010000` | 11140 |
| 서울강남구 | `3220000` | 11680 |
| 강원춘천시 | `4181000` | 51110 |
| 경기수원시 | `3740000` | 41110 |
| 경기성남시 | `3780000` | 41130 |

> **[확인됨] 응답에 별도의 법정동코드/행정동코드 필드는 없다.** 주소는 문자열(`LOTNO_ADDR`, `ROAD_NM_ADDR`)뿐이다.

### 공식 매핑표가 존재한다 — **[확인됨]**

`개방자치단체코드_영업상태코드.xlsx` 시트 `1. 개방자치단체코드` 를 직접 파싱한 결과 **총 261행**:

| 구분 | 행수 | 예시 |
| --- | --- | --- |
| 시도 전체 | 17 | `서울특별시 전체` = `6110000_ALL` |
| 시도 본청 | 16 | `서울특별시` = `6110000`, `세종특별자치시` = `5690000` |
| **시군구** | **228** | `서울종로구` = `3000000` |

자치단체 코드는 전부 7자리다.

### 프로젝트 `sigungu.csv` 와의 대조 — **[확인됨]**

`data/static/sigungu.csv`(264행)와 위 228개를 **`시도 2자 약칭 + 시군구명(공백 제거)`** 규칙으로 조인해봤다.

> 결과: **228건 전부 매칭. LOCALDATA 쪽에 프로젝트가 못 잡는 자치단체는 0건.**

매칭되지 않는 프로젝트 쪽 36행은 전부 **일반구 35개 + 세종특별자치시 1개** 다:

```
수원시 장안/권선/팔달/영통구, 성남시 수정/중원/분당구, 안양시 만안/동안구,
부천시 원미/소사/오정구, 안산시 상록/단원구, 고양시 덕양/일산동/일산서구,
용인시 처인/기흥/수지구, 청주시 상당/서원/흥덕/청원구, 천안시 동남/서북구,
포항시 남/북구, 창원시 의창/성산/마산합포/마산회원/진해구, 전주시 완산/덕진구
+ 세종특별자치시
```

**해석**: 인허가 권한이 일반구가 아니라 **시** 에 있어 LOCALDATA는 시 단위(`경기수원시` = `3740000`)로만 데이터를 준다. 세종은 시군구 목록이 아니라 **시도 본청 코드 `5690000`** 을 써야 한다.

→ 시군구별 인프라 집계를 만들 때 **일반구 35개는 상위 시의 값으로 채우거나, 주소 문자열로 재분배해야 한다.** 어느 쪽을 택할지는 정책 결정 사항이라 여기서 정하지 않는다.

**보조 수단**: `LOTNO_ADDR`(지번주소) 파싱. 단 실측상 **도로명주소는 결측률 42.8%**, 지번주소는 대부분 존재한다. 좌표 결측률은 전체 10.0%(영업중만 3.6%).

---

## 2.6 페이지네이션과 호출 제한 — **[확인됨]**

| 항목 | 값 |
| --- | --- |
| 페이지네이션 | `pageNo` + `numOfRows` (offset 방식) |
| **최대 `numOfRows`** | **100** (Swagger 명시: `한 페이지 결과 수(max: 100)`) |
| 신청 가능 트래픽 | 개발계정 **10,000건/일** |
| 운영계정 | 활용사례 등록 시 신청하면 증가 가능 |
| 비용 / 라이선스 | 무료 / 이용허락범위 제한 없음 |
| 심의 | 개발·운영 모두 **자동승인** |
| 데이터 갱신 | 매일 갱신, **D-2 기준** 현행화 |
| 초당 호출 제한 | 에러코드 `23` 존재하나 **구체 수치 [미확인]** |

> ### **[확인됨] 실용성 경고**
> 일반음식점 전국 데이터는 **2,129,830건**이다. `numOfRows` 상한 100 + 일 10,000회 제한이면 전량 수집에 **21,299회 호출**이 필요하다. **하루 만에 불가능하고 2~3일이 걸린다.**
> 전량 적재 목적이면 API가 아니라 **벌크 CSV**(2.8절)를 써야 한다.

---

## 2.7 인증키 — data.go.kr 키 하나로 통일 가능한가? **[확인됨]**

### 결론: **가능하다. 단 데이터셋별 활용신청이 필요하다.**

- **`authKey` 는 소멸했다.** localdata.go.kr 자체가 없어졌으므로 별도 회원가입/키발급이 필요 없다.
- 이제 **data.go.kr 의 `serviceKey` 하나만 쓴다.** → `DATA_GO_KR_SERVICE_KEY` 환경변수 단일화 가능.

### 실측 검증

프로젝트가 이미 보유한 data.go.kr 키(국토부 실거래가용으로 활용신청 완료 상태)를 그대로 `1741000` 계열에 넣어봤다.

```
GET https://apis.data.go.kr/1741000/general_restaurants/info?serviceKey={SERVICE_KEY}&pageNo=1&numOfRows=2&returnType=json
→ HTTP 403
{"OpenAPI_ServiceResponse":{"cmmMsgHeader":{
   "errMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR",
   "returnAuthMsg":"등록되지 않은 서비스키","returnReasonCode":"30"}}}
```

동일한 키가 국토부 API에서는 `resultCode 000` 으로 정상 동작한다. 따라서:

> **키 문자열은 계정 단위로 하나지만, 권한은 데이터셋 단위로 부여된다.**
> **환경변수는 1개면 되지만, 쓸 업종 수만큼 포털에서 활용신청 버튼을 눌러야 한다.** (자동승인이라 즉시 사용 가능)

### 게이트웨이 에러코드 — **[확인됨]**

| 에러메시지 | 코드 | 설명 |
| --- | --- | --- |
| `APPLICATION_ERROR` | 01 | GW 내부 처리 중 예기치 않은 오류 |
| `HTTP_ERROR` | 04 | 허용되지 않은 HTTP 요청 / 기관 API 응답 처리 실패 |
| `SERVICETIMEOUT_ERROR` | 05 | 연결 실패 또는 응답 대기시간 초과 |
| `INVALID_REQUEST_PARAMETER_ERROR` | 10 | 요청 파라미터 값/형식 오류 |
| `NO_OPENAPI_SERVICE_ERROR` | 12 | 서비스 미존재 또는 폐기 |
| `SERVICE_KEY_IS_NULL` | 20 | 인증키 미포함 |
| `PERMISSION_DENIED` | 20 | GW 접근 권한 거부 |
| `SERVICE_ACCESS_DENIED_ERROR` | 20 | 이용 권한 미확인 |
| `LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR` | 22 | **일일 호출 허용량 초과** |
| `LIMITED_NUMBER_OF_SERVICE_REQUESTS_PER_SECOND_EXCEEDS_ERROR` | 23 | 초당 호출 허용량 초과 |
| `BLACKLIST_IP_ACCESS_ERROR` | 29 | 차단된 IP |
| `SERVICE_KEY_IS_NOT_REGISTERED_ERROR` | 30 | 미등록 인증키 |
| `DEADLINE_HAS_EXPIRED_ERROR` | 31 | 인증키 사용기한 만료 |

---

## 2.8 벌크 CSV — 시드 구축의 현실적 경로 **[확인됨]**

```
GET https://file.localdata.go.kr/file/download/{업종slug}/info?orgCode={7자리코드}
Referer: https://www.data.go.kr/      ← 필수
```

실측 검증 (서울종로구 일반음식점):

| 항목 | 값 |
| --- | --- |
| Referer 없음 | HTTP **302** → `/error.html` |
| Referer 있음 | HTTP **200**, `text/csv;charset=UTF-8`, **6,057,833 bytes**, **20,558행**(헤더 포함) |
| 인증키 | **불필요** |
| 인코딩 | **CP949**, 헤더는 한글 컬럼명 |

`orgCode` 는 세 형태 모두 동작한다 — **[확인됨]**
- `3000000` → 서울종로구
- `6110000` → 서울특별시 본청만
- `6110000_ALL` → 서울특별시 전체

CSV 헤더 (일반음식점, 39컬럼) — **[확인됨]**

```
개방자치단체코드,관리번호,인허가일자,영업상태명,폐업일자,소재지면적,소재지우편번호,
도로명우편번호,사업장명,업태구분명,데이터갱신구분,건물소유구분명,공장사무직직원수,
공장생산직직원수,공장판매직직원수,급수시설구분명,남성종사자수,다중이용업소여부,
데이터갱신시점,도로명주소,등급구분명,보증액,본사직원수,상세영업상태명,상세영업상태코드,
시설총규모,여성종사자수,영업상태코드,영업장주변구분명,월세액,위생업태명,전통업소주된음식,
전통업소지정번호,전화번호,좌표정보(X),좌표정보(Y),지번주소,홈페이지,최종수정시점
```

실제 데이터 행 예시:

```
3000000,3000000-101-1999-10679,1999-09-03,폐업,2004-01-06,46.17,110-380,,혜원감자탕,
한식,I,,,,,상수도전용,0,N,2026-01-14 14:27:17,,기타,,,폐업,02,46.17,0,03,
유흥업소밀집지역,,한식,,,027434587,,,서울특별시 종로구 권농동 158-1 ,,1999-09-14 00:00:00
```

> **시군구별 업소 수 시드를 만드는 것이 목적이라면 API 호출 없이 이 CSV만으로 완결된다.**
> 228개 자치단체 × 업종 수만큼 내려받아 `영업상태코드='01'` 을 카운트하면 된다.

> **[미확인]**: `file.localdata.go.kr` 벌크 서버의 공식 유지 기간/SLA. 공식 문서 없이 동작만 실측했다. 운영 의존 전에 확인이 필요하다.

---

## 2.9 업종 ↔ 엔드포인트 slug 매핑 — **[확인됨]**

data.go.kr 검색으로 확인한, 프로젝트가 쓸 만한 업종:

| 업종 | 데이터셋 ID | 엔드포인트 slug |
| --- | --- | --- |
| 일반음식점 | 15154916 | `general_restaurants` |
| 휴게음식점 | 15154921 | `rest_cafes` |
| 제과점영업 | 15155252 | `bakeries` |
| 의원 | 15154874 | `clinics` |
| 병원 | 15154458 | `hospitals` |
| 약국 | 15154822 | `pharmacies` |
| 체육도장업 | 15155085 | `martial_arts_dojo` |
| 종합체육시설업 | 15155071 | `comprehensive_sports_facilities` |
| 등록체육시설업 | 15155018 | `registered_sports_facilities` |
| 체력단련장업 | 15155077 | `fitness_centers` |
| 미용업 | 15154918 | `beauty_salons` |
| 세탁업 | 15154927 | `laundries` |
| 목욕장업 | 15155091 | `public_baths` |
| PC방(인터넷컴퓨터게임시설제공업) | 15154951 | `pc_bangs` |
| 노래연습장업 | 15155135 | `karaoke_rooms` |
| 담배소매업 | 15155031 | `tobacco_retailers` |
| 대규모점포 | 15154948 | `large_scale_retail_stores` |
| 통신판매업 | 15154963 | `ecommerce_businesses` |

데이터셋 명명 규칙은 `행정안전부_<분야>_<업종> 조회서비스` 다 (분야: 식품 / 건강 / 생활 / 문화 / 동물 / 자원환경 등).

### 존재하지 않는 업종 — **[확인됨]**

- **학원 / 교습소** — 없다. `무도학원업`(15155029)만 존재한다. 일반 학원은 **교육청 소관**으로 이 체계 밖이다.
- **어린이집 / 유치원** — 없다. 보건복지부 / 교육부 소관 별도 데이터다.
- **편의점 / 소매업** — 전용 데이터셋이 없다. `대규모점포`, `담배소매업` 이 가장 근접하다.

**[미확인]**: 199개 전 업종의 slug 전수표. 위 18종만 확인했다.

---

## 2.10 기존 `data/legacy/infra.csv` 검증

현재 파일: 헤더 `sigungu_code,opnSvcId,num`, **3,696행** = 14개 `opnSvcId` × 264개 시군구.

```
01_01_01_P  01_01_02_P  01_01_06_P  03_05_04_P  03_05_05_P  03_09_01_P  03_13_02_P
05_18_01_P  06_20_01_P  07_24_04_P  07_24_05_P  10_37_01_P  10_42_01_P  11_44_01_P
```

| 검증 항목 | 결과 |
| --- | --- |
| `11_44_01_P` 형식이 LOCALDATA `opnSvcId` 형식과 일치하는가 | **[확인됨] 형식은 일치한다.** `NN_NN_NN_P` 패턴이며 `07_24_04_P`=일반음식점 등 실재 값 흔적이 확인된다. |
| 개별 `opnSvcId` ↔ 서비스명 매핑 | **[미확인]** — 원본 개방서비스 목록 페이지가 폐쇄되어 14개 값이 각각 어떤 업종인지 확인할 수 없다. |
| 신 API에서 사용 가능한가 | **[확인됨] 불가.** 신 API에 `opnSvcId` 파라미터 자체가 없다. 업종은 URL slug 로 지정한다. |

> **결론: 이 시드 CSV 스키마는 재설계가 필요하다.**
> - `opnSvcId` 컬럼 → 엔드포인트 slug 문자열 또는 프로젝트 자체 `industry_code` 로 교체
> - `sigungu_code`(5자리) ↔ `OPN_ATMY_GRP_CD`(7자리) 매핑 테이블 추가
> - 참고: `.claude/skills/seed-data/SKILL.md` 기준 `infraJob` 이 요구하는 헤더는 `sigungu_code,industry_code,count,ratio,score` 로 현재 파일과 이미 불일치한다.

---

# 3. 국토교통부 아파트 전월세 실거래가 API

## 3.1 프로젝트 현재 설정과 공식 스펙 대조 — **[확인됨] 유효**

`src/main/java/SDD/smash/domain/dwelling/infrastructure/external/MolitAptRentApiAdapter.java` 가 쓰는 값:

| 프로퍼티 | 값 (backend.env) | 검증 결과 |
| --- | --- | --- |
| `apis.molit.base-url` | `https://apis.data.go.kr/1613000/RTMSDataSvcAptRent` | **[확인됨] 유효** |
| `apis.molit.path` | `getRTMSDataSvcAptRent` | **[확인됨] 유효** |
| `apis.molit.service-key` | (생략) | **[확인됨] 정상 인증** — `resultCode 000` |

## 3.2 공식 데이터셋 페이지

| 항목 | 값 | 라벨 |
| --- | --- | --- |
| 데이터셋 URL | `https://www.data.go.kr/data/15126474/openapi.do` | **[확인됨]** |
| OpenAPI 명 | 국토교통부_아파트 전월세 실거래가 자료 | **[확인됨]** |
| 제공기관 / 관리부서 | 국토교통부 / 부동산소비자보호기획단 | **[확인됨]** |
| 등록일 / 수정일 | 2024-01-25 / **2026-07-29** | **[확인됨]** |
| API 유형 / 기본 포맷 | REST / **XML** | **[확인됨]** |
| 활용신청 수 | 8,481 | **[확인됨]** |
| 심의 / 비용 | 개발·운영 자동승인 / 무료, 이용허락범위 제한 없음 | **[확인됨]** |
| 트래픽 | 개발계정 **10,000건/일** | **[확인됨]** |
| 참고문서 | `아파트 전월세 실거래가 자료 기술문서.hwp` (포털 로그인 후 다운로드) | **[확인됨]** 존재 / 내용 **[미확인]** |
| 데이터 갱신주기 | 포털 상세 페이지에 표기 없음 | **[미확인]** |

관련 데이터셋(참고): 15126472 단독/다가구 전월세, 15126468 아파트 매매 상세.

## 3.3 요청 파라미터 — **[확인됨, 실호출 검증]**

```
https://apis.data.go.kr/1613000/RTMSDataSvcAptRent/getRTMSDataSvcAptRent
  ?serviceKey={SERVICE_KEY}
  &LAWD_CD=11110
  &DEAL_YMD=202606
  &pageNo=1
  &numOfRows=1000
  &_type=json
```

| 이름 | 필수 | 설명 | 검증 |
| --- | --- | --- | --- |
| `serviceKey` | 필수 | 공공데이터포털 인증키 | **[확인됨]** |
| `LAWD_CD` | 필수 | 법정동코드 **앞 5자리** (예: 서울 종로구 `11110`) | **[확인됨]** |
| `DEAL_YMD` | 필수 | 계약년월 6자리 (예: `202606`) | **[확인됨]** |
| `pageNo` | 선택 | 페이지 번호 | **[확인됨]** |
| `numOfRows` | 선택 | 한 페이지 결과 수 | **[확인됨]** |
| `_type` | 선택 | `json` 지정 시 JSON 반환. 미지정 시 **XML** | **[확인됨 — 실호출로 동작 검증]** / 공식 기술문서 기재 여부는 **[미확인]** |

### `numOfRows` 상한 — **[확인됨]** 상한 미관측

강남구(`11680`) 202605, `totalCount=1892` 기준:

| `numOfRows` | 반환 건수 |
| --- | --- |
| 1000 | 1000 |
| 5000 | 1892 (전량) |
| 10000 | 1892 (전량) |

→ 5000 이상도 정상 동작한다. 명시적 상한은 **[미확인]**.

## 3.4 응답 필드 — **[확인됨, 실측]**

응답 경로: `response.body.items.item[]` (프로젝트 어댑터가 쓰는 `/response/body/items/item` 과 일치)

| 필드명 | 설명 | 실측 예시 |
| --- | --- | --- |
| `aptNm` | 아파트명 | `경희궁자이(3단지)` |
| `aptSeq` | 아파트 일련번호 | `11110-2446` |
| `buildYear` | 건축년도 | `2017` |
| `dealYear` / `dealMonth` / `dealDay` | 계약 연/월/일 | `2026` / `6` / `25` |
| **`deposit`** | **보증금액(만원). 콤마 포함 문자열** | `"95,000"` |
| **`monthlyRent`** | **월세금액(만원). 숫자** | `20` |
| `excluUseAr` | 전용면적(㎡) | `59.7547` |
| `floor` | 층 | `5` |
| `jibun` | 지번 | `233` |
| `umdNm` | 법정동명 | `숭인동` |
| `sggCd` | 시군구코드(5자리) | `11110` |
| `contractTerm` | 계약기간 | `26.06~28.06` |
| `contractType` | 계약구분 | `신규` / `갱신` |
| `preDeposit` / `preMonthlyRent` | 종전 보증금 / 월세 | `"95,000"` / `" "` |
| `roadnm` | 도로명 | `경교장길 35` |
| `roadnmcd`, `roadnmsggcd`, `roadnmbonbun`, `roadnmbubun`, `roadnmbcd`, `roadnmseq` | 도로명 관련 코드 | — |
| `useRRRight` | 갱신요구권 사용 여부 | `" "` |

> **주의**: `deposit` 은 `"42,000"` 처럼 **콤마가 들어간 문자열**이고 `monthlyRent` 는 숫자다.
> 프로젝트의 `MapperUtil.num()` 은 `replaceAll("[^0-9-]", "")` 로 비숫자를 제거하므로 **현재 파싱은 정상**이다.
> 값이 없는 필드는 `null` 이 아니라 **공백 문자열 `" "`** 로 온다.

## 3.5 오류 응답 — **[확인됨, 실측]**

인증 실패 (HTTP **403**, `application/json`):

```json
{
  "OpenAPI_ServiceResponse": {
    "cmmMsgHeader": {
      "errMsg": "SERVICE_KEY_IS_NOT_REGISTERED_ERROR",
      "returnAuthMsg": "등록되지 않은 서비스키",
      "returnReasonCode": "30"
    }
  }
}
```

존재하지 않는 `LAWD_CD`(예: `99999`) → **에러가 아니다.** HTTP 200 + `resultCode 000` + 빈 결과:

```json
{"response":{"header":{"resultCode":"000","resultMsg":"OK"},
 "body":{"items":"","numOfRows":1,"pageNo":1,"totalCount":0}}}
```

> **`items` 가 객체가 아니라 빈 문자열 `""` 로 온다.** 프로젝트 어댑터의 `root.at("/response/body/items/item")` 는 이 경우 missing node 가 되어 `List.of()` 를 반환하므로 안전하다.

게이트웨이 공통 에러코드는 [2.7절 표](#27-인증키--datagokr-키-하나로-통일-가능한가-확인됨)와 동일하다.

## 3.6 자료 확정 시점 — fallback 설계용 **[확인됨]**

### 공식 근거

| 출처 | 문구 |
| --- | --- |
| `https://rt.molit.go.kr/pt/info/info.do` | 공개 기준: **"실시간 취합 후 익일 공개"** |
| 〃 | 전월세 공개 대상: "2021년 6월부터 임대차계약 신고를 한 주택… 및 2011년 1월부터 주민센터 및 일부 공개 가능한 대법원 등기소의 주택 확정일자 자료를 대상" |
| `https://rt.molit.go.kr/pt/xls/xls.do` | **"신고정보가 실시간 변경, 해제되어 제공시점에 따라 공개건수 및 내용이 상이할 수 있는 점 참고하시길 바랍니다."** |
| 주택 임대차 계약 신고제 | 임대차 계약 당사자는 **계약 체결일부터 30일 이내** 신고 (2021-06-01 이후 계약 대상) |

### 실측 — 당월 자료는 크게 미완성이다

`LAWD_CD=11110`(종로구), 조회 시점 **2026-08-13**:

| `DEAL_YMD` | `totalCount` |
| --- | --- |
| 202603 | 175 |
| 202604 | 110 |
| 202605 | 181 |
| 202606 | 171 |
| 202607 | 142 |
| **202608 (당월)** | **23** |
| 202609 (미래) | 0 |

> **설계 지침**
> - **당월(`YearMonth.now()`)은 신뢰할 수 없다.** 8월 13일 시점에 당월 건수가 평월의 약 13% 수준이다.
> - 신고 기한이 30일이므로 **어떤 달의 자료가 안정되는 시점은 그 달이 끝나고 30일 이상 지난 뒤**다. 즉 안전한 기준월은 **`now().minusMonths(2)`** 다.
> - 미래 월은 에러가 아니라 `totalCount=0` 으로 온다. **빈 응답과 장애를 구분하려면 `totalCount` 를 봐야 한다.**
> - 위 임계값은 종로구 1개 시군구 실측에 근거한 관찰이다. **국토부가 문서로 명시한 "확정 시점" 은 없다 — [미확인].**

## 3.7 `LAWD_CD` 와 프로젝트 `sigungu.csv` 의 불일치 — **[확인됨]**

**일반구를 가진 시의 상위 시 코드는 결과가 항상 0이다.**

| `LAWD_CD` | 202605 | 202606 |
| --- | --- | --- |
| `41110` 수원시 | **0** | **0** |
| `41111` 수원시 장안구 | 396 | 325 |
| `41113` 수원시 권선구 | 617 | 652 |
| `41130` 성남시 | **0** | **0** |
| `41135` 성남시 분당구 | 1070 | 930 |

`data/static/sigungu.csv` 264행 중 아래 **12개가 이런 상위 시 코드**다:

```
41110 수원시, 41130 성남시, 41170 안양시, 41190 부천시, 41270 안산시, 41280 고양시,
41460 용인시, 43110 청주시, 44130 천안시, 47110 포항시, 48120 창원시, 52110 전주시
```

> 이 12개는 매 배치마다 무의미한 호출 + `No records for sigungu=...` 경고를 만든다. 수집 대상에서 제외하거나 하위 구 합산으로 처리하는 편이 낫다.
>
> **2장의 LOCALDATA와 정반대라는 점에 유의한다.** LOCALDATA는 **시** 단위만 있고 일반구가 없는데, MOLIT은 **구** 단위만 있고 시가 비어 있다.

---

# 4. 환경변수 종합

## 4.1 현재 존재하는 것 (`backend.env`)

```
MOLIT_BASE_URL=https://apis.data.go.kr/1613000/RTMSDataSvcAptRent   # [확인됨] 유효
MOLIT_PATH=getRTMSDataSvcAptRent                                    # [확인됨] 유효
MOLIT_SERVICE_KEY=***                                               # [확인됨] 정상 인증
DEALYMD=202509                                                      # dwelling.dealYmd 로 주입
POPULATION_FILEPATH=                                                # 비어 있음
INFRA_FILEPATH=/app/data/infra.csv                                  # 경로 재편으로 stale
```

## 4.2 인구 수집(KOSIS)에 필요한 것

```
KOSIS_API_KEY=                                  # https://kosis.kr/openapi/ 에서 발급 (data.go.kr 키와 별개)
KOSIS_BASE_URL=https://kosis.kr/openapi         # HTTPS 필수 (HTTP 제공 종료)
KOSIS_POPULATION_ORG_ID=101
KOSIS_POPULATION_TBL_ID=DT_1B040A3
KOSIS_POPULATION_ITM_ID=T20                     # 총인구수
KOSIS_POPULATION_PRD_SE=M
```

## 4.3 인허가 수집에 필요한 것

```
DATA_GO_KR_SERVICE_KEY=                         # MOLIT_SERVICE_KEY 와 동일한 키를 재사용 가능
                                                # 단 업종별 데이터셋마다 활용신청 필요(자동승인)
LOCALDATA_API_BASE_URL=https://apis.data.go.kr/1741000
LOCALDATA_BULK_BASE_URL=https://file.localdata.go.kr/file/download
                                                # 벌크 CSV. 인증 불필요, Referer 헤더 필수
```

> **키 통합에 대한 결론**
> - `MOLIT_SERVICE_KEY` 와 인허가용 키는 **같은 data.go.kr 계정 키를 쓸 수 있다.** → `DATA_GO_KR_SERVICE_KEY` 하나로 통합 가능.
> - **KOSIS 키는 별개다.** 통합할 수 없다.
> - 키 통합은 코드 변경이므로 이 문서에서는 제안만 하고 적용하지 않았다.

---

# 5. 프로젝트 적용 시 확인된 이슈

조사 과정에서 실측으로 드러난 것들이다. **수정하지 않았고, 여기 기록만 한다.**

## 5.1 `MolitAptRentApiAdapter` — 페이지네이션 누락으로 데이터 유실 **[확인됨]**

```java
private static final int PAGE_NO = 1;
private static final int ROWS = 1000;
```

`pageNo` 를 1로 고정하고 `numOfRows=1000` 만 요청하는데, `totalCount` 를 확인하지 않는다.

실측: 강남구(`11680`) 202605 의 `totalCount` 는 **1,892건**이다. → **892건이 조용히 버려진다.**

전월세 평균·중앙값을 계산하는 배치라 표본 절반이 빠지면 결과가 왜곡된다.
(참고: `numOfRows=5000` 으로 올리면 1회에 전량이 왔다. 상한은 미확인.)

## 5.2 상위 시 코드 12개가 항상 빈 응답 **[확인됨]**

3.7절 참조. `DwellingBatchConfig.dwellingReader` 가 `getAllSigunguCodes()` 전량을 도는데, 그중 12개는 구조적으로 결과가 0이다.

## 5.3 `data/` 디렉터리 재편 반영 필요 **[확인됨]**

조사 중 저장소가 커밋 `3dc6678`("데이터 볼륨을 정적/생성 파일로 분리하고 시드 CSV 를 추적 대상으로 전환")으로 갱신되었다.

```
data/static/     sido.csv, sigungu.csv, level_top.csv, level_middle.csv   (docker-compose: :ro 마운트)
data/generated/  (비어 있음)                                              (docker-compose: 쓰기 가능)
data/legacy/     infra.csv
```

`backend.env` 의 `SIDO_FILEPATH=/app/data/sido.csv`, `INFRA_FILEPATH=/app/data/infra.csv` 는 **재편 이전 경로**라 현재 마운트와 맞지 않는다.

API로 생성할 `population.csv` / `infra.csv` 는 `data/generated/` 에 두는 것이 이 구조의 의도로 보인다.

## 5.4 시드 스키마와의 정합 **[확인됨]**

`.claude/skills/seed-data/SKILL.md` 기준 목표 스키마:

| Job | 헤더 | 인코딩 | 이 문서와의 관계 |
| --- | --- | --- | --- |
| `populationJob` | `sigungu_code,population` | MS949 | KOSIS `C1`(5자리) + `DT`(itmId=T20) 로 생성 가능 |
| `infraJob` | `sigungu_code,industry_code,count,ratio,score` | MS949 | 벌크 CSV 의 `개방자치단체코드` → 시군구 5자리 변환 후 `영업상태코드='01'` 카운트 |

`ratio`/`score` 는 `new BigDecimal(...)` 로 파싱하므로 빈 값이면 배치가 실패한다. API 응답에는 이 두 값이 없으므로 **프로젝트가 직접 계산해 채워야 한다.**

---

## 부록: 확인하지 못한 항목 모음

| # | 항목 | 사유 |
| --- | --- | --- |
| 1 | KOSIS 전체 오류코드 목록 | 개발가이드 HTML에 없음. PDF 텍스트 추출 실패 |
| 2 | KOSIS 일일 쿼터 / 분당 호출 제한 수치 | 제한 존재는 공지로 확인, 수치는 공지 상세 페이지 404 |
| 3 | KOSIS `jsonVD` 파라미터의 의미 | 공식 예제 코드엔 등장하나 파라미터 표에 없음 |
| ~~4~~ | ~~KOSIS `DT_1B040A3` 의 `C1` 시군구 5자리 실응답~~ | **해소됨(2026-08-13)**. 실호출로 시군구 5자리 확인(`C1=11110` 종로구) |
| 5 | KOSIS `12 전남광주통합특별시` 와 `29`/`46` 의 중복 집계 여부 | 통계표 메타데이터만으로 판단 불가 |
| 6 | 구 LOCALDATA `openDataApi` 파라미터 정확한 철자 | 시스템 폐쇄로 원본 문서 소실 |
| 7 | `11_44_01_P` 등 개별 `opnSvcId` ↔ 업종명 | 개방서비스 목록 페이지 폐쇄 |
| 8 | `DTL_SALS_STTS_CD` 전 업종 통합 코드표 | 공식 배포 없음. 업종별 상이 |
| 9 | `DAT_UPDT_SE` 값 정의 (`I`/`U` 외) | 코드표 미배포 |
| 10 | 인허가 API `returnType` 허용값 문자열 | Swagger에 열거값 미명시 |
| 11 | 인허가 199종 전체 slug 목록 | 18종만 수집 |
| 12 | `file.localdata.go.kr` 벌크 서버 공식 SLA / 유지 기간 | 공식 문서 없이 동작만 실측 |
| 13 | MOLIT `numOfRows` 명시적 상한 | 5000·10000 동작 확인, 상한 미관측 |
| 14 | MOLIT `_type=json` 의 공식 문서 기재 여부 | 동작은 확인. 기술문서(hwp)는 포털 로그인 필요 |
| 15 | MOLIT 데이터 갱신주기 (포털 표기) | 상세 페이지에 항목 없음 |
| 16 | MOLIT 월별 자료 "확정" 시점의 공식 정의 | 국토부 문서에 명시 없음. 실측 관찰로 대체 |
| 17 | 후보 B(15108092) operation 경로 / 파라미터 / 응답 필드 | 상세기능 탭 AJAX. 부적합 확정으로 추가 조사 중단 |
