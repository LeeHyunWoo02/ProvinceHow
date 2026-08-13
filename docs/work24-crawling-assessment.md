# 고용24(work24.go.kr) 채용정보 수집 가능성 조사

- 조사일: **2026-08-13**
- 조사 대상: 고용24 공개 채용정보 상세검색 화면 (`/wk/a/b/1200/`)
- 조사 방식: `curl` 수동 요청 17회(리다이렉트 포함 실제 HTTP 왕복 23회), 요청 간 3초 이상 간격,
  User-Agent `ProvinceHow/1.0 (+https://github.com/LeeHyunWoo02/ProvinceHow; research contact: lhw0824@hamagroups.io)`
- 로그인·캡차 우회·접근제어 우회·비공개 API 접근은 시도하지 않았음
- 코드 변경 없음. 산출물은 본 문서와 `src/test/resources/fixtures/work24/` HTML fixture 2개

---

## 0. 결론 요약 (먼저 읽을 것)

| 항목 | 결과 |
| --- | --- |
| robots.txt | 해당 경로 **허용** |
| 이용약관 | **자동수집·재배포를 제한하는 조항 존재** (제14조의2, 제17조) |
| 저작권정책 | **공공누리 제4유형 = 비상업적 + 2차적 저작물 작성 금지**, 서브페이지 딥링크 사전허락 필요 |
| **최종 결론** | **스크래핑 방식 수집은 권장하지 않음(사실상 불가).** 공식 **OPEN-API**로 전환할 것 |
| 기술적 실현가능성 | 별개로도 부정적: 시군구×직종 전수 조회는 하루 1회 수집으로 불가능 (아래 8장) |

robots.txt만 보면 허용이지만, robots.txt는 약관·저작권정책보다 하위 근거다.
**약관과 저작권정책이 명시적으로 제한하고 있으므로 그쪽이 결론을 지배한다.**

---

## 1. robots.txt 확인 결과 (2026-08-13)

`https://www.work24.go.kr/robots.txt`

- `User-Agent: *` 에 대해 `Allow: /`
- `Disallow` 는 다음 4개뿐:
  - `/cm/common/`
  - `/sa/`
  - `/ei/`
  - `/cm/f/c/0100/selectUnifySearchPost.do`

→ 조사 대상 경로 `/wk/a/b/1200/`, `/wk/a/b/1300/`, `/wk/l/b/1100/`, `/cm/c/d/0130/`, `/cm/e/a/0110/` 는
모두 **robots.txt 상으로는 허용**된다.

단, robots.txt의 허용은 "크롤러 접근을 기술적으로 막지 않았다"는 의미일 뿐,
**수집한 데이터의 이용·재배포 권리를 부여하지 않는다.** 아래 2·3장이 실제 판단 근거다.

---

## 2. 이용약관 확인 결과

- URL: `https://www.work24.go.kr/cm/c/d/0130/retrieveUtzeStptPost.do`
  (**POST 전용.** GET 요청 시 `405`. POST 시 `302` → `/cm/c/d/0130/retrieveUtzeStpt.do` 로 리다이렉트)
- 문서명: 고용24 개인회원/기업회원 이용약관 (한국고용정보원)

### 2-1. 제 14 조의 2 (사전 협의되지 않은 자동화된 도구의 접근 제한)

> 2. 한국고용정보원은 다음 각 호에 해당하는 경우 서비스 접근을 제한할 수 있습니다.
>    1) 사전 허락 없이 자동화된 수단을 이용하여 개인정보를 스크래핑하는 행위
>    2) 사전 허락 없이 자동화된 수단을 이용하여 서비스에 로그인을 시도 또는 로그인 하는 행위(인증정보를 이용하여 로그인 후 개인정보를 수집하는 행위를 포함)
>    3) 한국고용정보원과 사전에 서면 또는 전자적 방식으로 전송 규격 및 보안 정책을 협의하지 않은 경우
>    4) 과도한 트래픽을 유발하여 한국고용정보원의 시스템 성능을 저하하거나 다른 이용자의 정상적인 서비스 이용에 장애를 줄 우려가 있는 경우
>    5) 보안상 취약점이 발견되었거나 해킹 등 부정 의심 접속으로 판단되는 경우
>    6) IP를 지속적으로 바꿔가며 접속하는 행위
>    7) CAPTCHA 등을 우회하거나 무력화하는 행위 등을 시도

해석:
- 1)·2)는 문언상 **"개인정보"** 스크래핑/로그인에 관한 조항이다. 공개 채용공고(기업 구인정보)는
  정보주체의 개인정보로 보기 어려우므로 1)·2)에 곧바로 해당한다고 단정하기는 어렵다.
