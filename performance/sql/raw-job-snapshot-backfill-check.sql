SELECT
    source,
    COUNT(*) AS job_count,
    SUM(CASE WHEN raw_data IS NULL OR raw_data = '' THEN 1 ELSE 0 END) AS missing_raw_data_count,
    SUM(CASE WHEN raw_snapshot_key IS NULL OR raw_snapshot_key = '' THEN 1 ELSE 0 END) AS missing_snapshot_key_count,
    SUM(CASE WHEN raw_snapshot_hash IS NULL OR raw_snapshot_hash = '' THEN 1 ELSE 0 END) AS missing_snapshot_hash_count,
    SUM(CASE WHEN raw_snapshot_size_bytes IS NULL OR raw_snapshot_size_bytes <= 0 THEN 1 ELSE 0 END) AS invalid_snapshot_size_count,
    SUM(CASE WHEN raw_snapshot_storage_type IS NULL OR raw_snapshot_storage_type = '' THEN 1 ELSE 0 END) AS missing_snapshot_storage_type_count,
    SUM(CASE WHEN raw_snapshot_saved_at IS NULL THEN 1 ELSE 0 END) AS missing_snapshot_saved_at_count
FROM jobs
WHERE source IN ('JUMPIT', 'WANTED')
GROUP BY source
ORDER BY source;

SELECT
    source,
    id,
    external_id,
    title,
    raw_snapshot_key,
    raw_snapshot_hash,
    raw_snapshot_size_bytes,
    raw_snapshot_storage_type,
    raw_snapshot_saved_at
FROM jobs
WHERE source IN ('JUMPIT', 'WANTED')
  AND raw_snapshot_key IS NOT NULL
ORDER BY raw_snapshot_saved_at DESC, id DESC
LIMIT 20;

SELECT
    'RAW_SNAPSHOT_BACKFILL_BLOCKER' AS check_name,
    source,
    id,
    external_id,
    title,
    CASE
        WHEN raw_data IS NULL OR raw_data = '' THEN 'MISSING_RAW_DATA'
        WHEN raw_snapshot_key IS NULL OR raw_snapshot_key = '' THEN 'MISSING_SNAPSHOT_KEY'
        WHEN raw_snapshot_hash IS NULL OR raw_snapshot_hash = '' THEN 'MISSING_SNAPSHOT_HASH'
        WHEN raw_snapshot_size_bytes IS NULL OR raw_snapshot_size_bytes <= 0 THEN 'INVALID_SNAPSHOT_SIZE'
        WHEN raw_snapshot_storage_type IS NULL OR raw_snapshot_storage_type = '' THEN 'MISSING_STORAGE_TYPE'
        WHEN raw_snapshot_saved_at IS NULL THEN 'MISSING_SAVED_AT'
        ELSE 'OK'
    END AS reason
FROM jobs
WHERE source IN ('JUMPIT', 'WANTED')
  AND raw_data IS NOT NULL
  AND raw_data <> ''
  AND (
      raw_snapshot_key IS NULL
      OR raw_snapshot_key = ''
      OR raw_snapshot_hash IS NULL
      OR raw_snapshot_hash = ''
      OR raw_snapshot_size_bytes IS NULL
      OR raw_snapshot_size_bytes <= 0
      OR raw_snapshot_storage_type IS NULL
      OR raw_snapshot_storage_type = ''
      OR raw_snapshot_saved_at IS NULL
  )
ORDER BY source, id
LIMIT 50;
