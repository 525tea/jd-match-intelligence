# Search API Saturation Tuning 200k 1000RPS Report

## 목적

이 테스트는 Elasticsearch + Redis cache 적용 후 검색 API의 안정 처리 상한과 다음 병목 지점을 확인하기 위해 수행했다.

기존 500VU ramp 테스트는 전체 23분 평균 RPS와 latency를 확인하는 데 적합했지만, 특정 목표 RPS에서 backend가 어느 지점부터 latency cliff에 진입하는지는 분리해서 보기 어려웠다. 이번 작업은 k6 constant-arrival-rate workload로 목표 RPS를 고정하고, Tomcat/Hikari/transaction/tracing 설정을 조정하며 안정 처리 가능한 구간과 포화 구간을 구분한다.

## 측정 환경

| 항목 | 값 |
|---|---|
| 인스턴스 | m5.xlarge (4 vCPU, 16GB RAM) |
| k6 위치 | EC2 내부, backend 직접 호출 (`localhost:8080`) |
| fixture | `jobflow_perf.jobs` 200,000건 (`source='perf_fixture'`) |
| ES 인덱스 | `jobflow-jobs-performance` (200k docs) |
| 캐시 | `CACHE_ENABLED=true`, Redis `jobSearch` cache |
| 대상 API | `GET /jobs/search?keyword=...&limit=10` |
| workload | constant-arrival-rate, 120초 |
| 튜닝 범위 | Tomcat thread/connection, Hikari pool, search transaction boundary, tracing sampling, security session policy |

테스트는 backend/gateway/mysql/redis/elasticsearch/kafka/prometheus/grafana와 k6가 같은 EC2 안에서 실행되는 조건이다. 따라서 결과는 단일 호스트에서 관측한 성능 기준선이며, k6 generator와 application stack이 CPU를 공유한다.

## 변경 요약

| 구분 | 변경 | 의도 |
|---|---|---|
| Tomcat | `max-threads`, `accept-count`, `max-connections` 성능 프로파일 환경변수화 | 높은 동시 요청에서 servlet thread/connection queue가 먼저 막히지 않도록 조정 |
| HikariCP | `maximum-pool-size`, `minimum-idle` 성능 프로파일 환경변수화 | cache hit workload에서도 transaction boundary 때문에 발생하는 DB connection 대기 완화 |
| Transaction | `JobService.searchJobs`에 `Propagation.NOT_SUPPORTED` 적용 | cache hit 검색 응답 매핑 경로가 class-level read-only transaction을 열지 않도록 분리 |
| Tracing | 성능 프로파일에서 tracing sampling probability를 `0.0`으로 설정 가능하게 변경 | saturation 측정에서 Zipkin span export 비용과 drop noise 제거 |
| Security | API security session policy를 `STATELESS`로 변경 | JWT 기반 API 요청에서 servlet session 생성 비용과 session metric noise 제거 |
| Guard | performance ops regression guard에 saturation 관련 기본값 검증 추가 | 운영 성능 프로파일 설정이 다시 빠지는 회귀 방지 |

## k6 Summary 비교

| 단계 | 목표 RPS | 실제 RPS | 총 요청 | dropped iterations | p50 | p90 | p95 | p99 | max | 판단 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| tuning 전 | 1000 | 536.71 | 66,907 | 53,094 | 7,038ms | 12,112ms | 12,968ms | 13,916ms | 15,468ms | 실패 |
| Tomcat/Hikari 조정 후 | 1000 | 540.72 | 67,877 | 52,124 | 6,750ms | 12,157ms | 12,835ms | 14,159ms | 18,261ms | 실패 |
| transaction 조정 후 | 1000 | 634.07 | 78,835 | 41,166 | 5,178ms | 9,806ms | 10,789ms | 15,084ms | 18,940ms | 실패 |
| tracing off 후 | 1000 | 693.98 | 86,156 | 33,844 | 5,159ms | 7,862ms | 9,783ms | 12,403ms | 15,487ms | 실패 |
| clean probe | 900 | 899.97 | 108,001 | 0 | 2.66ms | 69.45ms | 120.47ms | 320.15ms | 1,652ms | 안정 |
| clean probe | 950 | 949.96 | 114,000 | 0 | 5.50ms | 156.77ms | 275.88ms | 1,351ms | 3,826ms | 안정 |
| clean probe | 1000 | 999.93 | 120,001 | 0 | 11.36ms | 154.15ms | 280.03ms | 1,773ms | 3,935ms | 안정 |
| clean probe | 1100 | 1093.90 | 132,000 | 0 | 276.96ms | 1,180ms | 2,679ms | 4,703ms | 8,959ms | 경계 |
| clean probe | 1200 | 984.48 | 125,116 | 18,885 | 6,550ms | 9,512ms | 10,587ms | 12,485ms | 21,408ms | 포화 |
| Tomcat 700 probe | 1100 | 665.21 | 85,874 | 46,126 | 10,843ms | 14,668ms | 17,417ms | 21,019ms | 23,760ms | 실패 |
| stateless probe | 1050 | 691.76 | 87,520 | 38,481 | 8,011ms | 10,827ms | 12,484ms | 15,326ms | 23,490ms | 실패 |