- 그러나 **3)** 은 대상을 개인정보로 한정하지 않고 **"사전에 전송 규격·보안 정책을 협의하지 않은 경우"**
  자체를 접근 제한 사유로 규정한다. 사전 협의 없는 자동 수집은 이 항에 정면으로 걸린다.
- **4)** 는 트래픽 기준이므로 저빈도 수집이면 회피 가능하나, 3)을 해소하지는 못한다.

### 2-2. 제 17 조 (이용자의 의무) — 가장 직접적인 제한

> 1. 이용자는 서비스를 이용할 때 다음 각 호의 행위를 하지 않아야 합니다.
>    (…)
>    2) **서비스를 이용하여 얻은 정보를 한국고용정보원의 사전 승낙없이 이용자의 이용이외의 목적으로 복제하거나 이를 출판, 방송 등에 사용하거나 제3자에게 제공하는 행위**

해석: 본 프로젝트는 수집한 채용정보를 **가공하여 자사 플랫폼 이용자(제3자)에게 제공**하는 것이 목적이다.
이는 "이용자의 이용 이외의 목적으로 복제" + "제3자에게 제공"에 해당하며,
**한국고용정보원의 사전 승낙이 없으면 약관 위반**이다.

### 2-3. 제 19 조 (게재된 자료에 대한 권리)

> 2. **게시자의 사전 동의가 없이는 이용자는 서비스를 이용하여 얻은 정보를 가공, 판매하는 행위 등 서비스에 게재된 자료를 상업적 목적으로 이용할 수 없습니다.**

해석: `sigungu_code, job_code, count` 형태의 **집계 CSV 생성은 명백한 "가공"** 이다.
상업적 목적이라면 게시자(구인기업) 사전 동의까지 요구된다.

### 2-4. 약관의 인적 적용 범위 (참고)

> 제 3 조 … 이 약관은 고용24에 게시하여 **회원가입을 완료함으로써** 효력을 발생합니다.
> 제 5 조 … 이 약관은 고용24의 **이용자**에게 적용합니다.

비회원 비로그인 접근에 약관이 그대로 구속력을 갖는지는 다툼의 여지가 있다.
그러나 **제14조의2는 "서비스 접근을 제한할 수 있다"는 운영자 권한 규정**이라 회원 여부와 무관하게 발동 가능하고,
아래 저작권정책은 회원 여부와 완전히 무관하게 적용된다. 따라서 이 논점으로 수집을 정당화할 수 없다.

---

## 3. 저작권정책 확인 결과 (수집 여부를 결정짓는 핵심)

- URL: `https://www.work24.go.kr/cm/c/d/0130/retrieveCpyrPolyPost.do` (POST 전용)

> 본 정책의 목적은 고용24 홈페이지를 통하여 제공되는 정보가 정보출처를 밝히지 않고,
> 무단사용, 변조, 상업적인 용도 등으로 사용되어 정보 이용자에게 피해를 끼치는 사례를 방지하기 위함입니다.
>
> 고용24에서 제공하는 콘텐츠는 저작권법에 의하여 보호받는 저작물로, 별도의 저작권 표시 또는 다른 출처를
> 명시한 경우를 제외하고는 원칙적으로 한국고용정보원에 저작권이 있으며, 저작권법 제24조의 2(공공저작물의
> 자유이용)에 따라 별도의 이용 허락 없이 자유이용이 가능합니다.
>
> 단, 자유이용이 가능한 저작물은 **"공공저작물 자유이용허락 표시 기준(공공누리, KOGL) 제4유형:
> 출처표시, 비상업적 이용만 가능, 2차적 저작물 작성 금지"** 를 부착하여 개방하고 있으므로
> 공공누리 표시가 부착된 저작물인지를 확인한 이후에 자유이용하시기 바랍니다.
>
> **공공누리가 부착되지 않은 자료는 담당자와 사전에 협의한 이후에 사용하여 주시기 바랍니다.**
>
> 다른 인터넷 사이트상의 화면에서 한국고용정보원의 고용24 메인화면으로 링크시키는 것은 허용되지만
> **세부화면(서브도메인)으로 링크시키는 것은 사전에 허락을 받지 않고는 허용되지 않습니다.**
> 또한 메인페이지로의 링크시에도 링크 사실을 저희 한국고용정보원에 통지하여야 합니다.
>
> 고용24 자료를 무단 변경, 복제·배포하는 경우 저작권법 제136조, 137조, 138조에 의한 권리의 침해죄,
> 부정발행 등의 죄, 출처명시 위반의 죄 등에 의거 법적 처벌을 받을 수 있음을 알려드립니다.

### 이 정책이 본 프로젝트에 미치는 영향

