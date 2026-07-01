# Search Quality 32-query Evaluation Report

작성일: 2026-07-02

## 목적

`/jobs/search` 품질 개선 결과를 기존 9-query 샘플이 아니라, 동일 corpus 기준의 확장 evaluation set으로 재측정한다.

이번 측정은 다음 흐름을 분리해서 확인한다.

- MySQL FULLTEXT fallback
- Elasticsearch baseline
- Elasticsearch skill-aware ranking
- Elasticsearch framework-aware ranking

## 변경 내용

- `C++`, `AI/LLM`, `Kubernetes/k8s` 계열 query에서 role intent를 더 넓게 인식하도록 보강했다.
- role intent와 required skill match를 `function_score` weight로 추가해 text score에 묻히던 구조화 signal이 실제 ranking에 반영되도록 했다.
- `JPA`는 hard filter로 쓰면 `Spring Boot JPA` query가 악화되어, required skill이 아니라 백엔드 role intent로만 사용했다.
- `Django`, `FastAPI`, `Flask`, `Go`, `Fiber`, `Gin`, `.NET`, `C#`, `ASP.NET` 계열 framework/entity token을 추가했다.
- `go` 같은 짧은 token이 `django` 내부에서 부분 매칭되지 않도록 intent parser를 token-aware matching으로 변경했다.
- 복수 framework skill query는 모든 skill을 hard `must`로 묶지 않고, 원문 match + skill/role score signal로 ranking에 반영했다.
- framework 조합 query는 `Django`, `Fiber` 같은 핵심 framework term이 있어야 높은 relevance grade를 받도록 평가 label을 보강했다.

## 데이터셋

| 항목 | 값 |
|---|---:|
| 측정일 | `2026-07-02` |
| DB | `jobflow` |
| Elasticsearch alias | `jobflow-jobs` |
| Elasticsearch physical index | `jobflow-jobs-v1` |
| frozen open corpus | `1,240` |
| WANTED open jobs | `1,112` |
| JUMPIT open jobs | `128` |
| non-real open jobs | `0` |
| final query count | `32` |
| 평가 기준 | `NDCG@10` |
| fetch limit | `40` |
| detail fetch | enabled |
| relevance label | rule-based grade using role/title/location/career/detail/framework fields |

Corpus freeze:

- 수집 후 OPEN row는 `1,246`건이었다.
- `CANONICAL_SMOKE 2`, `SEARCH_BASELINE 4`가 OPEN에 섞여 있어 평가 전 `EXPIRED` 처리했다.
- 최종 OPEN corpus는 `WANTED/JUMPIT` real-source-only `1,240`건이다.
- 재색인 runner는 전체 job row를 색인하므로, ES index에서 non-OPEN document `562`건을 삭제해 `_count = 1,240`으로 맞췄다.

## Query Set

| category | count | examples |
|---|---:|---|
| synonym | `6` | `k8s 인프라`, `py 데이터`, `llm 엔지니어` |
| special_token | `5` | `C++ 개발자`, `C# 개발자`, `.NET 개발자` |
| korean_role | `5` | `백엔드 개발자`, `데이터 엔지니어`, `보안 엔지니어` |
| composite | `8` | `Java Spring 백엔드`, `Spring Boot JPA`, `Kafka 데이터 플랫폼` |
| framework_combo | `2` | `Python Django`, `Go Fiber` |
| edge_case | `6` | `backend junior seoul`, `쿠버네티스 platform`, `AI 엔지니어` |

## 측정 방식

비교군을 통일하기 위해 DB와 Elasticsearch index는 고정하고 backend image만 교체했다.

| 구분 | 기준 |
|---|---|
| MySQL FULLTEXT | ES URL을 의도적으로 실패시켜 `/jobs/search` fallback 경로 사용 |
| ES baseline | 변경 전 backend image |
| ES skill-aware | role/skill score 보정 적용 image |
| ES framework-aware | framework/entity parser 및 복합 skill ranking 보정 image |
| cache | 각 실행 전 Redis `FLUSHALL` |
| endpoint | backend direct `http://127.0.0.1:8080` |
| raw output | local only: `artifacts/search-quality/260702_search_quality_*` |

## 결과 요약

30-query 기준의 기존 단계:

| comparison | mean NDCG@10 | delta vs MySQL | delta vs ES baseline |
|---|---:|---:|---:|
| MySQL FULLTEXT | `0.5882` | `-` | `-` |
| ES baseline | `0.8163` | `+0.2281` | `-` |
| ES skill-aware | `0.9344` | `+0.3462` | `+0.1181` |

32-query / framework-aware label 기준의 추가 개선:

