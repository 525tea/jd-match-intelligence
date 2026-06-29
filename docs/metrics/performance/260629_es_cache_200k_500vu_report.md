# ES + Redis Cache 200k ramp 500VU Stress Test

## 목적

MySQL FULLTEXT, Elasticsearch 캐시 없음 기준선 다음 단계로, 동일한 200,000건 검색 데이터셋에서 Elasticsearch + Redis 캐시 적용 후 고VU 부하를 측정한다.

이번 테스트의 목적은 ES 단독 검색에서 발생하던 500VU 구간 latency를 Redis 캐시가 얼마나 흡수하는지 확인하고, Grafana에서 jobSearch 캐시 hit rate가 실제로 관측되는지 검증하는 것이다.

## 측정 환경

| 항목 | 값 |
|---|---|
| 인스턴스 | t3.xlarge (4 vCPU, 16GB RAM) |
| k6 위치 | EC2 내부 (`localhost:8080` backend 직접) |
| fixture | `jobflow_perf.jobs` 200,000건 (`source='perf_fixture'`) |
| ES 인덱스 | `jobflow-jobs-performance` (200k docs) |
| 캐시 | `CACHE_ENABLED=true`, Redis `jobSearch` cache |
| 검색 키워드 | `백엔드`, `Spring Boot`, `프론트엔드`, `React`, `데이터 엔지니어`, `DevOps`, `Kubernetes`, `Python`, `Java`, `TypeScript` |
| VU 시나리오 | ramp 50→100→200→500 VU, 각 3분, 500VU 10분 유지, 1분 ramp-down |
| 총 시간 | 23분 |
| 대상 API | `GET /jobs/search?keyword=...&limit=10` |

## 사전 검증

| 항목 | 결과 |
|---|---|
| backend liveness | `UP` |
| Prometheus endpoint | `ok` |
| auth preflight | `ok` |
| 검색 preflight | `ok` |
| MySQL fixture count | 200,000 |
| Elasticsearch document count | 200,000 |
| cache env | `CACHE_ENABLED=true` |
| jobSearch cache preflight | hit delta 2, miss delta 0 |

캐시 metric은 Spring Redis cache statistics를 활성화한 뒤 `cache_gets_total{cache="jobSearch", result="hit"}` 기준으로 확인했다.

## k6 Summary 결과

| 지표 | 값 |
|---|---:|
| 총 요청 수 | 418,336 |
| 평균 RPS | 303 req/s |
| 총 iterations | 418,336 |
| check 성공률 | **100%** |
| check 실패율 | **0%** |
| HTTP 실패율 | **0%** |
| avg latency | 10.45ms |
| p50 latency | 4.55ms |
| p90 latency | 20.39ms |
| p95 latency | **32.67ms** |
| p99 latency | 91.05ms |
| max latency | 1,133.56ms |
| data received | 2.15 GB |
| data sent | 148.60 MB |

## Thresholds

| 조건 | 결과 |
|---|---|
| `http_req_failed rate < 50%` | pass (0%) |
| `http_req_duration{endpoint:jobs_search} p(95) < 10,000ms` | pass (32.67ms) |

## 이전 기준선 대비

| 지표 | MySQL FULLTEXT 200k+500VU | ES 200k+500VU (캐시 없음) | ES + Redis 200k+500VU |
|---|---:|---:|---:|
| error rate | 86.65% | 0% | **0%** |
| 평균 RPS | 305.5 iter/s, 실질 성공 ~40 req/s | 86 req/s | **303 req/s** |
| p95 latency | 전체 9.53ms, 성공 응답 31.75ms | 5,520ms | **32.67ms** |
| p99 latency | 성공 응답 77.78ms | 6,568ms | **91.05ms** |
| 완주 | O, 다만 실패율 높음 | O | **O** |

MySQL 결과는 실패 응답이 빠르게 반환되어 전체 p95가 낮게 보이는 특성이 있다. 실질 비교는 error rate와 성공 처리량을 함께 봐야 한다.

## Grafana 관찰

| 항목 | 관찰 |
|---|---|
| RPS | 500VU sustain 구간에서 약 490~500 req/s plateau 관측 |
| p95 latency | 대체로 수십 ms 수준, p99에서 일시적 spike 관측 |
| error rate | 테스트 구간 0% |
| cache hit rate | 거의 100% 유지 |
| cache hits/s | ramp-up에 따라 증가 후 sustain 구간에서 약 450~500 req/s 수준 |
| cache misses/s | preflight 이후 거의 0에 수렴 |
| HikariCP active | cache hit workload에서도 active connection 변동 관측, max=20 선 근처까지 접근 |
| JVM Heap | 테스트 중 증가와 GC 변동 관측, OOM 없음 |
| ES indexing rate | 0, 조회 전용 테스트 확인 |

Grafana의 500 req/s plateau는 sustain 구간 시계열 기준이다. k6 평균 RPS 303 req/s는 ramp-up/ramp-down과 전체 23분 실행 구간을 포함한 평균이다.

## 판단

ES 단독 대비 Redis 캐시 적용 후 500VU 조건에서 p95 latency가 5.52s에서 32.67ms로 낮아졌다. error rate는 0%로 유지됐고, Grafana에서도 jobSearch cache hit rate가 거의 100%로 관측됐다.

따라서 이번 테스트는 반복 검색 키워드가 많은 read-heavy workload에서 Redis 캐시가 ES 검색 부하와 응답 시간을 크게 줄인다는 근거로 사용할 수 있다.

단, 이번 시나리오는 10개 인기 키워드를 반복 조회하는 높은 cache-hit workload다. 전체 검색 트래픽 일반화에는 keyword cardinality를 높인 mixed hit-rate 테스트가 별도로 필요하다.

## 산출물

| 파일 | 경로 |
|---|---|
| k6 JSON | `jobflow-server-env/artifacts/260629_es_cache_200k_500vu/k6/260629_k6_es_cache_200k_500vu.json` |
| 대표 Grafana 캡처 | `jobflow-server-env/artifacts/260629_es_cache_200k_500vu/grafana/260629_es_cache_500vu_plateau.png` |
| 추가 Grafana 후보 | `jobflow-server-env/artifacts/260629_es_cache_200k_500vu/grafana/260629_es_cache_500vu_late_candidate.png` |
| 추가 Grafana 후보 | `jobflow-server-env/artifacts/260629_es_cache_200k_500vu/grafana/260629_es_cache_500vu_late_spike_candidate.png` |

Raw JSON과 Grafana PNG는 로컬 artifact로만 보관한다.

## 면접에서 설명할 포인트

- MySQL FULLTEXT는 200k+고VU 조건에서 커넥션 풀 포화와 높은 실패율이 발생했다.
- ES 단독은 실패율 0%로 완주했지만 500VU에서 p95가 5.52s로 높았다.
- Redis 캐시 적용 후 같은 200k+500VU 조건에서 p95 32.67ms, error rate 0%, cache hit rate 거의 100%를 확인했다.
- 이 결과는 "검색 엔진 도입"뿐 아니라 "반복 조회 트래픽을 캐시 계층으로 흡수하는 구조"의 효과를 정량적으로 보여준다.
- 단, 인기 키워드 반복 조회 시나리오이므로 mixed hit-rate 부하는 후속 검증으로 분리하는 것이 맞다.
