# Analysis API Cache Highload Before/After Report

## 목적

이 테스트는 분석 계열 API의 Redis cache 적용 상태를 고부하 조건에서 before/after로 검증하기 위해 수행했다.

대상은 gap analysis, JD match, job recommendation API다. 검색 API와 달리 분석 API는 사용자/프로젝트 맥락이 포함되므로, 캐시가 활성화되었을 때 실제 hit/miss metric이 쌓이는지와 200VU 10분 부하에서 안정적으로 응답하는지를 확인한다.

## 측정 환경

| 항목 | 값 |
|---|---|
| 인스턴스 | m5.xlarge (4 vCPU, 16GB RAM) |
| k6 위치 | EC2 내부, backend 직접 호출 (`localhost:8080`) |
| 대상 API | gap analysis, JD match, job recommendation |
| workload | 200VU, 10분, VU당 1초 pacing |
| backend profile | `local,performance` |
| cache disabled run | `CACHE_ENABLED=false` |
| cache enabled run | `CACHE_ENABLED=true`, Redis reset 후 warmup |
| tracing | `TRACING_SAMPLING_PROBABILITY=0.0` |
| reindex | `ELASTICSEARCH_REINDEX_ON_STARTUP=false` |

## 변경 요약

| 구분 | 변경 | 의도 |
|---|---|---|
| k6 script | `stress-analysis-cache.js` 추가 | 분석 API 3종을 같은 부하 조건에서 반복 호출 |
| runner | `run-stress-analysis-cache.sh` 추가 | auth, liveness, cache env, Redis reset, warmup, k6 summary export 자동화 |
| Grafana | cache hit rate 패널을 분석 API cache까지 확장 | `gapAnalysis`, `jdMatch`, `jobRecommendation` cache hit/miss 관측 |
| Ops guard | analysis cache runner와 Grafana cache query 회귀 검증 추가 | 성능 검증 스크립트/대시보드 누락 방지 |
| Staging env | 명령행 환경변수가 `.env`보다 우선하도록 수정 | `PERF_CACHE_ENABLED=false/true` before/after 재현성 확보 |

## k6 Summary 비교

| 조건 | 총 요청 | 실제 RPS | 실패율 | avg | p50 | p90 | p95 | p99 | max | 판단 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| cache disabled | 119,411 | 198.73 | 0.00% | 4.78ms | 2.79ms | 6.08ms | 9.23ms | 28.74ms | 1.10s | 안정 |
| cache enabled | 119,309 | 198.59 | 0.00% | 5.65ms | 2.93ms | 6.80ms | 10.16ms | 26.95ms | 1.46s | 안정 |

두 조건 모두 200VU 10분을 HTTP failure 0%, check success 100%, interrupted iteration 0으로 완료했다.

## Endpoint별 p95 비교

| Endpoint | cache disabled p95 | cache enabled p95 | 변화 |
|---|---:|---:|---:|
| gap analysis | 9.50ms | 10.14ms | +0.64ms |
| JD match | 8.89ms | 10.05ms | +1.15ms |
| job recommendation | 9.32ms | 10.33ms | +1.01ms |

200VU 조건에서는 cache enabled가 latency를 의미 있게 낮추지는 않았다. disabled run도 이미 p95 10ms 미만으로 충분히 빠른 경로였기 때문에, 이 테스트 결과를 "latency 개선"으로 해석하면 안 된다.

## Cache Metric

Cache enabled run 직후 backend Prometheus metric에서 다음 값을 확인했다.

| Cache | hit | miss | hit rate |
|---|---:|---:|---:|
| `gapAnalysis` | 39,808 | 2 | 99.99% |
| `jdMatch` | 39,807 | 2 | 99.99% |
| `jobRecommendation` | 39,714 | 4 | 99.99% |

Redis reset 이후 warmup 단계에서도 `hit_delta=6`, `miss_delta=3`이 확인되었다. 따라서 캐시가 활성화되지 않았거나 metric이 누락된 상태는 아니다.

## Grafana 관찰

| 항목 | 관찰 |
|---|---|
| HTTP Request Rate | 분석 API 3종이 endpoint별 약 60~66 req/s로 관측됨 |
| 전체 RPS | k6 summary 기준 198.59 req/s |
| P95 / P99 Latency | 분석 API line은 낮은 ms 구간에 유지됨 |
| Error Rate | 0% |
| Cache Hit Rate | `gapAnalysis`, `jdMatch`, `jobRecommendation` hit rate가 90~100% 근처에서 유지됨 |
| HikariCP | active connection은 max 20 아래에서 유지, pending 0 |

