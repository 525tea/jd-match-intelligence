# Raw Job Snapshot Backfill Report - 2026-06-22

## 목적

기존 `jobs.raw_data`에 보관하던 JUMPIT/WANTED 공고 원본 JSON/HTML을 raw snapshot storage로 분리하고, DB에는 replay 가능한 metadata만 남기는 구조를 검증했다.

이번 검증의 핵심은 다음과 같다.

- 기존 수집 공고의 `raw_data`를 local file snapshot으로 저장할 수 있는가
- `jobs.raw_snapshot_key`, `raw_snapshot_hash`, `raw_snapshot_size_bytes`, `raw_snapshot_storage_type`, `raw_snapshot_saved_at`이 누락 없이 채워지는가
- 이후 raw replay backfill이 DB `raw_data`가 아닌 snapshot key 기반으로도 원문을 다시 읽을 수 있는 기반이 생겼는가

## 실행 환경

| 항목 | 값 |
|---|---|
| 실행일 | 2026-06-22 |
| 대상 source | `JUMPIT`, `WANTED` |
| 저장 방식 | `LOCAL_FILE` |
| 실행 스크립트 | `performance/collector/backfill-raw-job-snapshot.sh` |
| 검증 SQL | `performance/sql/raw-job-snapshot-backfill-check.sql` |
| 적용 migration | `V19__add_raw_snapshot_metadata.sql` |

## 실행 명령

```bash
SOURCES=JUMPIT,WANTED \
RAW_SNAPSHOT_STORAGE_ROOT=../build/raw-snapshots \
bash performance/collector/backfill-raw-job-snapshot.sh
```

## 검증 결과

### Source별 snapshot metadata 누락 현황

| source | job_count | missing_raw_data_count | missing_snapshot_key_count | missing_snapshot_hash_count | invalid_snapshot_size_count | missing_snapshot_storage_type_count | missing_snapshot_saved_at_count |
|---|---:|---:|---:|---:|---:|---:|---:|
| JUMPIT | 150 | 0 | 0 | 0 | 0 | 0 | 0 |
| WANTED | 155 | 0 | 0 | 0 | 0 | 0 | 0 |

결과적으로 JUMPIT/WANTED 총 305건 모두 raw snapshot metadata가 정상 채워졌다.

### Snapshot sample

| source | id | external_id | title | raw_snapshot_size_bytes | raw_snapshot_storage_type |
|---|---:|---|---|---:|---|
| WANTED | 459 | 366664 | Data Engineer 4~7년 | 6242 | LOCAL_FILE |
| WANTED | 458 | 366667 | 프론트엔드 개발자 (1년~3년이상) | 9225 | LOCAL_FILE |
| WANTED | 457 | 366678 | IT LAB Team Leader (7년 이상) | 6457 | LOCAL_FILE |
| WANTED | 456 | 366680 | CTO Staff (3년 이상) | 6101 | LOCAL_FILE |
| WANTED | 455 | 366681 | Software Engineer / Frontend (5년 이상) | 6399 | LOCAL_FILE |

저장 key는 source/externalId/hash 기반으로 생성된다.

예시:

```text
wanted/366664/8de463f4d2c6f90c7dbd9d403e2b5e2b231bde967b779edfbe6620c60b857bcf.json
```

## Blocker Check

다음 조건에 해당하는 row를 blocker로 간주했다.

- `raw_data`가 있는데 `raw_snapshot_key`가 없음
- `raw_snapshot_hash`가 없음
- `raw_snapshot_size_bytes`가 없거나 0 이하
- `raw_snapshot_storage_type`이 없음
- `raw_snapshot_saved_at`이 없음

검증 결과:

```text
RAW_SNAPSHOT_BACKFILL_BLOCKER = 0 rows
```

즉, 원본 raw data를 가진 JUMPIT/WANTED row 중 snapshot metadata가 누락된 row는 없다.

## 해석

이번 작업으로 공고 원본 보존 전략이 다음 구조로 분리됐다.

| 책임 | 저장 위치 |
|---|---|
| 원본 JSON/HTML snapshot | file/object storage |
| replay에 필요한 참조 metadata | MySQL `jobs.raw_snapshot_*` |
| 기존 parser replay/backfill | `raw_data` 우선, 없으면 snapshot key fallback |

이 구조는 DB `raw_data` 컬럼 비대화를 줄이고, 장기적으로 OCI Object Storage 또는 S3로 이동할 수 있는 경계를 만든다.

## 후속 확인

- 현재 구현은 `LOCAL_FILE` storage를 사용한다.
- staging/performance 배포 시에는 storage root path를 명시적으로 고정해야 한다.
- OCI/S3 같은 외부 object storage 전환은 storage adapter만 교체하는 후속 작업으로 분리한다.
- raw snapshot에 개인정보나 민감정보가 섞일 수 있는 source가 추가되면 보존 기간과 masking 정책을 별도로 정의해야 한다.
