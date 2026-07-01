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
| Cache hit path 인증 최적화 | JWT claims 기반 인증으로 전환 | 캐시 hit 요청마다 `UserRepository.findById`가 실행되어 Hikari acquire가 증가하던 병목 제거 |
| Miss path query 축소 | 최신 프로젝트 skill snapshot 조회를 단일 query 우선 경로로 변경 | cache miss 경로에서 프로젝트 소유 확인, 최신 분석 조회, skill id 조회가 분리되어 발생하던 DB round-trip 축소 |
| Performance profile tuning | Hikari max 80, min idle 20, Tomcat max threads 240 | 5분 sustained 측정 중 miss spike가 Hikari 20개를 포화시키던 병목 완화 |

## 결과 요약

| 시나리오 | 목표 RPS | 실제 RPS | dropped | failure rate | p95 | p99 | 판단 |
|---|---:|---:|---:|---:|---:|---:|---|
| no-cache baseline | 300 | 298.51 | 178 | 0% | 3213.75ms | 4072.99ms | baseline limit |
| mixed 70% hot | 1500 | 1494.32 | 0 | 0% | 64.56ms | 240.35ms | official stable |
| hot cache best | 2000 | 1999.91 | 0 | 0% | 47.75ms | 114.99ms | best case |
| hot cache reconfirm | 2000 | 1996.07 | 0 | 0% | 810.88ms | 1284.06ms | variance observed |
| hot cache limit | 3000 | 2142.81 | 98057 | 0% | 2740.63ms | 3162.12ms | limit, not success |

이 표는 캐시 경계 재설계 직후의 1차 측정이다. 이후 JWT 인증 경로에 남아 있던 DB 조회 병목을 제거한 뒤 `mixed 1500RPS`와 `hot 2000RPS`를 재측정했으며, 최종 대표 수치는 아래 "JWT 인증 경로 개선 후 재측정" 결과를 사용한다. `3000RPS`는 dropped iterations가 크게 발생했으므로 성공 결과가 아니라 한계 탐색 결과다.

## JWT 인증 경로 개선 후 재측정

초기 cache rework 이후에도 hot cache hit 요청에서 인증 필터가 매 요청마다 사용자 DB 조회를 수행했다. 이로 인해 비즈니스 계산은 cache hit으로 우회되더라도 Hikari acquire가 요청 수에 비례해 증가했고, 2000RPS hot reconfirm에서 dropped iterations와 초 단위 tail latency가 발생했다.

JWT 인증 경로를 token claims 기반으로 바꾼 뒤 동일 EC2 2대 구성에서 재측정했다.

| 시나리오 | 목표 RPS | 실제 RPS | dropped | failure rate | p95 | p99 | 판단 |
|---|---:|---:|---:|---:|---:|---:|---|
| hot 2000RPS, 1차 reconfirm | 2000 | 1963.81 | 4328 | 0% | 2016.83ms | 2501.10ms | 초반 드롭 발생, 대표 수치 제외 |
| mixed 70% hot | 1500 | 1499.95 | 0 | 0% | 1.77ms | 5.33ms | stable |
| hot 2000RPS, warm rerun | 2000 | 1999.95 | 0 | 0% | 2.96ms | 11.25ms | stable |

Endpoint별 p95도 hot 2000RPS warm rerun에서 모두 한 자리 ms로 유지됐다.

| endpoint | p95 | p99 | max |
|---|---:|---:|---:|
| `GET /gap-analysis/projects/{userProjectId}` | 3.28ms | 10.23ms | 39.56ms |
| `GET /projects/{userProjectId}/job-matches` | 3.68ms | 19.60ms | 176.46ms |
| `GET /recommendations/jobs` | 2.06ms | 6.75ms | 44.26ms |

Prometheus snapshot 기준으로 cache hit run 중 Hikari acquire delta는 요청 수 대비 매우 작게 유지됐다. 즉 캐시 hit path에서 남아 있던 DB 조회 병목이 제거됐다고 판단한다.

| 시나리오 | cache hit delta | cache miss delta | Hikari acquire delta | Hikari timeout | active/pending after |
|---|---:|---:|---:|---:|---:|
| mixed 70% hot | 180049 | 9 | 47 | 0 | 0 / 0 |
| hot 2000RPS warm rerun | 240050 | 9 | 53 | 0 | 0 / 0 |

위 cache delta에는 k6 본 부하 전 warmup이 포함된다. k6 summary 기준 본 부하 구간은 mixed 1500RPS에서 cache hit delta 180001, hot 2000RPS warm rerun에서 cache hit delta 240002, miss delta 0이었다.

## 5분 Sustained 재측정

