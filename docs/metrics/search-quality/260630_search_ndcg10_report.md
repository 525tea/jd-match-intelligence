# Search NDCG@10 Quality Report

## 목적

`/jobs/search`의 검색 품질을 기존 binary `Precision@5`에서 graded relevance 기반 `NDCG@10`으로 확장해 측정한다.

이번 평가는 다음 질문에 답한다.

- 주요 직무/기술 query에서 현재 Elasticsearch ranking의 top 10 품질은 어느 정도인가?
- 검색 후보군에 상세 공고의 skill/description 텍스트를 붙여 TF-IDF skill similarity로 재정렬하면 ranking 품질이 개선되는가?
- 성능 개선 이후 검색 품질을 함께 설명할 수 있는 정량 근거가 있는가?

## 측정 기준

| 항목 | 값 |
| --- | --- |
| 측정일 | 2026-06-30 |
| 대상 API | `/jobs/search`, `/jobs/{id}` |
| 평가 기준 | `NDCG@10` |
| fetch limit | `40` |
| query 수 | `9` |
| 평가 스크립트 | `performance/elasticsearch/search-ndcg10-evaluate.sh` |
| raw output | local only: `artifacts/search-quality/260630_search_ndcg10_real_summary.json`, `artifacts/search-quality/260630_search_ndcg10_real_rows.csv` |

측정은 backend direct `http://127.0.0.1:8080` 기준으로 수행했다. Gateway `http://127.0.0.1:8081/api`는 상세 조회를 query당 최대 40건 수행하는 평가 스크립트에서 rate limit `429`가 발생할 수 있으므로, ranking 품질 측정에는 backend direct endpoint를 사용한다.

## 데이터 상태

측정 시점의 공고 source 분포:

| source | job count |
| --- | ---: |
| `NOTIFICATION_MOCK_LOAD` | `500` |
| `WANTED` | `155` |
| `JUMPIT` | `150` |
| `SEARCH_BASELINE` | `5` |
| `DAILY_DIGEST_SMOKE` | `4` |
| `ANALYTICS_SMOKE` | `3` |
| `CANONICAL_SMOKE` | `2` |
| `NOTIFICATION_SMOKE` | `2` |
| `MANUAL` | `1` |

실제 source 기준 open 공고 수는 `285`건이다.

## 실행 명령

```bash
mkdir -p artifacts/search-quality

BASE_URL=http://127.0.0.1:8080 \
RUN_LABEL=260630_search_ndcg10_real \
OUTPUT_FILE=artifacts/search-quality/260630_search_ndcg10_real_rows.csv \
SUMMARY_FILE=artifacts/search-quality/260630_search_ndcg10_real_summary.json \
bash performance/elasticsearch/search-ndcg10-evaluate.sh
```

## 최종 결과

| metric | value |
| --- | ---: |
| query count | `9` |
| baseline mean NDCG@10 | `0.8823` |
| skill TF-IDF rerank mean NDCG@10 | `0.7946` |
| mean delta | `-0.0877` |

## Query별 결과

| query | result count | baseline NDCG@10 | skill TF-IDF rerank NDCG@10 | delta | top 10 relevance grades |
| --- | ---: | ---: | ---: | ---: | --- |
| `backend junior seoul` | `40` | `1.0000` | `1.0000` | `+0.0000` | `3,3,3,3,3,3,3,3,3,3` |
| `백엔드 개발자` | `40` | `1.0000` | `1.0000` | `+0.0000` | `3,3,3,3,3,3,3,3,3,3` |
| `프론트엔드 React` | `40` | `1.0000` | `0.8122` | `-0.1878` | `3,3,3,3,3,3,3,3,3,3` |
| `쿠버네티스 플랫폼` | `40` | `0.7673` | `0.4402` | `-0.3271` | `3,3,2,3,0,2,0,0,0,0` |
| `C++ 개발자` | `40` | `0.5678` | `0.7898` | `+0.2220` | `3,0,0,0,2,0,0,2,2,0` |
| `Node.js 백엔드` | `28` | `1.0000` | `0.9306` | `-0.0694` | `3,3,3,3,3,3,3,3,3,3` |
| `데이터 엔지니어` | `40` | `0.9573` | `0.6840` | `-0.2733` | `3,3,3,2,2,2,2,0,2,2` |
| `AI 엔지니어` | `40` | `0.6482` | `0.7439` | `+0.0957` | `3,2,2,3,2,2,2,3,2,2` |
| `보안 엔지니어` | `40` | `1.0000` | `0.7506` | `-0.2494` | `3,3,3,3,3,3,3,3,3,3` |

