# W4-15 Search Reindex Baseline Report

## 목적

실제 수집 공고를 Elasticsearch에 재색인하고, 검색 인덱스가 운영 가능한 구조로 전환됐는지 확인한다.

이번 리포트는 다음 질문에 답하기 위한 기준선이다.

- MySQL `jobs`에 저장된 실제 공고가 Elasticsearch 검색 index로 반영되는가?
- `jobflow-jobs`를 직접 index로 쓰지 않고 alias로 사용 가능한가?
- `jobflow-jobs-v1` physical index에 analyzer/mapping이 적용되는가?
- `C++`, `C#`, `Node.js`, `.NET`, `ASP.NET`, `Objective-C` 같은 기술명이 tokenizer에서 깨지지 않는가?
- `/jobs/search`가 실제 reindex 문서를 대상으로 결과를 반환하는가?
- 이후 W4-18 검색 튜닝의 before 기준선으로 Precision@K를 기록할 수 있는가?

## 측정 기준

| 항목             | 값                                                                                                                          |
| -------------- | -------------------------------------------------------------------------------------------------------------------------- |
| 측정일            | 2026-06-10                                                                                                                 |
| 대상 DB          | local MySQL `jobflow`                                                                                                      |
| 검색 alias       | `jobflow-jobs`                                                                                                             |
| physical index | `jobflow-jobs-v1`                                                                                                          |
| 주요 스크립트        | `reindex-real-jobs.sh`, `real-job-search-reindex-smoke.sh`, `search-precision-baseline.sh`, `job-search-analyzer-smoke.sh` |
| 기준 데이터         | W4-14 JUMPIT/WANTED 실제 수집 공고                                                                                               |

## 구현 요약

### Index Alias 구조

기존 구조:

```text
backend search/indexing -> jobflow-jobs index 직접 접근
```

변경 구조:

```text
backend search/indexing -> jobflow-jobs alias -> jobflow-jobs-v1 physical index
```

적용 내용:

- `JobSearchProperties`
  - `indexName`: 검색/색인이 바라보는 alias
  - `physicalIndexName`: 실제 생성되는 physical index
- `JobSearchIndexService`
  - physical index 생성
  - mapping/settings 적용
  - alias 연결
  - `isWriteIndex=true` 설정
- `JobSearchIndexingService`
  - 단건 색인
  - bulk 색인
  - reindex 후 refresh 지원
- `JobSearchReindexRunner`
  - `app.search.elasticsearch.reindex-on-startup=true`일 때 실행
  - DB `jobs`를 id 오름차순 batch로 조회
  - alias 기준으로 bulk 색인
  - 마지막에 alias refresh 수행

## Analyzer 변경

추가한 character filter:

| 입력 | normalized token |
| --- | --- |
| `C++` | `cplusplus` |
| `C#` | `csharp` |
| `Node.js` | `nodejs` |
| `.NET` | `dotnet` |
| `ASP.NET` | `aspnet` |
| `Objective-C` | `objectivec` |

기존 synonym filter는 유지한다.

| 입력 | synonym |
| --- | --- |
| `k8s` | `kubernetes` |
| `js` | `javascript` |
| `spring` | `spring boot` |
| `백엔드` | `backend` |
| `쿠버네티스` | `kubernetes` |
| `스프링` | `spring` |

## 실행 순서

### 1. Analyzer Smoke

```bash
bash performance/elasticsearch/job-search-analyzer-smoke.sh
```

결과 기록:

```text
### k8s
8
k
kubernetes
s

### Kubernetes
8
k
kubernetes
s
버네
쿠
티스

### 백엔드
backend
백
엔드

### backend
backend
백
엔드

### 스프링
spring
스프링

### spring boot
boot
spring

### js
javascript
js

### javascript
javascript
js

### C++
cplusplus

### C#
csharp

### Node.js
nodejs

### .NET
dotnet

### ASP.NET
aspnet

### Objective-C
objectivec

Analyzer smoke completed.
```

판단:

| 항목 | 결과 |
| --- | --- |
| synonym smoke | PASS |
| char filter smoke | PASS |

### 2. Legacy Index 제거 + Reindex

기존 `jobflow-jobs`가 index로 존재하는 로컬 환경에서는 최초 1회만 legacy index를 삭제한다.

