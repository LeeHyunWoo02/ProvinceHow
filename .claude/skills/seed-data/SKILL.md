---
name: seed-data
description: smash(ProvinceHow) 서비스 기동 전 DB에 사전 적재해야 하는 시드 CSV(data/)를 준비·검증·재적재하는 워크플로우. Spring Batch Seed Job 9개의 파일 스펙(경로 환경변수, 인코딩, 컬럼), @Order 기반 FK 선후관계, BatchGuard/SEED_VERSION 재실행 제어, scripts/verify-seed.sh 리포트 해석과 실패 유형별 대응을 다룬다. 시드 CSV를 추가·교체하거나, 앱 기동 전 데이터를 점검하거나, 배치가 실패·skip 됐을 때 원인을 찾거나, 새 Seed 배치를 추가할 때 사용한다. 배치 코드 자체의 작성 규칙은 persistence-conventions, 배치의 계층 배치는 architecture-conventions를 따른다.
---

# seed-data

## 0. 이 스킬의 전제

**적재는 애플리케이션이 한다.** 별도의 실행 명령이나 도구가 없다.

```
docker compose up --build
   ├─ mysql      (healthcheck 통과까지 대기)
   ├─ my-redis   (healthcheck 통과까지 대기)
   └─ backend
        └─ ApplicationReadyEvent
             └─ @Order(1..10) Runner 순차 실행
                  ├─ @ConditionalOnProperty(seed.jobs.<키>.enabled) 가 false면 빈 등록 자체가 안 됨
                  ├─ BatchGuard.alreadyDone(jobName, SEED_VERSION) 이면 skip
                  └─ 아니면 Job 실행 (CSV → data DB)
```

따라서 사람이 할 일은 **적재 실행이 아니라 기동 전 준비와 사후 확인**이다. 이 스킬은 그 앞뒤를 다룬다.

- `data/`는 **`.gitignore`에 있어 커밋되지 않는다.** 팀원마다 파일 구성이 다를 수 있으므로 기동 전 검증이 필수다.
- `docker-compose.yaml`이 `./data`를 컨테이너의 **`/app/data`(read-only)** 로 마운트한다. 경로 환경변수는 **컨테이너 기준**(`/app/data/...`)이어야 한다.

## 0.1 DB는 컨테이너다 (RDS 아님)

MySQL 컨테이너 **1개에 스키마 2개**를 둔다.

| 스키마 | 용도 | 생성 주체 |
|---|---|---|
| `smash_data` | 업무 데이터. 모든 JPA 엔티티 (`DataDBConfig`) | mysql 이미지의 `MYSQL_DATABASE` |
| `smash_meta` | Spring Batch 메타 테이블 (`MetaDBConfig`) | `docker/mysql/init/01-init-meta-db.sh` (최초 1회) |

**반드시 지켜야 할 것 3가지**

1. **`DRIVER=com.mysql.cj.jdbc.Driver`** — RDS 시절의 `software.amazon.jdbc.Driver`(AWS JDBC Wrapper)를 쓰면 컨테이너 MySQL에 붙지 않는다.
2. **JDBC URL의 호스트는 compose 서비스명 `mysql`** 이다. `localhost`가 아니다(컨테이너 내부에서 보는 이름).
3. **`BATCH_SCHEMA_INIT=always`** — 새로 만든 `smash_meta`는 비어 있다. `never`로 두면 `BATCH_JOB_INSTANCE` 등이 없어 **모든 배치가 기동과 동시에 실패**한다. 테이블이 생성된 뒤에는 `never`로 낮춰도 된다.

업무 테이블은 `hibernate.hbm2ddl.auto=update`가 자동 생성하므로 별도 DDL이 필요 없다.

## 0.2 배치 on/off 스위치

각 Runner에 `@ConditionalOnProperty(name = "seed.jobs.<키>.enabled", havingValue = "true")`가 붙어 있다.
소스 파일이 아직 없는 배치는 프로퍼티에서 `false`로 꺼져 있어 **기동을 막지 않는다.**

| 배치 | seed.jobs 키 | 현재 |
|---|---|---|
| Sido / Sigungu / JobCodeTop / JobCodeMiddle | `sido` `sigungu` `job-code-top` `job-code-middle` | `true` |
| Population / Industry / Infra / InfraScore / JobCount | `population` `industry` `infra` `infra-score` `job-count` | `false` |