| 정책 조항 | 프로젝트에 대한 영향 |
| --- | --- |
| 공공누리 **제4유형 – 2차적 저작물 작성 금지** | `sigungu_code,job_code,count` 집계 CSV 생성이 **2차적 저작물 작성에 해당** → 금지 |
| 공공누리 **제4유형 – 비상업적 이용만 가능** | 서비스가 상업적 성격을 띠면 **불가** |
| **공공누리 미부착 자료는 사전 협의 필요** | 채용정보 검색결과 화면에는 공공누리 표시가 **부착되어 있지 않음** → **사전 협의 필요 대상** |
| 세부화면 딥링크 사전 허락 필요 | 공고 상세 링크(`empDetailAuthView.do?wantedAuthNo=…`)를 서비스에 노출하는 것도 사전 허락 대상 |

**즉, 가장 관대하게 해석해도(공공누리 4유형 적용) 집계 CSV 생성은 "2차적 저작물 작성 금지"에 걸리고,
엄격하게 보면 채용검색 결과는 애초에 공공누리 미부착 자료라 "사전 협의" 없이는 사용할 수 없다.**

---

## 4. 수집 허용 여부 최종 결론

> ### 결론: **현재 상태로는 수집(스크래핑) 불가.**

근거를 정리하면:

1. **이용약관 제17조 1항 2호** — 얻은 정보를 사전 승낙 없이 이용 외 목적으로 복제하거나 제3자에게 제공하는 행위 금지
2. **이용약관 제19조 2항** — 서비스에서 얻은 정보의 가공·상업적 이용 금지
3. **이용약관 제14조의2 2항 3호** — 사전 협의되지 않은 자동화 도구 접근은 접근 제한 사유
4. **저작권정책** — 공공누리 제4유형(2차적 저작물 작성 금지 / 비상업적)이며, 채용검색 결과는 공공누리 미부착 → 사전 협의 필요

robots.txt가 허용이라는 점은 위 4개를 뒤집지 못한다.

### 대신 취해야 할 경로: 공식 OPEN-API

고용24는 **정확히 우리가 필요한 데이터를 공식 API로 제공**하고 있다.
(`https://www.work24.go.kr/cm/e/a/0110/selectOpenApiIntroPost.do`)

> 한국고용정보원 고용24 시스템에서는 오픈(OPEN) API를 통해 제공함으로써 정부3.0 서비스를 실현하고 있습니다.
> 오픈(OPEN) API는 HTTP 기반(표준 프로토콜)으로 제공되며, 결과 데이터는 XML 방식(UTF-8 인코딩)으로 전송됩니다.

제공 목록 중 본 프로젝트 관련 항목:

| OPEN-API | 프로젝트 용도 |
| --- | --- |
| **채용정보** | 공고 목록 — 수집 대상 본체 |
| **공통코드 – 채용(지역, 직종, 종류 등)** | 지역코드·직종코드 마스터. 현재 seed CSV 대체/검증 가능 |
| **한국고용직업분류 매핑코드** | KECO 분류 매핑. `level_top` / `level_middle` 정합성 확보 |
| 공채속보 / 공채기업정보 | 보조 |

이용 절차:

1. 고용24 **기업회원** 가입 (OPEN-API는 기업회원 전용 서비스)
2. 로그인 후 `OPEN-API > 서비스 소개 및 신청` 메뉴에서 인증키 신청
3. 담당자 심사 → 인증키 발급
4. 개발명세서 포맷에 따라 사용

주의: `3. 발급된 인증키는 타 기관에 양도할 수 없습니다.` /
`5. 활용제한 사유가 발생한 경우, 한국고용정보원에서는 OPEN-API 활용을 제한할 수 있습니다.`

> **권고 조치**: 인증키 신청 시 신청 목적란에 "시군구×직종별 채용공고 건수 집계 및 이주 지원 서비스 내
> 통계 제공"을 명시하고, 상업적 이용 여부와 재배포 범위를 담당자와 **사전 협의**할 것.
> 이 협의가 약관 제14조의2 3호와 저작권정책의 "사전 협의" 요건을 동시에 해소한다.

---

## 5. 확인된 HTTP 엔드포인트 / 파라미터 (기술 기록)

아래는 **기술적 사실 기록**이다. 4장 결론에 따라 실제 수집에 사용해서는 안 되며,
OPEN-API 전환 시 파라미터 대조용 참고자료로만 쓴다.

### 5-1. 요청 흐름

| 단계 | Method | URL | 비고 |
| --- | --- | --- | --- |
| 검색 폼 제출 | POST | `/wk/a/b/1200/retriveDtlEmpSrchListInPost.do` | `<form id="mForm">` |
| ↓ 302 리다이렉트 | | | |
| **결과 페이지** | **GET** | `/wk/a/b/1200/retriveDtlEmpSrchList.do` | **GET 단독 호출 가능** |