```bash
DELETE_LEGACY_ALIAS_INDEX=true bash performance/elasticsearch/reindex-real-jobs.sh
```

이후 재실행:

```bash
bash performance/elasticsearch/reindex-real-jobs.sh
```

결과 기록:

```text
TODO: reindex runner output 전체 로그는 별도 보관.
reindex smoke의 ES alias count 기준으로 314건 색인 확인.
```

판단:

| 항목 | 값 |
| --- | ---: |
| indexedCount | 314 |
| reindex batch size | 100 |
| alias | `jobflow-jobs` |
| physical index | `jobflow-jobs-v1` |

### 3. Reindex Smoke

backend 서버 실행 후 smoke를 수행한다.

```bash
bash performance/elasticsearch/real-job-search-reindex-smoke.sh
```

결과 기록:

```text
ES_URL=http://localhost:9200
BASE_URL=http://localhost:8080
INDEX_ALIAS=jobflow-jobs
LIMIT=5

### Alias
{
  "jobflow-jobs-v1": {
    "aliases": {
      "jobflow-jobs": {
        "is_write_index": true
      }
    }
  }
}

### Document Count
{
  "count": 314,
  "_shards": {
    "total": 1,
    "successful": 1,
    "skipped": 0,
    "failed": 0
  }
}

All smoke queries returned at least one result.
Real job search reindex smoke completed.
```

판단:

| 항목 | 값 |
| --- | ---: |
| ES document count | 314 |
| `/jobs/search` smoke query count | 6 |
| failed query count | 0 |

Smoke query:

| query | 목적 |
| --- | --- |
| `백엔드 개발자` | 기본 backend 검색 |
| `프론트엔드 React` | frontend + 기술명 검색 |
| `C++ 개발자` | 특수문자 기술명 검색 |
| `Node.js 백엔드` | dot 기술명 검색 |
| `쿠버네티스 플랫폼` | synonym 검색 |
| `AI 엔지니어` | AI role 검색 |

### 4. Precision@K Baseline

```bash
BASE_URL=http://localhost:8080 LIMIT=5 \
  bash performance/elasticsearch/search-precision-baseline.sh
```

결과 기록:

```text
summary,total_hits,total_relevant,precision_at_5
summary,40,33,0.8250
```

요약:

| metric | value |
| --- | ---: |
| total_hits | 40 |
| total_relevant | 33 |
| precision@5 | 0.8250 |

Query별 결과:

| query | hits | relevant | precision@5 | note |
| --- | ---: | ---: | ---: | --- |
| 백엔드 개발자 | 5 | 5 | 1.00 | BACKEND 결과가 상위 5건을 채움 |
| 프론트엔드 React | 5 | 5 | 1.00 | FRONTEND/React 결과가 안정적으로 노출 |
| 쿠버네티스 플랫폼 | 5 | 5 | 1.00 | `k8s`, `Kubernetes`, 플랫폼 synonym 계열 확인 |
| C++ 개발자 | 5 | 3 | 0.60 | `C++` char filter는 동작하나 ANDROID/AI 개발자 혼입 |
| Node.js 백엔드 | 5 | 5 | 1.00 | Node.js 포함 백엔드 및 BACKEND 결과 노출 |
| 데이터 엔지니어 | 5 | 0 | 0.00 | DATA_ENGINEER strict label 기준에서는 전부 불일치 |
| AI 엔지니어 | 5 | 5 | 1.00 | role 불일치 결과도 title keyword로 relevant 처리 |
| 보안 엔지니어 | 5 | 5 | 1.00 | SECURITY 결과가 상위 5건을 채움 |

### Precision@K 상세 결과

