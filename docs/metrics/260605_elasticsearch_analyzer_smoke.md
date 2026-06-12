# Elasticsearch Analyzer Smoke

## 실행 조건

- Date: 2026-06-05
- Environment:
  - local Docker Compose Elasticsearch
  - image: `jobflow-elasticsearch:8.17.0-nori`
  - plugin: `analysis-nori`
- Index: `jobflow-jobs`
- Analyzer: `jobflow_korean_tech`
- Script: `performance/elasticsearch/job-search-analyzer-smoke.sh`

## 실행 명령

```bash
bash /Users/iyejin/dev/jobflow/performance/elasticsearch/job-search-analyzer-smoke.sh
```

## Analyzer 구성

- tokenizer: `nori_tokenizer`
- filter:
  - `lowercase`
  - `jobflow_tech_synonym`

Synonym entries:

```text
k8s, kubernetes
js, javascript
spring, spring boot
백엔드, backend
쿠버네티스, kubernetes
스프링, spring
```

## Smoke 결과

### `k8s`

```text
8
k
kubernetes
s
```

### `Kubernetes`

```text
8
k
kubernetes
s
버네
쿠
티스
```

### `백엔드`

```text
backend
백
엔드
```

### `backend`

```text
backend
백
엔드
```

### `스프링`

```text
spring
스프링
```

### `spring boot`

```text
boot
spring
```

### `js`

```text
javascript
js
```

### `javascript`

```text
javascript
js
```

## 해석

- `k8s` 입력에서 `kubernetes` token이 생성됐다.
- `Kubernetes` 입력에서 `kubernetes` token이 유지되고, synonym에 의해 `k8s` 쪽 token도 함께 생성됐다.
- `백엔드` 입력에서 `backend` token이 생성됐다.
- `backend` 입력에서 `백`, `엔드` token이 생성됐다.
- `스프링` 입력에서 `spring` token이 생성됐다.
- `js`와 `javascript`는 양방향 synonym token이 생성됐다.

## 결론

W3-1 MySQL FULLTEXT baseline에서 확인한 기술 용어 동의어와 한글/영문 혼합 검색 한계를 Elasticsearch analyzer 계층에서 해결할 수 있는 기반을 확인했다.

이번 smoke는 ranking 품질이나 최종 검색 결과를 검증하는 것이 아니라, index analyzer가 의도한 token expansion을 수행하는지 확인하는 기준선이다.