결과 페이지는 GET 단독으로 정상 응답한다(세션 쿠키 불필요, `200 OK` 확인).

### 5-2. GET 파라미터

| 파라미터 | 값 예시 | 확인 결과 |
| --- | --- | --- |
| `region` | `11110` | 시군구 행정표준코드 5자리. **동작 확인** |
| `region` | `11000` | **시도 단위 동작 확인** (서울 전체 → 29,578건) |
| `region` | `11110\|11140` | **파이프(`\|`, URL인코딩 `%7C`)로 다중 지정 가능** (종로+중구 → 3,038건) |
| `occupation` | `011` | **동작 확인.** 종로구 1,419건 → 39건으로 필터됨 |
| `pageIndex` | `1`,`2`,`3` | 페이지 번호 |
| `currentPageNo` | `1`,`2`,`3` | `pageIndex`와 **동일 기능(별칭)**. 둘 중 하나만 보내도 동작 |
| `resultCnt` | `10`,`50` | 페이지당 건수 |
| `siteClcd` | `all` | 정보제공처 구분 |
| `sortField` | `DATE` | 정렬 기준 |
| `sortOrderBy` | `DESC` | 정렬 방향 |
| `empTpGbcd` | `1` | 고용형태 구분 |

> **정정**: 사전 관측이었던 "`occupation` 을 넣어도 결과가 안 바뀐다"는 **오관측이다.**
> `occupation=011` 은 정상적으로 필터링된다 (아래 7장).

> **정정**: 사전 관측이었던 "실제 바이트가 EUC-KR/MS949로 보인다"도 **오관측이다.**
> 응답 헤더 `Content-Type: text/html;charset=UTF-8` 이며, 바이트를 UTF-8로 디코딩하면 전량 성공하고
> EUC-KR로는 디코딩 실패한다. **페이지는 순수 UTF-8이다.**

### 5-3. 정상 응답 헤더 (2026-08-13 관측)

```
HTTP/1.1 200
Content-Type: text/html;charset=UTF-8
Transfer-Encoding: chunked
Vary: Accept-Encoding
Set-Cookie: route=…; Path=/; Secure; HttpOnly
Set-Cookie: HPSESSIONID=…; Path=/; Secure; HttpOnly; SameSite=None
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Content-Language: ko-KR
```

- 조사 중 **403 / 429 / 캡차 응답은 한 번도 발생하지 않았다** (의도적 유발 시도 없음).
- `Retry-After`, `RateLimit-*` 계열 헤더는 관측되지 않았다.
- 참고로 **비정상 접근 시 패턴**은 확인되었다. 잘못된 method로 접근하면
  `HTTP 405` + `Content-Type: text/html; chareset=UTF-8;charset=UTF-8`(오타 포함) 로
  다음 본문이 온다:
  ```html
  <script>alert('정상적인 접근 방식이 아닙니다.'); location.href='https://www.work24.go.kr/cm/main.do' </script>
  ```

#### 차단 감지 전략 (구현 시)

1. **HTTP status**: `200` 이외(특히 `403`, `429`, `503`)는 즉시 중단 + 백오프
2. **본문 시그니처**: 응답 본문에 `정상적인 접근 방식이 아닙니다` 또는
   `location.href='https://www.work24.go.kr/cm/main.do'` 가 있으면 차단으로 간주
3. **구조 시그니처**: `var paginationInfo = {` 가 응답에 없으면 정상 결과 페이지가 아님 → 차단/장애로 간주
   (0건 결과에도 `paginationInfo` 는 존재하므로 "0건"과 "차단"을 안전하게 구분할 수 있다)
4. `Content-Type` 이 `text/html` 이 아니거나 본문 길이가 비정상적으로 짧으면(< 50KB) 이상 신호

---

## 6. 페이지네이션 동작 및 총건수 획득

### 6-1. 총건수: **획득 가능**

결과 페이지 인라인 `<script>` 안에 아래 객체가 그대로 렌더링된다.

```javascript
var paginationInfo = {
    currentPageNo         : 1,
    recordCountPerPage    : 50,
    pageSize              : 10,
    totalRecordCount      : 1419,
    totalPageCount        : 29,
    firstPageNoOnPageList : 1,
    lastPageNoOnPageList  : 10,
    firstRecordIndex      : 0,
    lastRecordIndex       : 50,
    lastPageNo            : 29,
    firstPageNo           : 1
}
```

- **`totalRecordCount` 가 곧 총 검색 건수**다. (종로구 `region=11110&empTpGbcd=1` → 1,419건)
- 페이지네이션 링크를 파싱해 유추할 필요가 **전혀 없다.**
- **1페이지 1회 요청만으로 총건수를 얻을 수 있다.** → 프로젝트가 원하는 `count` 는 이 값 하나면 끝난다.

