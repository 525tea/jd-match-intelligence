# Search API Cold/Mixed/Spike Capacity Report

## 목적

이 테스트는 200k Elasticsearch 인덱스와 Redis `jobSearch` cache를 사용하는 `/jobs/search` 검색 API의 처리량을 cache 상태별로 분리해 검증하기 위해 수행했다.

이전 3000RPS 측정은 Redis hot-cache path의 sustained capacity를 확인했지만, 운영 환경에서는 모든 요청이 cache hit로만 흐르지 않는다. 따라서 이번 측정은 다음 세 가지 조건을 분리했다.

- hot-cache spike: 반복 검색어가 cache에 올라간 상태에서 500 -> 3000RPS 급증을 처리하는지 확인
- mixed hit-rate: 70% hot keyword, 30% long-tail keyword 조건에서 운영형 hit/miss 부하를 확인
- cold/miss-only: 매 요청이 새로운 long-tail keyword로 들어와 Redis hit 없이 Elasticsearch 검색 경로를 타는 한계를 확인

## 측정 환경

| 항목 | 값 |
|---|---|
| application stack | m5.xlarge 단일 EC2 |
| load generator | c6i.xlarge 별도 EC2 |
| fixture | `jobflow_perf.jobs` 200,000건 (`source='perf_fixture'`) |
| Elasticsearch index | `jobflow-jobs-performance` 200,000 docs |
| 대상 API | `GET /jobs/search?keyword={keyword}&limit=10` |
| 호출 경로 | backend direct, private VPC path |
| cache | Redis `jobSearch`, `CACHE_ENABLED=true` |
| k6 executor | `ramping-arrival-rate`, `constant-arrival-rate` |
| 공통 판정 기준 | checks 99% 초과, HTTP failure rate 1% 미만, p95 1000ms 미만, dropped iterations 0 |

이 테스트는 단순 목록 조회나 MySQL 직접 조회가 아니라 검색 API 성능 측정이다. Redis hit 조건에서는 cache가 검색 결과를 반환하고, miss 조건에서는 Elasticsearch 검색 경로를 사용한다.

## 시나리오

| 시나리오 | 설명 | cache 기대 |
|---|---|---|
| hot-cache spike | hot keyword warm-up 후 500RPS에서 3000RPS로 상승 | hit 중심 |
| mixed 70% hot | 70%는 반복 keyword, 30%는 long-tail keyword | hit/miss 혼합 |
| cold/miss-only | 매 요청마다 long-tail keyword를 새로 생성 | miss 중심 |

## 결과 요약

| 조건 | 목표 RPS | 실제 평균 RPS | dropped iterations | failure rate | p95 | p99 | max | cache hit rate | 판단 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| hot-cache spike 500 -> 3000 | 3000 steady | 2056.79 | 0 | 0% | 14.66ms | 133.10ms | 392.27ms | 100.00% | 성공 |
| mixed 70% hot | 1000 | 999.95 | 0 | 0% | 82.50ms | 608.93ms | 2058.43ms | 70.00% | 성공 |
| mixed 70% hot | 1500 | 1499.81 | 0 | 0% | 27.95ms | 50.52ms | 185.27ms | 70.00% | 안정 |
| mixed 70% hot | 2000 | 1993.50 | 133 | 0% | 1207.66ms | 1312.56ms | 1889.56ms | 70.01% | 한계 노출 |
| cold/miss-only | 500 | 499.98 | 0 | 0% | 6.79ms | 13.69ms | 63.39ms | 0.00% | 성공 |
| cold/miss-only | 750 | 749.97 | 0 | 0% | 60.49ms | 164.22ms | 894.14ms | 0.00% | 안정 |
| cold/miss-only | 1000 | 987.75 | 165 | 0% | 1548.28ms | 1782.84ms | 2564.53ms | 0.00% | 한계 노출 |

3000RPS spike의 실제 평균 RPS가 2056.79인 이유는 ramp-up, steady, ramp-down 구간을 모두 포함한 평균이기 때문이다. Grafana에서는 3000RPS plateau가 형성됐고, k6 summary에서는 dropped iterations 0과 HTTP failure rate 0%를 확인했다.

## Cache Metric