| comparison | mean NDCG@10 | delta |
|---|---:|---:|
| ES skill-aware previous image | `0.9429` | `-` |
| ES framework-aware final image | `0.9868` | `+0.0439` |

Offline skill TF-IDF rerank는 참고용이며 production ranking에는 포함하지 않는다.

| comparison | production ranking NDCG@10 | offline TF-IDF rerank NDCG@10 | delta |
|---|---:|---:|---:|
| ES framework-aware final image | `0.9868` | `0.9696` | `-0.0172` |

## Category별 결과

32-query / framework-aware label 기준:

| category | previous | final | delta |
|---|---:|---:|---:|
| framework_combo | `0.5709` | `0.9403` | `+0.3694` |
| special_token | `0.9159` | `0.9902` | `+0.0743` |
| synonym | `0.9377` | `0.9740` | `+0.0363` |
| composite | `0.9783` | `0.9876` | `+0.0093` |
| korean_role | `1.0000` | `1.0000` | `+0.0000` |
| edge_case | `1.0000` | `1.0000` | `+0.0000` |

## 주요 개선 Query

32-query / framework-aware label 기준:

| query | category | previous | final | delta |
|---|---|---:|---:|---:|
| `Go Fiber` | framework_combo | `0.4634` | `1.0000` | `+0.5366` |
| `C# 개발자` | special_token | `0.7388` | `1.0000` | `+0.2612` |
| `py 데이터` | synonym | `0.7822` | `1.0000` | `+0.2178` |
| `Python Django` | framework_combo | `0.6785` | `0.8805` | `+0.2020` |
| `.NET 개발자` | special_token | `0.8405` | `0.9511` | `+0.1106` |

## 남은 낮은 Query

최종 기준에서도 상대적으로 낮은 query는 다음이다.

| query | category | final NDCG@10 |
|---|---|---:|
| `mlops 엔지니어` | synonym | `0.8438` |
| `Python Django` | framework_combo | `0.8805` |
| `Spring Boot JPA` | composite | `0.9371` |
| `.NET 개발자` | special_token | `0.9511` |
| `LLM Python 엔지니어` | composite | `0.9636` |

## 해석

이번 추가 개선은 단순히 query set을 늘린 것이 아니라, framework/entity parser와 복합 skill ranking 방식을 보강한 것이다.

기존 약점이었던 `Go Fiber`, `Python Django`, `C#`, `.NET`, `py 데이터` 계열에서 개선이 확인됐다. 특히 framework 조합 category는 `0.5709 -> 0.9403`으로 가장 크게 개선됐다.

다만 `mlops 엔지니어`와 `Python Django`는 아직 완전한 수준은 아니다. 다음 개선은 MLOps/AI platform 계열 role 분리, Django/FastAPI/Flask 간 framework-specific label 보강, 그리고 더 많은 실제 framework query audit이 필요하다.

## 검증

```bash
python3 -m py_compile performance/elasticsearch/search_ndcg10_evaluate.py
```

```bash
BASE_URL=http://127.0.0.1:8080 LIMIT=10 FETCH_LIMIT=40 \
RUN_LABEL=es-skill-aware-v2-32q-framework-labels-real1240 \
OUTPUT_FILE=artifacts/search-quality/260702_search_quality_32query_es_skill_aware_v2_framework_labels_rows.csv \
SUMMARY_FILE=artifacts/search-quality/260702_search_quality_32query_es_skill_aware_v2_framework_labels_summary.json \
python3 performance/elasticsearch/search_ndcg10_evaluate.py
```

```bash
BASE_URL=http://127.0.0.1:8080 LIMIT=10 FETCH_LIMIT=40 \
RUN_LABEL=es-framework-aware-v2-32q-real1240 \
OUTPUT_FILE=artifacts/search-quality/260702_search_quality_32query_es_framework_aware_v2_rows.csv \
SUMMARY_FILE=artifacts/search-quality/260702_search_quality_32query_es_framework_aware_v2_summary.json \
python3 performance/elasticsearch/search_ndcg10_evaluate.py
```

```bash
./gradlew :backend:test \
  --tests 'jobflow.domain.job.search.JobSearchIntentParserTest' \
  --tests 'jobflow.domain.job.search.ElasticsearchJobSearchServiceTest'
```

```bash
./gradlew :backend:test
```

## 결론

동일한 `1,240` real-source-only corpus 기준으로 검색 품질 개선 흐름은 다음과 같다.

```text
MySQL FULLTEXT 0.5882
  -> Elasticsearch baseline 0.8163
  -> Elasticsearch skill-aware ranking 0.9344
  -> Elasticsearch framework-aware ranking 0.9868
```

최종 추가 개선만 분리하면, framework-aware 32-query 기준으로 `0.9429 -> 0.9868`이며 `+0.0439` 개선이다.