HTTP failure rate는 모든 실행에서 0%였다. 다만 saturation 판단은 실패율만으로 하지 않고, 목표 arrival rate 달성 여부, dropped iterations, p95/p99 latency를 함께 본다.

초기 1000RPS 측정은 포화 상태와 관측 부하가 섞여 실제 처리량이 536~694RPS에 머물렀다. 이후 clean probe에서는 background monitor를 정리하고 동일 조건을 재측정해 1000RPS 안정 처리를 확인했다. 따라서 최종 기준선은 clean probe 결과를 따른다.

1100RPS는 dropped iterations 없이 목표 arrival rate에는 도달했지만 p95가 2.68s까지 상승했다. 따라서 안정 처리 구간이 아니라 saturation 직전 경계 구간으로 분류한다. Tomcat thread를 700으로 늘린 실험은 처리량과 latency가 모두 악화되어, 단순 thread 증설이 현재 병목 해법이 아님을 확인했다.

## Bottleneck Probe 결과

| 지표 | 1000RPS clean probe | 1200RPS clean probe | 해석 |
|---|---:|---:|---|
| 실제 RPS | 999.93 | 984.48 | 1200RPS에서는 목표 arrival rate 미달 |
| dropped iterations | 0 | 18,885 | 1200RPS에서 k6가 예정된 요청 시작을 따라가지 못함 |
| p95 latency | 280.03ms | 10,586.75ms | 1200RPS에서 latency cliff 발생 |
| p99 latency | 1,773.33ms | 12,484.52ms | tail latency가 초 단위로 악화 |
| Hikari active max | 30 / 100 | 27 / 100 | connection pool이 max에 붙지 않음 |
| Hikari pending max | 0 | 0 | DB connection 대기열이 포화 원인은 아님 |
| Hikari timeout total max | 0 | 0 | connection timeout 없음 |
| jobSearch miss delta | 11 | 392 | cache miss는 낮은 비율이며 주 병목으로 보기 어려움 |
| active HTTP requests max | 500 | 500 | servlet 처리 동시성이 max thread 설정과 같은 500까지 도달 |
| backend container CPU max | 241.55% | 240.44% | backend가 4 vCPU 중 약 2.4 core까지 사용 |
| system CPU usage max | 0.989 | 1.0 | host 전체 CPU가 포화권에 진입 |
| loadavg max | 18.22 | 41.58 | 1200RPS에서 runnable/blocked 작업이 급증 |

추가 probe에서는 1100RPS가 dropped iterations 없이 실행됐지만 p95가 2.68s로 상승했다. 같은 1100RPS에서 `SERVER_TOMCAT_THREADS_MAX=700`으로 올리면 실제 처리량이 665.21RPS까지 떨어지고 p95가 17.42s로 악화됐다. 이는 현재 한계가 단순 thread 수 부족이 아니라, host CPU queueing과 request 처리 비용이 함께 나타나는 saturation 패턴이라는 근거다.

## 주요 관찰

### 1. 최종 안정 기준선은 1000RPS다

clean probe 기준 1000RPS는 실제 999.93RPS로 목표를 달성했고, dropped iterations는 0이었다. p95 latency는 280.03ms, p99 latency는 1.77s였다.

따라서 이번 작업의 최종 결론은 900RPS가 아니라, 단일 m5.xlarge 통합 스택에서 `/jobs/search` cache-hit workload를 1000RPS까지 안정 처리했다는 것이다.

### 2. 1200RPS에서 포화가 명확히 발생했다

1200RPS에서는 실제 처리량이 984.48RPS에 머물렀고, dropped iterations가 18,885건 발생했다. p95 latency는 10.59s, p99 latency는 12.48s로 상승했다. k6 VU도 max 8000까지 올라갔다.

