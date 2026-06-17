export const tagLabels = {
  EVENT_DRIVEN: '이벤트 드리븐',
  CACHE_STRATEGY: '캐시 전략',
  BATCH_PROCESSING: '배치 처리',
  HIGH_TRAFFIC: '대용량 트래픽',
  DISTRIBUTED_SYSTEM: '분산 시스템',
  CI_CD: 'CI/CD',
  TESTING: '테스트',
  AUTH: '인증/인가',
  OBSERVABILITY: '모니터링',
  IDEMPOTENCY: '멱등 처리',
};

export const filters = {
  roles: [
    ['BACKEND', '백엔드'], ['FRONTEND', '프론트엔드'], ['FULLSTACK', '풀스택'], ['DEVOPS', 'DevOps'],
    ['DATA_ENGINEER', '데이터 엔지니어'], ['AI_ENGINEER', 'AI 엔지니어'], ['ML_ENGINEER', 'ML 엔지니어'],
    ['SECURITY', '보안'], ['QA', 'QA'], ['PM', 'PM'], ['MLOPS', 'MLOps'], ['LLM', 'LLM'], ['ETC', '기타'],
  ],
  careers: [['ANY', '전체'], ['NEWCOMER', '신입'], ['JUNIOR', '~3년차'], ['MID', '3~7년차'], ['SENIOR', '시니어'], ['LEAD', '리드']],
  employmentTypes: [['FULL_TIME', '정규직'], ['CONTRACT', '계약직'], ['INTERN', '인턴'], ['PART_TIME', '파트타임'], ['FREELANCE', '프리랜서']],
  remoteTypes: [['ONSITE', '오피스'], ['REMOTE', '원격'], ['HYBRID', '하이브리드'], ['FLEXIBLE', '유연근무']],
  deadlines: [['ALL', '전체'], ['TODAY', '오늘 마감'], ['D3', '3일 이내'], ['D7', '7일 이내'], ['NONE', '마감일 없음']],
  skills: ['Java', 'Spring Boot', 'JPA', 'MySQL', 'Redis', 'Kafka', 'Docker', 'Kubernetes', 'AWS', 'React', 'TypeScript', 'Python', 'Go', 'Elasticsearch'],
  experienceTags: ['EVENT_DRIVEN', 'CACHE_STRATEGY', 'CI_CD', 'TESTING', 'AUTH', 'OBSERVABILITY', 'HIGH_TRAFFIC', 'DISTRIBUTED_SYSTEM'],
};

export const mockUser = { id: 1, name: '사용자', email: 'user@example.com', role: 'USER', githubConnected: true };

export const mockProjects = [
  {
    userProjectId: 1,
    name: 'commerce-api',
    repo: 'github.com/example-org/commerce-api',
    connected: true,
    analyzedAt: '2시간 전',
    matchedJobs: 18,
    skillsTotal: 24,
    tagsTotal: 7,
    summary: '결제 승인, 정산, 이벤트 발행 흐름이 분리된 커머스 결제 API',
    domain: ['결제 승인', '정산', '이벤트 발행'],
    architecture: 'Hexagonal · Event-Driven',
    previewSkills: ['Java', 'Spring Boot', 'JPA', 'Kafka'],
    overview: '결제 승인에서 정산, 이벤트 발행까지 책임을 분리한 커머스 결제 API입니다. Kafka 기반 비동기 전파와 멱등 처리, 재시도 설계가 들어 있습니다.',
    stats: { commits: 412, files: 168, coverage: 64, contributors: 3 },
    dirs: [
      ['domain/payment', 34], ['domain/settlement', 22], ['infra/kafka', 18], ['api/controller', 14], ['common', 12],
    ],
  },
  {
    userProjectId: 2,
    name: 'search-indexer',
    repo: 'github.com/example-org/search-indexer',
    connected: true,
    analyzedAt: '어제',
    matchedJobs: 12,
    skillsTotal: 20,
    tagsTotal: 6,
    summary: '공고 검색과 추천 후보 생성을 위한 검색 인덱싱 서비스',
    domain: ['검색 색인', '추천 후보', '캐시'],
    architecture: 'Pipeline · CQRS-lite',
    previewSkills: ['Java', 'Redis', 'Elasticsearch', 'Docker'],
    overview: '공고 데이터를 색인하고 추천 후보를 생성하는 검색 인덱싱 서비스입니다. 변경 이벤트 기반 증분 색인과 Redis 캐시를 사용합니다.',
    stats: { commits: 286, files: 121, coverage: 57, contributors: 2 },
    dirs: [['indexer/core', 38], ['recommend', 24], ['cache', 18], ['api', 12], ['common', 8]],
  },
  {
    userProjectId: 3,
    name: 'payment-worker',
    repo: '이름만 입력됨',
    connected: false,
    analyzedAt: '미분석',
    matchedJobs: 0,
    skillsTotal: 0,
    tagsTotal: 0,
    summary: '비동기 정산 워커로 추정되는 프로젝트. GitHub 연결 필요',
    previewSkills: ['Kafka', 'Batch', 'MySQL'],
  },
];

