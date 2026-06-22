-- Source display sections quality check for w6-26.
-- Verifies that user-facing job detail sections are stored separately from
-- normalized search/matching description text.

DROP TEMPORARY TABLE IF EXISTS source_display_section_quality_target;

CREATE TEMPORARY TABLE source_display_section_quality_target AS
SELECT
    j.id,
    j.source,
    j.external_id,
    j.title,
    j.company_name,
    j.role,
    j.description,
    j.description_sections
FROM jobs j
WHERE j.source IN ('JUMPIT', 'WANTED');

SELECT
    'SOURCE_DISPLAY_SECTION_SUMMARY' AS check_name,
    source,
    COUNT(*) AS job_count,
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
        END) AS inline_middle_dot_split_count,
    SUM(CASE
            WHEN description_sections LIKE '%AI Agent N\\\\nlayer%'
                OR description_sections LIKE '%AI Agent N\\nlayer%'
                OR description_sections LIKE '%One\\\\nTeam%'
                OR description_sections LIKE '%One\\nTeam%'
                THEN 1
            ELSE 0
        END) AS word_internal_split_count,
    SUM(CASE
            WHEN description_sections LIKE '%포함)1%'
                OR description_sections LIKE '%****%'
                THEN 1
            ELSE 0
        END) AS display_noise_count,
    SUM(CASE
            WHEN source = 'JUMPIT'
                AND external_id = '54124188'
                AND description_sections LIKE '%채용절차 및 기타 지원 유의사항%'
                AND (
                    description_sections NOT LIKE '%[채용절차]%'
                    OR description_sections NOT LIKE '%[지원 시 주의사항]%'
                )
                THEN 1
            ELSE 0
        END) AS jumpit_representative_process_missing_count
FROM source_display_section_quality_target
GROUP BY source
ORDER BY source;

SELECT
    'SOURCE_DISPLAY_SECTION_BLOCKER' AS check_name,
    source,
    id,
    external_id,
    title,
    CASE
        WHEN description_sections IS NULL
            OR JSON_LENGTH(description_sections) = 0
            THEN 'MISSING_DESCRIPTION_SECTIONS'
        WHEN description_sections LIKE '%CS\\\\n• 관제%'
            OR description_sections LIKE '%CS\\n• 관제%'
            THEN 'INLINE_MIDDLE_DOT_SPLIT'
        WHEN description_sections LIKE '%AI Agent N\\\\nlayer%'
            OR description_sections LIKE '%AI Agent N\\nlayer%'
            OR description_sections LIKE '%One\\\\nTeam%'
            OR description_sections LIKE '%One\\nTeam%'
            THEN 'WORD_INTERNAL_SPLIT'
        WHEN description_sections LIKE '%포함)1%'
            OR description_sections LIKE '%****%'
            THEN 'DISPLAY_NOISE'
        WHEN source = 'JUMPIT'
            AND external_id = '54124188'
            AND description_sections LIKE '%채용절차 및 기타 지원 유의사항%'
            AND (
                description_sections NOT LIKE '%[채용절차]%'
                OR description_sections NOT LIKE '%[지원 시 주의사항]%'
            )
            THEN 'JUMPIT_REPRESENTATIVE_PROCESS_MISSING'
        ELSE 'UNKNOWN'
        END AS reason,
    JSON_PRETTY(description_sections) AS description_sections_sample
FROM source_display_section_quality_target
WHERE description_sections IS NULL
   OR JSON_LENGTH(description_sections) = 0
   OR description_sections LIKE '%CS\\\\n• 관제%'
   OR description_sections LIKE '%CS\\n• 관제%'
   OR description_sections LIKE '%AI Agent N\\\\nlayer%'
   OR description_sections LIKE '%AI Agent N\\nlayer%'
   OR description_sections LIKE '%One\\\\nTeam%'
   OR description_sections LIKE '%One\\nTeam%'
   OR description_sections LIKE '%포함)1%'
   OR description_sections LIKE '%****%'
   OR (
        source = 'JUMPIT'
        AND external_id = '54124188'
        AND description_sections LIKE '%채용절차 및 기타 지원 유의사항%'
        AND (
            description_sections NOT LIKE '%[채용절차]%'
            OR description_sections NOT LIKE '%[지원 시 주의사항]%'
        )
   )
ORDER BY source, id
LIMIT 50;

SELECT
    'REPRESENTATIVE_SOURCE_DISPLAY_SECTION_SAMPLE' AS check_name,
    source,
    id,
    external_id,
    title,
    JSON_PRETTY(description_sections) AS description_sections_sample
FROM source_display_section_quality_target
WHERE (source = 'WANTED' AND external_id IN ('366664', '366667'))
   OR (source = 'JUMPIT' AND id = 155)
ORDER BY source, id;

DROP TEMPORARY TABLE IF EXISTS source_display_section_quality_target;
