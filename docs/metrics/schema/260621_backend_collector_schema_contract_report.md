# Backend/Collector Schema Contract Report

작성일: 2026-06-21

## 목적

backend와 collector는 같은 MySQL schema를 공유하지만, 각 모듈이 JPA entity를 별도로 관리한다.

이번 작업은 common module 분리까지 확장하지 않고, 현재 MVP 단계에서 필요한 schema ownership과 drift 방지 전략을 자동 테스트로 고정하는 것을 목표로 한다.

## 작업 범위

- backend를 Flyway migration owner로 정의
- collector를 backend-owned schema consumer로 정의
- collector가 backend Flyway migration으로 생성된 MySQL schema에 JPA mapping을 검증하도록 테스트 추가
- backend에서 공유 table/column/index contract를 MySQL 기준으로 검증하는 테스트 추가
- common module 분리는 후속 고도화 후보로 보류

## 공유 Schema Ownership

| 영역 | Owner | Consumer | Contract |
| --- | --- | --- | --- |
| Flyway migration files | backend | collector | backend migration history를 schema source of truth로 사용 |
| jobs | backend | collector | 공고 수집/검색/상세 공통 모델 |
| skills | backend | collector | 스킬 정규화 및 매칭 공통 사전 |
| skill_aliases | backend | collector | 검색/추출 alias 사전 |
| experience_tag_codes | backend | collector | 경험 태그 공통 사전 |
| jd_phrase_tag_mapping | backend | collector | JD 문구와 경험 태그 매핑 |
| job_skills | backend | collector | 공고-스킬 관계 |
| job_experience_tags | backend | collector | 공고-경험 태그 관계 |
| outbox_events | backend | collector | outbox event contract |
| normalization_candidates | backend | collector | 스킬/섹션 라벨 검토 후보 |

## 추가한 테스트

### Collector MySQL Schema Contract

파일:

```text
collector/src/test/java/jobflow/collector/integration/CollectorSharedSchemaContractIntegrationTest.java
```

검증 내용:

- collector 테스트가 MySQL Testcontainer로 실행된다.
- collector 테스트가 backend Flyway migration을 사용한다.
- collector JPA mapping이 backend-owned schema에 대해 validate 된다.
- 공유 table, column, 주요 index가 유지되는지 확인한다.

### Collector Test Support

파일:

```text
collector/src/test/java/jobflow/collector/support/MySqlSchemaContractTestSupport.java
```

역할:

- collector integration test용 MySQL 8.4 Testcontainer 제공
- 테스트 datasource 동적 설정
- backend migration 경로 탐색
- `spring.jpa.hibernate.ddl-auto=validate` 적용
- collector runner 비활성화로 schema test를 deterministic하게 유지

### Backend Shared Schema Contract

파일:

```text
backend/src/test/java/jobflow/integration/SharedSchemaContractIntegrationTest.java
```

검증 내용:

- backend Flyway schema에 collector와 공유하는 table이 존재하는지 확인한다.
- 공유 column의 MySQL data type과 nullable contract를 확인한다.
- deduplication, search, relation uniqueness, candidate review에 필요한 주요 index가 유지되는지 확인한다.

## 검증 명령

```bash
./gradlew -p backend test --tests jobflow.integration.SharedSchemaContractIntegrationTest
./gradlew -p backend test --tests jobflow.integration.MySqlSchemaMigrationIntegrationTest
./gradlew -p collector test --tests jobflow.collector.integration.CollectorSharedSchemaContractIntegrationTest
```

기대 결과:

```text
BUILD SUCCESSFUL
```

## 결과

- Backend shared schema contract test: 통과
- Backend MySQL migration test: 통과
- Collector shared schema contract test: 통과

## 판단

이번 작업에서는 common module을 만들지 않았다.

이유:

- 현재 프로젝트는 MVP 단계다.
- backend가 schema owner이고 collector가 schema consumer라는 책임 경계를 명확히 두는 편이 더 단순하다.
- collector는 backend migration으로 생성된 실제 MySQL schema에 대해 mapping validate를 수행한다.
- shared common module은 entity 변경 빈도가 높아지거나 module boundary 유지 비용이 커질 때 검토하는 것이 적절하다.

현재 단계에서는 backend-owned Flyway migration과 cross-module MySQL contract test 조합으로 schema drift를 충분히 조기에 감지할 수 있다.
