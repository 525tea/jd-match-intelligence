-- WANTED detail parsing quality check for w6-25.
-- Run after raw job description replay backfill.

SET @wanted_data_engineer_external_id = '366664';
SET @wanted_middle_dot_external_id = '366667';

DROP TEMPORARY TABLE IF EXISTS wanted_detail_parsing_quality_target;

CREATE TEMPORARY TABLE wanted_detail_parsing_quality_target AS
SELECT
    j.id,
    j.external_id,
    j.title,
    j.company_name,
    j.role,
    j.deadline_at,
    j.description,
    j.description_sections,
    j.raw_snapshot_key,
    j.raw_snapshot_hash,
    j.raw_snapshot_storage_type,
    j.raw_snapshot_saved_at
FROM jobs j
WHERE j.source = 'WANTED';

SELECT
    'WANTED_DETAIL_PARSING_SUMMARY' AS check_name,
    COUNT(*) AS wanted_job_count,
    SUM(CASE
            WHEN LOWER(title) LIKE '%data engineer%'
                AND role <> 'DATA_ENGINEER'
                THEN 1
            ELSE 0
        END) AS data_engineer_role_mismatch_count,
    SUM(CASE
            WHEN description LIKE '%anti bot%'
                OR description LIKE '%N layer 아키텍처%'
                THEN 1
            ELSE 0
        END) AS word_internal_break_suspect_count,
    SUM(CASE
            WHEN REGEXP_LIKE(description, 'CS[[:space:]]*[\n\r]*[[:space:]]*•[[:space:]]*(관제|고객)')
                THEN 1
            ELSE 0
        END) AS inline_middle_dot_bullet_suspect_count,
    SUM(CASE
            WHEN raw_snapshot_key IS NULL
                OR raw_snapshot_hash IS NULL
                OR raw_snapshot_storage_type IS NULL
                OR raw_snapshot_saved_at IS NULL
                THEN 1
            ELSE 0
        END) AS missing_snapshot_metadata_count,
    SUM(CASE
            WHEN description_sections IS NULL
                OR JSON_LENGTH(description_sections) = 0
                THEN 1
            ELSE 0
        END) AS missing_description_sections_count,
    SUM(CASE
            WHEN description_sections LIKE '%CS\\\\n• 관제%'
                OR description_sections LIKE '%CS\\n• 관제%'
                THEN 1
            ELSE 0
        END) AS display_section_inline_middle_dot_split_count,
    SUM(CASE
            WHEN description_sections LIKE '%AI Agent N\\\\nlayer%'
                OR description_sections LIKE '%AI Agent N\\nlayer%'
                OR description_sections LIKE '%One\\\\nTeam%'
                OR description_sections LIKE '%One\\nTeam%'
                THEN 1
            ELSE 0
        END) AS display_section_word_split_count,
    SUM(CASE
            WHEN description_sections LIKE '% • %'
                OR description_sections LIKE '% ・ %'
                THEN 1
            ELSE 0
        END) AS display_section_flat_bullet_count
FROM wanted_detail_parsing_quality_target;

SELECT
    'ROLE_MISMATCH' AS check_name,
    id,
    external_id,
    title,
    company_name,
    role,
    LEFT(description, 500) AS description_sample
FROM wanted_detail_parsing_quality_target
WHERE LOWER(title) LIKE '%data engineer%'
  AND role <> 'DATA_ENGINEER'
ORDER BY id
LIMIT 20;

SELECT
    'WORD_INTERNAL_BREAK_SUSPECT' AS check_name,
    id,
    external_id,
    title,
    company_name,
    role,
    LEFT(description, 800) AS description_sample
FROM wanted_detail_parsing_quality_target
WHERE description LIKE '%anti bot%'
   OR description LIKE '%N layer 아키텍처%'
ORDER BY id
LIMIT 20;

SELECT
    'INLINE_MIDDLE_DOT_BULLET_SUSPECT' AS check_name,
    id,
    external_id,
    title,
    company_name,
    role,
    LEFT(description, 800) AS description_sample
FROM wanted_detail_parsing_quality_target
WHERE REGEXP_LIKE(description, 'CS[[:space:]]*[\n\r]*[[:space:]]*•[[:space:]]*(관제|고객)')
ORDER BY id
LIMIT 20;

SELECT
    'REPRESENTATIVE_DETAIL_SAMPLE' AS check_name,
    id,
    external_id,
    title,
    company_name,
    role,
    CASE
        WHEN deadline_at IS NULL THEN '상시/마감일 원문 없음'
        ELSE DATE_FORMAT(deadline_at, '%Y-%m-%d %H:%i:%s')
        END AS deadline_display_hint,
    LEFT(description, 1200) AS description_sample,
    JSON_PRETTY(description_sections) AS description_sections_sample
FROM wanted_detail_parsing_quality_target
WHERE external_id IN (
                      @wanted_data_engineer_external_id COLLATE utf8mb4_unicode_ci,
                      @wanted_middle_dot_external_id COLLATE utf8mb4_unicode_ci
    )
ORDER BY external_id;

SELECT
    'DESCRIPTION_SECTIONS_MISSING' AS check_name,
    id,
    external_id,
    title,
    company_name,
    role,
    LEFT(description, 500) AS description_sample
FROM wanted_detail_parsing_quality_target
WHERE description_sections IS NULL
   OR JSON_LENGTH(description_sections) = 0
ORDER BY id
LIMIT 20;

SELECT
    'DESCRIPTION_SECTIONS_DISPLAY_SPLIT_SUSPECT' AS check_name,
    id,
    external_id,
    title,
    company_name,
    role,
    JSON_PRETTY(description_sections) AS description_sections_sample
FROM wanted_detail_parsing_quality_target
WHERE description_sections LIKE '%CS\\\\n• 관제%'
   OR description_sections LIKE '%CS\\n• 관제%'
   OR description_sections LIKE '%AI Agent N\\\\nlayer%'
   OR description_sections LIKE '%AI Agent N\\nlayer%'
   OR description_sections LIKE '%One\\\\nTeam%'
   OR description_sections LIKE '%One\\nTeam%'
   OR description_sections LIKE '% • %'
   OR description_sections LIKE '% ・ %'
ORDER BY id
LIMIT 20;

DROP TEMPORARY TABLE IF EXISTS wanted_detail_parsing_quality_target;
