# Search NDCG@10 Quality Report

## 목적

`/jobs/search`의 상위 결과 품질을 binary `Precision@5`에서 graded relevance 기반 `NDCG@10`으로 확장해 측정한다.

이번 평가는 다음 질문에 답한다.

- 주요 직무/기술 query에서 현재 Elasticsearch ranking의 top 10 품질은 어느 정도인가?
- 검색 후보군에 상세 공고의 skill/description 텍스트를 붙여 TF-IDF skill similarity로 재정렬하면 ranking 품질이 개선되는가?
- 기존 속도 개선 결과가 검색 품질 저하를 동반하지 않는가?

## 측정 기준

| 항목 | 값 |
| --- | --- |
| 측정일 | `YYYY-MM-DD` |
| 대상 API | `/jobs/search`, `/jobs/{id}` |
| 평가 기준 | `NDCG@10` |
| fetch limit | `40` |
| query 수 | `9` |
| 평가 스크립트 | `performance/elasticsearch/search-ndcg10-evaluate.sh` |
| raw output | local only |

## 실행 명령

```bash
mkdir -p artifacts/search-quality

BASE_URL=http://127.0.0.1:8080 \
RUN_LABEL=260630_search_ndcg10 \
OUTPUT_FILE=artifacts/search-quality/260630_search_ndcg10_rows.csv \
SUMMARY_FILE=artifacts/search-quality/260630_search_ndcg10_summary.json \
bash performance/elasticsearch/search-ndcg10-evaluate.sh
```

Gateway `http://127.0.0.1:8081/api`는 상세 조회를 반복하는 평가 스크립트에서 rate limit `429`가 발생할 수 있다. ranking 품질 측정은 backend direct endpoint를 사용한다.

threshold를 gate로 사용할 때:

```bash
BASE_URL=http://127.0.0.1:8080 \
MIN_NDCG=0.80 \
FAIL_ON_THRESHOLD=true \
bash performance/elasticsearch/search-ndcg10-evaluate.sh
```

## 최종 결과

| metric | value |
| --- | ---: |
| query count | `TODO` |
| baseline mean NDCG@10 | `TODO` |
| skill TF-IDF rerank mean NDCG@10 | `TODO` |
| mean delta | `TODO` |

## Query별 결과

| query | baseline NDCG@10 | skill TF-IDF rerank NDCG@10 | delta |
| --- | ---: | ---: | ---: |
| `backend junior seoul` | `TODO` | `TODO` | `TODO` |
| `백엔드 개발자` | `TODO` | `TODO` | `TODO` |
| `프론트엔드 React` | `TODO` | `TODO` | `TODO` |
| `쿠버네티스 플랫폼` | `TODO` | `TODO` | `TODO` |
| `C++ 개발자` | `TODO` | `TODO` | `TODO` |
| `Node.js 백엔드` | `TODO` | `TODO` | `TODO` |
| `데이터 엔지니어` | `TODO` | `TODO` | `TODO` |
| `AI 엔지니어` | `TODO` | `TODO` | `TODO` |
| `보안 엔지니어` | `TODO` | `TODO` | `TODO` |

## 판정 기준

| 항목 | 기준 | 결과 |
| --- | --- | --- |
| Search API success | 모든 query가 성공 응답을 반환 | `TODO` |
| Detail API success | TF-IDF 평가용 상세 조회가 성공 | `TODO` |
| Baseline quality | baseline mean NDCG@10이 threshold 이상 | `TODO` |
| Rerank comparison | TF-IDF rerank가 baseline 대비 악화/개선되는지 확인 | `TODO` |

## 해석

`TODO`: baseline이 충분히 높으면 현재 Elasticsearch ranking 품질을 성능 개선 결과와 함께 제시한다.

`TODO`: TF-IDF rerank가 개선되면 production ranking 후보로 분리하고, 악화되면 현재 방식의 근거와 TF-IDF 적용 한계를 기록한다.
