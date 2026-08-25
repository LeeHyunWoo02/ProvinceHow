# 관측성 (Actuator · Prometheus · Grafana)

서비스의 트래픽·지연·에러율과 런타임 상태를 수집·시각화하는 구성이다.
대상 서버는 **OCI Ampere A1 1 OCPU / 6GB** 단일 인스턴스이고, 모든 설정값은 그 제약을 전제로 정했다.

---

## 1. 구성

```
                 인터넷
                    │  443
              ┌─────▼─────┐
              │   caddy   │  api.jibang.info      → backend:8080
              │           │  grafana.jibang.info  → grafana:3000
              └─────┬─────┘
        ┌───────────┴───────────┐
        │                       │
  ┌─────▼──────┐         ┌──────▼──────┐      ┌────────────┐
  │  backend   │◀────────│ prometheus  │◀─────│  grafana   │
  │ 8080 서비스 │  scrape │   :9090     │query │   :3000    │
  │ 8081 관리   │  30s    │  (미개방)    │      │  (미개방)   │
  └────────────┘         └─────────────┘      └────────────┘
```

포트 정책이 곧 접근 통제다.

| 컨테이너 | publish | 외부에서 도달 가능한가 |
|---|---|---|
| `backend` 8080 | `127.0.0.1:8080` | Caddy 경유만 (`api.jibang.info`) |
| `backend` 8081 (`/actuator`) | **없음** | 불가 — compose 내부 네트워크뿐 |
| `prometheus` 9090 | **없음** | 불가 |
| `grafana` 3000 | **없음** | Caddy 경유만 (`grafana.jibang.info`) |

> ⚠️ 관리 포트를 분리했어도 **앱의 `SecurityFilterChain` 은 관리 포트에도 적용된다.**
> 허용 규칙이 없으면 Prometheus 스크랩이 403 으로 막힌다. 그래서 `SecurityConfig` 에
> `/actuator/health`, `/actuator/prometheus` 를 명시적으로 `permitAll()` 해 두었다.
> `EndpointRequest.toAnyEndpoint()` 는 쓸 수 없다 — 관리 포트를 분리하면 `PathMappedEndpoints`
> 빈이 자식(관리) 컨텍스트에만 생겨서 메인 컨텍스트의 매처가 항상 "무시"로 빠진다.
> 이 계약은 `ActuatorPrometheusIntegrationTest` 가 검증한다.

---

## 2. 파일

| 파일 | 역할 |
|---|---|
| `src/main/resources/application-{prod,dev}.properties` | 관리 포트, 노출 엔드포인트, 히스토그램 버킷 범위 |
| `docker/prometheus/prometheus.yml` | 스크랩 대상과 주기 |
| `docker/grafana/provisioning/datasources/prometheus.yml` | 데이터소스 (uid=`prometheus`) |
| `docker/grafana/provisioning/dashboards/dashboards.yml` | 대시보드 프로비저닝 경로 |
| `docker/grafana/dashboards/smash-api.json` | 대시보드 정본 |
| `docker/caddy/Caddyfile` | `grafana.jibang.info` 사이트 블록 |
| `docker-compose.deploy.yaml` | 서버용. 포트 미개방 + 메모리/CPU 상한 |
| `docker-compose.yaml` | 로컬용. 9090/3000 을 직접 열어 확인 |

**대시보드의 정본은 레포다.** `allowUiUpdates: false` 라서 Grafana UI 에서 고친 내용은
컨테이너를 다시 만들면 파일 내용으로 되돌아간다. 패널을 바꾸려면 JSON 을 고쳐 커밋한다.

---

## 3. 수집하는 것

![Grafana 대시보드](images/grafana-dashboard.png)

위 캡처는 로컬 검증 스택이다. 시드 데이터 없이 트래픽만 발생시킨 상태라 캐시는 전부 미스(0%)이고,
외부 API 패널은 비어 있다 — 검증 때문에 일일 호출 한도를 태우지 않으려고 수집 배치를 껐기 때문이다.


