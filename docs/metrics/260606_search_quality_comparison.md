# Search Quality Comparison: MySQL FULLTEXT vs Elasticsearch

## 실행 조건

- Date: 2026-06-06
- Environment:
  - local Docker Compose MySQL
  - local Docker Compose Elasticsearch
  - local backend API
- MySQL baseline:
  - `performance/sql/mysql-fulltext-search-quality-fixture.sql`
  - `performance/sql/mysql-fulltext-search-quality-check.sql`
- Elasticsearch quality check:
  - `performance/elasticsearch/elasticsearch-search-quality-check.sh`
- Elasticsearch index:
  - `jobflow-jobs`
- Elasticsearch fixture source:
  - `W3_8_QUALITY`

## 목적

W3 검색 개선 흐름에서 MySQL FULLTEXT와 Elasticsearch의 역할을 분리해 확인한다.

MySQL FULLTEXT는 fallback baseline으로 유지할 수 있는지 확인하고, Elasticsearch는 기술 용어 동의어와 한국어/영문 혼합 검색 품질을 개선할 수 있는지 확인한다.

## MySQL FULLTEXT Baseline Recap

W3-1 기준선에서는 직접 keyword matching은 동작했다.

하지만 다음 한계가 확인됐다.

| Query | Returned | Not Returned |
| --- | --- | --- |
| `Kubernetes` | `kubernetes-platform-engineer` | `k8s-platform-engineer` |
| `k8s` | `k8s-platform-engineer` | `kubernetes-platform-engineer` |

해석:

- MySQL FULLTEXT 자체가 불가능하다는 의미는 아니다.
- synonym table, query expansion, application rewrite를 직접 구현하면 해결할 수 있다.
- 다만 그 책임이 application/query 계층으로 올라온다.
- JobFlow에서는 검색 품질 실험과 ranking 제어를 Elasticsearch로 분리하기로 했다.

## Elasticsearch Quality Check

### 실행 명령

```bash
cd /Users/iyejin/dev/jobflow
ES_URL=http://localhost:9200 INDEX_NAME=jobflow-jobs \
  bash performance/elasticsearch/elasticsearch-search-quality-check.sh
```

### `k8s`

```text
kubernetes-platform-engineer    Kubernetes 플랫폼 엔지니어      deadline=null   score=18.324041
k8s-platform-engineer           k8s 플랫폼 엔지니어             deadline=null   score=16.049334
```

### `Kubernetes`

```text
kubernetes-platform-engineer    Kubernetes 플랫폼 엔지니어      deadline=null   score=18.349909
k8s-platform-engineer           k8s 플랫폼 엔지니어             deadline=null   score=13.436651
```

### `백엔드 플랫폼`

```text
deadline-later-backend          백엔드 플랫폼 개발자            deadline=2026-07-31T23:59:00    score=10.46088
deadline-urgent-backend         백엔드 플랫폼 개발자            deadline=2026-06-06T23:59:00    score=10.46088
backend-spring-engineer         백엔드 개발자                   deadline=null                   score=7.1661143
kubernetes-platform-engineer    Kubernetes 플랫폼 엔지니어      deadline=null                   score=4.7774096
k8s-platform-engineer           k8s 플랫폼 엔지니어             deadline=null                   score=4.184352
```

### `Spring`

```text
backend-spring-engineer         백엔드 개발자                   deadline=null                   score=4.711905
deadline-later-backend          백엔드 플랫폼 개발자            deadline=2026-07-31T23:59:00    score=4.711905
deadline-urgent-backend         백엔드 플랫폼 개발자            deadline=2026-06-06T23:59:00    score=4.711905
```

## 비교 결과

| 항목 | MySQL FULLTEXT baseline | Elasticsearch |
| --- | --- | --- |
| 직접 keyword matching | 가능 | 가능 |
| `k8s` -> `Kubernetes` 확장 | 기본 미지원 | synonym analyzer로 확인 |
| `Kubernetes` -> `k8s` 확장 | 기본 미지원 | synonym analyzer로 확인 |
| 한글/영문 혼합 검색 | 제한적 | nori + synonym 기반 확장 가능 |
| ranking 제어 | FULLTEXT score + SQL 정렬 중심 | function score로 확장 가능 |
| fallback 역할 | 적합 | primary search quality layer |

## 해석

Elasticsearch quality check에서 `k8s`와 `Kubernetes`가 서로의 공고를 함께 반환했다.

이는 W3-1 MySQL FULLTEXT baseline에서 확인한 동의어 미처리 한계를 analyzer/synonym 계층에서 해결할 수 있음을 보여준다.

`백엔드 플랫폼`, `Spring` 검색은 backend fixture를 안정적으로 반환했다. 다만 이 문서의 Elasticsearch check는 `_search` 직접 질의이므로, API function score ranking 검증과는 목적이 다르다.

- analyzer/synonym 검색 품질: `elasticsearch-search-quality-check.sh`
- API function score ranking: `job-search-api-smoke.sh`
- latency baseline: `mysql-fulltext-search-baseline.js`

## 결론

MySQL FULLTEXT는 fallback baseline으로 유지한다.

Elasticsearch는 다음 이유로 primary search quality layer로 유지한다.

- 기술 용어 동의어 검색을 analyzer 설정으로 관리할 수 있다.
- 한국어/영문 혼합 검색을 nori tokenizer와 synonym filter로 확장할 수 있다.
- 검색 품질 실험과 ranking 제어를 transaction DB query에서 분리할 수 있다.
- 장애 시 MySQL FULLTEXT fallback으로 기본 검색은 유지할 수 있다.

후속 작업에서는 Elasticsearch index에 `job_skills`, `job_experience_tags`를 포함하고, Kafka/Debezium 기반 색인 동기화와 reindex 전략을 보강한다.