```text
"백엔드 개발자",1,13,"백엔드 개발자","Example Backend",BACKEND,34.203819274902344,true
"백엔드 개발자",2,1,"백엔드 개발자","JobFlow",BACKEND,30.83559799194336,true
"백엔드 개발자",3,288,"백엔드 개발자","아토머스(마인드카페)",BACKEND,30.6686954498291,true
"백엔드 개발자",4,243,"백엔드개발자","필라넷",BACKEND,30.58419418334961,true
"백엔드 개발자",5,296,"백엔드 개발자(Nest.js)","키노라이츠",BACKEND,27.784860610961914,true
"프론트엔드 React",1,297,"초기 멤버 프론트엔드 개발자 (Next.js / React)","위플로우",FRONTEND,20.957887649536133,true
"프론트엔드 React",2,285,"[맘가이드] 앱 서비스 프론트엔드(React-native 중심) 개발","인포그린",FRONTEND,18.89227867126465,true
"프론트엔드 React",3,433,"프론트엔드 엔지니어","에이아이구루",FRONTEND,16.48554229736328,true
"프론트엔드 React",4,437,"프론트엔드 엔지니어","그로잉세일즈",FRONTEND,15.8696928024292,true
"프론트엔드 React",5,406,"프론트엔드 엔지니어","로민",FRONTEND,15.486553192138672,true
"쿠버네티스 플랫폼",1,11,"Kubernetes 플랫폼 엔지니어","Example Cloud",DEVOPS,92.68411254882812,true
"쿠버네티스 플랫폼",2,12,"k8s 플랫폼 엔지니어","Example Infra",DEVOPS,34.44712448120117,true
"쿠버네티스 플랫폼",3,17,"플랫폼 엔지니어","JobFlow",BACKEND,23.95636558532715,true
"쿠버네티스 플랫폼",4,348,"데이터 플랫폼 엔지니어","이터널그룹",DEVOPS,23.1634578704834,true
"쿠버네티스 플랫폼",5,418,"시니어 플랫폼 엔지니어","위밋모빌리티",DEVOPS,23.1634578704834,true
"C++ 개발자",1,280,"Automotive Test Manager","티맵모빌리티",ANDROID,17.570240020751953,false
"C++ 개발자",2,332,"임베디드 Embedded C/C++ IT SW 개발자 2년이상","파더스랩",EMBEDDED_SOFTWARE,17.398067474365234,true
"C++ 개발자",3,426,".Net 개발자","솔탑",HARDWARE_ENGINEER,10.473459243774414,true
"C++ 개발자",4,273,"AI 개발자","시옷",AI_ENGINEER,9.857610702514648,false
"C++ 개발자",5,311,"Software Engineer (개발자)","크픽",SOFTWARE_ENGINEER,9.82868766784668,true
"Node.js 백엔드",1,13,"백엔드 개발자","Example Backend",BACKEND,34.203819274902344,true
"Node.js 백엔드",2,282,"[모시러] 서비스 백엔드 개발자 | Node.js · React · 운영 자동화","버틀러",BACKEND,26.969928741455078,true
"Node.js 백엔드",3,299,"Senior Backend Engineer","오지큐(OGQ)",BACKEND,26.650575637817383,true
"Node.js 백엔드",4,315,"백엔드 엔지니어","씨드로닉스",BACKEND,26.650575637817383,true
"Node.js 백엔드",5,423,"백엔드 엔지니어","에이아이구루",BACKEND,26.650575637817383,true
"데이터 엔지니어",1,348,"데이터 플랫폼 엔지니어","이터널그룹",DEVOPS,20.359298706054688,false
"데이터 엔지니어",2,345,"데이터과학(딜리버리)","우아한형제들(배달의민족)",DATA_SCIENTIST,15.619154930114746,false
"데이터 엔지니어",3,279,"데이터 사이언티스트","엔라이즈(NRISE)",DATA_SCIENTIST,14.552993774414062,false
"데이터 엔지니어",4,278,"데이터 분석가 3년 이상","큐라엘",DATA_ANALYST,12.870807647705078,false
"데이터 엔지니어",5,266,"[오케스트로AGI] Cloud 데이터 분석가(Cloud AIOps)","오케스트로",DATA_ANALYST,11.080558776855469,false
"AI 엔지니어",1,259,"AI Agent Engineer (AI 에이전트 엔지니어)","오토메타",BACKEND,15.538139343261719,true
"AI 엔지니어",2,369,"AI 엔지니어 채용","레플리",AI_ENGINEER,14.934346199035645,true
"AI 엔지니어",3,212,"AI 컴파일러 엔지니어","에너자이",BACKEND,14.911781311035156,true
"AI 엔지니어",4,436,"의류 훼손 탐지 AI 엔지니어","리터놀",AI_ENGINEER,14.142354965209961,true
"AI 엔지니어",5,223,"AI 모델 개발 엔지니어","클로봇",PM,13.820503234863281,true
"보안 엔지니어",1,402,"보안 솔루션 엔지니어 경력직 채용","26도라인",SECURITY,15.61795711517334,true
"보안 엔지니어",2,376,"정보보안","미디어로그",SECURITY,14.248330116271973,true
"보안 엔지니어",3,367,"보안칩 SW 개발자","아이씨티케이홀딩스(ICTK)",SECURITY,11.272332191467285,true
"보안 엔지니어",4,411,"인프라 관리 및 보안 관리","나이스지니데이타",SECURITY,11.139623641967773,true
"보안 엔지니어",5,194,"해양 사이버보안 전문가(3년 이상)","싸이터",SECURITY,10.684062004089355,true
```