Actuator 가 기본 제공하는 것만으로 대시보드 12개 패널이 채워진다.

| 메트릭 | 패널 | 비고 |
|---|---|---|
| `http_server_requests_seconds_*` | 요청률, p95, 상태코드별, 엔드포인트별 | 히스토그램 버킷은 **10ms~5s** 로 제한 (카디널리티 절약) |
| `jvm_memory_*`, `jvm_gc_pause_seconds` | 힙 사용률, 힙 추이, 초당 GC 정지 | 컨테이너는 `MaxRAMPercentage=40` |
| `hikaricp_connections_*` | 커넥션풀 | `pool` 태그 = `smash-data` / `smash-meta` |
| `tomcat_threads_*` | Tomcat 스레드 | `server.tomcat.mbeanregistry.enabled=true` 필요 |
| `system_cpu_usage`, `process_cpu_usage` | CPU | 1 OCPU 이므로 1.0 이 상한 |

### 3.1 커스텀 메트릭 (`SDD.smash.global.metrics`)

Actuator 가 주지 않는, 이 서비스만의 값이다. 계측기는 `global/metrics` 에 있고 호출은
`infrastructure` 어댑터에서만 한다 — `domain`/`application` 은 계측기를 알지 못한다.

| 메트릭 | 태그 | 어디서 세는가 |
|---|---|---|
| `smash_cache_lookups_total` | `cache`, `result`=hit\|miss\|error | 6개 Redis 캐시 어댑터의 `find()` |
| `smash_external_api_calls_total` | `api`, `outcome`=success\|failure\|skipped | 수집원 5곳의 HTTP 호출 지점 |
| `smash_external_api_budget_used` / `_limit` | `api` | `LocalDataApiAdapter` 의 일일 예산 계량기 |

설계상 정한 것

- **`result=error` 를 `miss` 와 분리한다.** 캐시 어댑터는 Redis 장애를 미스로 흡수해 응답을
  살린다. 구분하지 않으면 "Redis 가 죽어서 미스"와 "캐시가 비어서 미스"가 같은 선으로 보인다.
- **`outcome=skipped` 를 `failure` 와 분리한다.** API 키가 없으면 호출조차 하지 않는다.
  이를 실패로 세면 "서버가 죽었다"와 "우리가 키를 안 넣었다"가 같은 그래프가 된다.
- **태그 키는 메트릭마다 고정한다.** 실패 사유별 태그를 추가하면 같은 메트릭 이름에 태그 키가
  갈려 Micrometer 가 Prometheus 노출을 거부한다. 사유는 기존 `log.warn` 이 남긴다
  (`CacheMetricsTest`/`ExternalApiMetricsTest` 가 이 계약을 검증한다).
- **`api` 태그는 어댑터가 아니라 수집원 단위다.** 사람인은 어댑터가 3개(사용자 채용카드,
  지역 프로필, JobCount 배치)지만 일일 한도 500회는 셋이 공유하므로 `api="saramin"` 하나다.
- **호출 1회 = 계측 1건.** 재시도는 별개의 HTTP 호출이므로 시도마다 센다. LOCALDATA 는
  예산도 시도 단위로 깎이므로 계측과 예산 소모가 일치한다.

### 아직 없는 것

- **레이트리밋 차단율** — `ApiRateLimitFilter` 가 `SecurityConfig` 에서 **주석 처리돼 등록되지 않은 상태**다.
  필터를 살리는 것은 관측성과 별개의 결정이므로 건드리지 않았다. 그래서 대시보드의
  "상태코드별 요청률" 패널에 429 는 나타나지 않는다.
- **사람인 예산 게이지** — 사람인은 배치 실행 안에서만 호출 수를 세고 일일 누계를 들고 있지 않아
  게이지를 붙일 상태가 없다. 대신 "최근 24시간 외부 API 호출 수" 패널의
  `increase(smash_external_api_calls_total{api="saramin"}[24h])` 로 500회/일 한도와 대조한다.