이는 애플리케이션이 5xx를 반환한 실패가 아니라, 요청을 처리하는 시간이 길어져 k6가 목표 arrival rate를 유지하기 위해 필요한 실행 컨텍스트를 계속 늘리다가 한계에 도달한 패턴이다.

### 3. HikariCP pool exhaustion은 1차 병목이 아니다

Grafana 캡처에서는 HikariCP 패널이 눈에 띄었지만, clean probe 원시 metric 기준으로는 HikariCP가 1차 병목이라고 보기 어렵다.

1000RPS에서 Hikari active max는 30/100, pending max는 0이었다. 1200RPS에서도 Hikari active max는 27/100, pending max는 0, timeout total은 0이었다. `jobSearch` cache miss도 낮은 비율이었다.

따라서 이번 포화는 DB connection pool 고갈보다 servlet request concurrency, backend CPU, host CPU queueing 쪽에 가깝다.

### 4. 다음 병목 후보는 servlet worker/CPU queueing이다

1200RPS에서 `http_server_requests_active_seconds_gcount`가 500까지 올라갔다. 이 값은 성능 프로파일의 `SERVER_TOMCAT_THREADS_MAX=500`과 일치한다. 같은 구간에서 host system CPU usage는 1.0, loadavg는 41.58까지 상승했다.

따라서 다음 개선은 Hikari pool을 단순히 늘리는 방향이 아니라, 다음 항목을 우선 검증해야 한다.

- k6 load generator를 별도 host로 분리해 application stack CPU와 부하 발생 CPU를 분리
- backend와 MySQL/Redis/Elasticsearch/Kafka를 분리해 단일 host CPU 경합 제거
- `/jobs/search` response payload size, JSON serialization, DTO mapping 비용 측정
- cache value payload를 더 작게 저장하거나 search summary 전용 cache를 분리
- Tomcat thread 수를 더 늘리기 전에 CPU 사용률과 context switching 비용 확인

### 5. Tomcat thread 증설은 현재 조건에서 해법이 아니었다

1100RPS 조건에서 Tomcat max thread를 500에서 700으로 올린 실험은 오히려 처리량과 tail latency를 악화시켰다. 500-thread 조건에서는 실제 1093.90RPS까지 도달했지만 p95가 2.68s로 상승했고, 700-thread 조건에서는 실제 665.21RPS, p95 17.42s, dropped iterations 46,126건으로 악화됐다.

따라서 다음 개선은 Tomcat thread를 더 키우는 방식이 아니라, 요청 1건당 CPU 비용을 낮추거나 load generator/backend/Redis/Elasticsearch를 분리해 host CPU 경합을 줄이는 방향이어야 한다.

### 6. Stateless session 전환은 올바른 운영 설정이지만 단독 RPS 해법은 아니었다

JWT 기반 API는 서버 세션을 만들 필요가 없으므로 security session policy를 `STATELESS`로 전환했다. 전환 후 `tomcat_sessions_created_sessions_total`이 0으로 유지되어 검색 요청이 servlet session을 생성하지 않는 것을 확인했다.

다만 stateless 전환 후 1050RPS probe는 실제 691.76RPS, dropped iterations 38,481건, p95 12.48s로 실패했다. 즉 session 생성 noise는 제거됐지만, 1000RPS 이후의 주 병목은 session 자체가 아니라 CPU/worker queueing 및 cache-hit 응답 처리 비용 쪽에 남아 있다.

### 7. Transaction boundary 조정은 병목 완화에 기여했다

`JobService`는 class-level read-only transaction을 사용하고 있었다. 이 상태에서는 cache hit 검색 요청도 service method 진입 시 transaction boundary를 열 수 있어, Redis cache hit workload에서도 Hikari connection 관련 압력이 관측됐다.

`searchJobs`에 `Propagation.NOT_SUPPORTED`를 적용해 검색 응답 매핑 경로를 transaction 밖에서 실행하도록 분리했다. 이 변경 후 1000RPS 시도에서 실제 처리량은 약 634RPS까지 증가했고, 이후 tracing noise 제거와 clean probe를 통해 1000RPS 안정 구간을 확인할 수 있었다.

### 8. Tracing은 saturation 측정에서 별도 변수로 분리했다

Saturation test 중 Zipkin span export drop과 관측 부하가 함께 발생했다. 이번 작업에서는 성능 프로파일에서 `PERF_TRACING_SAMPLING_PROBABILITY=0.0`을 명시할 수 있게 하고, saturation 측정에서는 tracing sampling을 비활성화했다.