파일을 준비했다면 **프로퍼티를 `true`로 바꿔야** 실제로 적재된다. 파일만 넣고 플래그를 안 켜는 실수가 잦다.

---

## 1. 시드 배치 스펙

| @Order | Job | 프로퍼티 | 환경변수 | 기본 파일명 | 인코딩 | 헤더 |
|---|---|---|---|---|---|---|
| 1 | `SidoJob` | `sido.filePath` | `SIDO_FILEPATH` | `sido.csv` | UTF-8 | `sido_code,name` |
| 2 | `SigunguJob` | `sigungu.filePath` | `SIGUNGU_FILEPATH` | `sigungu.csv` | UTF-8 | `sigungu_code,sido_code,name` |
| 3 | `jobCodeTopJob` | `jobCodeTop.filePath` | `JOBCODETOP_FILEPATH` | `level_top.csv` | MS949 | `code,name` |
| 4 | `jobCodeMiddleJob` | `jobCodeMiddle.filePath` | `JOBCODEMIDDLE_FILEPATH` | `level_middle.csv` | MS949 | `code,name,upstream_code` |
| 5 | `populationJob` | `population.filePath` | `POPULATION_FILEPATH` | `population.csv` | MS949 | `sigungu_code,population` |
| 6 | `industryJob` | `industry.filePath` | `INDUSTRY_FILEPATH` | `industry.csv` | UTF-8 | `code,name,major` |
| 7 | `infraJob` | `infra.filePath` | `INFRA_FILEPATH` | `infra.csv` | MS949 | `sigungu_code,industry_code,count,ratio` |
| 8 | `infraScoreJob` | `infraScore.filePath` | `INFRASCORE_FILEPATH` | `infra_score.csv` | MS949 | `sigungu_code,score` |
| 9 | `jobCountJob` | `jobCount.filePath` | `JOBCOUNT_FILEPATH` | `job_count.csv` | MS949 | `sigungu_code,job_code,count` |

### 1.1 FK 선후관계 (`@Order`가 이 순서를 보장한다)

```
sido ──▶ sigungu ──┬──▶ population
                   ├──▶ infra ◀── industry
                   ├──▶ infra_score
                   └──▶ job_count ◀── level_middle ◀── level_top
```

**Processor가 부모에 없는 코드를 만나면 그 행을 `null` 반환으로 조용히 skip 한다.** 예외가 나지 않으므로 "성공했는데 데이터가 비어 있는" 상태가 만들어질 수 있다. 그래서 참조 무결성 사전 검증이 중요하다.

### 1.2 인코딩이 파일마다 다르다

`sido`/`sigungu`/`industry`는 **UTF-8**, 나머지는 **MS949(CP949)** 로 읽는다. 리더에 하드코딩되어 있으므로 **파일을 그 인코딩에 맞춰 준비**해야 한다. 값이 전부 ASCII인 파일(예: `infra.csv`)은 어느 쪽이든 무해하다.

### 1.3 `level_middle` 파싱 주의

`JobCodeMiddleBatch`는 `delimited()`가 아니라 직접 `line.split(",")`을 쓰고 **이름에 콤마가 들어갈 수 있다**고 가정한다.

```java
dto.setName(String.join(",", Arrays.copyOfRange(values, 1, values.length - 1)));
dto.setUpstream(values[values.length - 1]);   // 항상 마지막 컬럼
```

→ 이름 안의 콤마는 허용된다. 단 **`upstream_code`는 반드시 마지막 컬럼**이어야 한다.

---

## 2. 기동 전 워크플로우

### 1) 환경변수 파일

```bash
cp backend.env.example backend.env      # 값을 채운다. backend.env는 커밋하지 않는다
```

`backend.env` 하나를 **backend와 mysql 두 서비스가 공유**한다. mysql 이미지용
(`MYSQL_ROOT_PASSWORD`/`MYSQL_DATABASE`/`MYSQL_USER`/`MYSQL_PASSWORD`)과
앱용(`MYSQL_DATA_URL`/`MYSQL_META_URL`/...)이 한 파일에 있으니 **양쪽 값이 서로 맞는지** 확인한다.

### 2) 파일 배치