- **MySQL 사용자 이벤트 로그** — 추천 입력 조건 분포 분석용. Prometheus 가 대체하지 못하는
  고카디널리티 원본이라 별도 축으로 남아 있다.

---

## 4. 리소스 설정 근거 (1 OCPU / 6GB)

| 설정 | 값 | 이유 |
|---|---|---|
| `scrape_interval` | 30s | 기본 15s 는 1코어에서 과하다. 대시보드 해상도는 30s 로 충분 |
| `--storage.tsdb.retention.time` | 15d | 추세 판단에 2주면 된다 |
| `--storage.tsdb.retention.size` | 512MB | 디스크 폭주 방지. 둘 중 먼저 걸리는 쪽이 블록을 지운다 |
| prometheus `mem_limit` / `cpus` | 384~512m / 0.5 | 상한이 없으면 TSDB 컴팩션이 백엔드의 CPU 를 빼앗는다 |
| grafana `mem_limit` / `cpus` | 384m / 0.5 | 플러그인 설치·업데이트 확인·리포팅을 모두 끈 상태 기준 |
| 히스토그램 버킷 | 10ms~5s | 버킷 수가 곧 시계열 수다. 전 구간을 다 두면 낭비 |

부하가 늘어 CPU 가 포화되면 **스크랩 주기(30s → 60s)를 먼저** 늘린다.

---

## 5. 배포 후 확인 절차

```bash
# 1) 컨테이너 상태
docker compose -f docker-compose.deploy.yaml ps

# 2) 관리 포트가 외부에 열려 있지 않은지 (반드시 실패해야 한다)
curl -sS -m 5 http://<서버공인IP>:8081/actuator/prometheus ; echo "exit=$?"
curl -sS -m 5 https://api.jibang.info/actuator/prometheus   # 404 여야 한다

# 3) prometheus 컨테이너 안에서는 스크랩이 되는지 (200 + 메트릭 본문)
docker compose -f docker-compose.deploy.yaml exec prometheus \
  wget -qO- http://backend:8081/actuator/prometheus | head -5

# 4) 스크랩 타깃이 UP 인지
docker compose -f docker-compose.deploy.yaml exec prometheus \
  wget -qO- 'http://localhost:9090/api/v1/targets?state=active' | head -c 400

# 5) Grafana
#    https://grafana.jibang.info 로그인 → 폴더 smash → "smash · API 트래픽과 런타임"
```

`grafana.jibang.info` 의 A 레코드가 없으면 Caddy 가 그 사이트의 인증서 발급만 반복 실패한다
(`api.jibang.info` 는 영향 없음). Caddy 로그에서 ACME 실패가 보이면 DNS 를 먼저 확인한다.

> ⚠️ **`GF_SECURITY_ADMIN_PASSWORD` 는 첫 기동에만 적용된다.** Grafana 는 첫 기동 때
> 관리자 계정을 자기 SQLite DB(`grafana-data` 볼륨)에 만들고, 그 뒤로는 이 환경변수를 무시한다.
> 실측(grafana 13.1.4): 값을 바꿔 재기동하면 **새 비밀번호는 401, 옛 비밀번호가 그대로 200**.
>
> 그래서 **Grafana 를 처음 띄우는 배포 전에** 서버 `backend.env` 에 값을 채워야 한다.
> 비운 채로 한 번 뜨면 `admin/admin` 으로 고정되고, 이후 바꾸려면 컨테이너 안에서
> `grafana cli admin reset-admin-password <새비번>` 을 실행해야 한다
> (볼륨을 지우는 방법은 대시보드 주석·사용자까지 함께 날아가므로 쓰지 않는다).

---

## 6. 로컬에서 보기

```bash
docker compose up -d            # backend + mysql + redis + prometheus + grafana
# Prometheus  http://localhost:9090
# Grafana     http://localhost:3001  (admin / admin)  ← 3000 은 프론트 개발 서버와 충돌
```

로컬에는 Caddy 가 없어 두 포트를 직접 열어 둔다. 서버용 compose 에는 열지 않는다.
