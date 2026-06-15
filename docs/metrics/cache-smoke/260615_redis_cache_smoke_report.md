# Redis Cache Smoke Report

## Summary

Redis cache smoke verification confirmed that the cached API paths create one Redis cache entry on the first request and reuse it on the second request.

Verified endpoints:

- Skill trend API
- Gap analysis API
- JD match API

All three smoke runs produced:

- `hit_delta_total=1`
- `miss_delta_total=1`
- `cache_key_count_after_second=1`

This means the first request missed Redis and populated the cache, while the second request hit Redis using the same cache key.

## Environment

| Item | Value |
| --- | --- |
| API base URL | `http://127.0.0.1:8080` |
| Redis verification | `redis-cli INFO stats`, key scan by cache prefix |
| Cache clear mode | `CLEAR_CACHE=true` |
| Target month | `2026-06-01` |
| Limit | `10` |
| Authenticated fixture | `gap-smoke@example.com` smoke fixture |

## Results

| Mode | First request | Second request | Data count | Hit delta | Miss delta | Cache keys after second |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `trend-skills` | `0.033867s` | `0.009848s` | `10` | `1` | `1` | `1` |
| `gap-analysis` | `0.200848s` | `0.027425s` | `1` | `1` | `1` | `1` |
| `jd-match` | `0.311541s` | `0.024253s` | `10` | `1` | `1` | `1` |

## Latency Change

| Mode | Change |
| --- | ---: |
| `trend-skills` | `0.033867s -> 0.009848s` |
| `gap-analysis` | `0.200848s -> 0.027425s` |
| `jd-match` | `0.311541s -> 0.024253s` |

## Verification Commands

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
USER_PROJECT_ID=1 \
EMAIL='gap-smoke@example.com' \
PASSWORD='<smoke-fixture-password>' \
TARGET_ROLES=BACKEND,FULLSTACK \
LIMIT=10 \
CLEAR_CACHE=true \
bash performance/cache/redis-cache-smoke.sh
```

```bash
BASE_URL=http://127.0.0.1:8080 \
MODE=jd-match \
USER_PROJECT_ID=1 \
EMAIL='gap-smoke@example.com' \
PASSWORD='<smoke-fixture-password>' \
TARGET_ROLES=BACKEND,FULLSTACK \
TARGET_CAREER_LEVEL=MID \
LIMIT=10 \
CLEAR_CACHE=true \
bash performance/cache/redis-cache-smoke.sh
```

## Notes

- API response JSON format is unchanged.
- Redis value storage uses server-side cache serialization only and is not part of the public API contract.
- Cache hit verification should be repeated after adding Prometheus/Grafana metrics so application-level cache hit rate can be compared with Redis keyspace counters.
