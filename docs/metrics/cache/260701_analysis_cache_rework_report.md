# Analysis API Cache Rework Report

## 목적

이 테스트는 분석 API 캐시 적용 효과를 no-cache baseline, 현실형 mixed workload, hot-cache capacity, endpoint 단독 probe로 분리해 검증하기 위해 수행했다.

기존 before/after 측정은 cache enabled/disabled 경계가 명확하지 않았고, 단일 수치만으로는 "캐시가 어느 부하에서 무엇을 개선했는지" 설명하기 어려웠다. 이번 작업에서는 `CACHE_ENABLED=false`가 실제로 Redis cache를 우회하도록 수정한 뒤, 동일한 분석 API 묶음을 여러 workload로 재측정했다.

대상 API는 다음 세 개다.

| API | 성격 |
|---|---|
| `GET /gap-analysis/projects/{userProjectId}` | 프로젝트와 채용공고 간 gap 분석 |
| `GET /projects/{userProjectId}/job-matches` | 프로젝트 기반 채용공고 매칭 |
| `GET /recommendations/jobs` | 추천 채용공고 조회 |

## 측정 환경

| 항목 | 값 |
|---|---|
| application stack | m5.xlarge 단일 EC2 |
| load generator | c6i.xlarge 별도 EC2 |
| 호출 경로 | backend direct |
| profile | `local,performance` |
| cache | Redis `gapAnalysis`, `jdMatch`, `jobRecommendation` |
| baseline mode | `CACHE_ENABLED=false`, `NoOpCacheManager` |
| cache mode | `CACHE_ENABLED=true` |
| 판정 기준 | dropped iterations 0, HTTP failure rate 0%, p95/p99 latency |

Raw JSON, Prometheus snapshot, Grafana PNG는 로컬 artifact로만 보관한다.

## 변경 요약

| 구분 | 변경 | 이유 |
|---|---|---|
| Disabled cache semantics | `CACHE_ENABLED=false`일 때 `NoOpCacheManager` 생성 | no-cache baseline이 Redis cache를 참조하지 않도록 보장 |
| Regression test | disabled cache mode 단위 테스트 추가 | baseline 측정 조건이 다시 깨지는 것을 방지 |
| Workload 분리 | no-cache, mixed, hot, endpoint probe로 분리 | 캐시 효과와 한계 조건을 같은 그래프로 뭉개지 않기 위함 |
| Dashboard 개선 | Analysis API Total RPS / Analysis API p95,p99 패널 추가 | 분석 API 3종만 별도 관찰 가능하게 함 |
| Artifact 정리 | official TSV와 Grafana capture를 local artifact로 정리 | 보고서 수치와 원본 산출물 추적성 확보 |

## 결과 요약

| 시나리오 | 목표 RPS | 실제 RPS | dropped | failure rate | p95 | p99 | 판단 |
|---|---:|---:|---:|---:|---:|---:|---|
| no-cache baseline | 300 | 298.51 | 178 | 0% | 3213.75ms | 4072.99ms | baseline limit |
| mixed 70% hot | 1500 | 1494.32 | 0 | 0% | 64.56ms | 240.35ms | official stable |
| hot cache best | 2000 | 1999.91 | 0 | 0% | 47.75ms | 114.99ms | best case |
| hot cache reconfirm | 2000 | 1996.07 | 0 | 0% | 810.88ms | 1284.06ms | variance observed |
| hot cache limit | 3000 | 2142.81 | 98057 | 0% | 2740.63ms | 3162.12ms | limit, not success |

공식 성과로는 `mixed 70% hot` 결과를 채택한다. `hot cache best`는 좋은 상한 사례지만 재확인 run에서 p95 편차가 컸기 때문에 대표 수치로 과장하지 않는다. `3000RPS`는 dropped iterations가 크게 발생했으므로 성공 결과가 아니라 한계 탐색 결과다.

## 개선 효과

| 비교 | RPS | p95 | p99 | dropped |
|---|---:|---:|---:|---:|
| no-cache baseline | 298.51 | 3213.75ms | 4072.99ms | 178 |
| mixed 70% hot | 1494.32 | 64.56ms | 240.35ms | 0 |
| 개선 배율 | 5.0x 처리량 | 49.8x 빠른 p95 | 16.9x 빠른 p99 | dropped 제거 |

no-cache baseline은 300RPS에서도 dropped iterations와 초 단위 tail latency가 발생했다. 반면 mixed workload는 약 1500RPS에서 dropped 0, failure 0%, p95 64.56ms를 기록했다. 따라서 이 결과는 "분석 API의 반복 계산을 Redis cache boundary 안으로 넣으면 현실형 반복 부하에서 처리량과 tail latency가 동시에 개선된다"는 근거로 사용한다.

## Endpoint Probe

| endpoint probe | 목표 RPS | 실제 RPS | dropped | failure rate | p95 | p99 |
|---|---:|---:|---:|---:|---:|---:|
| gap analysis | 700 | 699.98 | 0 | 0% | 3.17ms | 7.01ms |
| JD match | 700 | 699.97 | 0 | 0% | 2.93ms | 6.12ms |
| job recommendations | 700 | 699.97 | 0 | 0% | 2.56ms | 6.02ms |

각 endpoint를 단독으로 호출하면 cache hit 상태에서 p95가 한 자리 ms 수준까지 내려간다. 즉 세부 계산 로직 자체는 캐시 경계 안으로 들어갔고, mixed/high-load에서 나타나는 tail latency는 단일 계산 함수보다는 aggregate traffic, 인증/프로젝트 조회, Redis 역직렬화, 응답 직렬화, Tomcat scheduling, 일시적인 Hikari pending 같은 주변 비용과 동시성 영향으로 보는 것이 타당하다.