`data/` 아래에 §1 표의 파일명으로 둔다. 다른 이름을 쓰려면 경로 환경변수를 그에 맞게 바꾼다.
새로 준비한 파일이 있으면 **`seed.jobs.<키>.enabled=true`로 켠다**(§0.2).

### 3) 검증

```bash
bash scripts/verify-seed.sh
```

점검 항목: 배치 활성 여부 · 파일 존재 · 헤더/컬럼 수 · 인코딩/BOM · 데이터 행 수 · **참조 무결성** ·
배치 소스와 스펙 드리프트 · 미사용 파일 · **DB 접속 설정**(드라이버/호스트/`BATCH_SCHEMA_INIT`) · compose 구성.

`exit 0`이면 적재 가능, `exit 1`이면 기동해도 실패하거나 비어서 적재된다.
비활성 배치는 `SKIP`으로 표시되고 실패로 치지 않는다.

경로 환경변수를 다시 뽑고 싶으면:

```bash
bash scripts/verify-seed.sh --emit-env
```

### 4) 기동

```bash
docker compose up --build
```

mysql/redis의 healthcheck가 통과한 뒤에 backend가 뜬다. **DB가 준비되기 전에 Seed 배치가 도는 일은 없다.**

### 5) 운영(prod) 배포 — Oracle Cloud

운영도 **컨테이너 DB**다(RDS 아님). dev와 파일만 다르고 절차는 같다.

```bash
cp backend.prod.env.example backend.prod.env    # 실제 비밀번호로 채운다
bash scripts/verify-seed.sh --prod              # prod 구성 점검
docker compose -f docker-compose.prod.yaml up -d --build
docker compose -f docker-compose.prod.yaml logs -f backend
```

| dev | prod |
|---|---|
| `backend.env` / `docker-compose.yaml` | `backend.prod.env` / `docker-compose.prod.yaml` |
| `dev.Dockerfile` (profile=dev) | `Dockerfile` (profile=prod) |
| mysql·redis 포트 호스트 노출 | **노출 안 함** (컨테이너 네트워크 내부만) |
| redis 휘발 | redis `appendonly yes` + 볼륨 |

**운영 주의**

- **비밀번호를 예제 기본값(`CHANGE_ME`)으로 두지 않는다.** `--prod` 점검이 FAIL로 잡는다.
- **`REDIS_SSL=false`** — 컨테이너 Redis는 TLS를 쓰지 않는다. `true`면 연결이 실패한다(과거 관리형 Redis 설정의 잔재).
- **최초 배포는 `BATCH_SCHEMA_INIT=always`**, 배치 메타 테이블이 생성된 뒤 재배포부터는 `never`로 낮춘다.
- **OCI Ampere A1은 arm64**다. 사용 이미지(`eclipse-temurin:17`, `mysql:8.0`, `redis:7`)는 모두 multi-arch라 **인스턴스에서 직접 빌드하면** 그대로 동작한다. 다른 머신에서 빌드해 올린다면 `--platform linux/arm64`로 맞춘다.
- **OCI는 인스턴스 iptables와 VCN 보안 목록 양쪽**을 열어야 외부에서 접속된다. 둘 중 하나만 열면 연결이 안 된다.
- `docker compose down -v`는 **운영 데이터를 지운다.** 운영에서는 쓰지 않는다. 재적재는 `SEED_VERSION`을 올리는 방식으로만 한다(§4).
- 볼륨 백업: `docker run --rm -v smash-prod_mysql-data:/var/lib/mysql -v $(pwd):/backup alpine tar czf /backup/mysql-$(date +%F).tar.gz /var/lib/mysql`

### 5) 사후 확인 — 로그

```bash
docker compose logs backend | grep -iE "Job:|COMPLETED|FAILED|Already|Skip"
```

- `Already <jobName> : <seedVersion>` → BatchGuard가 막았다. 재적재하려면 §4.
- `Status=COMPLETED` 인데 테이블이 비었다면 → **Processor가 전부 skip 했다.** 참조 무결성 문제다(§3-D).
- `FlatFileParseException` → 컬럼 수/구분자 문제(§3-B).

---

## 3. 실패 유형별 대응

