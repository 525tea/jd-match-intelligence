-- Real job zero-skill analysis for skill alias/seed improvement.
-- Run in an IntelliJ/MySQL console after collecting JUMPIT/WANTED jobs.
--
-- Parameters:
--   @min_id: only analyze jobs with id >= this value.
--   @source_filter: one of 'ALL', 'JUMPIT', 'WANTED'.

SET @min_id = 0;
SET @source_filter = 'ALL';

WITH target_jobs AS (
    SELECT
        j.id,
        j.source,
        j.external_id,
        j.title,
        j.company_name,
        j.role,
        j.career_level,
        j.role_detail,
        j.description,
        j.raw_data,
        j.original_url,
        COUNT(DISTINCT js.id) AS skill_count
    FROM jobs j
             LEFT JOIN job_skills js ON js.job_id = j.id
    WHERE j.source IN ('JUMPIT', 'WANTED')
      AND j.id >= @min_id
      AND (@source_filter = 'ALL' OR j.source = @source_filter)
    GROUP BY
        j.id,
        j.source,
        j.external_id,
        j.title,
        j.company_name,
        j.role,
        j.career_level,
        j.role_detail,
        j.description,
        j.raw_data,
        j.original_url
),
     zero_skill_jobs AS (
         SELECT *
         FROM target_jobs
         WHERE skill_count = 0
     )
SELECT
    source,
    COUNT(*) AS zero_skill_count
FROM zero_skill_jobs
GROUP BY source
ORDER BY source;

SELECT
    source,
    id,
    external_id,
    title,
    company_name,
    role,
    career_level,
    role_detail,
    LEFT(REGEXP_REPLACE(COALESCE(description, ''), '[[:space:]]+', ' '), 500) AS description_preview,
    original_url
FROM zero_skill_jobs
ORDER BY source ASC, id DESC
LIMIT 100;

WITH keyword_candidates AS (
    SELECT 'C' AS keyword, 'c' AS normalized_keyword UNION ALL
    SELECT 'C++', 'c++' UNION ALL
    SELECT 'C#', 'c#' UNION ALL
    SELECT 'Go', 'go' UNION ALL
    SELECT 'Golang', 'golang' UNION ALL
    SELECT 'Rust', 'rust' UNION ALL
    SELECT 'Swift', 'swift' UNION ALL
    SELECT 'Objective-C', 'objective c' UNION ALL
    SELECT 'Dart', 'dart' UNION ALL
    SELECT 'PHP', 'php' UNION ALL
    SELECT 'Ruby', 'ruby' UNION ALL
    SELECT 'Scala', 'scala' UNION ALL
    SELECT 'Bash', 'bash' UNION ALL
    SELECT 'Shell', 'shell' UNION ALL
    SELECT 'MATLAB', 'matlab' UNION ALL
    SELECT 'Simulink', 'simulink' UNION ALL
    SELECT 'Network', 'network' UNION ALL
    SELECT 'TCP/IP', 'tcp/ip' UNION ALL
    SELECT 'BGP', 'bgp' UNION ALL
    SELECT 'OSPF', 'ospf' UNION ALL
    SELECT 'SSH', 'ssh' UNION ALL
    SELECT 'Apache', 'apache' UNION ALL
    SELECT 'DBMS', 'dbms' UNION ALL
    SELECT 'ISMS', 'isms' UNION ALL
    SELECT 'CISSP', 'cissp' UNION ALL
    SELECT 'RF', 'rf' UNION ALL
    SELECT 'Spectrum Analyzer', 'spectrum analyzer' UNION ALL
    SELECT 'Network Analyzer', 'network analyzer' UNION ALL
    SELECT 'ROS', 'ros' UNION ALL
    SELECT 'ROS2', 'ros2' UNION ALL
    SELECT 'RTOS', 'rtos' UNION ALL
    SELECT 'Linux Kernel', 'linux kernel' UNION ALL
    SELECT 'Embedded Linux', 'embedded linux' UNION ALL
    SELECT 'Firmware', 'firmware' UNION ALL
    SELECT 'FW', 'fw' UNION ALL
    SELECT 'Embedded', 'embedded' UNION ALL
    SELECT '임베디드', '임베디드' UNION ALL
    SELECT '펌웨어', '펌웨어' UNION ALL
    SELECT 'FPGA', 'fpga' UNION ALL
    SELECT 'Verilog', 'verilog' UNION ALL
    SELECT 'VHDL', 'vhdl' UNION ALL
    SELECT 'RTL', 'rtl' UNION ALL
    SELECT 'PLC', 'plc' UNION ALL
    SELECT 'OpenCV', 'opencv' UNION ALL
    SELECT 'TensorFlow', 'tensorflow' UNION ALL
    SELECT 'PyTorch', 'pytorch' UNION ALL
    SELECT 'YOLO', 'yolo' UNION ALL
    SELECT 'SAP', 'sap' UNION ALL
    SELECT 'ERP', 'erp' UNION ALL
    SELECT 'ABAP', 'abap' UNION ALL
    SELECT 'React Native', 'react native' UNION ALL
    SELECT 'Flutter', 'flutter' UNION ALL
    SELECT 'Figma', 'figma' UNION ALL
    SELECT 'Notion', 'notion' UNION ALL
    SELECT 'Jira', 'jira'
),
     normalized_zero_skill_jobs AS (
         SELECT
             source,
             id,
             external_id,
             title,
             company_name,
             role,
             LOWER(REGEXP_REPLACE(CONCAT_WS(' ', title, role_detail, description, raw_data), '[^0-9a-zA-Z가-힣+#.]+', ' ')) AS normalized_text
         FROM zero_skill_jobs
     )
SELECT
    z.source,
    k.keyword,
    COUNT(*) AS matched_zero_skill_job_count,
    GROUP_CONCAT(CONCAT(z.external_id, ':', z.title) ORDER BY z.id DESC SEPARATOR ' | ') AS examples
FROM normalized_zero_skill_jobs z
         JOIN keyword_candidates k
              ON CONCAT(' ', z.normalized_text, ' ') LIKE CONCAT('% ', k.normalized_keyword, ' %')
GROUP BY z.source, k.keyword
ORDER BY z.source ASC, matched_zero_skill_job_count DESC, k.keyword ASC;