> **중요**: `<input type="hidden" id="listTotCnt" value="49"/>` 는 총건수가 **아니다.**
> 현재 페이지의 행 인덱스 상한(행 수 - 1)이다. `resultCnt=50` → `49`, `resultCnt=10` → `9`.
> **총건수로 오독하면 안 된다.** 0건일 때는 이 input 자체가 렌더링되지 않는다.

추출 정규식 예:

```
var\s+paginationInfo\s*=\s*\{[^}]*?totalRecordCount\s*:\s*(\d+)
```

### 6-2. 페이지 파라미터

- `pageIndex` 와 `currentPageNo` 는 **동일 기능**이다.
  `pageIndex=3` 단독, `currentPageNo=3` 단독 모두 3페이지를 반환했고 **첫 행의 `wantedAuthNo` 가 완전히 일치**했다.
- 하나만 보내면 되지만, 화면 폼은 둘 다 보낸다. 둘 다 보내되 값을 일치시키는 것이 가장 안전하다.

### 6-3. 마지막 페이지 초과 시 동작 — **빈 목록 반환 (반복 아님)**

`region=11110` (전체 29페이지) 에 `pageIndex=100` 요청 결과:

| 항목 | 값 |
| --- | --- |
| HTTP status | `200` |
| `<tr id="listN">` 행 수 | **0** |
| `listTotCnt` hidden input | **없음** |
| `paginationInfo.currentPageNo` | `100` (요청값 그대로 반영) |
| `paginationInfo.totalRecordCount` | `1419` (유지) |
| `paginationInfo.lastPageNo` | `29` (유지) |

→ **마지막 페이지를 반복하지 않고 빈 목록을 반환한다.** 따라서 "같은 페이지 반복 감지" 로직은 불필요하다.

**종료 조건은 다음 중 아무거나로 충분하며, 1번이 가장 견고하다:**

1. `paginationInfo.currentPageNo > paginationInfo.lastPageNo` → 중단
2. 파싱된 행 수 == 0 → 중단
3. 애초에 `totalRecordCount` / `totalPageCount` 를 읽고 필요한 페이지 수를 미리 계산 → **권장**

### 6-4. 0건 결과

`region=47940`(울릉군) + `occupation=011` 로 **진짜 0건 조건 확보에 성공**했다.
(fixture `search-result-empty.html` 로 저장)

| 항목 | 값 |
| --- | --- |
| `paginationInfo.totalRecordCount` | **0** |
| `paginationInfo.totalPageCount` | `1` |
| 행 수 | 0 |
| `listTotCnt` | 없음 |

→ **0건과 "페이지 초과"는 `totalRecordCount` 로 구분 가능하다** (0건은 `0`, 페이지 초과는 원래 총건수 유지).

---

## 7. 직종 코드 체계 결론

### 7-1. 결론: **프로젝트의 KECO 중분류 3자리와 동일 체계다.**

`occupation=011` 로 조회하자 결과 페이지가 코드명을 그대로 에코백했다.

```javascript
/** 직종 템플릿 호출**/
var codeNo  = '011';
var codeNm  = '행정·경영·금융·보험 관리직';
```

프로젝트 seed 파일 `data/static/level_middle.csv` 1행:

```csv
code,name,upstream_code
011,행정·경영·금융·보험 관리직,01
```

**코드와 명칭이 완전히 일치한다.** 즉 고용24의 `occupation` 파라미터는
**한국고용직업분류(KECO) 중분류 3자리 코드**를 그대로 사용하며,
프로젝트의 `level_middle.csv` 를 **변환 없이 그대로 넣을 수 있다.**

필터링도 정상 동작한다:

| 조건 | totalRecordCount |
| --- | --- |
| `region=11110` (직종 무관) | 1,419 |
| `region=11110&occupation=011` | **39** |

### 7-2. 직종 분류 깊이

검색 화면 안내 문구:

> 직종 : 최대 10개의 직종 선택이 가능합니다.
> 체크박스를 클릭하면 직종이 선택되고, '직종명'을 클릭하면 하위 분류가 보여집니다.
> **3차 분류** 직종을 선택하시면 해당 직종에 대한 키워드로 채용정보를 검색하실 수 있습니다.

고용24는 **1차 / 2차 / 3차 분류** 3단계를 쓴다.
프로젝트의 `level_top`(13건) = 1차 대분류, `level_middle`(114건) = 2차 중분류에 대응한다.

- `occupation` 은 최대 10개까지 선택 가능 → **다중 지정을 지원할 가능성이 높다**
  (`region` 이 `|` 구분자를 쓰므로 `occupation` 도 동일 규약일 것으로 추정. **미검증**)