export const mockSkills = [
  { skillId: 1, skillName: 'Java', normalizedName: 'java', category: 'LANGUAGE', confidence: 98, source: 'STATIC', evidence: '커밋 142개·파일 38개에서 결제 도메인 핵심 서비스로 검출' },
  { skillId: 2, skillName: 'Spring Boot', normalizedName: 'spring boot', category: 'FRAMEWORK', confidence: 95, source: 'STATIC', evidence: 'API 엔트리포인트와 설정 파일에서 핵심 프레임워크로 검출' },
  { skillId: 3, skillName: 'JPA', normalizedName: 'jpa', category: 'FRAMEWORK', confidence: 88, source: 'STATIC', evidence: 'Entity, Repository, Query 메서드 사용 패턴에서 검출' },
  { skillId: 4, skillName: 'MySQL', normalizedName: 'mysql', category: 'DATABASE', confidence: 80, source: 'STATIC', evidence: '마이그레이션과 datasource 설정 기반 후보' },
  { skillId: 5, skillName: 'Redis', normalizedName: 'redis', category: 'DATABASE', confidence: 72, source: 'LLM', evidence: '캐시 설정과 세션 저장소 의존성에서 검출' },
  { skillId: 6, skillName: 'Kafka', normalizedName: 'kafka', category: 'INFRA', confidence: 65, source: 'LLM', evidence: '이벤트 발행 모듈과 토픽 네이밍에서 후보로 추출' },
  { skillId: 7, skillName: 'Docker', normalizedName: 'docker', category: 'INFRA', confidence: 60, source: 'STATIC', evidence: 'Dockerfile, compose 설정, 배포 스크립트에서 검출' },
  { skillId: 8, skillName: 'AWS', normalizedName: 'aws', category: 'INFRA', confidence: 54, source: 'LLM', evidence: 'S3, CloudWatch 환경변수와 배포 문맥에서 후보로 추출' },
];

export const mockExperienceTags = [
  { tagCode: 'EVENT_DRIVEN', tagName: '이벤트 드리븐', confidence: 92, evidence: '주문·정산 이벤트를 Kafka 토픽으로 분리해 후속 흐름을 비동기 처리' },
  { tagCode: 'CI_CD', tagName: 'CI/CD', confidence: 84, evidence: '테스트와 빌드 파이프라인을 분리해 배포 전 검증 자동화' },
  { tagCode: 'IDEMPOTENCY', tagName: '멱등 처리', confidence: 78, evidence: '결제 승인 API에 멱등 키와 재시도 전략 적용' },
  { tagCode: 'TESTING', tagName: '테스트', confidence: 70, evidence: '실패, 중복 승인, 정산 예외 케이스 테스트가 반복 확인됨' },
];