2분 run만으로는 장기 안정성을 주장하기 부족하므로, 동일 EC2 2대 구성에서 5분 sustained run을 추가로 수행했다. 최종 대표 수치는 이 섹션을 기준으로 한다.

| 시나리오 | 목표 RPS | 실제 RPS | 요청 수 | dropped | failure rate | p95 | p99 | max | 판단 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| warmed mixed 70% hot | 1500 | 1499.99 | 450001 | 0 | 0% | 1.58ms | 4.11ms | 54.06ms | stable |
| hot cache | 2000 | 1999.99 | 600001 | 0 | 0% | 2.83ms | 8.42ms | 115.13ms | stable |

Endpoint별 p95/p99도 5분 run에서 한 자리 ms 수준을 유지했다.

| 시나리오 | endpoint | p95 | p99 | max |
|---|---|---:|---:|---:|
| warmed mixed 1500RPS | `GET /gap-analysis/projects/{userProjectId}` | 1.61ms | 4.15ms | 54.06ms |
| warmed mixed 1500RPS | `GET /projects/{userProjectId}/job-matches` | 1.59ms | 4.13ms | 51.66ms |
| warmed mixed 1500RPS | `GET /recommendations/jobs` | 1.55ms | 4.05ms | 48.87ms |
| hot 2000RPS | `GET /gap-analysis/projects/{userProjectId}` | 2.86ms | 8.51ms | 115.13ms |
| hot 2000RPS | `GET /projects/{userProjectId}/job-matches` | 2.86ms | 8.49ms | 112.93ms |
| hot 2000RPS | `GET /recommendations/jobs` | 2.79ms | 8.21ms | 108.53ms |

5분 warmed mixed run의 cache delta는 hit 449968, miss 33이었다. hot 2000RPS run의 cache delta는 hit 599966, miss 35였다. 두 run 모두 Hikari timeout은 0이며, 종료 후 active/pending은 0이었다.

단, `USER_PROJECT_IDS=600`, `LONG_TAIL_VARIANTS=1000000`으로 30% traffic을 계속 새로운 key miss로 만드는 true continuous-miss mixed 1500RPS는 5분 sustained 조건에서 통과하지 못했다. Hikari pool을 80으로 올리고 프로젝트 skill snapshot query를 줄인 뒤에도 초반 VU가 상한까지 증가해 목표 arrival rate를 안정적으로 따라가지 못했다. 따라서 공식 문구는 "warmed working-set mixed 1500RPS"와 "cold miss-only 500RPS"를 분리해서 사용한다.

## Cold Miss Workload 재설계 후 측정

기존 cold/miss-only run은 cache key 다양성이 부족해 순수 miss 한계로 보기 어려웠다. 분석 API cache key는 `userId`, `userProjectId`, `targetRoles`, `targetCareerLevel`, `limit` 조합에 의해 결정되는데, staging 성능 DB에는 테스트 사용자 프로젝트가 1개뿐이었다. 따라서 role/limit 조합을 바꿔도 고RPS run 중 같은 cache key가 빠르게 반복됐다.

이번 재측정에서는 `jobflow_perf`에 성능 테스트 전용 프로젝트 fixture 600개를 추가하고, 원본 프로젝트의 분석/스킬/경험태그 snapshot을 복제했다. k6 스크립트도 `USER_PROJECT_IDS`를 받아 endpoint별 long-tail key를 전역 iteration 기준으로 펼치도록 수정했다. 또한 cold run 종료 후 `analysis_cache_misses_run_delta / http_reqs`가 `0.95` 미만이면 실패 처리하도록 validation gate를 추가했다.

| 시나리오 | 목표 RPS | 실제 RPS | dropped | failure rate | p95 | p99 | miss ratio | 판단 |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| cold 300RPS v1 | 300 | 300.00 | 0 | 0% | 3.58ms | 7.02ms | 0.138 | key 반복으로 무효 |
| cold 300RPS v2 | 300 | 300.00 | 0 | 0% | 7.08ms | 16.33ms | 0.999 | stable |
| cold 500RPS | 500 | 499.97 | 0 | 0% | 14.77ms | 29.55ms | 0.999 | stable |

Endpoint별 p95/p99는 cold 500RPS에서도 30ms 이하 p99를 유지했다.

| endpoint | p95 | p99 | max |
|---|---:|---:|---:|
| `GET /gap-analysis/projects/{userProjectId}` | 15.32ms | 30.22ms | 96.49ms |
| `GET /projects/{userProjectId}/job-matches` | 14.00ms | 27.97ms | 84.24ms |
| `GET /recommendations/jobs` | 14.94ms | 30.11ms | 69.03ms |

