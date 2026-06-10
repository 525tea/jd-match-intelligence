INSERT INTO jd_phrase_tag_mapping (phrase, normalized_phrase, tag_code, confidence)
VALUES
    ('대규모 서비스', '대규모 서비스', 'HIGH_TRAFFIC', 0.8500),
    ('대규모 데이터', '대규모 데이터', 'HIGH_TRAFFIC', 0.8500),
    ('대용량 데이터', '대용량 데이터', 'HIGH_TRAFFIC', 0.8500),

    ('확장성', '확장성', 'SCALABILITY', 0.8500),
    ('시스템 확장', '시스템 확장', 'SCALABILITY', 0.8500),
    ('서비스 확장', '서비스 확장', 'SCALABILITY', 0.8500),
    ('플랫폼 확장', '플랫폼 확장', 'SCALABILITY', 0.8500),

    ('최적화', '최적화', 'PERFORMANCE', 0.8500),
    ('고도화', '고도화', 'PERFORMANCE', 0.8000),
    ('성능 검증', '성능 검증', 'PERFORMANCE', 0.9000),
    ('성능 개선', '성능 개선', 'PERFORMANCE', 0.9000),
    ('알고리즘 최적화', '알고리즘 최적화', 'PERFORMANCE', 0.9000),
    ('추론 성능', '추론 성능', 'PERFORMANCE', 0.8500),

    ('유지보수', '유지보수', 'RELIABILITY', 0.8500),
    ('기술지원', '기술지원', 'RELIABILITY', 0.8000),
    ('기술 지원', '기술 지원', 'RELIABILITY', 0.8000),
    ('운영 경험', '운영 경험', 'RELIABILITY', 0.8500),
    ('문제 정의', '문제 정의', 'RELIABILITY', 0.8000),
    ('문제 해결', '문제 해결', 'RELIABILITY', 0.8500),
    ('이슈 대응', '이슈 대응', 'RELIABILITY', 0.8000),
    ('재현성 확보', '재현성 확보', 'RELIABILITY', 0.8000),

    ('테스트', '테스트', 'TESTING', 0.8000),
    ('검증', '검증', 'TESTING', 0.8000),
    ('시뮬레이션', '시뮬레이션', 'TESTING', 0.8500),
    ('품질 검증', '품질 검증', 'TESTING', 0.8500),
    ('테스트 케이스', '테스트 케이스', 'TESTING', 0.8500),

    ('배포', '배포', 'CI_CD', 0.8000),
    ('서비스 배포', '서비스 배포', 'CI_CD', 0.8500),
    ('모델 배포', '모델 배포', 'CI_CD', 0.8500),
    ('파이프라인 운영', '파이프라인 운영', 'CI_CD', 0.8500),

    ('대시보드', '대시보드', 'MONITORING', 0.8000),
    ('운영 모니터링', '운영 모니터링', 'MONITORING', 0.8500),

    ('인프라', '인프라', 'CLOUD_INFRA', 0.8000),
    ('환경 구축', '환경 구축', 'CLOUD_INFRA', 0.8500),
    ('시스템 통합', '시스템 통합', 'CLOUD_INFRA', 0.8500),
    ('데이터 파이프라인', '데이터 파이프라인', 'CLOUD_INFRA', 0.8500),
    ('파이프라인 구축', '파이프라인 구축', 'CLOUD_INFRA', 0.8500),
    ('자동화 인프라', '자동화 인프라', 'CLOUD_INFRA', 0.8500),

    ('보안 요구사항', '보안 요구사항', 'SECURITY', 0.8500),
    ('인증 대응', '인증 대응', 'SECURITY', 0.8500),
    ('위협 모델링', '위협 모델링', 'SECURITY', 0.8500),
    ('위험 평가', '위험 평가', 'SECURITY', 0.8500)
    ON DUPLICATE KEY UPDATE
        phrase = VALUES(phrase),
        confidence = VALUES(confidence),
        enabled = TRUE;