- 2자리 대분류 코드(`01`)를 `occupation` 에 직접 넣을 수 있는지는 **미검증**

### 7-3. 직종 코드 목록 AJAX 엔드포인트

직종 트리는 인라인이 아니라 AJAX로 로드된다. 결과 페이지 초기화 시
`fn_requestJobSubList('', '', 'mainJob')` 로 1차 분류를 가져온다.

'직종별' 화면(`/wk/a/b/1300/`)에서 다음 JSON 엔드포인트를 확인했다:

```
POST /wk/l/b/1100/retrieveJobsListAjax.do
params: { jobsKeyword, resumeMngYn, relYn, ckWorkRegionCd }
```

응답 객체 필드(콜백 코드에서 확인): `jobs3depthCd`, `jobsCategoryNm`, `totalEmpCount`, `jobsCount`

> `totalEmpCount` / `jobsCount` 를 직종별로 돌려준다는 점에 주목. 8장 참조.

---

## 8. 결과 행 파싱 계약 (selector 전략)

fixture `search-result-page1.html` 의 첫 행 기준.

### 8-1. 행 구조

```html
<tr id="list1">
  <td class="al_left pd24">   <!-- 1) 회사명 / 공고제목 -->
    <input type="checkbox" id="chkboxWantedAuthNo0"
           value="KJAU002608130003|VALIDATION|정나눔실버케어|[재가요양보호사 모집] 숭인동 / 2등급 / 여자어르신"/>
    <a class="cp_name underline_hover" onclick="fnOpenPopup('1088010731');">정나눔실버케어</a>
    <a href="/wk/a/b/1500/empDetailAuthView.do?wantedAuthNo=KJAU002608130003&infoTypeCd=VALIDATION&infoTypeGroup=tb_workinfoworknet"
       class="t3_sb underline_hover" target="_new" data-emp-detail>
       [재가요양보호사 모집] 숭인동 / 2등급 / 여자어르신
    </a>
  </td>
  <td class="link pd24">      <!-- 2) 급여/경력/학력/근무일/근무지역 -->
    <ul class="emp_info_dtl">
      <li class="dollar"> … 시급 10,320 원 이상 </li>
      <li class="member"> 경력무관 / 학력무관 </li>
      <li class="time">   주6일 </li>
      <li class="site"><p>서울특별시 종로구 지봉로14길</p></li>
    </ul>
  </td>
  <td class="pd24">           <!-- 3) 마감 D-day / 마감일 / 등록일 -->
    <strong class="t3_sb clr_red" id="dDayInfo0"></strong>   <!-- 비어있음! -->
    <script>
      var date = '2026-10-12'; var closeDt = '26/10/12';
      var closeTpNm = ''; var wantedYn = 'Y'; var index = '0';
      … D-day 계산 로직 …
    </script>
    <p class="s1_r">마감일 : 2026-10-12</p>
    <p class="s1_r">등록일 : 2026-08-13</p>
  </td>
</tr>
```

### 8-2. 필드별 추출 전략 (단일 CSS 클래스 의존 회피)

| 필드 | 1순위 selector | 폴백 | 비고 |
| --- | --- | --- | --- |
| **행** | `tr[id^="list"]` | `table tbody tr` | id 접두사가 가장 안정적 |
| **(a) wantedAuthNo** | `a[href*="empDetailAuthView.do"]` → href에서 `wantedAuthNo=([A-Za-z0-9]+)` 정규식 | `input[id^="chkboxWantedAuthNo"]` 의 `value` 를 `\|` 로 split → `[0]` | **두 경로 모두 존재 → 교차검증 가능** |
| 회사명 | 체크박스 `value` split `[2]` | `a.cp_name` 텍스트 | 체크박스 쪽이 마크업 변화에 강함 |
| 공고제목 | 체크박스 `value` split `[3]` | `a[href*="empDetailAuthView.do"]` 텍스트 | 제목에 `\|` 가 들어가면 split 주의 → `split(limit=4)` 사용 |
| infoTypeCd | 체크박스 `value` split `[1]` | href의 `infoTypeCd` 쿼리 | |
| **(b) 근무지역 텍스트** | `li.site` 의 텍스트 | 2번째 `td` 내 `ul.emp_info_dtl > li` 중 **마지막** | 아래 경고 참조 |
| **(c) 마감일** | 3번째 `td` 의 `p` 중 텍스트가 `마감일` 로 시작하는 것 → `:` 뒤 파싱 | 인라인 script의 `var date = '(\d{4}-\d{2}-\d{2})'` | **텍스트 접두사 매칭이 클래스(`s1_r`)보다 안정적** |
| 등록일 | 3번째 `td` 의 `p` 중 텍스트가 `등록일` 로 시작 | — | |
| **(d) 마감여부** | **DOM에서 못 뽑음. 아래 참조** | | |

