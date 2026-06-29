# k6 Round 1 Monolith Baseline Report

## 목적

k6 Round 1은 Kafka, Debezium, MSA 분리 전의 단일 배포 환경 성능 기준선을 고정하기 위한 측정이다.

이번 측정에서는 Docker Compose 기반 staging/performance stack을 AWS EC2 단일 인스턴스에 배포하고, 공고 목록, 공고 검색, 추천 공고, 갭 분석 API를 대상으로 baseline을 수집했다.

측정값은 다음 두 기준으로 분리했다.

- internal baseline: EC2 내부에서 k6를 실행해 `localhost` endpoint를 호출한 서버 내부 처리 기준
- external baseline: 로컬 Mac에서 EC2 public endpoint를 호출한 외부 클라이언트 기준

사용자 관점 성능 지표는 external baseline을 기준으로 해석한다. internal baseline은 네트워크 지연을 제거하고 서버 내부 처리 성능을 확인하기 위한 진단용 수치다.

## 측정 환경

| 항목 | 값 |
| --- | --- |
| 배포 환경 | AWS EC2 단일 인스턴스 |
| 인스턴스 유형 | `t3.xlarge` |
| 배포 방식 | Docker Compose |
| 주요 서비스 | gateway, backend, MySQL, Redis, Elasticsearch, Prometheus, Grafana, Zipkin |
| 성능 DB | `jobflow_perf` |
| 성능 fixture | 1,000 jobs |
| Elasticsearch alias | `jobflow-jobs-performance` |
| k6 조건 | 20 VU, 10분 |
| 대상 API | `/jobs`, `/jobs/search`, `/recommendations/jobs`, `/gap-analysis/projects/{userProjectId}` |
| Round 1 rate limit | Gateway rate limit 제외 |

## 사전 이슈

### Gateway rate limit으로 인한 실패

초기 측정에서는 Gateway fixed-window rate limit이 부하 테스트 트래픽을 제한해 429 응답이 대량 발생했다.

이 실패는 애플리케이션 처리 성능 문제가 아니라, 부하 테스트 목적과 Gateway 보호 정책이 충돌한 사례다. Round 1 baseline에서는 서버 처리 성능을 측정하기 위해 performance profile에서 Gateway rate limit을 비활성화했다.

해당 실패 run은 운영 정책 검증 관점에서는 의미가 있지만, 모놀리식 baseline 수치에는 포함하지 않는다.

## 최종 측정 결과

### External Baseline

로컬 Mac에서 EC2 public endpoint로 요청을 보낸 외부 클라이언트 기준 결과다.

| 지표 | 결과 |
| --- | ---: |
| VU / duration | 20 VU / 10m |
| 총 HTTP 요청 수 | 43,798 |
| HTTP request rate | 72.84 req/s |
| iteration 수 | 10,949 |
| iteration rate | 18.21 iter/s |
| check success rate | 100.00% |
| HTTP failure rate | 0.00% |
| 전체 avg latency | 23.15ms |
| 전체 p50 latency | 21.84ms |
| 전체 p90 latency | 27.74ms |
| 전체 p95 latency | 30.48ms |
| 전체 p99 latency | 43.49ms |
| 전체 max latency | 882.52ms |

Endpoint별 latency는 다음과 같다.

| Endpoint | avg | p50 | p90 | p95 | p99 | max |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `/jobs` | 27.84ms | 25.78ms | 32.04ms | 37.60ms | 60.68ms | 794.03ms |
| `/jobs/search` | 24.24ms | 22.90ms | 26.99ms | 30.12ms | 44.35ms | 793.92ms |
| `/recommendations/jobs` | 21.12ms | 20.39ms | 23.32ms | 25.34ms | 33.63ms | 882.52ms |
| `/gap-analysis/projects/{userProjectId}` | 19.40ms | 18.96ms | 21.43ms | 22.63ms | 27.86ms | 320.47ms |

### Internal Baseline

EC2 내부에서 k6를 실행해 `localhost` endpoint를 호출한 서버 내부 처리 기준 결과다.

| 지표 | 결과 |
| --- | ---: |
| VU / duration | 20 VU / 10m |
| 총 HTTP 요청 수 | 46,754 |
| HTTP request rate | 77.77 req/s |
| iteration 수 | 11,688 |
| iteration rate | 19.44 iter/s |
| check success rate | 100.00% |
| HTTP failure rate | 0.00% |
| 전체 avg latency | 6.44ms |
| 전체 p50 latency | 5.16ms |
| 전체 p90 latency | 11.79ms |
| 전체 p95 latency | 15.67ms |
| 전체 p99 latency | 25.93ms |
| 전체 max latency | 120.46ms |

