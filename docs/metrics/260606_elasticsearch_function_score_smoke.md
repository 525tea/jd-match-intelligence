# Elasticsearch Function Score Search Smoke

## 실행 조건

- Date: 2026-06-06
- Environment:
  - local backend API
  - local Docker Compose Elasticsearch
  - image: `jobflow-elasticsearch:9.2.8-nori`
  - plugin: `analysis-nori`
- Index: `jobflow-jobs`
- API: `GET /jobs/search`
- Script: `performance/elasticsearch/job-search-api-smoke.sh`

## 실행 명령

```bash
ES_URL=http://localhost:9200 BASE_URL=http://localhost:8080 INDEX_NAME=jobflow-jobs \
  bash performance/elasticsearch/job-search-api-smoke.sh
```

## Smoke Fixture

| id | source | title | deadlineAt | 목적 |
| --- | --- | --- | --- | --- |
| 900001 | W3_4_SMOKE | 백엔드 개발자 | 2026-06-07T23:59:00 | 마감 임박 공고 |
| 900002 | W3_4_SMOKE | 백엔드 개발자 | 2026-07-31T23:59:00 | 장기 마감 공고 |
| 900003 | W3_4_SMOKE | Kubernetes 플랫폼 엔지니어 | 2026-06-20T23:59:00 | k8s/Kubernetes synonym 검증 |

## 검색 결과

### `백엔드`

```text
900001  백엔드 개발자   deadline=2026-06-07T23:59:00    score=7.654122829437256
900002  백엔드 개발자   deadline=2026-07-31T23:59:00    score=6.254755973815918
```

### `k8s`

```text
900003  Kubernetes 플랫폼 엔지니어      deadline=2026-06-20T23:59:00    score=15.983586311340332
```

### `Kubernetes`

```text
900003  Kubernetes 플랫폼 엔지니어      deadline=2026-06-20T23:59:00    score=16.085786819458008
```

## Postman 검증

- `GET http://localhost:9200/jobflow-jobs/_search`
- query:

```json
{
  "query": {
    "term": {
      "source": "W3_4_SMOKE"
    }
  }
}
```

- 결과:
  - total hits: 3
  - `_id`: `900001`, `900002`, `900003`

## 해석

- `백엔드` 검색에서 같은 title/description을 가진 두 공고 중 마감일이 더 가까운 `900001`이 `900002`보다 상위 노출됐다.
- `k8s`와 `Kubernetes` 검색 모두 `900003`을 반환했다.
- W3-3 analyzer/synonym 설정이 W3-4 API 검색 경로에서도 작동함을 확인했다.
- Function score query가 keyword relevance 외에 마감 임박 ranking signal을 반영하는 것을 smoke 수준에서 확인했다.

## 결론

Elasticsearch를 `/jobs/search` primary 검색 경로에 연결하고, function score ranking이 실제 API 응답 순서에 영향을 주는 것을 확인했다.

이번 smoke는 대량 성능 측정이 아니라 검색 품질/랭킹 동작 확인용이다. MySQL FULLTEXT와 Elasticsearch의 latency 및 품질 비교는 후속 W3 검색 품질 비교 단계에서 별도 측정한다.