| 증상 | 원인 | 대응 |
|---|---|---|
| **A. `FileNotFoundException` / `strict(true)` 실패** | 경로 환경변수가 호스트 경로이거나 파일 없음 | 환경변수를 **컨테이너 경로 `/app/data/...`** 로. `docker compose config`로 마운트 확인 |
| **B. `FlatFileParseException`, `IndexOutOfBounds`** | CSV 컬럼 수가 리더의 `names()`와 다름 | 헤더/컬럼을 §1 표에 맞춘다. 컬럼을 늘릴 수 없으면 배치 리더를 고쳐야 한다 |
| **C. 한글이 `???`/깨진 문자로 적재됨** | 파일 인코딩 ≠ 리더 `encoding()` | 파일을 해당 인코딩으로 변환: `iconv -f UTF-8 -t CP949 in.csv > out.csv` |
| **D. COMPLETED인데 테이블이 비어 있음** | Processor가 FK 미매칭으로 전부 skip | `verify-seed.sh`의 "2. 참조 무결성" 확인. 선행 배치(`@Order`가 작은 쪽)가 실제로 적재됐는지 DB에서 확인 |
| **E. 배치가 아예 실행되지 않음** | `BatchGuard`가 같은 `SEED_VERSION`의 COMPLETED 이력을 찾음 | §4 재적재 절차 |
| **F. `SEED_VERSION` 미설정** | `seed.version` 프로퍼티가 빈 값 | `backend.env`에 `SEED_VERSION` 추가. 없으면 재실행 방지가 동작하지 않는다 |
| **G. 앞 배치가 실패했는데 뒤 배치가 계속 실행됨** | Runner들이 서로 독립적(`@Order`는 순서만 보장) | 로그에서 **가장 작은 `@Order`의 실패부터** 해결한다. 뒤 실패는 대부분 파생 증상이다 |
| **H. 숫자 컬럼 파싱 실패** | 천 단위 콤마, 공백, BOM | 배치가 `normalize()`/`digitsOnly()`로 처리하지만, 값에 콤마가 있으면 컬럼이 밀린다. 따옴표 없이 콤마 없는 숫자로 준비 |
| **I. `Table 'smash_meta.BATCH_JOB_INSTANCE' doesn't exist`** | 새 meta 스키마에 배치 테이블이 없음 | `BATCH_SCHEMA_INIT=always` 로 두고 재기동 (§0.1) |
| **J. `Communications link failure` / `Connection refused`** | DB 호스트가 `localhost`이거나 컨테이너가 아직 안 뜸 | JDBC URL 호스트를 compose 서비스명 `mysql`로. `depends_on: condition: service_healthy` 확인 |
| **K. `Public Key Retrieval is not allowed`** | MySQL 8 기본 인증 + URL 옵션 누락 | URL에 `allowPublicKeyRetrieval=true&useSSL=false` 추가 |
| **L. 한글이 `???`로 저장됨(파일 인코딩은 맞는데)** | DB/커넥션 문자셋이 utf8mb4가 아님 | compose의 `--character-set-server=utf8mb4` 와 URL의 `characterEncoding=UTF-8` 확인 |
| **M. 배치가 아예 로그에 안 나옴** | `seed.jobs.<키>.enabled=false` 라 Runner 빈이 등록되지 않음 | 프로퍼티를 `true`로 (§0.2). BatchGuard skip과 달리 **로그조차 남지 않는다** |

### 3.1 DB에서 직접 확인

```bash
# 업무 데이터
docker compose exec mysql mysql -usmash -p smash_data
# 배치 이력
docker compose exec mysql mysql -usmash -p smash_meta
```

```sql
-- 선행 데이터 적재 여부 (smash_data)
SELECT COUNT(*) FROM Sido;  SELECT COUNT(*) FROM Sigungu;
SELECT COUNT(*) FROM Industry; SELECT COUNT(*) FROM JobCodeMiddle;

-- 배치 실행 이력 (meta DB)
SELECT ji.JOB_NAME, je.STATUS, je.START_TIME, jp.PARAMETER_VALUE AS seed_version
FROM BATCH_JOB_EXECUTION je
JOIN BATCH_JOB_INSTANCE ji  ON ji.JOB_INSTANCE_ID = je.JOB_INSTANCE_ID
LEFT JOIN BATCH_JOB_EXECUTION_PARAMS jp
       ON jp.JOB_EXECUTION_ID = je.JOB_EXECUTION_ID AND jp.PARAMETER_NAME = 'seedVersion'
ORDER BY je.START_TIME DESC;
```

