const filterOptions = {
  roles: [
    'BACKEND', 'FRONTEND', 'FULLSTACK', 'ANDROID', 'IOS', 'DEVOPS', 'SRE', 'DBA', 'SECURITY',
    'DATA_ENGINEER', 'ML_ENGINEER', 'AI_ENGINEER', 'QA', 'PM', 'CROSS_PLATFORM',
    'SOFTWARE_ENGINEER', 'EMBEDDED_SOFTWARE', 'APPLICATION_SOFTWARE', 'DATA_ANALYST',
    'DATA_SCIENTIST', 'ETC',
  ],
  careers: ['ANY', 'NEWCOMER', 'JUNIOR', 'MID', 'SENIOR', 'LEAD'],
  employmentTypes: ['FULL_TIME', 'CONTRACT', 'INTERN', 'PART_TIME', 'FREELANCE', 'ETC'],
  remoteTypes: ['ONSITE', 'REMOTE', 'HYBRID', 'FLEXIBLE'],
  statuses: ['OPEN', 'CLOSED', 'EXPIRED', 'HIDDEN'],
  regions: ['서울', '경기', '인천', '부산', '대전', '대구', '광주', '원격', '전국'],
  companySizes: ['스타트업', '중소기업', '중견기업', '대기업', '외국계'],
  deadlines: ['전체', '오늘 마감', '3일 이내', '7일 이내', '상시'],
  skills: [
    'Java', 'JavaScript', 'TypeScript', 'Python', 'Go', 'Kotlin', 'Spring Boot',
    'Spring Security', 'Spring Data JPA', 'React', 'Node.js', 'MySQL', 'PostgreSQL',
    'Redis', 'Elasticsearch', 'MongoDB', 'Docker', 'Kubernetes', 'AWS', 'Kafka',
    'Jenkins', 'Nginx', 'JWT', 'OAuth2',
  ],
  experienceTags: [
    'CI_CD', 'TESTING', 'CLOUD_INFRA', 'MONITORING', 'PERFORMANCE', 'RELIABILITY',
    'EVENT_DRIVEN', 'CACHE_STRATEGY', 'HIGH_TRAFFIC', 'DISTRIBUTED_SYSTEM',
  ],
};

export const JF = {
  user: { name: '사용자', role: 'USER', level: '', handle: '', github: false },

  projects: { analyzed: 0, pending: 0, skillsTotal: 0, tagsTotal: 0 },
  projectList: [],
  matches: [],
  recommendations: [],
  skills: [],
  expTags: [],
  applications: [],
  pipeline: [
    { key: 'APPLIED', label: '지원', count: 0, tone: 'neutral' },
    { key: 'DOCUMENT_PASSED', label: '서류', count: 0, tone: 'neutral' },
    { key: 'CODING_TEST', label: '코테', count: 0, tone: 'active' },
    { key: 'INTERVIEW', label: '면접', count: 0, tone: 'active' },
    { key: 'OFFER', label: '오퍼', count: 0, tone: 'strong' },
  ],

  saved: 0,
  viewed: 0,
  ignored: 0,
  userJobs: { saved: [], viewed: [], ignored: [] },

  popular: [],
  closing: [],
  trends: [],
  market: { totalCount: 0, avgOpenDays: 0 },
  gapSkills: [],
  insight: null,
  listings: [],

  filterOptions,
  tagLabel: {
    EVENT_DRIVEN: '이벤트 드리븐',
    CACHE_STRATEGY: '캐시 전략',
    BATCH_PROCESSING: '배치 처리',
    HIGH_TRAFFIC: '대용량 트래픽',
    DISTRIBUTED_SYSTEM: '분산 시스템',
    CI_CD: 'CI/CD',
    TESTING: '테스트',
    AUTH: '인증/인가',
    OBSERVABILITY: '모니터링',
    CLOUD_INFRA: '클라우드/인프라',
    MONITORING: '모니터링',
    PERFORMANCE: '성능 최적화',
    RELIABILITY: '안정성',
  },

  onboarding: {
    roles: ['백엔드', '풀스택', '소프트웨어 엔지니어', '프론트엔드'],
    careers: ['학생·취준생', '신입 (1년 미만)', '주니어 (1~3년)', '미드 (3~5년)'],
    skills: {
      '백엔드': ['Java', 'Spring Boot', 'JPA', 'MySQL', 'PostgreSQL', 'Redis', 'Kafka', 'Docker', 'Kubernetes', 'AWS', 'Python', 'Go', 'Kotlin', 'Node.js', 'MongoDB', 'gRPC', 'Jenkins', 'Nginx'],
      default: ['JavaScript', 'TypeScript', 'React', 'Node.js', 'Python', 'Java', 'Spring Boot', 'Docker', 'AWS', 'MySQL', 'Redis', 'GraphQL'],
    },
  },
};