export const mockJobs = [
  {
    id: 101, jobId: 101, companyName: '코어페이', logo: 'CP', title: '코어페이 결제 플랫폼 백엔드 엔지니어 신입 채용', role: 'BACKEND', roleDetail: 'Backend Engineer', careerLevel: 'NEWCOMER', minExperienceYears: 0, maxExperienceYears: 0, deadlineAt: '2026-06-21T23:59:59', status: 'OPEN', locationRegion: '서울', locationCity: '강남', remoteType: 'ONSITE', score: { totalScore: 94, skillMatchRate: 94 }, matchScore: 94, views: 326,
    matchedRequiredSkills: ['Java', 'Spring Boot', 'JPA'], missingRequiredSkills: ['Kubernetes'], matchedPreferredSkills: ['MySQL'], missingPreferredSkills: ['AWS'], matchedExperienceTags: [{ tagCode: 'EVENT_DRIVEN', tagName: '이벤트 드리븐' }, { tagCode: 'CI_CD', tagName: 'CI/CD' }], missingExperienceTags: [{ tagCode: 'OBSERVABILITY', tagName: '모니터링' }],
    skills: [{ skillName: 'Java', requirementType: 'REQUIRED' }, { skillName: 'Spring Boot', requirementType: 'REQUIRED' }, { skillName: 'JPA', requirementType: 'REQUIRED' }, { skillName: 'MySQL', requirementType: 'PREFERRED' }, { skillName: 'Kubernetes', requirementType: 'PREFERRED' }],
    experienceTags: [{ tagCode: 'EVENT_DRIVEN', tagName: '이벤트 드리븐' }, { tagCode: 'CI_CD', tagName: 'CI/CD' }, { tagCode: 'OBSERVABILITY', tagName: '모니터링' }],
    companyIntro: '실시간 결제 승인과 정산 인프라를 만드는 핀테크 플랫폼입니다.',
    description: '결제 플랫폼의 핵심 거래 서버를 함께 만들 백엔드 엔지니어를 찾습니다. 대용량 트래픽 환경에서 안정적인 결제 처리와 정산 시스템을 설계·운영합니다.\n\n주요 업무\n- 대용량 트래픽을 고려한 백엔드 API와 배치/이벤트 처리 흐름 설계\n- 장애 상황에서도 거래 데이터가 안전하게 보존되도록 모니터링과 재시도 구조 개선\n- 프론트엔드, 데이터, 인프라 팀과 협업해 신규 기능의 기술 요구사항 구체화',
  },
  {
    id: 102, jobId: 102, companyName: '넥스트커머스', logo: 'NC', title: '넥스트커머스 플랫폼 백엔드 주니어 개발자 채용', role: 'BACKEND', roleDetail: 'Platform Backend', careerLevel: 'JUNIOR', minExperienceYears: 1, maxExperienceYears: 3, deadlineAt: '2026-06-26T23:59:59', status: 'OPEN', locationRegion: '서울', locationCity: '성수', remoteType: 'HYBRID', score: { totalScore: 88, skillMatchRate: 88 }, matchScore: 88, views: 284,
    matchedRequiredSkills: ['Java', 'Redis', 'Docker'], missingRequiredSkills: ['Kafka'], matchedPreferredSkills: [], missingPreferredSkills: ['AWS'], matchedExperienceTags: [{ tagCode: 'HIGH_TRAFFIC', tagName: '대용량 트래픽' }, { tagCode: 'CACHE_STRATEGY', tagName: '캐시 전략' }], missingExperienceTags: [],
    skills: [{ skillName: 'Java', requirementType: 'REQUIRED' }, { skillName: 'Redis', requirementType: 'REQUIRED' }, { skillName: 'Docker', requirementType: 'REQUIRED' }, { skillName: 'Kafka', requirementType: 'PREFERRED' }],
    experienceTags: [{ tagCode: 'HIGH_TRAFFIC', tagName: '대용량 트래픽' }, { tagCode: 'CACHE_STRATEGY', tagName: '캐시 전략' }],
    companyIntro: '상품·주문·추천 도메인을 운영하는 커머스 SaaS 팀입니다.',
    description: '상품·주문 도메인 백엔드를 담당합니다. 이벤트 기반 아키텍처와 캐시 전략으로 트래픽을 견디는 시스템을 만듭니다.',
  },
  {
    id: 103, jobId: 103, companyName: '플로우랩', logo: 'FL', title: '플로우랩 서버 엔지니어 신입 채용', role: 'BACKEND', roleDetail: 'Server Engineer', careerLevel: 'NEWCOMER', minExperienceYears: 0, maxExperienceYears: 0, deadlineAt: '2026-06-29T23:59:59', status: 'OPEN', locationRegion: '경기', locationCity: '판교', remoteType: 'ONSITE', score: { totalScore: 81, skillMatchRate: 81 }, matchScore: 81, views: 211,
    matchedRequiredSkills: ['Spring Boot', 'AWS'], missingRequiredSkills: ['Kubernetes'], matchedPreferredSkills: [], missingPreferredSkills: ['Terraform'], matchedExperienceTags: [{ tagCode: 'CI_CD', tagName: 'CI/CD' }], missingExperienceTags: [{ tagCode: 'OBSERVABILITY', tagName: '모니터링' }],
    skills: [{ skillName: 'Spring Boot', requirementType: 'REQUIRED' }, { skillName: 'AWS', requirementType: 'REQUIRED' }, { skillName: 'Kubernetes', requirementType: 'REQUIRED' }, { skillName: 'Terraform', requirementType: 'PREFERRED' }],
    experienceTags: [{ tagCode: 'CI_CD', tagName: 'CI/CD' }, { tagCode: 'OBSERVABILITY', tagName: '모니터링' }],
    companyIntro: '워크플로우 자동화 SaaS를 만드는 생산성 도구 팀입니다.',
    description: '워크플로우 자동화 SaaS의 서버 인프라를 설계·운영합니다. IaC와 CI/CD 파이프라인으로 배포 자동화를 고도화합니다.',
  },
];

