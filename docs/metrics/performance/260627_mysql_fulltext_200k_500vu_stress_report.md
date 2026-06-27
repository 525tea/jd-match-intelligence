# MySQL FULLTEXT 200k Stress Test Report

## 목적

MySQL FULLTEXT 검색(`MATCH...AGAINST BOOLEAN MODE`)이 200,000건 데이터셋에서 500VU 부하를 받을 때 어느 지점에서 포화되는지 확인한다.

이 테스트는 Elasticsearch 도입 전 MySQL 한계치를 수치로 고정하기 위한 stress test다. Round 1 baseline(1,000건, 20VU)이 정상 동작했으므로, 이번 결과는 대규모 데이터와 고부하 조건에서의 병목 지점을 식별하는 데 사용한다.

## 측정 환경

| 항목 | 값 |
| --- | --- |
| 배포 환경 | AWS EC2 단일 인스턴스 |
| 인스턴스 유형 | `m5.xlarge` (4 vCPU, 16GB, 비버스트) |
| 배포 방식 | Docker Compose (performance stack) |
| 성능 DB | `jobflow_perf` |
| 성능 fixture | 200,000 jobs (synthetic, MOD 분포) |
| 검색 방식 | MySQL FULLTEXT `MATCH...AGAINST BOOLEAN MODE` |
| k6 VU 패턴 | 50→100→200→500 (각 3분) + 500VU 10분 sustain + 1분 rampdown |
| 총 테스트 시간 | 23분 |
| 대상 API | `GET /api/jobs/search?keyword=...&limit=10` |
| threshold | `http_req_failed rate < 50%`, `p(95) < 60,000ms` |

## k6 Summary 결과

| 지표 | 값 |
| --- | ---: |
| 총 iterations | 421,759 |
| iteration rate | 305.5 iter/s |
| check 성공률 | **13.34%** |
| check 실패율 | **86.65%** |
| HTTP 실패율 | **86.65%** (365,470 / 421,759) |
| avg latency (전체) | 2.34ms |
| p50 latency (전체) | 0.73ms |
| p90 latency (전체) | 6.24ms |
| p95 latency (전체) | **9.53ms** ✓ |
| p99 latency (전체) | 22.3ms |
| max latency | 273.52ms |
| avg latency (성공 응답만) | 11.59ms |
| p95 latency (성공 응답만) | 31.75ms |
| p99 latency (성공 응답만) | 77.78ms |
| data received | 384 MB (278 kB/s) |
| data sent | 52 MB (37 kB/s) |

### threshold 판정

| threshold | 결과 |
| --- | --- |
| `p(95) < 60,000ms` | ✓ PASS (9.53ms) |
| `http_req_failed rate < 50%` | ✗ FAIL (86.65%) |

> p95가 낮게 나온 이유: 실패 응답(connection refused / timeout)은 수µs~수ms 만에 반환되므로 전체 분포를 낮추는 효과가 있다. 성공 응답만의 p95는 31.75ms.

## Grafana 관측

테스트 구간: 20:00 ~ 20:23 (KST)

| 패널 | 관측 |
| --- | --- |
| RPS | 피크 ~200 req/s, 500VU sustain 진입 시 급락 |
| p95 Latency | 200VU→500VU 전환 구간에서 600ms 스파이크, 이후 100~200ms 유지 |
| Error Rate | 50VU 구간 ~20%, 100VU 구간 ~30%, 200VU→500VU 구간 **40~50%**, rampdown 후 0%로 복귀 |
| HikariCP Active | 200VU 진입 시 max=20 빨간선 도달, 이후 테스트 종료까지 지속 포화 |
| JVM Heap | Tenured Gen ~80→96MiB, 점진 증가, OOM 없음 |
| Cache Hit Rate | 0% (Redis 없음, MySQL 직접 조회) |
| ES Indexing Rate | 0 (이번 테스트 대상 아님) |

Grafana 캡처: `260627_mysql_fulltext_200k_500vu_grafana.png`

## 해석

### 1. 병목: HikariCP 커넥션 풀 포화

200VU 진입 시 HikariCP active connections가 max(20)에 도달했다. 이후 신규 요청은 커넥션 대기 → 타임아웃 → 에러로 처리됐다.

MySQL FULLTEXT 쿼리 자체의 처리 시간(성공 응답 기준 avg 11.59ms)보다, **커넥션 확보 실패**가 에러율 86%의 직접 원인이다.

### 2. MySQL FULLTEXT는 200VU가 사실상 한계

50~100VU 구간에서 error rate 20~30%는 이미 간헐적 포화를 의미한다. 200VU 이상부터는 안정적인 서비스가 불가능한 수준이다.

500VU 조건에서 초당 처리 가능한 성공 요청은 305.5 iter/s × 13.34% ≈ **약 40 req/s**.

### 3. 성공 요청의 latency는 양호

성공한 13.34% 응답의 p95는 31.75ms, p99는 77.78ms로 MySQL 처리 성능 자체는 나쁘지 않다. 문제는 커넥션을 얻지 못해 대부분의 요청이 서비스되지 않는 것이다.

### 4. Elasticsearch 도입의 근거

이번 결과는 MySQL FULLTEXT 한계의 정량적 근거가 된다.

- 200VU 이상: HikariCP 포화로 서비스 불가
- 500VU: error rate 86.65%, 실질 처리량 ~40 req/s

다음 테스트(w8-5, w8-6)에서 Elasticsearch + Redis 캐시 적용 후 동일 조건으로 비교해 개선 폭을 측정한다.

## 산출물

Raw JSON은 권한 오류로 저장 실패. 터미널 summary와 Grafana 캡처로 대체.

보관 artifact (`jobflow-server-env/artifacts/mysql-fulltext-200k/`):

- `260627_mysql_fulltext_200k_500vu_grafana.png`

## 면접에서 설명할 포인트

- MySQL FULLTEXT는 200VU 이상에서 HikariCP 커넥션 풀 포화로 에러율이 40% 이상으로 치솟는다.
- 500VU 기준 실질 처리 가능한 요청은 ~40 req/s로, 의미 있는 서비스 수준이 아니다.
- p95가 낮게 보이는 트릭: 대부분 실패가 빠르게 반환되므로 전체 분포가 낮아진다. 성공 응답만의 p95(31.75ms)가 실질 지표다.
- 이 수치가 Elasticsearch + Redis 도입의 정량적 근거로 사용된다.