## 해석

baseline mean NDCG@10은 `0.8823`으로, 실제 source 285건 기준 주요 검색 query의 상위 ranking 품질이 안정적인 편임을 확인했다.

`backend junior seoul`, `백엔드 개발자`, `프론트엔드 React`, `Node.js 백엔드`, `보안 엔지니어`는 baseline NDCG@10 `1.0000`으로 top 10 전부가 grade 3 관련 결과였다. `데이터 엔지니어`도 `0.9573`으로 높은 품질을 보였다.

반면 `C++ 개발자`는 baseline NDCG@10 `0.5678`, `AI 엔지니어`는 `0.6482`, `쿠버네티스 플랫폼`은 `0.7673`으로 개선 여지가 남아 있다.

따라서 현재 수치는 다음처럼 해석한다.

- 현재 수집 데이터가 커버하는 주요 직무/기술 query에서는 검색 상위 품질이 안정적이다.
- `C++`, `AI`, `쿠버네티스 플랫폼` query는 top 10 안에 관련도 낮은 결과가 섞여 있어 후속 ranking/alias 튜닝 대상이다.
- TF-IDF skill rerank는 `C++ 개발자`와 `AI 엔지니어`에서는 개선을 보였지만, 전체 평균은 baseline보다 낮았다. 따라서 현재 TF-IDF rerank를 production ranking에 바로 적용하는 것은 적절하지 않다.

## 판정

| 항목 | 결과 | 근거 |
| --- | --- | --- |
| Search API success | PASS | 9개 query 모두 평가 완료 |
| Detail API success | PASS | `FETCH_DETAILS_FOR_TFIDF=true` 기준 상세 조회 포함 평가 완료 |
| Baseline quality | PASS | baseline mean NDCG@10 `0.8823` |
| Query coverage | PASS | 모든 query에서 결과 반환, `Node.js 백엔드`도 28건 반환 |
| TF-IDF rerank comparison | FAIL TO ADOPT | mean delta `-0.0877`, production 적용 보류 |

## 후속 작업

| 항목 | 이유 | 방향 |
| --- | --- | --- |
| `C++ 개발자` ranking 보강 | baseline NDCG@10 `0.5678` | C/C++ alias, embedded/software role intent, title exact boost 검토 |
| `AI 엔지니어` ranking 보강 | baseline NDCG@10 `0.6482` | AI role classifier와 `AI Vision`, `AI 모델`, `AI Agent` intent 보정 |
| `쿠버네티스 플랫폼` ranking 보강 | baseline NDCG@10 `0.7673` | platform/devops intent와 frontend/backend title noise 분리 |
| TF-IDF rerank 보류 | 평균 delta `-0.0877` | production ranking 적용하지 않고 query별 보조 signal로만 재검토 |

## 결론

NDCG@10 평가 도구는 정상 동작했고, 실제 source 285건 기준 검색 품질을 query별로 분해해 볼 수 있게 됐다.

현재 Elasticsearch baseline ranking은 mean NDCG@10 `0.8823`으로 최종 검색 품질 기준선으로 사용할 수 있다. 다만 TF-IDF skill rerank는 전체 평균을 낮추므로 production ranking에 적용하지 않는다. 후속 개선은 `C++`, `AI`, `쿠버네티스 플랫폼` query의 ranking noise를 줄이는 방향이 적절하다.