export const mockListings = [
  { id: 201, companyName: 'CJ ENM', logo: 'CJ', title: 'CJ ENM 커머스부문 [추천 플랫폼 엔지니어] 경력사원 채용', role: 'BACKEND', roleDetail: '추천 플랫폼 엔지니어', careerLevel: 'JUNIOR', minExperienceYears: 1, maxExperienceYears: 3, deadlineAt: '2026-06-23T23:59:59', status: 'OPEN', views: 528, skills: [{ skillName: 'Java' }, { skillName: 'Spring Boot' }, { skillName: 'Kafka' }], experienceTags: [{ tagCode: 'EVENT_DRIVEN' }, { tagCode: 'HIGH_TRAFFIC' }], matchScore: 91 },
  ...mockJobs,
  { id: 204, companyName: '리플로우', logo: 'RF', title: '리플로우 백엔드 엔지니어 신입 채용', role: 'BACKEND', roleDetail: 'Backend Engineer', careerLevel: 'NEWCOMER', minExperienceYears: 0, maxExperienceYears: 0, deadlineAt: '2026-06-21T23:59:59', views: 412, skills: [{ skillName: 'Java' }, { skillName: 'Spring Boot' }, { skillName: 'Kafka' }], experienceTags: [{ tagCode: 'EVENT_DRIVEN' }], matchScore: 88 },
  { id: 205, companyName: '그리드원', logo: 'G1', title: '그리드원 서버 개발자 신입 및 주니어 채용', role: 'BACKEND', roleDetail: 'Server Developer', careerLevel: 'JUNIOR', minExperienceYears: 1, maxExperienceYears: 3, deadlineAt: '2026-06-24T23:59:59', views: 368, skills: [{ skillName: 'Java' }, { skillName: 'MySQL' }, { skillName: 'Redis' }], experienceTags: [{ tagCode: 'CACHE_STRATEGY' }], matchScore: 84 },
  { id: 206, companyName: '노바랩스', logo: 'NL', title: '노바랩스 Go 서버 개발자 채용', role: 'BACKEND', roleDetail: 'Server Developer', careerLevel: 'JUNIOR', minExperienceYears: 1, maxExperienceYears: 3, deadlineAt: '2026-06-19T23:59:59', views: 184, skills: [{ skillName: 'Go' }, { skillName: 'gRPC' }, { skillName: 'Kubernetes' }], experienceTags: [{ tagCode: 'OBSERVABILITY' }], matchScore: 59 },
];