Endpoint별 latency는 다음과 같다.

| Endpoint | avg | p50 | p90 | p95 | p99 | max |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `/jobs` | 10.65ms | 9.23ms | 16.66ms | 21.07ms | 32.41ms | 102.78ms |
| `/jobs/search` | 7.89ms | 5.99ms | 13.17ms | 16.67ms | 26.77ms | 96.21ms |
| `/recommendations/jobs` | 4.73ms | 3.50ms | 8.02ms | 10.42ms | 18.08ms | 120.46ms |
| `/gap-analysis/projects/{userProjectId}` | 2.48ms | 1.84ms | 3.96ms | 5.47ms | 10.11ms | 59.20ms |

## Grafana 관측

Grafana `JobFlow Backend Observability` dashboard에서 다음 항목을 함께 확인했다.

- HTTP request rate
- P95 / P99 latency
- Error rate
- Cache hit rate
- JVM heap / non-heap memory

최종 external baseline run에서는 error rate가 0%로 유지됐다. cache hit rate는 0%로 표시되었으며, 이는 Round 1이 캐시 최적화 전 baseline이라는 점에서 이후 cache 적용 전후 비교 기준으로 사용한다.

JVM memory는 테스트 중 급격한 OOM 징후 없이 안정적으로 유지됐다.

## 해석

### 1. External baseline을 최종 사용자 관점 기준으로 사용한다

internal baseline은 EC2 내부에서 서버가 자기 자신을 호출한 결과이므로 네트워크 지연이 제거되어 있다. 따라서 사용자 체감 성능으로 설명하기에는 부적절하다.

external baseline은 로컬 Mac에서 EC2 public endpoint로 요청을 보낸 결과이므로 실제 외부 클라이언트 기준에 더 가깝다. Round 1 최종 수치는 external baseline을 기준으로 기록한다.

### 2. 현재 부하 조건에서는 애플리케이션 병목이 드러나지 않았다

20 VU, 10분 조건에서 failure rate는 0%였고, 모든 endpoint의 p95 latency가 threshold보다 충분히 낮았다.

다만 이 결과는 1,000건 성능 fixture와 20 VU 조건에서의 baseline이다. 시스템 한계치를 찾기 위한 stress test 결과는 아니다.

### 3. 검색 API가 상대적으로 가장 느린 축에 속한다

external baseline 기준 p95는 `/jobs`가 37.60ms, `/jobs/search`가 30.12ms였다.

두 API 모두 threshold를 크게 하회했지만, 이후 데이터 규모 확대나 동시성 증가 시 공고 목록과 검색 API를 우선 관찰할 필요가 있다.

### 4. Round 2 비교 기준선이 고정됐다

Round 1은 Kafka, Debezium, 캐시, 서비스 분리 전 기준선이다.

이후 Round 2에서는 동일한 외부 클라이언트 기준으로 측정해 다음 항목을 비교한다.

- error rate 변화
- endpoint별 p95 / p99 latency 변화
- cache hit rate 변화
- JVM memory 변화
- request rate 변화
- Kafka consumer lag 또는 event 처리량

## 산출물

Raw JSON과 Grafana 캡처는 Git에 커밋하지 않고 로컬 artifact로 보관한다.

보관 artifact:

- `k6_round1_20vu_10m_summary_external_mac_to_ec2.json`
- `k6_round1_20vu_10m_summary_passed.json`
- `k6_round1_20vu_5m_failed_by_rate_limit.json`
- `05_grafana_round1_20vu_10m_external_mac_to_ec2.png`
- `04_grafana_round1_20vu_10m_passed.png`

## 주요 해석 포인트

- 부하 테스트 수치를 하나로 뭉개지 않고 internal baseline과 external baseline으로 분리했다.
- 사용자 관점 수치는 외부 클라이언트에서 EC2 public endpoint로 요청한 external baseline만 사용한다.
- Gateway rate limit이 baseline 측정을 방해하는 것을 확인하고, performance profile에서는 rate limit을 제외했다.
- Round 1은 시스템 한계치가 아니라 Kafka, Debezium, 캐시, 서비스 분리 전 비교 기준선이다.
- Grafana로 latency, error rate, cache hit rate, JVM memory를 함께 관측했다.

## 다음 단계

- Round 1 결과를 before 기준으로 고정한다.
- Round 2에서는 캐시 또는 비동기 처리 도입 후 동일 조건으로 재측정한다.
- 데이터 규모나 VU를 늘리는 stress test는 Round 1 baseline과 분리해 별도 작업으로 진행한다.