배치 메타는 **meta DB**, 업무 데이터는 **data DB**다. 접속 대상을 혼동하지 않는다.

---

## 4. 재적재 (SEED_VERSION)

`BatchGuard.alreadyDone(jobName, seedVersion)`은 **같은 `jobName` + 같은 `seedVersion`으로 COMPLETED 이력이 있으면 skip** 한다.

**재적재 절차 (점진)**

1. `backend.env`의 `SEED_VERSION`을 올린다: `SEED_VERSION=2026-08-10-2`
2. 필요하면 대상 테이블을 비운다 — 대부분의 배치가 `ON DUPLICATE KEY UPDATE` Upsert라 **덮어쓰기만 하면 되는 경우가 많다.** 행을 지워야 하는 변경(삭제된 코드 정리 등)일 때만 비운다.
3. `docker compose up --build`

**완전 초기화 (DB를 통째로 버림)**

컨테이너 DB이므로 볼륨을 지우면 깨끗한 상태에서 다시 시작할 수 있다. 개발 중 가장 확실한 방법이다.

```bash
docker compose down -v          # mysql-data 볼륨까지 삭제
docker compose up --build
```

- `-v`를 빼면 볼륨이 남아 **이전 데이터와 배치 이력이 그대로**다.
- 볼륨을 지우면 `smash_meta`도 사라지므로 `docker/mysql/init/01-init-meta-db.sh`가 다시 실행되고,
  `BATCH_SCHEMA_INIT=always`가 배치 테이블을 다시 만든다. 이때 `SEED_VERSION`은 올리지 않아도 된다(이력이 없으므로).

**주의**
- `SEED_VERSION`을 올리면 **9개 배치가 전부 다시 돈다.** 하나만 다시 돌리는 장치는 없다. (`dwellingJob`은 외부 API를 12개월치 호출하므로 시간이 오래 걸린다.)
- `BatchGuard`는 최근 **20개 JobInstance**만 조회한다(`getJobInstances(jobName, 0, 20)`). 버전을 자주 올리면 오래된 이력이 조회 범위를 벗어나 **이미 돌린 버전을 다시 돌 수 있다.**
- 버전 문자열은 날짜 기반으로 단조 증가시킨다. 임의 문자열을 재사용하지 않는다.

---

## 5. 새 Seed 배치를 추가할 때

1. **배치 구현** — `persistence-conventions` §7 (Upsert Writer, `@Qualifier("dataDBSource")`, Processor에서 FK 검증 후 `null` skip)
2. **프로퍼티 추가** — `application-dev.properties` / `application-prod.properties` **양쪽에**
   - `<key>.filePath=${<ENV>_FILEPATH:}`
   - `seed.jobs.<케밥-키>.enabled=false` (파일이 준비되면 `true`)
3. **Runner 작성** — `@Component` + `@ConditionalOnProperty(name = "seed.jobs.<케밥-키>.enabled", havingValue = "true")` + `@EventListener(ApplicationReadyEvent)` + `@Order(n)`. **참조하는 테이블의 배치보다 큰 n**을 준다
4. **`BatchGuard` 호출** — Runner 시작부에서 `alreadyDone(jobName, SEED_VERSION)` 확인
5. **`scripts/verify-seed.sh`의 `SPECS`에 행 추가** — `order|propKey|envVar|파일명|인코딩|컬럼수|헤더정규식|seed.jobs키`
6. **필요하면 `FKS`에도 행 추가** — 참조 무결성 검사. 컬럼이 가변이면 음수 인덱스(`-1` = 마지막 컬럼) 사용
7. **`backend.env.example`에 경로 변수 추가**
8. `bash scripts/verify-seed.sh` 로 3번 섹션("배치 소스 대조")이 PASS인지 확인 — **스펙 테이블 갱신을 빠뜨리면 여기서 잡힌다**

---

## 6. 현재 상태 (2026-08-10 기준)

`bash scripts/verify-seed.sh` → **PASS 19 / WARN 5 / FAIL 0 (exit 0)**
활성 배치 4개는 기동 가능하고, 나머지 5개는 `seed.jobs.*.enabled=false`로 꺼져 있어 기동을 막지 않는다.