export const mockApplications = [
  { id: 1, jobId: 101, companyName: '센티넬', jobTitle: '백엔드 엔지니어', status: 'INTERVIEW', jobStatus: 'OPEN', appliedAt: '2026-06-12T10:00:00' },
  { id: 2, jobId: 102, companyName: '데이터포지', jobTitle: '플랫폼 엔지니어', status: 'CODING_TEST', jobStatus: 'OPEN', appliedAt: '2026-06-13T13:00:00' },
  { id: 3, jobId: 103, companyName: '코어페이', jobTitle: '백엔드 엔지니어', status: 'DOCUMENT_PASSED', jobStatus: 'OPEN', appliedAt: '2026-06-14T15:00:00' },
  { id: 4, jobId: 204, companyName: '넥스트커머스', jobTitle: '플랫폼 백엔드', status: 'APPLIED', jobStatus: 'OPEN', appliedAt: '2026-06-15T09:30:00' },
];

export const mockTrends = [
  { skillId: 6, skillName: 'Kafka', jobCount: 1284, requiredCount: 526, preferredCount: 412, trendScore: 92, growth: 12, owned: false, insight: '주니어 백엔드 공고에서 이벤트 기반 주문·결제·알림 도메인 요구가 빠르게 늘고 있습니다.' },
  { skillId: 9, skillName: 'Kubernetes', jobCount: 1284, requiredCount: 488, preferredCount: 360, trendScore: 88, growth: 9, owned: false, insight: '운영 자동화와 배포 안정성을 보는 공고에서 빠르게 늘고 있습니다.' },
  { skillId: 5, skillName: 'Redis', jobCount: 1284, requiredCount: 680, preferredCount: 280, trendScore: 76, growth: 5, owned: true, insight: '캐시·세션·랭킹 기능을 다루는 공고에서 반복적으로 등장합니다.' },
  { skillId: 2, skillName: 'Spring Boot', jobCount: 1284, requiredCount: 1001, preferredCount: 152, trendScore: 72, growth: 3, owned: true, insight: '백엔드 주니어 공고에서 가장 안정적인 기본 스택으로 유지됩니다.' },
  { skillId: 7, skillName: 'Docker', jobCount: 1284, requiredCount: 860, preferredCount: 211, trendScore: 67, growth: 2, owned: true, insight: '배포 경험을 묻는 공고에서 기본 체크리스트처럼 등장합니다.' },
];

export const mockMarket = [{ role: 'BACKEND', careerLevel: 'JUNIOR', jobCount: 1284, openJobCount: 1032, closedJobCount: 190, expiredJobCount: 62, avgMinExperienceYears: 0.8, avgMaxExperienceYears: 3.2 }];

export const mockGapSkills = [
  { skill: 'Kubernetes', addedJobs: 7, demand: 38, unlocked: ['플로우랩', '노바랩스', '코어페이'], reason: '배포·운영 자동화를 요구하는 주니어 백엔드 공고에서 빠르게 증가' },
  { skill: 'Kafka', addedJobs: 4, demand: 41, unlocked: ['넥스트커머스', '리플로우'], reason: '커머스 주문·결제 이벤트 처리 공고에서 반복 요구' },
  { skill: 'Terraform', addedJobs: 3, demand: 24, unlocked: ['플로우랩'], reason: '클라우드 인프라 운영까지 맡는 서버 포지션에서 우대' },
  { skill: 'Grafana', addedJobs: 2, demand: 21, unlocked: ['플로우랩'], reason: '장애 대응과 관측 가능성 경험을 보는 공고에서 등장' },
];