이는 tracing을 제거한다는 의미가 아니라, 검색 API 처리 상한 측정에서 observability pipeline 비용을 별도 변수로 분리한다는 의미다. tracing overhead 자체는 별도 관측 부하 테스트에서 다루는 것이 맞다.

## Grafana 관찰

| 항목 | 관찰 |
|---|---|
| 1000RPS 성공 구간 | RPS가 약 1000 req/s 근처에서 유지되고 error rate 0% |
| 1000RPS latency | p95 280.03ms, p99 1.77s |
| cache hit rate | `jobSearch` hit rate가 거의 100% 근처 유지 |
| 1100RPS 경계 구간 | dropped iterations는 없지만 p95가 2.68s까지 상승 |
| 1200RPS 구간 | 실제 처리량이 약 984RPS에 머물고 dropped iterations와 초 단위 latency 발생 |
| HikariCP | active/pending 기준으로 pool exhaustion 아님 |
| HTTP active requests | 1200RPS 포화 구간에서 active request가 500까지 증가 |
| ES indexing rate | 0, 조회 전용 테스트 확인 |

## 결론

이번 작업의 결론은 다음과 같다.

- 단일 m5.xlarge 통합 성능 스택에서 Elasticsearch + Redis cache 기반 `/jobs/search`는 1000RPS까지 안정 처리했다.
- 1000RPS 조건에서 dropped iterations 0, HTTP failure rate 0%, p95 280.03ms를 확인했다.
- 1100RPS는 dropped iterations 없이 도달했지만 p95 2.68s로 상승해 안정 구간이 아니라 saturation 직전 경계 구간으로 분류한다.
- 1200RPS에서는 실제 처리량이 984.48RPS에 머물고 dropped iterations 18,885건, p95 10.59s가 발생했다.
- 원시 metric 기준으로 HikariCP pool exhaustion은 1차 병목이 아니다.
- Tomcat thread 증설과 stateless session 전환만으로는 1000RPS 이후의 병목을 해소하지 못했다.
- 다음 병목 후보는 servlet worker 동시성, CPU/host queueing, cache-hit 응답 처리 비용이다.

## 다음 성능 목표

현재 1000RPS는 단일 m5.xlarge 안에서 application stack과 k6 load generator를 함께 실행한 조건의 안정 기준선이다. 이후 성능 개선은 같은 조건에서 Hikari pool만 늘리는 방식이 아니라, 부하 발생과 서비스 실행 환경을 분리한 뒤 더 높은 RPS 목표를 검증하는 방향으로 진행한다.

| 단계 | 목표 | 완료 기준 | 주요 확인 항목 |
|---|---|---|---|
| Load generator 분리 | 외부 k6 또는 별도 EC2에서 동일 workload 재측정 | 1000RPS 재현, dropped iterations 0, p95 300ms 이하 | 기존 결과가 k6/application CPU 공유 영향을 받는지 분리 |
| CPU hotspot 측정 | JFR 또는 async profiler로 cache-hit search path 측정 | p95 상승 구간의 상위 CPU stack 식별 | Redis deserialize, JSON serialization, DTO mapping, security filter |
| Cache-hit path 최적화 | 응답 매핑/직렬화 비용 축소 | 1100RPS p95 500ms 이하, dropped iterations 0 | final response cache, cache serializer, payload size |
| 단일 backend 안정 목표 | 1,500RPS 안정 처리 | HTTP failure rate 0.1% 이하, dropped iterations 0, p95 500ms 이하, p99 1.5s 이하 | CPU, servlet worker, Redis round-trip, response serialization |
| 포화 지점 재탐색 | 1,500RPS 이후 250RPS 단위로 증가 | 최초 dropped iterations 또는 p95 2s 초과 지점 식별 | 1,750/2,000/2,250RPS 구간별 latency cliff |
| 구조 개선 목표 | 3,000RPS 안정 처리 | backend/Redis/ES/k6 분리 또는 backend replica 조건에서 p95 500ms 이하, p99 1.5s 이하 | scale-out 효과, Redis 병목, gateway 경유 비용 |

다음 작업의 1차 목표는 load generator를 분리한 상태에서 1000RPS를 재현하고, JFR로 cache-hit search path의 CPU hotspot을 확인하는 것이다. 그 다음 1100RPS tail latency를 먼저 낮춘 뒤 1,500RPS 안정 처리 여부를 확인한다. 1,500RPS에서 tail latency가 안정되면 250RPS 단위로 목표를 높여 실제 saturation cliff를 다시 찾는다. 3,000RPS는 단일 통합 EC2 기준이 아니라, backend와 부하 발생기를 분리하고 필요하면 backend replica 또는 인스턴스 스펙 조정을 포함한 구조 개선 목표로 둔다.