### 8-3. ⚠️ (d) 마감여부는 DOM에 없다 — 반드시 재계산해야 한다

`<strong id="dDayInfo0">` 는 **서버 응답 시점에 비어 있다.** D-day는 브라우저에서 JS가 채운다.
정적 파서는 같은 `<td>` 안 인라인 `<script>` 의 변수를 읽어 **로직을 재현**해야 한다.

추출할 변수: `var date`, `var wantedYn`

원본 JS 로직을 그대로 옮기면:

```
if (date.length != 10 || !date.contains("-"))  -> 판정불가(null)
else if (date.substring(0,4) == "2099")        -> "채용시까지"
else if (wantedYn == "Y")                      -> "채용시까지"
else {
    diff = date - today (일 단위)
    if (diff < 0)       -> "마감"
    else if (diff == 0) -> "오늘마감"
    else                -> "D-" + diff
}
```

> 주의: `wantedYn == 'Y'` 분기가 날짜 비교보다 **먼저** 오므로,
> `마감일 : 2026-10-12` 이면서 동시에 `채용시까지` 인 공고가 존재한다(첫 행이 바로 그 사례).
> **`마감일` 텍스트만 보고 마감 여부를 판단하면 틀린다.**

### 8-4. ⚠️ 근무지역 텍스트 주의

- 첫 행의 `li.site` 값은 `서울특별시 종로구 지봉로14길` 로 **도로명 주소**다.
  사전 조사에서 언급된 `서울특별시 종로구 창신제N동` 같은 **법정동/행정동 형태와 혼재**한다.
- 즉 **일관된 포맷이 아니다.** 시/도 + 시군구까지만 신뢰하고 그 뒤는 버리는 것이 안전하다.
- 애초에 `region` 파라미터로 시군구를 지정해 조회하므로 **행에서 지역을 역파싱할 필요가 없다.**
  요청한 `region` 코드를 그대로 쓰는 편이 정확하다.

### 8-5. ⚠️ 직종 정보는 결과 행에 없다 (재확인됨)

행 마크업 어디에도 **직종 코드/직종명이 없다.** 확인 완료.
따라서 "목록을 크롤링해서 직종을 추출"하는 접근은 **불가능**하며,
직종별 집계를 얻으려면 (i) `occupation` 파라미터로 조건을 나눠 조회하거나
(ii) 공고 상세 페이지를 건건이 조회해야 한다.

---

## 9. 구현 가능성 평가

프로젝트 목표: `sigungu_code, job_code, count` CSV 생성.

기준 수치 (`data/static/` 실측):

| 항목 | 건수 |
| --- | --- |
| 시군구 (`sigungu.csv`) | **264** |
| 직종 중분류 (`level_middle.csv`) | **114** |
| 직종 대분류 (`level_top.csv`) | **13** |

### 9-1. 방안별 요청 수 추정

요청 간 3초 간격(저빈도 예의) 기준.

| # | 방안 | 요청 수 | 소요시간(3s 간격) | 결과 정밀도 | 판정 |
| --- | --- | ---: | --- | --- | --- |
| A | 시군구 × 직종 중분류 전수 | 264 × 114 = **30,096** | **약 25.1시간** | 목표 그대로 | ❌ 하루 1회 수집 불가 |
| B | 시군구 × 직종 **대분류** | 264 × 13 = **3,432** | 약 2.9시간 | 대분류까지만 | △ 시간은 가능하나 정밀도 손실 + 2자리 코드 지원 미검증 |
| C | 시군구별 총건수만 (직종 무시) | **264** | 약 13분 | `job_code` 없음 | △ 목표 미달 |
| D | 시군구별 목록 전수 크롤링 후 직종 추출 | 전국 공고 수십만 건 × 상세페이지 1회 | 수백 시간 | 목표 그대로 | ❌ **직종이 행에 없어 상세 조회 필수 → 완전 불가** |
| E | `retrieveJobsListAjax.do` 직종별 집계 활용 | 264 (시군구당 1회) 추정 | 약 13분 | 목표 그대로(가능성) | ⚠️ **유망하나 미검증** |
| **F** | **공식 OPEN-API** | API 명세 의존, 대폭 감소 | — | 목표 그대로 | ✅ **권장** |

### 9-2. 방안 A가 불가능한 이유 (수치)

- 30,096 요청 × 3초 = 90,288초 ≈ **25.1시간** → 하루 1회 배치에 담기지 않는다(24시간 초과).
- 간격을 1초로 줄여도 8.4시간이며, 이는 약관 제14조의2 4호(과도한 트래픽)에 접근한다.
- 페이지당 응답이 **평균 800KB** 이므로 30,096회면 **약 24GB 전송**. 명백히 과도하다.
- 게다가 총건수만 필요한데 800KB짜리 전체 HTML을 받는다 → 극도로 비효율적.