## 발견한 결함과 수정

### Non-web reindex runner와 SecurityConfig 충돌

증상:

```text
Parameter 0 of method securityFilterChain required a bean of type HttpSecurity that could not be found
```

원인:

- reindex runner는 `spring.main.web-application-type=none`으로 실행된다.
- `SecurityConfig.securityFilterChain()`은 servlet web context에서만 제공되는 `HttpSecurity`를 요구한다.

수정:

- `securityFilterChain()`에 `@ConditionalOnWebApplication(type = SERVLET)`를 적용했다.
- non-web runner에서도 `PasswordEncoder` 등 일반 bean은 유지된다.

### Legacy index와 alias 이름 충돌

증상:

```text
Invalid alias name [jobflow-jobs]: an index or data stream exists with the same name as the alias
```

원인:

- 기존 W3/W4 검색 작업에서 `jobflow-jobs`가 실제 index로 생성되어 있었다.
- W4-15부터 `jobflow-jobs`는 alias로 사용한다.
- Elasticsearch는 같은 이름을 index와 alias에 동시에 사용할 수 없다.

수정:

- `reindex-real-jobs.sh`에 preflight를 추가했다.
- 기본값은 legacy index를 삭제하지 않고 실패한다.
- 명시적으로 `DELETE_LEGACY_ALIAS_INDEX=true`를 준 경우에만 기존 `jobflow-jobs` index를 삭제한다.

## 열린 리스크

| 리스크 | 현재 판단 | 후속 작업 |
| --- | --- | --- |
| Precision@K baseline은 role/keyword 휴리스틱 기반 | 완전한 relevance label은 아님 | W4-18에서 튜닝 전후 비교 기준으로 사용 |
| co-occurrence query expansion 미적용 | 이번 작업은 baseline 수집까지 | W4-18에서 minimum support filter와 함께 적용 |
| quality score ranking 미적용 | 이번 작업 범위 아님 | 추천/검색 ranking 후보로 별도 검증 |
| alias 전환은 v1 생성/연결까지만 구현 | v2 전환 자동화는 아직 없음 | 다음 reindex 개선 시 alias swap 명령/서비스 추가 |
| 검색 결과 relevance는 source 대표성에 영향 받음 | JUMPIT/WANTED 중심 데이터 기준 | SARAMIN 실제 수집 후 baseline 재측정 |
| 데이터 직군 query relevance 기준 미흡 | `데이터 엔지니어` query가 DATA_SCIENTIST/DATA_ANALYST/DEVOPS 결과를 반환했으나 strict label 기준 0점 | W4-18에서 query별 expected role/keyword label을 재검토 |
| 일부 seed/mock 공고 포함 | `Example Backend`, `JobFlow`, `Example Cloud` 등 기존 DB seed/mock 공고가 ES count와 검색 결과에 포함 | 필요 시 real-source-only baseline script를 추가 |

## 최종 판단

W4-15 기준으로 실제 수집 공고가 Elasticsearch alias index에 재색인되고,
특수문자 기술명 analyzer smoke와 /jobs/search smoke가 통과했다.

`jobflow-jobs` alias는 `jobflow-jobs-v1` physical index를 `is_write_index=true`로 가리키며,
alias 기준 document count는 314건이다. `/jobs/search` smoke query 6개는 모두 결과를 반환했다.

Precision@5 기준선은 0.8250으로 기록했으며,
W4-18 검색 튜닝은 이 수치를 before 기준으로 삼아 진행한다.
