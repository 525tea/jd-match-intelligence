// Prototype data ported from docs/design-inprocess/jobflow-data.js
// Same content across every concept so we compare visuals, not data.
export const JF = {
  user: { name: '사용자', role: 'Backend', level: 'Junior', handle: 'example-user', github: true },

  projects: { analyzed: 3, pending: 1, skillsTotal: 24, tagsTotal: 7 },
  projectList: [
    {
      name: 'commerce-api', repo: 'github.com/example-org/commerce-api', connected: true, analyzedAt: '2시간 전', skillsTotal: 24, tagsTotal: 7, matchedJobs: 18,
      previewSkills: ['Java', 'Spring Boot', 'JPA', 'Kafka'],
      repoVisual: 'commerce-api / main · Java 78%',
      summary: '결제 승인, 정산, 이벤트 발행 흐름이 분리된 커머스 결제 API',
      overview: '결제 승인 → 정산 → 이벤트 발행으로 책임이 분리된 커머스 결제 API입니다. 헥사고날 구조로 도메인과 인프라를 분리했고, Kafka로 주문·정산 이벤트를 비동기 전파합니다. 대용량 결제 트래픽을 가정한 멱등 처리와 재시도 설계가 들어 있습니다.',
      domain: ['결제 승인', '정산', '이벤트 발행'],
      architecture: 'Hexagonal · Event-Driven',
      stackGroups: [
        { label: 'Language', items: [{ n: 'Java', pct: 78 }, { n: 'SQL', pct: 14 }, { n: 'Shell', pct: 8 }] },
        { label: 'Framework', items: [{ n: 'Spring Boot' }, { n: 'Spring Data JPA' }, { n: 'Spring Kafka' }] },
        { label: 'Infra / DevOps', items: [{ n: 'Docker' }, { n: 'GitHub Actions' }, { n: 'AWS ECS' }] },
        { label: 'Data', items: [{ n: 'MySQL' }, { n: 'Redis' }, { n: 'Kafka' }] },
      ],
      stats: { commits: 412, files: 168, tests: 64, contributors: 3 },
      dirs: [
        { path: 'domain/payment', share: 34 },
        { path: 'domain/settlement', share: 22 },
        { path: 'infra/kafka', share: 18 },
        { path: 'api/controller', share: 14 },
        { path: 'common', share: 12 },
      ],
      detailTags: [
        { code: 'EVENT_DRIVEN', label: '이벤트 드리븐', sentence: 'commerce-api의 결제 모듈에서 주문·정산 이벤트를 Kafka로 발행/구독하는 이벤트 드리븐 아키텍처를 구현했습니다.' },
        { code: 'CI_CD', label: 'CI/CD', sentence: 'GitHub Actions로 테스트·빌드·ECS 배포 파이프라인을 구성해 메인 머지 시 자동 배포되도록 했습니다.' },
        { code: 'IDEMPOTENCY', label: '멱등 처리', sentence: '결제 승인 API에 멱등 키와 재시도 전략을 적용해 중복 결제를 방지했습니다.' },
      ],
    },
    {
      name: 'search-indexer', repo: 'github.com/example-org/search-indexer', connected: true, analyzedAt: '어제', skillsTotal: 20, tagsTotal: 6, matchedJobs: 12,
      previewSkills: ['Java', 'Redis', 'Elasticsearch', 'Docker'],
      repoVisual: 'search-indexer / main · Search 64%',
      summary: '공고 검색과 추천 후보 생성을 위한 검색 인덱싱 서비스',
      overview: '공고 데이터를 색인하고 추천 후보를 생성하는 검색 인덱싱 서비스입니다. 변경 이벤트를 받아 증분 색인하고, Redis 캐시로 인기 검색 응답을 가속합니다.',
      domain: ['검색 색인', '추천 후보', '캐시'],
      architecture: 'Pipeline · CQRS-lite',
      stackGroups: [
        { label: 'Language', items: [{ n: 'Java', pct: 64 }, { n: 'Kotlin', pct: 22 }, { n: 'Shell', pct: 14 }] },
        { label: 'Framework', items: [{ n: 'Spring Boot' }, { n: 'Spring Batch' }] },
        { label: 'Infra / DevOps', items: [{ n: 'Docker' }, { n: 'GitHub Actions' }] },
        { label: 'Data', items: [{ n: 'Elasticsearch' }, { n: 'Redis' }, { n: 'MySQL' }] },
      ],
      stats: { commits: 286, files: 121, tests: 57, contributors: 2 },
      dirs: [
        { path: 'indexer/core', share: 38 },
        { path: 'recommend', share: 24 },
        { path: 'cache', share: 18 },
        { path: 'api', share: 12 },
        { path: 'common', share: 8 },
      ],
      detailTags: [
        { code: 'CACHE_STRATEGY', label: '캐시 전략', sentence: '인기 검색어 응답에 Redis 캐시를 적용해 반복 조회 부하를 줄이는 캐시 계층을 설계했습니다.' },
        { code: 'BATCH_PROCESSING', label: '배치 처리', sentence: 'Spring Batch로 대량 공고 데이터를 증분 색인하는 배치 파이프라인을 구성했습니다.' },
      ],
    },
    {
      name: 'payment-worker', repo: '이름만 입력됨', connected: false, analyzedAt: '미분석', skillsTotal: 0, tagsTotal: 0, matchedJobs: 0,
      previewSkills: ['Kafka', 'Batch', 'MySQL'],
      repoVisual: 'connect GitHub repository',
      summary: '비동기 정산 워커로 추정되는 프로젝트. GitHub 연결 필요',
    },
  ],

  matches: [
    {
      company: 'CorePay', companyKo: '코어페이', logo: 'CP', fullTitle: '코어페이 결제 플랫폼 백엔드 엔지니어 신입 채용', title: '백엔드 엔지니어', role: 'Backend Engineer', level: '신입', location: '서울 강남',
      score: 94, required: 100, preferred: 67, applicants: 58,
      matched: ['Java', 'Spring Boot', 'JPA', 'MySQL'], missing: ['Kubernetes'],
      requiredSkills: ['Java', 'Spring Boot', 'JPA', 'MySQL'], preferredSkills: ['AWS', 'Redis', 'Jenkins'],
      tags: ['EVENT_DRIVEN', 'CI_CD'], deadline: 'D-4', views: 326,
      companyIntro: '실시간 결제 승인과 정산 인프라를 만드는 핀테크 플랫폼입니다.',
      desc: '결제 플랫폼의 핵심 거래 서버를 함께 만들 백엔드 엔지니어를 찾습니다. 대용량 트래픽 환경에서 안정적인 결제 처리와 정산 시스템을 설계·운영합니다.',
    },
    {
      company: 'NextCommerce', companyKo: '넥스트커머스', logo: 'NC', fullTitle: '넥스트커머스 플랫폼 백엔드 주니어 개발자 채용', title: '플랫폼 백엔드', role: 'Platform Backend', level: '신입~3년', location: '서울 성수',
      score: 88, required: 83, preferred: 50, applicants: 47,
      matched: ['Java', 'Redis', 'Docker'], missing: ['Kafka'],
      requiredSkills: ['Java', 'Redis', 'Docker', 'Kafka'], preferredSkills: ['AWS', 'MSA'],
      tags: ['HIGH_TRAFFIC', 'CACHE_STRATEGY'], deadline: 'D-9', views: 284,
      companyIntro: '상품·주문·추천 도메인을 운영하는 커머스 SaaS 팀입니다.',
      desc: '커머스 플랫폼의 상품·주문 도메인 백엔드를 담당합니다. 이벤트 기반 아키텍처와 캐시 전략으로 트래픽을 견디는 시스템을 만듭니다.',
    },
    {
      company: 'FlowLab', companyKo: '플로우랩', logo: 'FL', fullTitle: '플로우랩 서버 엔지니어 신입 채용', title: '서버 엔지니어', role: 'Server Engineer', level: '신입', location: '경기 판교',
      score: 81, required: 75, preferred: 60, applicants: 39,
      matched: ['Spring Boot', 'AWS'], missing: ['Kubernetes', 'Terraform'],
      requiredSkills: ['Spring Boot', 'AWS', 'Kubernetes', 'Terraform'], preferredSkills: ['Docker', 'Jenkins', 'Grafana'],
      tags: ['CI_CD', 'OBSERVABILITY'], deadline: 'D-12', views: 211,
      companyIntro: '워크플로우 자동화 SaaS를 만드는 생산성 도구 팀입니다.',
      desc: '워크플로우 자동화 SaaS의 서버 인프라를 설계·운영합니다. IaC와 CI/CD 파이프라인으로 배포 자동화를 고도화합니다.',
    },
  ],

  skills: [
    { name: 'Java', conf: 98, level: '근거 강도', commits: 142, files: 38, reason: '결제 승인·정산 도메인의 핵심 서비스와 테스트에서 반복 검출' },
    { name: 'Spring Boot', conf: 95, level: '근거 강도', commits: 118, files: 31, reason: 'API 서버 엔트리포인트, 의존성, 설정 파일에서 핵심 프레임워크로 검출' },
    { name: 'JPA', conf: 88, level: '근거 강도', commits: 76, files: 24, reason: 'Entity, Repository, Query 메서드 사용 패턴에서 검출' },
    { name: 'MySQL', conf: 80, level: '분석 신뢰도', commits: 44, files: 12, reason: '스키마 마이그레이션과 datasource 설정 기반 후보' },
    { name: 'Redis', conf: 72, level: '분석 신뢰도', commits: 29, files: 9, reason: '캐시 설정과 세션 저장소 의존성에서 검출' },
    { name: 'Kafka', conf: 65, level: '분석 신뢰도', commits: 22, files: 8, reason: '이벤트 발행 모듈과 메시지 토픽 네이밍에서 후보로 추출' },
    { name: 'Docker', conf: 60, level: '근거 강도', commits: 18, files: 5, reason: 'Dockerfile, compose 설정, 배포 스크립트에서 검출' },
    { name: 'AWS', conf: 54, level: '분석 신뢰도', commits: 12, files: 4, reason: 'S3, CloudWatch 환경변수와 배포 문맥에서 후보로 추출' },
  ],

  expTags: [
    { code: 'EVENT_DRIVEN', label: '이벤트 드리븐', sentence: 'commerce-api의 결제 모듈에서 승인 이벤트를 Kafka 토픽으로 분리해 후속 정산 흐름을 비동기 처리했습니다.' },
    { code: 'CACHE_STRATEGY', label: '캐시 전략', sentence: '상품·결제 조회 API에서 Redis 캐시를 사용해 반복 조회 부하를 줄이는 구조가 감지됐습니다.' },
    { code: 'CI_CD', label: 'CI/CD', sentence: '테스트와 빌드 파이프라인을 분리해 배포 전 검증 단계를 자동화한 흔적이 있습니다.' },
    { code: 'TESTING', label: '테스트', sentence: '결제 실패, 중복 승인, 정산 예외 케이스에 대한 테스트 코드가 반복적으로 확인됩니다.' },
  ],

  applications: [
    { company: '센티넬', title: '백엔드', status: 'INTERVIEW' },
    { company: '데이터포지', title: '플랫폼 엔지니어', status: 'CODING_TEST' },
    { company: '코어페이', title: '백엔드', status: 'DOCUMENT_PASSED' },
    { company: '넥스트커머스', title: '플랫폼 백엔드', status: 'APPLIED' },
  ],
  pipeline: [
    { key: 'APPLIED', label: '지원', count: 4, tone: 'neutral' },
    { key: 'DOCUMENT_PASSED', label: '서류', count: 3, tone: 'neutral' },
    { key: 'CODING_TEST', label: '코테', count: 2, tone: 'active' },
    { key: 'INTERVIEW', label: '면접', count: 1, tone: 'active' },
    { key: 'OFFER', label: '오퍼', count: 0, tone: 'strong' },
  ],

  saved: 8,
  viewed: 23,

  popular: [
    { companyKo: '리플로우', logo: 'RF', fullTitle: '리플로우 백엔드 엔지니어 신입 채용', title: '백엔드 엔지니어', role: 'Backend Engineer', level: '신입', location: '서울', deadline: 'D-4', skills: ['Java', 'Spring Boot', 'Kafka'], applicants: 142, views: 412, rank: 1 },
    { companyKo: '그리드원', logo: 'G1', fullTitle: '그리드원 서버 개발자 신입 및 주니어 채용', title: '서버 개발자', role: 'Server Developer', level: '신입~3년', location: '서울', deadline: 'D-7', skills: ['Java', 'MySQL', 'Redis'], applicants: 118, views: 368 },
    { companyKo: '베럴랩', logo: 'BL', fullTitle: '베럴랩 플랫폼 백엔드 엔지니어 채용', title: '플랫폼 백엔드', role: 'Platform Backend', level: '신입', location: '판교', deadline: 'D-3', skills: ['Kotlin', 'Spring', 'AWS'], applicants: 97, views: 295 },
    { companyKo: '큐브시스템', logo: 'QS', fullTitle: '큐브시스템 백엔드 엔지니어 주니어 채용', title: '백엔드 엔지니어', role: 'Backend Engineer', level: '신입', location: '서울', deadline: 'D-9', skills: ['Java', 'JPA', 'Docker'], applicants: 86, views: 244 },
  ],

  closing: [
    { companyKo: '스택플로우', title: '백엔드 엔지니어', deadline: 'D-1' },
    { companyKo: '노바랩스', title: '서버 개발자', deadline: 'D-2' },
    { companyKo: '코어페이', title: '백엔드 엔지니어', deadline: 'D-4' },
  ],

  trends: [
    { name: 'Docker', rate: 67, growth: 2, owned: true, insight: '배포 경험을 묻는 주니어 공고에서 기본 체크리스트처럼 등장합니다.' },
    { name: 'Spring Boot', rate: 78, growth: 3, owned: true, insight: '백엔드 주니어 공고에서 가장 안정적으로 높은 요구 비중을 유지합니다.' },
    { name: 'Redis', rate: 53, growth: 5, owned: true, insight: '캐시·세션·랭킹 기능을 다루는 공고에서 함께 등장합니다.' },
    { name: 'Kubernetes', rate: 38, growth: 9, owned: false, insight: '운영 자동화와 배포 안정성을 보는 공고에서 빠르게 늘고 있습니다.' },
    { name: 'Kafka', rate: 41, growth: 12, owned: false, insight: '이벤트 기반 주문·결제·알림 도메인에서 가장 빠르게 늘고 있습니다.' },
  ],
  market: { totalCount: 1284, avgOpenDays: 18 },

  // Based on backend enums and searchable job metadata:
  // JobRole, CareerLevel, EmploymentType, RemoteType, JobStatus, region, skill, experience tag.
  filterOptions: {
    roles: [
      'BACKEND', 'FRONTEND', 'FULLSTACK', 'ANDROID', 'IOS', 'DEVOPS', 'SRE', 'DBA', 'SECURITY',
      'DATA_ENGINEER', 'ML_ENGINEER', 'AI_ENGINEER', 'QA', 'PM', 'CROSS_PLATFORM',
      'SYSTEM_NETWORK', 'SYSTEM_SOFTWARE', 'SOFTWARE_ENGINEER', 'EMBEDDED_SOFTWARE',
      'ROBOT_SOFTWARE', 'IOT', 'APPLICATION_SOFTWARE', 'BLOCKCHAIN', 'WEB_PUBLISHING',
      'VR_AR_3D', 'ERP_SAP', 'GRAPHICS', 'HARDWARE_ENGINEER', 'IT_ETC', 'DATA_ANALYST',
      'DATA_SCIENTIST', 'MULTIMODAL_ENGINEER', 'GENERATIVE_AI', 'VISION_AUDIO_AI',
      'AUTONOMOUS_DRIVING', 'COMPUTER_VISION', 'AI_BUSINESS', 'AI_SERVICE_PLANNING',
      'AI_RESEARCHER', 'NLP', 'LLM', 'MLOPS', 'RAG', 'GAME_PM', 'GAME_OPERATION',
      'GAME_QA', 'GAME_CLIENT', 'GAME_SERVER', 'GAME_MOBILE', 'TECHNICAL_ARTIST',
      'GAME_ART', 'GAME_3D_MODELING', 'GAME_ANIMATION', 'GAME_EFFECT', 'GAME_INTERFACE',
      'GAME_DIRECTING_VIDEO', 'GAME_SOUND', 'GAME_ETC', 'ETC',
    ],
    careers: ['ANY', 'NEWCOMER', 'JUNIOR', 'MID', 'SENIOR', 'LEAD'],
    employmentTypes: ['FULL_TIME', 'CONTRACT', 'INTERN', 'PART_TIME', 'FREELANCE', 'MILITARY_SERVICE', 'ETC'],
    remoteTypes: ['ONSITE', 'REMOTE', 'HYBRID', 'FLEXIBLE'],
    statuses: ['OPEN', 'CLOSED', 'EXPIRED', 'HIDDEN'],
    regions: ['서울', '경기', '인천', '부산', '대전', '대구', '광주', '원격', '전국'],
    companySizes: ['스타트업', '중소기업', '중견기업', '대기업', '외국계'],
    deadlines: ['전체', '오늘 마감', '3일 이내', '7일 이내', '마감일 없음'],
    skills: [
      'Java', 'JavaScript', 'TypeScript', 'Python', 'Go', 'Kotlin', 'Spring Boot',
      'Spring Security', 'Spring Data JPA', 'React', 'Node.js', 'MySQL', 'PostgreSQL',
      'Redis', 'Elasticsearch', 'MongoDB', 'Docker', 'Docker Compose', 'Kubernetes',
      'AWS', 'AWS Lambda', 'Kafka', 'gRPC', 'Jenkins', 'Nginx', 'JWT', 'OAuth2',
    ],
    experienceTags: ['EVENT_DRIVEN', 'CACHE_STRATEGY', 'CI_CD', 'TESTING', 'AUTH', 'OBSERVABILITY', 'HIGH_TRAFFIC', 'DISTRIBUTED_SYSTEM'],
  },

  insight: { skill: 'Kubernetes', addedJobs: 7 },
  gapSkills: [
    { skill: 'Kubernetes', addedJobs: 7, demand: 38, owned: false, unlocked: ['플로우랩', '노바랩스', '코어페이'], reason: '배포·운영 자동화를 요구하는 주니어 백엔드 공고에서 빠르게 증가' },
    { skill: 'Kafka', addedJobs: 4, demand: 41, owned: false, unlocked: ['넥스트커머스', '리플로우'], reason: '커머스 주문·결제 이벤트 처리 공고에서 반복 요구' },
    { skill: 'Terraform', addedJobs: 3, demand: 24, owned: false, unlocked: ['플로우랩'], reason: '클라우드 인프라 운영까지 맡는 서버 포지션에서 우대' },
    { skill: 'Grafana', addedJobs: 2, demand: 21, owned: false, unlocked: ['플로우랩'], reason: '장애 대응과 관측 가능성 경험을 보는 공고에서 등장' },
  ],

  tagLabel: {
    EVENT_DRIVEN: '이벤트 드리븐', CACHE_STRATEGY: '캐시 전략', BATCH_PROCESSING: '배치 처리',
    HIGH_TRAFFIC: '대용량 트래픽', DISTRIBUTED_SYSTEM: '분산 시스템', CI_CD: 'CI/CD',
    TESTING: '테스트', AUTH: '인증/인가', OBSERVABILITY: '모니터링',
  },

  listings: [
    { companyKo: 'CJ ENM', logo: 'CJ', fullTitle: 'CJ ENM 커머스부문 [추천 플랫폼 엔지니어] 경력사원 채용', title: '추천 플랫폼 엔지니어', role: 'Backend Engineer', level: '1~3년', location: '서울 상암', deadline: 'D-6', skills: ['Java', 'Spring Boot', 'Kafka'], applicants: 164, views: 528, rank: 1, score: 91 },
    { companyKo: '리플로우', logo: 'RF', fullTitle: '리플로우 백엔드 엔지니어 신입 채용', title: '백엔드 엔지니어', role: 'Backend Engineer', level: '신입', location: '서울', deadline: 'D-4', skills: ['Java', 'Spring Boot', 'Kafka'], applicants: 142, views: 412, rank: 2, score: 88 },
    { companyKo: '그리드원', logo: 'G1', fullTitle: '그리드원 서버 개발자 신입 및 주니어 채용', title: '서버 개발자', role: 'Server Developer', level: '신입~3년', location: '서울', deadline: 'D-7', skills: ['Java', 'MySQL', 'Redis'], applicants: 118, views: 368, score: 84 },
    { companyKo: '베럴랩', logo: 'BL', fullTitle: '베럴랩 플랫폼 백엔드 엔지니어 채용', title: '플랫폼 백엔드', role: 'Platform Backend', level: '신입', location: '판교', deadline: 'D-3', skills: ['Kotlin', 'Spring', 'AWS'], applicants: 97, views: 295, score: 76 },
    { companyKo: '큐브시스템', logo: 'QS', fullTitle: '큐브시스템 백엔드 엔지니어 주니어 채용', title: '백엔드 엔지니어', role: 'Backend Engineer', level: '신입', location: '서울', deadline: 'D-9', skills: ['Java', 'JPA', 'Docker'], applicants: 86, views: 244, score: 79 },
    { companyKo: '스택플로우', logo: 'SF', fullTitle: '스택플로우 Python 백엔드 엔지니어 채용', title: '백엔드 엔지니어', role: 'Backend Engineer', level: '신입', location: '원격', deadline: 'D-1', skills: ['Python', 'Django', 'PostgreSQL'], applicants: 74, views: 219, score: 62 },
    { companyKo: '노바랩스', logo: 'NL', fullTitle: '노바랩스 Go 서버 개발자 채용', title: '서버 개발자', role: 'Server Developer', level: '1~3년', location: '부산', deadline: 'D-2', skills: ['Go', 'gRPC', 'Kubernetes'], applicants: 61, views: 184, score: 59 },
    { companyKo: '코어페이', logo: 'CP', fullTitle: '코어페이 결제 플랫폼 백엔드 엔지니어 신입 채용', title: '백엔드 엔지니어', role: 'Backend Engineer', level: '신입', location: '서울 강남', deadline: 'D-4', skills: ['Java', 'Spring Boot', 'JPA'], applicants: 58, views: 172, score: 94 },
    { companyKo: '넥스트커머스', logo: 'NC', fullTitle: '넥스트커머스 플랫폼 백엔드 주니어 개발자 채용', title: '플랫폼 백엔드', role: 'Platform Backend', level: '신입~3년', location: '서울 성수', deadline: 'D-9', skills: ['Java', 'Redis', 'Kafka'], applicants: 47, views: 151, score: 88 },
    { companyKo: '플로우랩', logo: 'FL', fullTitle: '플로우랩 서버 엔지니어 신입 채용', title: '서버 엔지니어', role: 'Server Engineer', level: '신입', location: '판교', deadline: 'D-12', skills: ['Spring Boot', 'AWS', 'Terraform'], applicants: 39, views: 138, score: 81 },
  ],

  onboarding: {
    roles: ['백엔드', '풀스택', '소프트웨어 엔지니어', '프론트엔드'],
    careers: ['학생·취준생', '신입 (1년 미만)', '주니어 (1~3년)', '미드 (3~5년)'],
    skills: {
      '백엔드': ['Java', 'Spring Boot', 'JPA', 'MySQL', 'PostgreSQL', 'Redis', 'Kafka', 'Docker', 'Kubernetes', 'AWS', 'Python', 'Go', 'Kotlin', 'Node.js', 'MongoDB', 'gRPC', 'Jenkins', 'Nginx'],
      'default': ['JavaScript', 'TypeScript', 'React', 'Node.js', 'Python', 'Java', 'Spring Boot', 'Docker', 'AWS', 'MySQL', 'Redis', 'GraphQL'],
    },
  },
};
