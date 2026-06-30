# ES + Redis Mixed Hit-rate 200k ramp 500VU Stress Test

## 목적

이 테스트는 Elasticsearch + Redis cache 적용 후, 인기 검색어 반복 조회에 가까운 high cache-hit workload를 넘어 mixed hit-rate 조건에서도 검색 API가 안정적으로 동작하는지 검증한다.

이전 ES + Redis cache stress test는 10개 인기 키워드를 반복 조회해 cache hit rate가 거의 100%에 가까운 조건을 측정했다. 이번 테스트는 hot traffic 비율을 70%, 50%, 30%로 낮추며 long-tail 검색어를 섞어 p95/p99 latency, error rate, Redis hit/miss, RPS 추이를 비교한다.

## 측정 환경

| 항목 | 값 |
|---|---|
| 인스턴스 | t3.xlarge (4 vCPU, 16GB RAM) |
| k6 위치 | EC2 내부 (`localhost:8080` backend 직접) |
| fixture | `jobflow_perf.jobs` 200,000건 (`source='perf_fixture'`) |
| ES 인덱스 | `jobflow-jobs-performance` (200k docs) |
| 캐시 | `CACHE_ENABLED=true`, Redis `jobSearch` cache |
| 대상 API | `GET /jobs/search?keyword=...&limit=10` |
| VU 시나리오 | ramp 50->100->200->500 VU, 각 3분, 500VU 10분 유지, 1분 ramp-down |
| 총 시간 | 23분 |
| mixed profile | hot traffic 70%, 50%, 30% |

## 사전 검증

| 항목 | 결과 |
|---|---|
| MySQL fixture count | 200,000 |
| Elasticsearch document count | 200,000 |
| backend env | `CACHE_ENABLED=true`, `ELASTICSEARCH_REINDEX_ON_STARTUP=false` |
| backend direct search | `Spring Boot` 검색 결과 반환 |
| gateway search | `Spring Boot` 검색 결과 반환 |
| jobSearch cache metric | `cache_gets_total{cache="jobSearch", result="hit|miss"}` 노출 |
| startup smoke | security event, outbox Kafka publish, Kafka consumer smoke 통과 |

성능 테스트 시작 전 staging/performance stack은 reindex를 skip했고, 이미 준비된 200,000건 MySQL fixture와 Elasticsearch index를 사용했다.

## k6 Summary 비교

| 지표 | 70% hot | 50% hot | 30% hot |
|---|---:|---:|---:|
| 총 요청 수 | 418,904 | 418,579 | 419,428 |
| 평균 RPS | 303.44 req/s | 303.17 req/s | 303.75 req/s |
| check 성공률 | 100% | 100% | 100% |
| HTTP 실패율 | 0% | 0% | 0% |
| avg latency | 9.06ms | 9.88ms | 7.84ms |
| p50 latency | 3.77ms | 3.95ms | 3.60ms |
| p90 latency | 17.03ms | 21.06ms | 15.74ms |
| p95 latency | 28.25ms | 36.63ms | 26.89ms |
| p99 latency | 78.97ms | 85.96ms | 64.54ms |
| max latency | 1,373.77ms | 908.05ms | 753.75ms |

## Hot/Long-tail Latency

| 지표 | 70% hot | 50% hot | 30% hot |
|---|---:|---:|---:|
| hot p95 | 28.00ms | 36.02ms | 26.83ms |
| hot p99 | 78.94ms | 85.32ms | 64.42ms |
| long-tail p95 | 28.83ms | 37.28ms | 26.91ms |
| long-tail p99 | 79.14ms | 86.68ms | 64.60ms |

hot traffic과 long-tail traffic의 p95 차이는 세 조건 모두 1.3ms 이내였다. 이번 mixed workload에서는 long-tail이 포함되어도 tail latency가 급격히 악화되지 않았다.

## Thresholds

| 조건 | 70% hot | 50% hot | 30% hot |
|---|---|---|---|
| `http_req_failed rate < 50%` | pass (0%) | pass (0%) | pass (0%) |
| `hot p95 < 10,000ms` | pass (28.00ms) | pass (36.02ms) | pass (26.83ms) |
| `long_tail p95 < 60,000ms` | pass (28.83ms) | pass (37.28ms) | pass (26.91ms) |

## 이전 기준선 대비

