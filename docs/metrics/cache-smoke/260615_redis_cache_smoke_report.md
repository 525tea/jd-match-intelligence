# Redis Cache Smoke Report

## 목적

Redis cache가 적용된 API들이 첫 요청에서 cache key를 만들고, 두 번째 요청에서 같은 key를 재사용하는지 확인한다.

이번 smoke는 다음 API를 대상으로 수행했다.

- Skill trend API
- Gap analysis API
- JD match API
- Job recommendation API

모든 cache hit smoke에서 다음 결과를 확인했다.

- `hit_delta_total=1`
- `miss_delta_total=1`
- `cache_key_count_after_second=1`

이는 첫 요청은 Redis miss로 cache를 채우고, 두 번째 요청은 같은 cache key로 Redis hit가 발생했다는 뜻이다.

또한 event-driven eviction smoke로 사용자 공고 행동 변경 시 오래된 추천 cache가 삭제되는지도 확인했다.

- `after_first_key_count=1`
- `after_second_key_count=1`
- `after_event_key_count=0`

## 측정 기준

| 항목 | 값 |
| --- | --- |
| 측정일 | 2026-06-15 |
| API base URL | `http://127.0.0.1:8080` |
| Redis 확인 방식 | `redis-cli INFO stats`, cache prefix 기준 key scan |
| Cache clear mode | `CLEAR_CACHE=true` |
| Target month | `2026-06-01` |
| Limit | `10` |
| 인증 방식 | JobFlow JWT |
| Raw response | local only |

## Cache Hit 결과

| Mode | 첫 요청 | 두 번째 요청 | Data count | Hit delta | Miss delta | 두 번째 요청 후 cache key 수 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `trend-skills` | `0.033867s` | `0.009848s` | `10` | `1` | `1` | `1` |
| `gap-analysis` | `0.200848s` | `0.027425s` | `1` | `1` | `1` | `1` |
| `jd-match` | `0.311541s` | `0.024253s` | `10` | `1` | `1` | `1` |
| `job-recommendation` | `0.107510s` | `0.008949s` | `10` | `1` | `1` | `1` |

## 응답 시간 변화

| Mode | 변화 |
| --- | ---: |
| `trend-skills` | `0.033867s -> 0.009848s` |
| `gap-analysis` | `0.200848s -> 0.027425s` |
| `jd-match` | `0.311541s -> 0.024253s` |
| `job-recommendation` | `0.107510s -> 0.008949s` |

## Event Eviction 결과

| Scenario | 실행 전 cache key 수 | 첫 요청 후 | 두 번째 요청 후 | 이벤트 후 | 결과 |
| --- | ---: | ---: | ---: | ---: | --- |
| 사용자 공고 행동 변경 후 추천 cache 삭제 | `0` | `1` | `1` | `0` | PASS |

event smoke는 추천 API 호출로 `jobRecommendation` cache를 만든 뒤, 상위 추천 공고에 대해 사용자 공고 행동을 발생시켰다.

이후 recommendation cache key count가 `0`으로 돌아왔으므로, 사용자 행동 변경이 stale recommendation result를 삭제한다는 것을 확인했다.

## 실행 명령

```bash
BASE_URL=http://127.0.0.1:8080 \
MODE=trend-skills \
CLEAR_CACHE=true \
MONTH=2026-06-01 \
LIMIT=10 \
bash performance/cache/redis-cache-smoke.sh
```

```bash
BASE_URL=http://127.0.0.1:8080 \
MODE=gap-analysis \
USER_PROJECT_ID=<owned-project-id> \
ACCESS_TOKEN='<JobFlow JWT>' \
TARGET_ROLES=BACKEND,FULLSTACK \
LIMIT=10 \
CLEAR_CACHE=true \
bash performance/cache/redis-cache-smoke.sh
```

```bash
BASE_URL=http://127.0.0.1:8080 \
MODE=jd-match \
USER_PROJECT_ID=<owned-project-id> \
ACCESS_TOKEN='<JobFlow JWT>' \
TARGET_ROLES=BACKEND,FULLSTACK \
TARGET_CAREER_LEVEL=MID \
LIMIT=10 \
CLEAR_CACHE=true \
bash performance/cache/redis-cache-smoke.sh
```

```bash
BASE_URL=http://127.0.0.1:8080 \
MODE=job-recommendation \
USER_PROJECT_ID=<owned-project-id> \
ACCESS_TOKEN='<JobFlow JWT>' \
TARGET_ROLES=BACKEND,FULLSTACK \
LIMIT=10 \
CLEAR_CACHE=true \
bash performance/cache/redis-cache-smoke.sh
```

```bash
BASE_URL=http://127.0.0.1:8080 \
USER_PROJECT_ID=<owned-project-id> \
ACCESS_TOKEN='<JobFlow JWT>' \
TARGET_ROLES=BACKEND,FULLSTACK \
LIMIT=10 \
USER_JOB_ACTION=view \
bash performance/cache/recommendation-cache-eviction-smoke.sh
```

주의:

- `ACCESS_TOKEN`은 JobFlow 로그인 후 발급되는 JWT다.
- GitHub provider access token, GitHub Client Secret, GitHub Actions token, encryption key를 넣지 않는다.
- 실제 token 값, 사용자 이메일, 개인 식별자는 문서에 기록하지 않는다.

## 해석

이번 smoke로 다음 cache 전략이 동작함을 확인했다.

| 변경 종류 | Eviction 범위 | 이유 |
| --- | --- | --- |
| Project analysis 변경 | user/project prefix | 영향을 받는 사용자와 프로젝트를 알고 있음 |
| Job skill index rebuild | namespace 전체 | 어떤 사용자와 프로젝트에 영향을 줄지 알 수 없음 |
| User job action 변경 | user prefix recommendation cache | 추천 점수와 ignored filter가 사용자 행동 상태를 사용함 |

Redis value 저장 방식은 서버 내부 cache serialization 구현 세부사항이며, 공개 API contract에는 포함하지 않는다.

## 남은 리스크

현재 smoke는 Redis keyspace counter와 cache key count를 기준으로 한다.

운영 수준 관측에서는 Prometheus/Grafana metric을 추가한 뒤 다음 항목을 함께 비교하는 것이 좋다.

- application-level cache hit rate
- Redis keyspace hit/miss
- cache eviction count
- API latency before/after

## 결론

PASS.

Redis cache hit smoke와 recommendation event eviction smoke 모두 기대한 결과를 반환했다.