### 9-3. 방안 E (유망 미검증 경로)

'직종별' 화면(`/wk/a/b/1300/retrieveJobsIntroCountListPost.do`)이 사용하는
`POST /wk/l/b/1100/retrieveJobsListAjax.do` 는 JSON 응답에
**`jobs3depthCd`, `jobsCategoryNm`, `totalEmpCount`, `jobsCount`** 를 포함한다.

즉 **"직종별 공고 건수"를 한 번에 배열로 돌려주는 집계 엔드포인트**로 보인다.
파라미터에 `ckWorkRegionCd`(지역코드) 가 있으므로,
**지역을 넣고 1회 호출 → 그 지역의 직종별 건수 전체** 를 얻을 가능성이 있다.

성립한다면 요청 수가 **30,096 → 264 로 약 114배 감소**한다.

미검증 항목:
- `ckWorkRegionCd` 가 시군구 5자리를 받는지
- 반환되는 `jobs3depthCd` 가 3차 분류인지, 중분류로 집계 가능한지
- `totalEmpCount` 와 `jobsCount` 의 정확한 의미 구분

> 다만 이 엔드포인트는 화면이 내부적으로 쓰는 AJAX이며 공개 문서가 없다.
> **4장 결론에 따라 실검증 및 사용은 하지 않았다.**
> 동일한 데이터를 OPEN-API의 `채용정보` + `공통코드(지역,직종)` 조합으로 정당하게 얻는 것이 옳다.

### 9-4. 권고 실행 순서

1. **고용24 기업회원 가입 → OPEN-API 인증키 신청** (담당자 심사 필요, 리드타임 확보할 것)
2. 신청 시 **수집 목적·재배포 범위·상업성 여부를 명시하여 사전 협의** → 약관 제14조의2 3호 및 저작권정책 요건 동시 해소
3. `공통코드(지역, 직종)` + `한국고용직업분류 매핑코드` API로 **현재 seed CSV(`sigungu`, `level_top`, `level_middle`) 검증**
   - 이미 `occupation=011` ↔ `level_middle.csv` 일치가 확인되었으므로 큰 차이는 없을 것으로 예상
4. `채용정보` API로 `sigungu_code, job_code, count` 집계 생성
5. 배치는 기존 Seed Job 규약(`.claude/skills/seed-data/SKILL.md`)에 맞춰 구성

---

## 10. 저장된 fixture

| 파일 | 크기 | 내용 |
| --- | --- | --- |
| `src/test/resources/fixtures/work24/search-result-page1.html` | 822,213 B | `region=11110&empTpGbcd=1&resultCnt=50&pageIndex=1` — 50행, `totalRecordCount=1419` |
| `src/test/resources/fixtures/work24/search-result-empty.html` | 483,314 B | `region=47940&occupation=011` — 0행, `totalRecordCount=0` |

- 둘 다 **원본 그대로**(잘라내기 없음), **UTF-8** 로 저장했다(원본이 이미 UTF-8이라 변환 불필요).
- 2MB 제한 이내다.

### ⚠️ fixture 취급 주의

이 두 파일에는 **실제 구인기업명·주소·공고 내용이 그대로 담겨 있다.**
3장 저작권정책(공공누리 제4유형, 공공누리 미부착 자료는 사전 협의 필요)을 고려하면
**공개 저장소에 커밋하는 것 자체가 재배포 논란 소지가 있다.**

권고:
- 공개 origin에 push하기 전에 `.gitignore` 처리하거나, 파서 계약 테스트에 필요한 최소 행만 남긴
  **익명화 버전으로 교체**할 것
- 지금은 조사 지시에 따라 원본 그대로 저장했으며, 커밋 여부는 **사용자 판단에 맡긴다**

---

## 11. 조사 중 관측된 저장소 변경 (참고)

조사 시작 시점 `git status` 는 clean 이었으나, 조사 도중 워킹트리가 변경되었다.
**본 조사는 코드를 일절 수정하지 않았으며**, 아래는 다른 프로세스에 의한 변경으로 보인다.

```
 M .gitignore
 M docker-compose.yaml
 M src/main/java/SDD/smash/domain/job/infrastructure/batch/JobCodeMiddleBatchConfig.java
 M src/main/java/SDD/smash/domain/job/infrastructure/batch/JobCodeTopBatchConfig.java
?? data/
?? src/test/java/SDD/smash/domain/job/
```

특히 `data/` 하위가 `data/static/`, `data/legacy/`, `data/generated/` 로 재배치되었다
(본 문서의 seed 파일 경로는 재배치 **후** 기준인 `data/static/…` 으로 기재했다).
