-- Security/network/hardware skill seed expansion from the remaining zero-skill real jobs.

INSERT INTO skills (name, normalized_name, category)
VALUES
    ('Network', 'network', 'INFRA'),
    ('TCP/IP', 'tcp/ip', 'INFRA'),
    ('BGP', 'bgp', 'INFRA'),
    ('OSPF', 'ospf', 'INFRA'),
    ('SSH', 'ssh', 'TOOL'),
    ('Apache HTTP Server', 'apache http server', 'INFRA'),
    ('DBMS', 'dbms', 'DATABASE'),
    ('ISMS', 'isms', 'METHODOLOGY'),
    ('CISSP', 'cissp', 'METHODOLOGY'),
    ('RF', 'rf', 'ETC'),
    ('Spectrum Analyzer', 'spectrum analyzer', 'TOOL'),
    ('Network Analyzer', 'network analyzer', 'TOOL')
ON DUPLICATE KEY UPDATE name = name;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'Network', 'network', 1.0000
FROM skills
WHERE normalized_name = 'network'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, '네트워크', '네트워크', 0.9500
FROM skills
WHERE normalized_name = 'network'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'TCP IP', 'tcp ip', 0.9500
FROM skills
WHERE normalized_name = 'tcp/ip'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'TCP/IP', 'tcp/ip', 1.0000
FROM skills
WHERE normalized_name = 'tcp/ip'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'Apache', 'apache', 0.9000
FROM skills
WHERE normalized_name = 'apache http server'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, '아파치', '아파치', 0.9000
FROM skills
WHERE normalized_name = 'apache http server'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'Spectrum', 'spectrum', 0.8500
FROM skills
WHERE normalized_name = 'spectrum analyzer'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'Spectrum analyzer', 'spectrum analyzer', 1.0000
FROM skills
WHERE normalized_name = 'spectrum analyzer'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'Network analyzer', 'network analyzer', 1.0000
FROM skills
WHERE normalized_name = 'network analyzer'
ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, '정보보호 인증', '정보보호 인증', 0.8500
FROM skills
WHERE normalized_name = 'isms'
ON DUPLICATE KEY UPDATE alias = alias;
