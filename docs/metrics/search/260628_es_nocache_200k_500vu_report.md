# ES 200k ramp 500VU Stress Test — 캐시 없음

## 목적

STORY 1-2단계. MySQL FULLTEXT(w8-3) 다음 단계로, 동일 조건에서 Elasticsearch inverted index의 순수 성능을 측정한다.

캐시 없는 상태에서 ES가 고VU 부하를 얼마나 감당하는지를 기준선으로 기록한다.  
w8-6 (ES + Redis) 단계의 before 기준으로 사용한다.

## 측정 환경

| 항목 | 값 |
|---|---|
| 인스턴스 | t3.xlarge (4 vCPU, 16GB RAM) |
| k6 위치 | EC2 내부 (`localhost:8080` backend 직접) |
| fixture | `jobflow_perf.jobs` 200,000건 (`source='perf_fixture'`) |
| ES 인덱스 | `jobflow-jobs-performance-v1` (200k docs, 1 shard / 0 replica) |
| 캐시 | `CACHE_ENABLED=false` |
| VU 시나리오 | ramp 50→100→200→500 VU, 각 3분, 500VU 10분 유지, 1분 ramp-down |
| 총 시간 | 23분 |

## 결과

| 지표 | 값 |
|---|---|
| 총 요청 수 | 118,751 |
| RPS | 86 req/s |
| error rate | **0%** |
| p50 | 2,815ms |
| p90 | 4,970ms |
| p95 | 5,520ms |
| p99 | 6,568ms |
| max | 10,168ms |
| checks 통과 | 237,502 / 237,502 |

## Thresholds

| 조건 | 결과 |
|---|---|
| `http_req_failed rate < 50%` | pass (0%) |
| `p(95) < 60,000ms` | pass (5,520ms) |

## MySQL FULLTEXT 대비

> w8-3 MySQL JSON artifact 없음 (early abort). Grafana 캡처 기반 추산.

| 지표 | MySQL 200k+500VU | ES 200k+500VU (캐시 없음) |
|---|---|---|
| error rate | ~80%+ (abort) | 0% |
| RPS | ~5 (붕괴) | 86 |
| p95 | 측정 불가 | 5,520ms |
| 완주 | ✗ | O (23분) |

## Grafana 관찰

| 항목 | 관찰 |
|---|---|
| RPS | 100VU 구간에서 ~100 req/s plateau. 500VU에서도 동일 수준 유지 (ES 처리 한계) |
| p95 latency | 500VU 구간 5~6s |
| error rate | 전 구간 0% |
| HikariCP active | 500VU 구간 최대 150개 |
| cache hit rate | 0% (캐시 비활성 확인) |
| ES indexing rate | 0 (조회 전용) |

## 판단

MySQL은 200k + 고VU에서 abort. ES는 동일 조건에서 0% error로 23분 완주.

단, 캐시 없이 500VU에서 p95 5.5s. w8-6 (ES + Redis)에서 캐시가 이 수치를 얼마나 낮추는지가 다음 비교 포인트.

## 산출물

| 파일 | 경로 |
|---|---|
| k6 JSON | `jobflow-server-env/artifacts/260628_es_nocache_200k_500vu/260628_k6_es_nocache_200k_500vu.json` |
| Grafana 캡처 | `jobflow-server-env/artifacts/260628_es_nocache_200k_500vu/260628_grafana_es_nocache_200k_500vu_stress.png` |
