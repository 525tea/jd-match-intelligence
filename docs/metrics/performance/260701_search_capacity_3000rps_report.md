# Search API Capacity Rework 200k 3000RPS Report

## 목적

이 테스트는 Elasticsearch + Redis cache 적용 후 `/jobs/search` hot-cache 경로가 고처리량 조건에서 어디까지 안정적으로 처리되는지 검증하기 위해 수행했다.

이전 saturation 테스트는 backend, Redis, Elasticsearch, MySQL, Grafana, Prometheus, k6가 같은 EC2 안에서 실행되어 application stack과 load generator가 CPU를 공유했다. 그 결과 1000RPS 이후 latency cliff와 dropped iterations가 발생했지만, 그 원인이 backend 한계인지 k6 generator 오염인지 분리하기 어려웠다.

이번 테스트는 k6 load generator를 별도 EC2로 분리하고, backend nofile/Tomcat connection queue 설정과 k6 ramping arrival profile을 적용해 3000RPS steady 구간을 검증한다.

## 측정 환경

| 항목 | 값 |
|---|---|
| application stack | m5.xlarge 단일 EC2 |
| load generator | c6i.xlarge 별도 EC2 |
| fixture | `jobflow_perf.jobs` 200,000건 (`source='perf_fixture'`) |
| Elasticsearch index | `jobflow-jobs-performance` 200,000 docs |
| cache | Redis `jobSearch`, `CACHE_ENABLED=true` |
| 대상 API | `GET /jobs/search?keyword=Spring%20Boot&limit=10` |
| 호출 경로 | backend direct, public search path |
| workload | k6 `ramping-arrival-rate` |
| profile | 1분 ramp-up, 2분 3000RPS steady, 30초 ramp-down |
| 판정 기준 | checks 99% 초과, HTTP failure rate 1% 미만, p95 1000ms 미만, dropped iterations 0 |

이 결과는 `/jobs/search`의 Redis hot-cache 처리량 검증이다. cold-cache Elasticsearch query throughput이나 mixed hit-rate workload 결과로 해석하지 않는다.

## 변경 요약

| 구분 | 변경 | 이유 |
|---|---|---|
| Load generator 분리 | k6를 별도 c6i.xlarge EC2에서 실행 | k6와 application stack의 CPU 경합 제거 |
| Backend file descriptor | performance profile backend `nofile` soft/hard limit을 65535로 설정 | 고동시 연결에서 FD limit이 먼저 병목이 되는 것을 방지 |
| Tomcat connection queue | `accept-count=1000`, `max-connections=20000` 기본값 적용 | 순간 connection backlog와 accept queue 여유 확보 |
| k6 process limit | runner에서 k6 실행 전 `ulimit -n`을 65535까지 상승 | load generator 측 socket/file descriptor 부족 방지 |
| Workload profile | `constant-arrival-rate` 외에 `ramping-arrival-rate` 추가 | 0 -> 3000RPS 즉시 진입 spike와 sustained capacity 검증을 분리 |
| Stability guard | summary JSON에서 `dropped_iterations`를 실패 조건으로 검증 | error rate가 0이어도 목표 arrival rate를 못 맞추는 run을 성공으로 오판하지 않기 위함 |

## 결과 요약

| 조건 | 목표 RPS | 전체 평균 RPS | 총 요청 | dropped iterations | failure rate | p50 | p90 | p95 | p99 | max | 판단 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 별도 runner, 2500RPS constant | 2500 | 2499.88 | 300,001 | 0 | 0% | 0.83ms | 2.47ms | 6.30ms | 25.43ms | 137.72ms | 안정 |
| 별도 runner, 3000RPS constant | 3000 | 2909.29 | 349,152 | 10,849 | 0% | 1.02ms | 783.01ms | 3242.90ms | 3909.94ms | 9859.51ms | 포화 |
| nofile/Tomcat 후 3000RPS constant | 3000 | 2800.65 | 336,114 | 23,887 | 0% | 5.28ms | 3604.28ms | 3940.34ms | 4770.34ms | 5393.79ms | 포화 |
| 별도 runner, 3000RPS ramp | 3000 steady | 2357.05 | 494,999 | 0 | 0% | 0.88ms | 6.33ms | 20.81ms | 137.77ms | 450.73ms | 안정 |

3000RPS ramp run의 전체 평균 RPS가 2357.05인 이유는 1분 ramp-up, 2분 3000RPS steady, 30초 ramp-down을 모두 포함한 평균이기 때문이다. 기대 요청 수는 `90,000 + 360,000 + 45,000 = 495,000`이고, 실제 총 요청 수는 494,999건으로 profile과 일치했다.

## Cache Metric

| 지표 | before | after | delta |
|---|---:|---:|---:|
| `jobSearch` hit | 336,194 | 831,193 | +494,999 |
| `jobSearch` miss | 22 | 22 | +0 |
| `jobSearch` pending | 0 | 0 | +0 |
| delta 기준 hit rate | - | - | 100% |

3000RPS ramp run은 전 구간에서 `jobSearch` cache hit만 증가했다. 따라서 이 결과는 Redis hot-cache path의 capacity 검증으로 해석한다.

## Grafana 관찰