| 배치 | 상태 |
|---|---|
| Sido / Sigungu / JobCodeTop / JobCodeMiddle | ✅ 활성. 스펙 일치, FK 전부 매칭 |
| Population / InfraScore / JobCount | ⏸ 비활성 — **소스 CSV 없음** |
| Industry | ⏸ 비활성 — 소스 CSV 없음. `infra`의 FK 대상이라 **먼저 준비해야 한다** |
| Infra | ⏸ 비활성 — 파일은 있으나 **컬럼 3개**(`sigungu_code,opnSvcId,num`), 배치는 **4개**(`sigungu_code,industry_code,count,ratio`) 요구 |

**남은 작업 순서** (의존성 때문에 이 순서를 지킨다)

1. **`industry.csv`** 준비 — `infra.csv`의 `industry_code` FK 대상. 헤더 `code,name,major`, **UTF-8**, `major`는 `HEALTH|FOOD|CULTURE|LIFE` 중 하나
2. **`infra.csv` 정합** — `ratio` 컬럼을 추가하고 `opnSvcId`→`industry_code`, `num`→`count`로 헤더 정정
   - 파일을 바꿀 수 없다면 `InfraBatch`의 `names(...)`/`fieldSetMapper`를 3컬럼으로 수정. 단 `Infra.ratio`가 `nullable = false`라 엔티티도 함께 조정해야 한다
3. **`population.csv`, `infra_score.csv`, `job_count.csv`** 준비
4. 준비된 배치의 **`seed.jobs.<키>.enabled=true`** 로 전환 (dev/prod 프로퍼티 양쪽)
5. `bash scripts/verify-seed.sh` 가 exit 0 인지 확인 후 기동

> `InfraBatch.fieldSetMapper`의 지역변수명이 `rawInfraName`인데 실제로는 `count` 위치(`readString(2)`)를 읽는다. 2번 항목을 손볼 때 함께 정리한다.

> `InfraBatch.fieldSetMapper`의 지역변수명이 `rawInfraName`인데 실제로는 `count` 위치(`readString(2)`)를 읽는다. 3번 항목을 손볼 때 함께 정리한다.

---

## 7. 체크리스트

**기동 전**
- [ ] `backend.env`가 있는가 (`cp backend.env.example backend.env`)
- [ ] `DRIVER=com.mysql.cj.jdbc.Driver` 인가 (AWS JDBC Wrapper 아님)
- [ ] JDBC URL 호스트가 **`mysql`** 인가 (`localhost` 아님)
- [ ] **`BATCH_SCHEMA_INIT=always`** 인가 (새 meta 스키마일 때)
- [ ] mysql 이미지용 변수 4개(`MYSQL_ROOT_PASSWORD`/`MYSQL_DATABASE`/`MYSQL_USER`/`MYSQL_PASSWORD`)가 앱용 값과 일치하는가
- [ ] 활성 배치의 CSV가 `data/`에 있는가
- [ ] 준비한 파일의 `seed.jobs.<키>.enabled=true` 를 켰는가
- [ ] `backend.env`에 `SEED_VERSION`이 있는가 / 재적재라면 올렸는가
- [ ] `bash scripts/verify-seed.sh` 가 exit 0 인가

**기동 후**
- [ ] 로그에 `FAILED`가 없는가
- [ ] 켠 배치가 실제로 로그에 나타나는가 (안 나오면 `enabled` 플래그를 의심)
- [ ] `Already <job>` 로그가 의도한 것인가 (재적재하려던 게 아닌가)
- [ ] 주요 테이블 건수가 CSV 행 수와 맞는가 (많이 적으면 Processor skip을 의심)

**배치 추가 시**
- [ ] `@Order`가 선행 배치보다 큰가
- [ ] `@ConditionalOnProperty(seed.jobs.<키>.enabled)` 를 붙였는가
- [ ] `BatchGuard`를 호출하는가
- [ ] dev/prod 프로퍼티 양쪽에 `filePath`와 `seed.jobs.<키>.enabled`를 추가했는가
- [ ] `scripts/verify-seed.sh`의 `SPECS`(+필요시 `FKS`)와 `backend.env.example`을 갱신했는가
