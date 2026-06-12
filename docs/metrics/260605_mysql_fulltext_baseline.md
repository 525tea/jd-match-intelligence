# MySQL FULLTEXT Search Baseline

## 실행 조건

- Date: 2026-06-05
- Environment:
  - local Docker Compose MySQL
  - local backend `application-local.yml`
  - local collector smoke data
- API: `GET /jobs/search`
- Search implementation: MySQL FULLTEXT `MATCH ... AGAINST`
- k6 execution:
  - Tool: Docker image `grafana/k6`
  - VUs: 5
  - Duration: 30s
  - Keywords: `백엔드`, `Spring`, `k8s`, `Kubernetes`
  - Limit: 10

## Smoke Test

Collector local profile로 실제 ZIGHANG 공고 1건을 수집하고 MySQL `jobs` table에 저장되는 것을 확인했다.
이 결과는 MySQL FULLTEXT 검색 baseline을 만들기 위한 초기 smoke였으며, ZIGHANG을 운영 수집 source로 확정했다는 의미는 아니다.

후속 실제 공고 compatibility smoke에서 ZIGHANG은 sitemap 후보 품질과 개발 직군 선별 효율 문제가 확인되어 experimental source로 격하했다.
운영 후보 source는 JUMPIT/WANTED를 우선하고, 사람인/잡코리아 분석 후 대표성 source를 추가한다.

Backend search API는 URL encoding된 keyword 요청에서 정상 응답했다.

```bash
curl -G 'http://localhost:8080/jobs/search' \
  --data-urlencode 'keyword=백엔드' \
  --data-urlencode 'limit=10'
```

확인 결과:

- `success=true`
- `data[].score` 포함
- 예시 score: `0.1812381148338318`

## Latency

```text
http_req_duration: avg=30.61ms min=11.16ms med=21.29ms p(50)=21.29ms p(90)=30.35ms p(95)=40.97ms p(99)=281.14ms max=284.64ms
http_req_failed: 0.00% 0 out of 145
http_reqs: 145 4.826817/s
```

| Metric | Value |
| --- | ---: |
| p50 | 21.29ms |
| p95 | 40.97ms |
| p99 | 281.14ms |
| failure rate | 0.00% |
| requests | 145 |
| throughput | 4.83 req/s |

## Search Quality Observation

### Direct keyword matching

- `Spring` 검색은 `backend-spring-engineer`와 기존 manual backend row를 반환했다.
- 직접 포함된 keyword는 baseline 검색으로 조회 가능했다.

### Synonym limitation

`SEARCH_BASELINE` fixture로 동의어 미처리 한계를 확인했다.

| Query | Returned | Not Returned |
| --- | --- | --- |
| `Kubernetes` | `kubernetes-platform-engineer` | `k8s-platform-engineer` |
| `k8s` | `k8s-platform-engineer` | `kubernetes-platform-engineer` |

결론:

- MySQL FULLTEXT는 `k8s`와 `Kubernetes`를 같은 의도로 해석하지 않는다.
- 검색 품질 개선에는 synonym analyzer가 필요하다.

### Deadline ranking limitation

`백엔드 플랫폼` 검색에서 두 공고의 본문/제목/role detail을 동일하게 두고 deadline과 created_at만 다르게 설정했다.

| external_id | deadline_at | created_at | Observed order |
| --- | --- | --- | ---: |
| `deadline-later-backend` | 2026-07-31 23:59:00 | 2026-06-05 00:00:00 | 1 |
| `deadline-urgent-backend` | 2026-06-06 23:59:00 | 2026-06-01 00:00:00 | 2 |

결론:

- 마감이 임박한 공고가 자동으로 상위 노출되지 않았다.
- 현재 baseline은 FULLTEXT score와 `created_at DESC` 중심 정렬이다.
- 마감일 가중치 같은 ranking 제어는 후속 Elasticsearch function score 또는 별도 ranking 로직이 필요하다.

## Conclusion

MySQL FULLTEXT baseline은 직접 keyword matching과 낮은 부하의 기본 검색 응답 기준선을 제공한다. 하지만 다음 한계가 확인됐다.

- `k8s` ↔ `Kubernetes` 동의어 미처리
- 한글 형태소/동의어 확장 부재
- 마감 임박 공고 가중치 ranking 부재

따라서 다음 검색 작업에서는 Elasticsearch analyzer/synonym과 function score 도입 근거를 확보한 상태로 진행한다.
