# Search Quality Gate Report

## 목적

검색 의도 ranking 튜닝 이후 `/jobs/search`가 주요 직무/기술 query에서 일정 수준 이상의 상위 결과 품질을 유지하는지 확인한다.

이번 리포트는 다음 질문에 답한다.

- Gateway 경유 `/jobs/search`가 실제 검색 결과를 안정적으로 반환하는가?
- 직무/기술/자연어 query set 기준 `Precision@5`가 gate threshold 이상인가?
- `C++ 개발자`처럼 명시 기술 토큰이 있는 query에서 선택적 must 전략이 검색 품질을 개선하는가?
- 검색 품질 튜닝이 다른 주요 query 품질을 악화시키지 않는가?

## 측정 기준

| 항목 | 값 |
| --- | --- |
| 측정일 | 2026-06-20 |
| 대상 API | `/api/jobs/search` |
| Gateway URL | `http://127.0.0.1:8081/api` |
| query 수 | `9` |
| 평가 기준 | `Precision@5` |
| fetch limit | `20` |
| gate threshold | `0.80` |
| 측정 스크립트 | `performance/elasticsearch/search-quality-gate.sh` |
| raw output | local only |

## 구현 요약

검색어에서 감지한 intent를 Elasticsearch ranking에 보조 신호로 반영한다.

| intent | 반영 방식 | 목적 |
| --- | --- | --- |
| role | `should` boost | 직무 의도와 맞는 공고를 상위로 보정 |
| careerLevel | `should` boost | 경력 의도와 맞는 공고를 상위로 보정 |
| locationRegion | `should` boost | 지역 의도와 맞는 공고를 상위로 보정 |
| explicit skill token | selective `must` | `C++`, `Node.js`, `Kubernetes`처럼 명시된 기술 토큰 노이즈 감소 |

중요한 점은 전역 `AND` 전략을 쓰지 않는 것이다.

기본 multi-match recall은 유지하고, 명시 기술 토큰이 있을 때만 선택적으로 must 조건을 추가한다.

## 실행 명령

```bash
BASE_URL=http://127.0.0.1:8081/api \
bash performance/elasticsearch/search-quality-gate.sh
```

CSV 파일까지 남길 때:

```bash
BASE_URL=http://127.0.0.1:8081/api \
OUTPUT_FILE=/tmp/jobflow-search-quality-gate-after-skill-must.csv \
bash performance/elasticsearch/search-quality-gate.sh
```

## 최종 결과

| metric | value |
| --- | ---: |
| total queries | `9` |
| total hits | `45` |
| total relevant | `41` |
| Precision@5 | `0.9111` |
| short query count | `0` |
| fetch limit | `20` |
| gate threshold | `0.80` |

판정:

| 항목 | 결과 |
| --- | --- |
| Search API success | PASS |
| Precision@5 gate | PASS |
| Full top-5 coverage | PASS |
| Regression guard | PASS |

## Query별 결과

| query | relevant / top 5 | precision |
| --- | ---: | ---: |
| `backend junior seoul` | `5 / 5` | `1.0000` |
| `백엔드 개발자` | `5 / 5` | `1.0000` |
| `프론트엔드 React` | `5 / 5` | `1.0000` |
| `쿠버네티스 플랫폼` | `4 / 5` | `0.8000` |
| `C++ 개발자` | `2 / 5` | `0.4000` |
| `Node.js 백엔드` | `5 / 5` | `1.0000` |
| `데이터 엔지니어` | `5 / 5` | `1.0000` |
| `AI 엔지니어` | `5 / 5` | `1.0000` |
| `보안 엔지니어` | `5 / 5` | `1.0000` |

## 주요 개선 확인

선택적 skill must 적용 전후 비교:

| query | before | after | delta |
| --- | ---: | ---: | ---: |
| overall Precision@5 | `0.8889` | `0.9111` | `+0.0222` |
| `C++ 개발자` relevant top 5 | `1 / 5` | `2 / 5` | `+1` |
| `데이터 엔지니어` relevant top 5 | `4 / 5` | `5 / 5` | `+1` |

확인된 변화:

- `C++ 개발자` query에서 명시 기술 토큰 관련 결과가 증가했다.
- `Node.js 백엔드` query는 top 1에 실제 Node.js 백엔드 공고가 노출됐다.
- `데이터 엔지니어` query는 top 5 전체가 relevant로 판정됐다.
- 전체 Precision@5가 gate threshold `0.80`을 넘는 `0.9111`로 유지됐다.

## 남은 리스크

| 항목 | 내용 | 후속 후보 |
| --- | --- | --- |
| `C++ 개발자` precision | 개선됐지만 `2 / 5`로 여전히 낮다 | C++ alias/role classifier 추가 튜닝 |
| `쿠버네티스 플랫폼` precision | `4 / 5`, frontend team lead가 한 건 섞인다 | platform query intent와 frontend title noise 분리 |
| seed/source 혼재 | `SEARCH_BASELINE`, `MANUAL`, `ANALYTICS_SMOKE`가 top 결과에 포함된다 | real-source-only gate 별도 운영 |

## 결론

검색 intent ranking과 selective skill must 적용 후, Gateway 기준 검색 품질 gate는 통과했다.

전체 Precision@5는 `0.9111`로 threshold `0.80`을 상회했고, 명시 기술 토큰 query의 일부 품질 개선도 확인했다.

다만 `C++ 개발자` query는 아직 낮은 precision을 보이므로, 다음 검색 품질 튜닝에서는 C++ alias와 role classifier를 함께 조정하는 것이 적절하다.
