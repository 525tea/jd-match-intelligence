DROP TABLE IF EXISTS real_job_role_validation_expected;

CREATE TABLE real_job_role_validation_expected (
                                                   source VARCHAR(50) COLLATE utf8mb4_unicode_ci NOT NULL,
                                                   external_id VARCHAR(100) COLLATE utf8mb4_unicode_ci NOT NULL,
                                                   expected_role VARCHAR(50) COLLATE utf8mb4_unicode_ci NOT NULL,
                                                   reason VARCHAR(500) COLLATE utf8mb4_unicode_ci NOT NULL,
                                                   PRIMARY KEY (source, external_id)
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO real_job_role_validation_expected (
    source,
    external_id,
    expected_role,
    reason
)
VALUES
    ('JUMPIT', '54122446', 'BACKEND', '리눅스 백엔드 서버 개발자 명시'),
    ('JUMPIT', '53786411', 'SECURITY', '해양 사이버보안 전문가'),
    ('JUMPIT', '53786374', 'SECURITY', '해양 사이버보안 전문가'),
    ('JUMPIT', '54122628', 'AI_ENGINEER', 'AI 개발자 명시'),
    ('JUMPIT', '54122755', 'AI_ENGINEER', 'AI 개발자 명시'),
    ('JUMPIT', '54105044', 'ETC', 'Robot Controls Engineer는 현재 enum에 직접 role 없음'),
    ('JUMPIT', '54122785', 'ETC', 'Application UI FW 개발자는 firmware/embedded 계열로 분리 필요'),
    ('JUMPIT', '54122814', 'ETC', 'Application 기능 FW 개발자는 firmware/embedded 계열로 분리 필요'),
    ('JUMPIT', '54111922', 'ETC', 'SAP ERP 컨설팅은 현재 개발 role enum에 직접 매핑하지 않음'),
    ('JUMPIT', '54123967', 'FULLSTACK', '일반 소프트웨어 엔지니어 공고로 임시 일반 SW role 매핑'),

    ('WANTED', '367000', 'BACKEND', '리눅스 백엔드 서버 개발자 명시'),
    ('WANTED', '367135', 'BACKEND', '서비스 백엔드 개발자 명시, React 포함이어도 backend 우선'),
    ('WANTED', '367249', 'BACKEND', '백엔드 시니어 개발자 명시'),
    ('WANTED', '367038', 'FRONTEND', '프론트엔드 개발자 명시'),
    ('WANTED', '366999', 'FRONTEND', 'Frontend Engineer 명시'),
    ('WANTED', '367078', 'DEVOPS', 'DevOps Engineer 명시'),
    ('WANTED', '367086', 'DEVOPS', '시스템 엔지니어/AWS/IDC 계열이면 DEVOPS'),
    ('WANTED', '367100', 'ETC', '자동화 제어 엔지니어는 현재 enum에 직접 role 없음'),
    ('WANTED', '367083', 'ETC', '항공/로보틱스 엔지니어는 현재 enum에 직접 role 없음'),
    ('WANTED', '367128', 'PM', 'Project Management Officer');
