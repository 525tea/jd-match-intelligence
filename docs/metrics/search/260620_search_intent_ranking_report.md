# Search Intent Ranking Smoke Report

## 목적

검색어에 포함된 직무, 경력, 지역 의도를 Elasticsearch ranking 보조 신호로 반영했을 때 실제 `/jobs/search` 응답의 상위 결과가 의도와 맞는지 확인한다.

이번 smoke는 다음 질문에 답한다.

- `backend junior seoul`처럼 구조화 의도가 섞인 검색어에서 `BACKEND`, `JUNIOR`, `Seoul` 신호가 상위 결과에 반영되는가?
- `프론트엔드 React` 검색에서 `FRONTEND` 공고가 상위에 유지되는가?
- `데이터 엔지니어` 검색에서 데이터 직군 계열 공고가 상위에 노출되는가?

## 구현 요약

`JobSearchIntentParser`가 검색어에서 다음 신호를 추출한다.

| intent | 예시 |
| --- | --- |
| role | `backend`, `백엔드`, `프론트엔드`, `데이터 엔지니어` |
| careerLevel | `junior`, `주니어`, `신입`, `senior` |
| locationRegion | `seoul`, `서울`, `경기`, `판교` |

Elasticsearch primary query는 기존 multi-match를 유지한다.

추출된 intent는 hard filter가 아니라 낮은 `should` boost로만 반영한다.

| field | query type | boost |
| --- | --- | ---: |
| `role` | term | `2.8` |
| `careerLevel` | term | `1.8` |
| `locationRegion` | match | `1.4` |

의도:

- 기존 recall을 유지한다.
- 명시적인 직무/경력/지역 신호가 있으면 같은 relevance 후보 안에서 더 맞는 결과를 위로 올린다.
- 전역 `AND` 전략처럼 전체 검색 품질을 흔드는 변경은 피한다.

## 실행 명령

```bash
BASE_URL=http://127.0.0.1:8081/api \
bash performance/elasticsearch/search-intent-smoke.sh
```

## Smoke 결과

### `backend junior seoul`

| rank | id | role | career | region | title |
| ---: | ---: | --- | --- | --- | --- |
| 1 | 13 | `BACKEND` | `ANY` | `Seoul` | 백엔드 개발자 |
| 2 | 159 | `BACKEND` | `JUNIOR` | `Seoul` | ML Backend Engineer |
| 3 | 423 | `BACKEND` | `JUNIOR` | `Seoul` | 백엔드 엔지니어 |
| 4 | 315 | `BACKEND` | `JUNIOR` | `Seoul` | 백엔드 엔지니어 |
| 5 | 299 | `BACKEND` | `SENIOR` | `Seoul` | Senior Backend Engineer |

| metric | value |
| --- | ---: |
| expected role | `BACKEND` |
| expected role hits | `5 / 5` |
| expected career | `JUNIOR` |
| expected career hits | `3 / 5` |
| expected region | `Seoul` |
| expected region hits | `5 / 5` |

### `프론트엔드 React`

| rank | id | role | career | region | title |
| ---: | ---: | --- | --- | --- | --- |
| 1 | 297 | `FRONTEND` | `ANY` | `Incheon` | 초기 멤버 프론트엔드 개발자 (Next.js / React) |
| 2 | 285 | `FRONTEND` | `MID` | `Seoul` | [맘가이드] 앱 서비스 프론트엔드(React-native 중심) 개발 |
| 3 | 437 | `FRONTEND` | `JUNIOR` | `Seoul` | 프론트엔드 엔지니어 |
| 4 | 406 | `FRONTEND` | `JUNIOR` | `Seoul` | 프론트엔드 엔지니어 |
| 5 | 433 | `FRONTEND` | `JUNIOR` | `Seoul` | 프론트엔드 엔지니어 |

| metric | value |
| --- | ---: |
| expected role | `FRONTEND` |
| expected role hits | `5 / 5` |

### `데이터 엔지니어`

| rank | id | role | career | region | title |
| ---: | ---: | --- | --- | --- | --- |
| 1 | 348 | `DEVOPS` | `NEWCOMER` | `Seoul` | 데이터 플랫폼 엔지니어 |
| 2 | 345 | `DATA_SCIENTIST` | `MID` | `Seoul` | 데이터과학(딜리버리) |
| 3 | 279 | `DATA_SCIENTIST` | `JUNIOR` | `Seoul` | 데이터 사이언티스트 |
| 4 | 278 | `DATA_ANALYST` | `JUNIOR` | `Seoul` | 데이터 분석가 3년 이상 |
| 5 | 189 | `BACKEND` | `JUNIOR` | `Seoul` | 데이터 플로우 및 시스템 아키텍처 분석가 채용 |

| metric | value |
| --- | ---: |
| expected role | `DATA_ENGINEER`, `DATA_SCIENTIST`, `DATA_ANALYST` |
| expected role hits | `3 / 5` |

## Summary

| metric | value |
| --- | ---: |
| total query count | `3` |
| passed query count | `3` |
| limit | `5` |

## 판단

| 항목 | 결과 |
| --- | --- |
| backend role intent | PASS |
| backend career intent | PASS |
| backend region intent | PASS |
| frontend role intent | PASS |
| data role-family intent | PASS |
| public Gateway path smoke | PASS |

## 후속 확인

- Precision@5 before/after CSV 비교로 전체 query set regression 여부를 추가 확인한다.
- `데이터 엔지니어` query는 `데이터 플랫폼 엔지니어`가 `DEVOPS`로 분류되어 상위에 올라온다. 제목 relevance는 높지만 role taxonomy가 `DATA_ENGINEER`가 아니므로 role classifier 또는 data platform role mapping을 별도 튜닝 후보로 남긴다.