Prometheus snapshot 기준으로 cold 500RPS run은 cache miss delta 60001, hit delta 57을 기록했다. Hikari acquire delta는 120039였고 active/pending after와 timeout은 모두 0이었다. 즉 이 run은 cache hit 최적화가 아니라 실제 miss 계산 경로를 지속적으로 통과한 측정이다.

## 개선 효과

| 비교 | RPS | p95 | p99 | dropped |
|---|---:|---:|---:|---:|
| no-cache baseline | 298.51 | 3213.75ms | 4072.99ms | 178 |
| warmed mixed 70% hot, 5분 sustained | 1499.99 | 1.58ms | 4.11ms | 0 |
| hot 2000RPS, 5분 sustained | 1999.99 | 2.83ms | 8.42ms | 0 |
| cold 500RPS, miss-only | 499.97 | 14.77ms | 29.55ms | 0 |

no-cache baseline은 300RPS에서도 dropped iterations와 초 단위 tail latency가 발생했다. 반면 cache hit path 개선 후 warmed mixed workload는 5분 동안 약 1500RPS에서 dropped 0, failure 0%, p95 1.58ms를 기록했다. hot cache도 5분 동안 약 2000RPS에서 dropped 0, failure 0%, p95 2.83ms를 기록했다. cold miss-only는 500RPS에서 dropped 0, failure 0%, p95 14.77ms를 기록했다. 따라서 이 결과는 "분석 API의 반복 계산을 Redis cache boundary 안으로 넣고 cache hit path의 DB 접근을 제거하면 warmed working-set traffic에서 1500~2000RPS를 안정 처리할 수 있다"는 근거로 사용한다.

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
| Analysis API p95/p99 | no-cache baseline은 초 단위, JWT fix 후 hot/mixed/cold는 ms 단위로 하락 |
| Error Rate | 공식 채택 run에서 0% |
| Cache Hit Rate | hot/mixed run은 cache hit, cold run은 miss ratio 0.999 수준으로 분리 확인 |
| HikariCP | JWT fix 후 hot/mixed는 acquire delta가 요청 수 대비 작고, cold miss-only는 acquire가 증가하지만 pending/timeout 없이 종료 |
| JVM Memory / Thread | OOM 없이 정상 변동, thread peak는 Tomcat 부하 구간에서 상승 |

## 해석

### 1. `CACHE_ENABLED=false` baseline은 코드 수정 후에야 신뢰할 수 있다

이전 설정은 cache disabled 값이 있어도 `RedisCacheManager` bean 자체는 생성될 수 있었다. 이번 수정으로 disabled mode에서는 `NoOpCacheManager`를 반환하므로, no-cache baseline이 실제로 Redis cache를 우회한다.

### 2. 분석 API의 공식 개선 수치는 5분 sustained workload 기준으로 설명한다

JWT 인증 경로 개선 전에는 hot cache 2000RPS 재확인 run에서 p95 편차가 컸다. 개선 후에는 warmed mixed 70% hot 1500RPS와 hot 2000RPS 모두 5분 sustained 조건에서 dropped 0, failure 0%로 통과했다. 따라서 공식 문구는 "warmed mixed 1500RPS 5분 안정 처리, hot 2000RPS 5분 재확인 통과"로 표현한다.

### 3. 3000RPS는 분석 API의 성공 수치가 아니다

3000RPS hot run은 실제 RPS가 목표에 못 미쳤고 dropped iterations가 크게 발생했다. HTTP failure rate가 0%라도 목표 arrival rate를 못 맞춘 run은 성공으로 보지 않는다. 이 결과는 향후 serialization, payload, endpoint aggregation, Tomcat/DB pool tuning의 한계 탐색 자료로만 사용한다.

### 4. cold/miss-only는 miss ratio gate 없이는 공식 수치로 쓰면 안 된다

분석 API의 cold workload는 cache key 공간이 충분하지 않으면 중간부터 hot workload로 바뀐다. 따라서 cold run은 `cache miss delta ~= http_reqs` 조건을 반드시 함께 검증해야 한다. 이번 작업에서는 miss ratio 0.95 이상을 통과 기준으로 두었고, 최종 cold 300/500RPS run은 각각 0.999 수준의 miss ratio를 기록했다.

### 5. true continuous-miss mixed 1500RPS는 아직 성공 수치가 아니다

이번 5분 재측정 중 `USER_PROJECT_IDS=600`과 큰 long-tail keyspace로 30% traffic을 계속 새로운 cache miss로 만드는 mixed run도 시도했다. 이 조건은 Hikari timeout은 제거됐지만 목표 1500RPS를 안정적으로 따라가지 못했다. 따라서 이 결과를 숨기지 않고, "warmed working-set mixed"와 "continuous-miss mixed"를 분리해 해석한다.