| 지표 | ES 200k+500VU (캐시 없음) | ES + Redis high cache-hit | ES + Redis mixed hit-rate |
|---|---:|---:|---:|
| error rate | 0% | 0% | 0% |
| 평균 RPS | 86 req/s | 303 req/s | 303 req/s |
| p95 latency | 5,520ms | 32.67ms | 26.89~36.63ms |
| p99 latency | 6,568ms | 91.05ms | 64.54~85.96ms |
| 완주 | O | O | O |

ES 단독은 실패율 0%로 완주했지만 500VU 구간에서 p95가 5.52s였다. Redis cache 적용 후 high cache-hit 조건뿐 아니라 mixed hit-rate 조건에서도 p95가 26.89~36.63ms 범위로 유지됐다.

## Grafana 관찰

| 항목 | 관찰 |
|---|---|
| RPS | 세 조건 모두 500VU sustain 구간에서 약 490~500 req/s plateau 관측 |
| error rate | 테스트 구간 0% |
| cache hit rate | 세 조건 모두 Grafana 기준 거의 100% 근처 유지 |
| p95 latency | 대체로 수십 ms 수준 |
| p99 latency | 일부 spike가 있었지만 전체 p99는 64.54~85.96ms 범위 |
| JVM Heap | 테스트 중 증가와 GC 변동 관측, OOM 없음 |
| HikariCP active | 테스트 중 변동 관측, connection pool exhaustion 없음 |
| ES indexing rate | 0, 조회 전용 테스트 확인 |

Grafana의 500 req/s plateau는 sustain 구간 시계열 기준이다. k6 평균 RPS 303 req/s는 ramp-up/ramp-down과 전체 23분 실행 구간을 포함한 평균이다.

## 판단

Elasticsearch + Redis cache 적용 후 200,000건 검색 데이터셋과 500VU 부하에서 70%, 50%, 30% hot traffic 조건 모두 error rate 0%로 완주했다. 전체 p95 latency는 26.89~36.63ms, p99 latency는 64.54~85.96ms 범위였다.

따라서 이번 테스트는 인기 키워드 반복 조회뿐 아니라 long-tail 검색어가 섞인 mixed workload에서도 Redis cache 기반 검색 경로가 안정적으로 동작한다는 근거로 사용할 수 있다.

단, 이번 mixed workload의 long-tail query도 제한된 k6 scenario 안에서 생성된다. 실제 운영 트래픽의 keyword cardinality, pagination, filtering 조건이 더 커질 경우에는 별도 workload로 추가 측정해야 한다.

## 산출물

| 파일 | 경로 |
|---|---|
| 70% hot k6 JSON | `jobflow-server-env/artifacts/260630_es_cache_mixed_70_hot_200k_500vu/260630_k6_es_cache_mixed_70_hot_200k_500vu.json` |
| 70% hot Grafana 캡처 | `jobflow-server-env/artifacts/260630_es_cache_mixed_70_hot_200k_500vu/260630_es_cache_mixed_70_hot_grafana_25m.png` |
| 50% hot k6 JSON | `jobflow-server-env/artifacts/260630_es_cache_mixed_50_hot_200k_500vu/260630_k6_es_cache_mixed_50_hot_200k_500vu.json` |
| 50% hot Grafana 캡처 | `jobflow-server-env/artifacts/260630_es_cache_mixed_50_hot_200k_500vu/260630_es_cache_mixed_50_hot_grafana_25m.png` |
| 30% hot k6 JSON | `jobflow-server-env/artifacts/260630_es_cache_mixed_30_hot_200k_500vu/260630_k6_es_cache_mixed_30_hot_200k_500vu.json` |
| 30% hot Grafana 캡처 | `jobflow-server-env/artifacts/260630_es_cache_mixed_30_hot_200k_500vu/260630_es_cache_mixed_30_hot_grafana_25m.png` |

Raw JSON과 Grafana PNG는 로컬 artifact로만 보관한다.

## 주요 해석

- high cache-hit workload에서 확인한 Redis cache 효과가 mixed hit-rate workload에서도 유지됐다.
- 70/50/30 hot traffic 조건 모두 500VU에서 error rate 0%로 완주했다.
- hot traffic과 long-tail traffic의 p95 차이가 작아, 이번 workload에서는 long-tail 요청이 tail latency를 크게 악화시키지 않았다.
- ES no-cache 기준선 대비 mixed hit-rate p95는 5,520ms에서 26.89~36.63ms 범위로 낮아졌다.
- 이번 결과는 검색 엔진 도입뿐 아니라, 반복 조회 트래픽을 Redis cache 계층으로 흡수하고 Prometheus/Grafana로 hit/miss와 latency를 검증한 사례다.