## Grafana 관찰

| 항목 | 관찰 |
|---|---|
| Analysis API Total RPS | mixed 70% hot에서 1500RPS 근처 plateau 형성 |
| Analysis API p95/p99 | no-cache baseline은 초 단위, mixed는 ms 단위로 하락 |
| Error Rate | 공식 채택 run에서 0% |
| Cache Hit Rate | mixed/hot run에서 분석 API cache hit 증가 확인 |
| HikariCP | 일부 high-load run에서 pending spike가 발생했으나 mixed 공식 run은 dropped 없이 종료 |
| JVM Memory / Thread | OOM 없이 정상 변동, thread peak는 Tomcat 부하 구간에서 상승 |

## 해석

### 1. `CACHE_ENABLED=false` baseline은 코드 수정 후에야 신뢰할 수 있다

이전 설정은 cache disabled 값이 있어도 `RedisCacheManager` bean 자체는 생성될 수 있었다. 이번 수정으로 disabled mode에서는 `NoOpCacheManager`를 반환하므로, no-cache baseline이 실제로 Redis cache를 우회한다.

### 2. 분석 API의 공식 개선 수치는 mixed workload 기준이 가장 정직하다

hot cache 2000RPS는 좋은 상한 사례지만, reconfirm run에서 p95 편차가 있었다. 반대로 mixed 70% hot은 반복 요청과 일부 long-tail 요청이 섞인 현실형 조건이며 dropped 0, failure 0%, p95 64.56ms를 기록했다. 따라서 공식 문구는 "mixed 1500RPS 안정 처리"가 가장 안전하다.

### 3. 3000RPS는 분석 API의 성공 수치가 아니다

3000RPS hot run은 실제 RPS가 목표에 못 미쳤고 dropped iterations가 크게 발생했다. HTTP failure rate가 0%라도 목표 arrival rate를 못 맞춘 run은 성공으로 보지 않는다. 이 결과는 향후 serialization, payload, endpoint aggregation, Tomcat/DB pool tuning의 한계 탐색 자료로만 사용한다.

## 결론

- 분석 API no-cache baseline은 300RPS에서도 p95 3213.75ms와 dropped 178을 기록했다.
- Redis cache 적용 후 mixed 70% hot workload는 1494.32RPS, dropped 0, failure 0%, p95 64.56ms, p99 240.35ms를 기록했다.
- 처리량은 약 5.0배 증가했고 p95 latency는 약 49.8배 개선됐다.
- endpoint 단독 probe는 세 분석 API 모두 700RPS에서 p95 3.17ms 이하를 기록해, 계산 로직이 cache boundary 안으로 들어갔음을 확인했다.
- 공식 성과는 "분석 API mixed cache workload 1500RPS 안정 처리"로 표현하고, 2000RPS hot은 best-case, 3000RPS는 limit exploration으로만 기록한다.

## 후속 작업

| 작업 | 목적 | 완료 기준 |
|---|---|---|
| Analysis API serialization profiling | hot cache hit 후에도 남는 tail latency 원인 확인 | JFR 또는 async-profiler로 Redis deserialize / JSON serialize / controller overhead 분리 |
| Endpoint aggregation profiling | 세 분석 API 동시 부하에서 Hikari pending과 thread peak 원인 확인 | endpoint별 request mix와 DB access count 분리 |
| Sustained mixed workload | 2분 이상 장기 mixed 부하 안정성 확인 | 5~10분 mixed run에서 dropped 0, error 0%, p95 안정 |

## 산출물

| 파일 | 경로 |
|---|---|
| 공식 결과 TSV | `artifacts/performance/260701_analysis_cache_rework/260701_analysis_cache_official_results.tsv` |
| no-cache baseline JSON | `artifacts/performance/260701_analysis_cache_rework/260701_k6_analysis_cache_disabled_no_cache_baseline_300rps_rerun.json` |
| mixed 70% hot JSON | `artifacts/performance/260701_analysis_cache_rework/260701_k6_analysis_cache_enabled_mixed_70_hot_1500rps_reconfirm.json` |
| hot cache best JSON | `artifacts/performance/260701_analysis_cache_rework/260701_k6_analysis_cache_hot_capacity_2000rps.json` |
| endpoint probe JSON | `artifacts/performance/260701_analysis_cache_rework/260701_k6_analysis_cache_endpoint_probe_gap_analysis_700rps.json` |
| endpoint probe JSON | `artifacts/performance/260701_analysis_cache_rework/260701_k6_analysis_cache_endpoint_probe_jd_match_700rps.json` |
| endpoint probe JSON | `artifacts/performance/260701_analysis_cache_rework/260701_k6_analysis_cache_endpoint_probe_recommendations_jobs_700rps.json` |
| no-cache Grafana capture | `artifacts/grafana/260701_analysis_cache_rework/official/260701_grafana_analysis_no_cache_baseline_300rps.png` |
| cold boundary Grafana capture | `artifacts/grafana/260701_analysis_cache_rework/official/260701_grafana_analysis_cold_500rps_boundary.png` |
| endpoint probe Grafana capture | `artifacts/grafana/260701_analysis_cache_rework/official/260701_grafana_analysis_endpoint_probe_700rps_sequence.png` |
| hot reconfirm Grafana capture | `artifacts/grafana/260701_analysis_cache_rework/official/260701_grafana_analysis_hot_2000rps_reconfirm.png` |