Grafana Backend Observability 대시보드는 전체 RPS를 합산하지 않고 endpoint별 request rate를 라인으로 표시한다. 따라서 k6 summary의 전체 RPS 198.59 req/s가 Grafana에서는 분석 API 3종 각각 약 60~66 req/s로 나뉘어 보인다.

## 해석

### 1. 캐시 적용은 검증됐지만, 200VU에서는 latency 개선 폭이 작다

cache enabled run에서 세 cache 모두 99.99% 수준의 hit rate를 보였다. 즉 Redis cache lookup 자체는 정상 동작한다.

다만 before 조건도 이미 p95 9.23ms로 낮았다. API 내부 계산 또는 DB/ES 접근이 이 workload에서 병목이 아니면, cache hit로 절약되는 시간이 p95에 크게 반영되지 않는다. enabled p95가 disabled보다 약 0.93ms 높게 나온 것도 이 범위에서는 측정 변동과 Redis serialization/deserialization 비용을 함께 봐야 한다.

### 2. 이 작업의 성과는 캐시 효과 과장이 아니라 검증 체계 확보다

이번 작업으로 다음을 재현 가능하게 만들었다.

- cache disabled/enabled를 명시 env로 전환
- `.env` 값이 명시 env를 덮어써 before/after가 무효화되는 문제 방지
- Redis reset 후 warmup을 통해 cold/warm cache 상태 구분
- k6 summary JSON 산출
- Grafana에서 analysis cache hit/miss 관측
- regression guard로 runner/dashboard/staging env 회귀 방지

### 3. 다음 개선은 더 무거운 분석 경로 또는 cold-cache 비용을 대상으로 잡아야 한다

200VU warm-cache 조건에서는 분석 API가 이미 충분히 빠르다. 따라서 다음 성능 개선은 같은 200VU에서 반복 호출만 늘리는 방식보다, 다음 중 하나로 설계해야 한다.

| 방향 | 목적 | 완료 기준 예시 |
|---|---|---|
| cold-cache 분석 | cache miss 시 계산/조회 비용 확인 | Redis reset 직후 첫 요청 p95/p99와 warm run 비교 |
| payload 확대 | 실제 이력서/프로젝트/채용공고 데이터가 큰 경우 비용 확인 | 상세 payload 조건에서 p95 변화 측정 |
| cache serializer 비교 | Redis 직렬화 비용 검증 | JDK serialization 대비 JSON/compact DTO cache p95, allocation 비교 |
| gateway 경유 비교 | 운영 호출 경로 비용 확인 | backend-direct와 gateway 경유 p95/RPS 비교 |
| higher RPS probe | 안정 처리 상한 확인 | 500VU 또는 constant-arrival-rate로 saturation 지점 탐색 |

## 결론

- 분석 API 3종은 200VU 10분 조건에서 cache disabled/enabled 모두 안정적으로 처리됐다.
- cache enabled 상태에서 `gapAnalysis`, `jdMatch`, `jobRecommendation` cache hit가 정상 관측됐다.
- 200VU warm-cache 조건에서는 latency 개선을 주장할 만큼의 차이는 없었다.
- 따라서 이번 작업의 결론은 "분석 API 캐시가 큰 latency 개선을 만들었다"가 아니라, "분석 API 캐시 before/after 검증 체계와 관측 지표를 확보했고, 현재 200VU 조건에서는 캐시 없는 경로도 충분히 빠르다"이다.
- 후속 개선은 cold-cache 비용, payload 크기, cache serialization, gateway 경유 비용, higher RPS saturation을 분리 측정해야 한다.

## 산출물

| 파일 | 경로 |
|---|---|
| cache disabled k6 summary | `jobflow-server-env/artifacts/260630_analysis_cache_200vu/260630_k6_analysis_cache_disabled_200vu.json` |
| cache enabled k6 summary | `jobflow-server-env/artifacts/260630_analysis_cache_200vu/260630_k6_analysis_cache_enabled_200vu.json` |
| Grafana Backend Observability screenshot | `jobflow-server-env/artifacts/260630_analysis_cache_200vu/260630_grafana_backend_analysis_cache_enabled_1603_1613.png` |
| Grafana Backend Observability live screenshot | `jobflow-server-env/artifacts/260630_analysis_cache_200vu/260630_grafana_backend_analysis_cache_enabled_live_161916.png` |