| 조건 | hit delta | miss delta | pending delta | 해석 |
|---|---:|---:|---:|---|
| hot-cache spike 500 -> 3000 | 452,499 | 0 | 0 | Redis hot-cache 경로만 사용 |
| mixed 70% hot 1000RPS | 84,001 | 36,000 | 0 | 의도한 70:30 hit/miss 비율 유지 |
| mixed 70% hot 1500RPS | 126,000 | 53,999 | 0 | 의도한 70:30 hit/miss 비율 유지 |
| mixed 70% hot 2000RPS | 167,929 | 71,940 | 0 | 비율은 유지됐지만 latency/dropped iteration 발생 |
| cold/miss-only 500RPS | 0 | 60,001 | 0 | Redis hit 없이 miss-only 경로 사용 |
| cold/miss-only 750RPS | 0 | 90,001 | 0 | Redis hit 없이 miss-only 경로 사용 |
| cold/miss-only 1000RPS | 0 | 119,835 | 0 | Redis hit 없이 miss-only 경로 사용, 포화 발생 |

## Grafana 관찰

| 조건 | 관찰 |
|---|---|
| hot-cache spike 3000RPS | 3000RPS plateau, Error Rate 0%, HikariCP pending 없음 |
| mixed 1500RPS | RPS plateau 유지, p95/p99 ms 단위, cache hit-rate 70% 유지 |
| mixed 2000RPS | p95가 1초를 넘고 dropped iterations 발생, mixed workload 한계 지점으로 판단 |
| cold 750RPS | miss-only 조건에서 p95 60.49ms, dropped iterations 0 |
| cold 1000RPS | p95 1.54s와 dropped iterations 발생, cold/miss-only 한계 지점으로 판단 |

## 해석

### 1. 3000RPS는 hot-cache spike 조건에서 성공했다

500RPS에서 3000RPS로 상승하는 hot-cache spike는 dropped iterations 0, HTTP failure rate 0%, p95 14.66ms를 기록했다. cache metric도 hit delta만 증가했으므로 이 결과는 Redis hot-cache path의 spike 대응 성능으로 해석한다.

### 2. 운영형 mixed workload의 안정 구간은 1500RPS다

70% hot, 30% long-tail 조건에서 1500RPS는 dropped iterations 0, failure rate 0%, p95 27.95ms로 안정적이었다. 반면 2000RPS에서는 HTTP error는 없었지만 p95가 1초를 넘고 dropped iterations가 발생했다.

따라서 mixed 70% workload의 안정 capacity는 1500RPS로 보고, 2000RPS는 한계 지점으로 기록한다.

### 3. cold/miss-only 안정 구간은 750RPS다

cache hit 없이 모든 요청이 long-tail miss로 들어가는 조건에서 750RPS는 p95 60.49ms와 dropped iterations 0을 기록했다. 1000RPS에서는 p95 1.54s와 dropped iterations가 발생했다.

따라서 cold/miss-only workload의 안정 capacity는 750RPS로 보고, 1000RPS는 Elasticsearch 검색 경로의 포화 경계로 기록한다.

### 4. Error Rate 0%만으로 성공 판정하지 않는다

2000RPS mixed와 1000RPS cold 모두 HTTP failure rate는 0%였지만, p95 threshold와 dropped iterations 기준을 넘었다. 이 작업에서는 error rate만 보지 않고 arrival-rate 달성 여부와 tail latency를 함께 성공 기준으로 사용했다.

## 결론

- `/jobs/search` hot-cache spike는 3000RPS steady 구간을 처리했다.
- 70% hot / 30% long-tail mixed workload는 1500RPS까지 안정적이며, 2000RPS에서 한계가 드러났다.
- cold/miss-only workload는 750RPS까지 안정적이며, 1000RPS에서 한계가 드러났다.
- 이번 결과는 "검색 API가 무조건 3000RPS"가 아니라, cache 상태별 용량을 분리해 운영형 성능을 설명할 수 있게 만든 결과다.

## 산출물

| 파일 | 경로 |
|---|---|
| k6 JSON / Prometheus snapshots | `jobflow-server-env/artifacts/260701_search_capacity_cold_mixed_spike/` |
| Grafana captures | `jobflow-server-env/artifacts/260701_search_capacity_cold_mixed_spike/grafana/` |

Raw JSON, Prometheus snapshot, Grafana PNG는 로컬 artifact로만 보관한다.