## 산출물

| 파일 | 경로 |
|---|---|
| 1000RPS 초기 실패 JSON | `jobflow-server-env/artifacts/260630_search_saturation_1000rps/260630_k6_es_cache_saturation_before_200k_1000rps.json` |
| 1000RPS transaction 조정 JSON | `jobflow-server-env/artifacts/260630_search_saturation_1000rps/260630_k6_es_cache_saturation_txfix_200k_1000rps.json` |
| 1000RPS tracing off JSON | `jobflow-server-env/artifacts/260630_search_saturation_1000rps/260630_k6_es_cache_saturation_txfix_traceoff_200k_1000rps.json` |
| 1000RPS clean probe JSON | `jobflow-server-env/artifacts/260630_search_saturation_bottleneck_probe/clean_1000/260630_k6_es_cache_saturation_bottleneck_clean_1000rps.json` |
| 1000RPS clean probe metrics | `jobflow-server-env/artifacts/260630_search_saturation_bottleneck_probe/clean_1000/metrics_1000rps_during.prom.log` |
| 1100RPS Tomcat 500 probe JSON | `jobflow-server-env/artifacts/260630_search_saturation_bottleneck_probe/clean_1100_tomcat500/260630_k6_es_cache_saturation_clean_tomcat500_1100rps.json` |
| 1100RPS Tomcat 500 probe metrics | `jobflow-server-env/artifacts/260630_search_saturation_bottleneck_probe/clean_1100_tomcat500/metrics_1100rps_during.prom.log` |
| 1100RPS Tomcat 700 probe JSON | `jobflow-server-env/artifacts/260630_search_saturation_bottleneck_probe/clean_1100_tomcat700/260630_k6_es_cache_saturation_clean_tomcat700_1100rps.json` |
| 1100RPS Tomcat 700 probe metrics | `jobflow-server-env/artifacts/260630_search_saturation_bottleneck_probe/clean_1100_tomcat700/metrics_1100rps_during.prom.log` |
| 1050RPS stateless probe JSON | `jobflow-server-env/artifacts/260630_search_saturation_bottleneck_probe/stateless_1050/260630_k6_es_cache_saturation_stateless_1050rps.json` |
| 1050RPS stateless probe metrics | `jobflow-server-env/artifacts/260630_search_saturation_bottleneck_probe/stateless_1050/metrics_1050rps_during.prom.log` |
| 1200RPS clean probe JSON | `jobflow-server-env/artifacts/260630_search_saturation_bottleneck_probe/clean_1200/260630_k6_es_cache_saturation_bottleneck_clean_1200rps.json` |
| 1200RPS clean probe metrics | `jobflow-server-env/artifacts/260630_search_saturation_bottleneck_probe/clean_1200/metrics_1200rps_during.prom.log` |
| tuning 전 host snapshot | `jobflow-server-env/artifacts/260630_search_saturation_1000rps/260630_before_saturation_tuning.txt` |
| tuning 후 host snapshot | `jobflow-server-env/artifacts/260630_search_saturation_1000rps/260630_after_saturation_tuning.txt` |

Raw JSON, snapshot text, Prometheus metric log, Grafana PNG는 로컬 artifact로만 보관한다.

## 주요 해석 포인트

- 성능 개선 수치는 최종적으로 1000RPS 안정 처리와 1200RPS 포화 지점 식별로 정리한다.
- tuning 전 1000RPS 시도는 실제 약 536RPS와 p95 12.97초였지만, 최종 clean probe는 999.93RPS, dropped 0, p95 280.03ms로 안정화됐다.
- 1100RPS는 실제 1093.90RPS까지 도달했지만 p95가 2.68초라 안정 처리 수치로 쓰지 않는다.
- 1200RPS에서 p95 10.59초와 dropped 18,885건이 발생하므로, 현재 단일 통합 EC2 기준 안정 상한은 1000RPS로 보는 것이 맞다.
- HikariCP active/pending/timeout 지표상 connection pool exhaustion은 1차 병목이 아니다.
- Tomcat thread를 700으로 늘린 실험은 성능을 악화시켰으므로, thread 증설은 현재 조건의 해법으로 보지 않는다.
- Stateless session 전환은 JWT API 운영 설정으로 맞지만, 1050RPS 안정화에는 충분하지 않았다.
- 다음 개선은 load generator 분리, JFR 기반 CPU hotspot 확인, response serialization/cache payload 비용 축소 방향으로 진행한다.