## 결론

- 분석 API no-cache baseline은 300RPS에서도 p95 3213.75ms와 dropped 178을 기록했다.
- Redis cache 적용 후 mixed 70% hot workload는 1494.32RPS, dropped 0, failure 0%, p95 64.56ms, p99 240.35ms를 기록했다.
- JWT 인증 경로의 DB 조회를 제거하고 5분 sustained로 재확인한 뒤 warmed mixed 70% hot workload는 1499.99RPS, dropped 0, failure 0%, p95 1.58ms, p99 4.11ms까지 개선됐다.
- 같은 개선 후 hot 2000RPS 5분 run은 1999.99RPS, dropped 0, failure 0%, p95 2.83ms, p99 8.42ms를 기록했다.
- cold miss-only workload는 fixture와 validation gate를 보강한 뒤 499.97RPS, dropped 0, failure 0%, p95 14.77ms, p99 29.55ms를 기록했다.
- no-cache baseline 대비 warmed mixed 1500RPS의 처리량은 약 5.0배 증가했고 p95 latency는 약 2030배 개선됐다.
- endpoint 단독 probe는 세 분석 API 모두 700RPS에서 p95 3.17ms 이하를 기록해, 계산 로직이 cache boundary 안으로 들어갔음을 확인했다.
- 공식 성과는 "분석 API warmed mixed cache workload 1500RPS 5분 안정 처리, hot cache 2000RPS 5분 재확인 통과, cold miss-only 500RPS 안정 처리"로 표현한다. 3000RPS와 true continuous-miss mixed 1500RPS는 limit exploration으로만 기록한다.

## 후속 작업

| 작업 | 목적 | 완료 기준 |
|---|---|---|
| Analysis API serialization profiling | hot cache hit 후에도 남는 tail latency 원인 확인 | JFR 또는 async-profiler로 Redis deserialize / JSON serialize / controller overhead 분리 |
| Endpoint aggregation profiling | 세 분석 API 동시 부하에서 Hikari pending과 thread peak 원인 확인 | endpoint별 request mix와 DB access count 분리 |
| True continuous-miss mixed capacity | 30% long-tail miss가 계속 발생하는 운영 worst-case 한계 확인 | RPS 단계별로 Hikari, MySQL query time, Redis lock/payload 비용을 분리 측정 |

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
| JWT fix hot 2000RPS JSON | `artifacts/performance/260701_analysis_cache_jwtfix/json/260701_k6_analysis_cache_jwtfix_hot_2000rps_rerun_warm.json` |
| JWT fix mixed 1500RPS JSON | `artifacts/performance/260701_analysis_cache_jwtfix/json/260701_k6_analysis_cache_jwtfix_mixed_70_hot_1500rps.json` |
| JWT fix hot Grafana capture | `artifacts/grafana/260701_analysis_cache_jwtfix/260701_grafana_analysis_cache_jwtfix_hot_2000rps_rerun_end.png` |
| JWT fix mixed Grafana capture | `artifacts/grafana/260701_analysis_cache_jwtfix/260701_grafana_analysis_cache_jwtfix_mixed_1500rps_end_v2.png` |
| cold miss 300RPS JSON | `artifacts/performance/260701_analysis_cache_cold/json/260701_k6_analysis_cache_cold_miss_300rps_v2.json` |
| cold miss 500RPS JSON | `artifacts/performance/260701_analysis_cache_cold/json/260701_k6_analysis_cache_cold_miss_500rps.json` |
| cold miss 300RPS Grafana capture | `artifacts/grafana/260701_analysis_cache_cold/260701_grafana_analysis_cache_cold_300rps_v2_end.png` |
| cold miss 500RPS Grafana capture | `artifacts/grafana/260701_analysis_cache_cold/260701_grafana_analysis_cache_cold_500rps_end.png` |
| 5분 warmed mixed 1500RPS JSON | `artifacts/performance/260701_analysis_cache_sustained/json/260701_k6_analysis_cache_sustained_mixed_70_hot_1500rps_warmed_5m_rerun.json` |
| 5분 hot 2000RPS JSON | `artifacts/performance/260701_analysis_cache_sustained/json/260701_k6_analysis_cache_sustained_hot_2000rps_warmed_5m.json` |
| 5분 warmed mixed Grafana capture | `artifacts/grafana/260701_analysis_cache_sustained/260701_grafana_mixed_1500rps_5m_end.png` |
| 5분 hot Grafana capture | `artifacts/grafana/260701_analysis_cache_sustained/260701_grafana_hot_2000rps_5m_end.png` |