| 항목 | 관찰 |
|---|---|
| `/jobs/search` RPS | ramp-up 이후 3000 req/s plateau 형성, ramp-down 정상 종료 |
| p95/p99 latency | steady 구간 p95/p99가 ms 단위로 유지되고, k6 summary p95 20.81ms / p99 137.77ms |
| Error Rate | 0% |
| Cache Hit Rate | `jobSearch` hit rate 100%, miss 증가 없음 |
| JVM Heap | OOM 없이 정상 변동 |
| HikariCP | active connection이 max에 붙지 않고 pending 증가 없음 |
| ES Indexing Rate | 0, 조회 전용 테스트 확인 |

## 해석

### 1. 3000RPS constant 실패는 backend 기능 실패가 아니라 spike/profile 문제였다

별도 runner에서도 0에서 3000RPS로 즉시 진입하는 constant profile은 dropped iterations와 초 단위 tail latency를 만들었다. HTTP failure rate는 0%였지만, dropped iterations가 발생했기 때문에 안정 처리량으로 인정하지 않는다.

반면 ramping profile에서는 동일한 3000RPS steady 구간을 2분 유지하면서 dropped iterations 0, failure rate 0%, p95 20.81ms를 기록했다. 따라서 이번 결과는 "즉시 3000RPS spike를 견딘다"가 아니라 "ramp-up이 있는 sustained 3000RPS hot-cache 검색 부하를 안정 처리한다"로 표현한다.

### 2. k6와 application stack 분리가 결과를 크게 바꿨다

단일 EC2 내부 k6에서는 1000RPS 이후 host CPU queueing과 dropped iterations가 관측됐다. 별도 c6i.xlarge runner로 k6를 분리하자 2500RPS constant에서 p95 6.30ms, 3000RPS ramp에서 p95 20.81ms를 기록했다.

이는 이전 병목 중 상당 부분이 application logic 자체라기보다 load generator와 service stack의 CPU 공유, connection burst, ramp profile 문제였음을 보여준다.

### 3. 이 결과는 cold ES 검색 성능이 아니라 Redis hot-cache 검색 성능이다

3000RPS ramp run에서 `jobSearch` hit delta는 +494,999, miss delta는 0이었다. 즉 모든 검색 요청이 Redis cache hit로 처리됐다.

따라서 이 수치는 "Elasticsearch cold query 3000RPS"가 아니라 "200k indexed dataset 위에서 자주 반복되는 검색어를 Redis `@Cacheable` 계층이 흡수할 때 `/jobs/search`가 3000RPS steady 구간을 처리했다"는 의미다.

## 결론

- 별도 c6i.xlarge k6 runner와 ramping arrival profile 조건에서 `/jobs/search` Redis hot-cache path는 3000RPS steady 구간을 안정 처리했다.
- 3000RPS ramp run은 총 494,999 요청, dropped iterations 0, HTTP failure rate 0%, p95 20.81ms, p99 137.77ms를 기록했다.
- cache metric 기준 `jobSearch` hit delta는 +494,999, miss delta는 0으로 hit rate 100%였다.
- 3000RPS constant immediate profile은 dropped iterations와 초 단위 latency가 발생했으므로 spike 대응 성공으로 표현하지 않는다.
- 이번 작업의 공개 성과는 "검색 API hot-cache path를 3000RPS sustained load까지 검증했다"로 정리한다.

## 후속 작업

| 작업 | 목적 | 완료 기준 |
|---|---|---|
| 3000RPS spike profile 개선 | 갑작스런 트래픽 급증 대응력 검증 | 500 -> 3000RPS 급상승에서 dropped iterations 0, p95 500ms 이하 |
| mixed hit-rate capacity 재측정 | hot-cache 100%가 아닌 운영형 keyword cardinality 반영 | 70/50/30 hot ratio에서 cold reset 후 RPS/p95/p99/hit-rate 비교 |
| cache value serialization 개선 | Redis hit 이후 역직렬화와 payload 비용 축소 | JFR에서 `ObjectInputStream`/byte buffer allocation 감소 |
| gateway 경유 측정 | 실제 외부 API path 확인 | `/api/jobs/search` 경유 RPS/p95와 backend direct 결과 비교 |

## 산출물

| 파일 | 경로 |
|---|---|
| 2500RPS constant k6 JSON | `jobflow-server-env/artifacts/260630_search_capacity_rework/260630_k6_es_cache_capacity_capacity_boundary_confirm_k6-runner_200k_2500rps.json` |
| 3000RPS constant 실패 JSON | `jobflow-server-env/artifacts/260630_search_capacity_rework/260630_k6_es_cache_capacity_capacity_runner_confirm_k6-runner_200k_3000rps.json` |
| 3000RPS nofile/Tomcat constant 실패 JSON | `jobflow-server-env/artifacts/260630_search_capacity_rework/260630_k6_es_cache_capacity_capacity_runner_nofile_tomcat_k6-runner_200k_3000rps.json` |
| 3000RPS ramp 성공 JSON | `jobflow-server-env/artifacts/260630_search_capacity_rework/260630_k6_es_cache_capacity_capacity_runner_ramp_k6-runner_200k_3000rps.json` |
| 3000RPS ramp Prometheus before/after | `jobflow-server-env/artifacts/260630_search_capacity_rework/260630_k6_es_cache_capacity_capacity_runner_ramp_k6-runner_200k_3000rps_prometheus_{before,after}.prom` |
| 3000RPS ramp Grafana capture | `jobflow-server-env/artifacts/260630_search_capacity_rework/grafana/260701_grafana_search_capacity_runner_3000rps_ramp_stable_final.png` |

Raw JSON, Prometheus snapshot, Grafana PNG는 로컬 artifact로만 보관한다.
