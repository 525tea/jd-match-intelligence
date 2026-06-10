-- Real job skill seed/alias expansion from JUMPIT/WANTED zero-skill analysis.
-- Keep this migration idempotent because local quality loops can replay data through backfill.

INSERT INTO skills (name, normalized_name, category)
VALUES
    ('C', 'c', 'LANGUAGE'),
    ('C++', 'c++', 'LANGUAGE'),
    ('C#', 'c#', 'LANGUAGE'),
    ('Go', 'go', 'LANGUAGE'),
    ('Rust', 'rust', 'LANGUAGE'),
    ('Swift', 'swift', 'LANGUAGE'),
    ('Objective-C', 'objective c', 'LANGUAGE'),
    ('Dart', 'dart', 'LANGUAGE'),
    ('PHP', 'php', 'LANGUAGE'),
    ('Ruby', 'ruby', 'LANGUAGE'),
    ('Scala', 'scala', 'LANGUAGE'),
    ('Bash', 'bash', 'LANGUAGE'),
    ('MATLAB', 'matlab', 'LANGUAGE'),
    ('ABAP', 'abap', 'LANGUAGE'),
    ('Verilog', 'verilog', 'LANGUAGE'),
    ('VHDL', 'vhdl', 'LANGUAGE'),

    ('React Native', 'react native', 'FRAMEWORK'),
    ('Flutter', 'flutter', 'FRAMEWORK'),
    ('OpenCV', 'opencv', 'FRAMEWORK'),
    ('TensorFlow', 'tensorflow', 'FRAMEWORK'),
    ('PyTorch', 'pytorch', 'FRAMEWORK'),
    ('scikit-learn', 'scikit learn', 'FRAMEWORK'),
    ('YOLO', 'yolo', 'FRAMEWORK'),
    ('ROS', 'ros', 'FRAMEWORK'),
    ('ROS2', 'ros2', 'FRAMEWORK'),

    ('RTOS', 'rtos', 'INFRA'),
    ('Linux Kernel', 'linux kernel', 'INFRA'),
    ('Embedded Linux', 'embedded linux', 'INFRA'),
    ('FPGA', 'fpga', 'ETC'),
    ('PLC', 'plc', 'ETC'),

    ('SAP ERP', 'sap erp', 'TOOL'),
    ('Figma', 'figma', 'TOOL'),
    ('Jira', 'jira', 'TOOL'),
    ('Confluence', 'confluence', 'TOOL'),
    ('Notion', 'notion', 'TOOL'),

    ('Software Engineering', 'software engineering', 'METHODOLOGY')
ON DUPLICATE KEY UPDATE name = name;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'Golang', 'golang', 0.9500
FROM skills
WHERE normalized_name = 'go'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'Objective C', 'objective c', 1.0000
FROM skills
WHERE normalized_name = 'objective c'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'Objective-C', 'objective c', 1.0000
FROM skills
WHERE normalized_name = 'objective c'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'Shell Script', 'shell script', 0.9000
FROM skills
WHERE normalized_name = 'bash'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'Shell', 'shell', 0.8500
FROM skills
WHERE normalized_name = 'bash'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'Node', 'node', 0.9000
FROM skills
WHERE normalized_name = 'node.js'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'NodeJS', 'nodejs', 0.9500
FROM skills
WHERE normalized_name = 'node.js'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'Node.js', 'node.js', 1.0000
FROM skills
WHERE normalized_name = 'node.js'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'React.js', 'react.js', 0.9500
FROM skills
WHERE normalized_name = 'react'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'ReactJS', 'reactjs', 0.9500
FROM skills
WHERE normalized_name = 'react'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'RN', 'rn', 0.8500
FROM skills
WHERE normalized_name = 'react native'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'ReactNative', 'reactnative', 0.9500
FROM skills
WHERE normalized_name = 'react native'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'Postgres', 'postgres', 0.9500
FROM skills
WHERE normalized_name = 'postgresql'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'Postgre', 'postgre', 0.9000
FROM skills
WHERE normalized_name = 'postgresql'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'PostgreSQL', 'postgresql', 1.0000
FROM skills
WHERE normalized_name = 'postgresql'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'MS SQL', 'ms sql', 0.9000
FROM skills
WHERE normalized_name = 'sql'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'MSSQL', 'mssql', 0.9000
FROM skills
WHERE normalized_name = 'sql'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'Oracle', 'oracle', 0.9000
FROM skills
WHERE normalized_name = 'oracle database'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'K8S', 'k8s', 0.9500
FROM skills
WHERE normalized_name = 'kubernetes'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'Kubernetes', 'kubernetes', 1.0000
FROM skills
WHERE normalized_name = 'kubernetes'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'Embedded Linux', 'embedded linux', 1.0000
FROM skills
WHERE normalized_name = 'embedded linux'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, '임베디드 리눅스', '임베디드 리눅스', 0.9500
FROM skills
WHERE normalized_name = 'embedded linux'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'Linux Kernel', 'linux kernel', 1.0000
FROM skills
WHERE normalized_name = 'linux kernel'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, '리눅스 커널', '리눅스 커널', 0.9500
FROM skills
WHERE normalized_name = 'linux kernel'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, '펌웨어', '펌웨어', 0.9500
FROM skills
WHERE normalized_name = 'rtos'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'Firmware', 'firmware', 0.9500
FROM skills
WHERE normalized_name = 'rtos'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'FW', 'fw', 0.8500
FROM skills
WHERE normalized_name = 'rtos'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'FreeRTOS', 'freertos', 0.9500
FROM skills
WHERE normalized_name = 'rtos'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'RTOS', 'rtos', 1.0000
FROM skills
WHERE normalized_name = 'rtos'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'ROS 2', 'ros 2', 0.9500
FROM skills
WHERE normalized_name = 'ros2'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'Robot Operating System', 'robot operating system', 0.9000
FROM skills
WHERE normalized_name = 'ros'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'Open CV', 'open cv', 0.9000
FROM skills
WHERE normalized_name = 'opencv'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'Pytorch', 'pytorch', 1.0000
FROM skills
WHERE normalized_name = 'pytorch'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'Torch', 'torch', 0.8500
FROM skills
WHERE normalized_name = 'pytorch'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'Tensorflow', 'tensorflow', 1.0000
FROM skills
WHERE normalized_name = 'tensorflow'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'Scikit Learn', 'scikit learn', 1.0000
FROM skills
WHERE normalized_name = 'scikit learn'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'sklearn', 'sklearn', 0.9500
FROM skills
WHERE normalized_name = 'scikit learn'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'SAP', 'sap', 0.9000
FROM skills
WHERE normalized_name = 'sap erp'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'ERP', 'erp', 0.8500
FROM skills
WHERE normalized_name = 'sap erp'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'SAP ERP', 'sap erp', 1.0000
FROM skills
WHERE normalized_name = 'sap erp'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'S/W', 's w', 0.8000
FROM skills
WHERE normalized_name = 'software engineering'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'SW', 'sw', 0.8000
FROM skills
WHERE normalized_name = 'software engineering'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, '소프트웨어 개발', '소프트웨어 개발', 0.8500
FROM skills
WHERE normalized_name = 'software engineering'
ON DUPLICATE KEY UPDATE alias = alias;
