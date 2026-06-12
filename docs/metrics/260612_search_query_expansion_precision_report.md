# Search Query Expansion Precision Report

## 목적

Analytics batch에서 집계한 skill co-occurrence 데이터를 검색 ranking 보조 신호로 연결했을 때, 실제 수집 공고 검색 품질이 악화되지 않는지 확인한다.

이번 리포트는 다음 질문에 답한다.

- 실제 수집 source만 대상으로 Precision@5를 측정할 수 있는가?
- seed/mock 공고를 제외한 검색 품질 기준선을 유지할 수 있는가?
- co-occurrence query expansion을 켰을 때 Precision@5가 악화되지 않는가?
- 전역 `AND` query 전략이 실제 검색 품질에 어떤 영향을 주는가?

## 측정 기준

| 항목 | 값 |
| --- | --- |
| 측정일 | 2026-06-12 |
| 대상 API | `/jobs/search` |
| 대상 source | `JUMPIT`, `WANTED` |
| query 수 | 8 |
| 평가 기준 | Precision@5 |
| fetch limit | 20 |
| allowed source filter | enabled |
| query expansion support filter | `cooccurrence_count >= 3` |
| query expansion boost | `0.05` |
| raw CSV | local only |

## 구현 요약

### Query Expansion 후보 산출

검색어에 명시적으로 포함된 skill만 기준으로 co-occurrence 후보를 찾는다.

적용 정책:

| 항목 | 값 |
| --- | --- |
| period type | `MONTHLY` |
| period start | latest `skill_cooccurrence.period_start` |
| max expansions | 3 |
| minimum support | `cooccurrence_count >= 3` |
| ordering | `cooccurrence_count DESC`, `lift_score DESC`, `co_skill.name ASC` |
| already mentioned skill | 제외 |

### Ranking 반영

원 검색어는 기존 multi-match 검색을 유지한다.

co-occurrence 후보는 낮은 boost의 `should` query로만 추가한다.

```text
primary query:
- 기존 OR 기반 multi_match 유지

expansion query:
- description
- roleDetail
- industry
- operator: AND
- boost: 0.05
```

의도:

- 원 검색 결과 recall을 유지한다.
- co-occurrence는 ranking을 뒤집는 신호가 아니라 보조 신호로만 사용한다.
- 복합 skill expansion은 일부 token만 매칭되어 점수가 과하게 붙지 않도록 `AND`를 적용한다.

## 측정 명령

### Query Expansion OFF

backend 실행:

```bash
SPRING_PROFILES_ACTIVE=local \
ELASTICSEARCH_QUERY_EXPANSION_ENABLED=false \
./gradlew :backend:bootRun
```

Precision 측정:

```bash
ALLOWED_SOURCES=JUMPIT,WANTED \
BASE_URL=http://localhost:8080 \
LIMIT=5 \
FETCH_LIMIT=20 \
RUN_LABEL=query-expansion-off \
OUTPUT_FILE=/tmp/jobflow-search-precision-off.csv \
bash performance/elasticsearch/search-precision-baseline.sh
```

### Query Expansion ON

backend 실행:

```bash
SPRING_PROFILES_ACTIVE=local \
ELASTICSEARCH_QUERY_EXPANSION_ENABLED=true \
./gradlew :backend:bootRun
```

Precision 측정:

```bash
ALLOWED_SOURCES=JUMPIT,WANTED \
BASE_URL=http://localhost:8080 \
LIMIT=5 \
FETCH_LIMIT=20 \
RUN_LABEL=query-expansion-on \
OUTPUT_FILE=/tmp/jobflow-search-precision-on.csv \
bash performance/elasticsearch/search-precision-baseline.sh
```

### 비교

```bash
BEFORE_FILE=/tmp/jobflow-search-precision-off.csv \
AFTER_FILE=/tmp/jobflow-search-precision-on.csv \
bash performance/elasticsearch/search-precision-compare.sh
```

## 최종 결과

| metric | expansion off | expansion on | delta |
| --- | ---: | ---: | ---: |
| Precision@5 | 0.9000 | 0.9000 | 0.0000 |
| total hits | 40 | 40 | 0 |
| total relevant | 36 | 36 | 0 |
| filtered out | 10 | 10 | 0 |
| short query count | 0 | 0 | 0 |

판단:

| 항목 | 결과 |
| --- | --- |
| real-source-only filtering | PASS |
| full top-5 coverage | PASS |
| Precision@5 regression | PASS |
| co-occurrence expansion safety | PASS |

## 중간 실험: 전역 AND 전략 폐기

전역 `Operator.And`를 primary query에 적용하는 실험을 진행했다.

결과:

| metric | before | after |
| --- | ---: | ---: |
| Precision@5 | 0.9000 | 0.7250 |

해석:

- `C++` 같은 기술 토큰 query에는 일부 도움이 있었다.
- 그러나 `데이터 엔지니어`, `보안 엔지니어`, `쿠버네티스 플랫폼` 같은 자연어/직무형 query에서 recall이 크게 떨어졌다.
- 전역 `AND`는 query intent를 구분하지 못해 전체 검색 품질을 해친다.

결론:

- 전역 `Operator.And` 적용은 폐기한다.
- primary query는 기존 OR 기반 multi-match를 유지한다.
- expansion query에만 제한적으로 `AND`를 적용한다.
- 기술 토큰 기반 선택적 must 전략은 후속 검색 고도화 과제로 분리한다.

## 결론

co-occurrence query expansion은 현재 설정에서 Precision@5를 개선하지는 않았다.

다만 실제 수집 source만 대상으로 한 Precision@5 기준에서 regression 없이 동작하는 것을 확인했다.

따라서 이번 단계의 의미는 다음과 같다.

- Analytics batch 산출물을 검색 ranking layer에 연결했다.
- seed/mock 공고를 제외한 real-source-only Precision@5 측정 체계를 마련했다.
- query expansion을 보수적으로 적용해 검색 품질 악화를 방지했다.
- 전역 `AND` 전략의 위험을 수치로 확인하고 폐기했다.

## 후속 작업

| 항목 | 내용 |
| --- | --- |
| Query intent parser | 기술 토큰, 직무명, 일반어를 분리해 query별 must/should 전략을 다르게 적용 |
| Selective must strategy | `C++`, `Node.js`, `Kubernetes` 같은 기술 토큰에만 선택적으로 must 적용 |
| Expansion candidate quality | co-occurrence 후보가 실제 query intent와 맞는지 query별 분석 |
| Ranking signal tuning | quality score, skill match, freshness 신호와 함께 function score 재조정 |
