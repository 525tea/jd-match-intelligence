import { api, authStore, unwrapList } from '../api/client.js';


const clone = (value) => JSON.parse(JSON.stringify(value));
const asList = unwrapList;

const initials = (text = '') => String(text).replace(/[^A-Za-z0-9가-힣]/g, ' ').trim().split(/\s+/).map((x) => x[0]).join('').slice(0, 2).toUpperCase() || 'JF';

const dday = (deadlineAt) => {
  if (!deadlineAt) return '상시';
  const now = new Date();
  const due = new Date(deadlineAt);
  if (Number.isNaN(due.getTime())) return '상시';
  const diff = Math.ceil((due.setHours(0, 0, 0, 0) - now.setHours(0, 0, 0, 0)) / 86400000);
  if (diff < 0) return '마감';
  if (diff === 0) return 'D-DAY';
  return `D-${diff}`;
};

const careerLabel = (career, min, max) => {
  if (min !== undefined && min !== null && max !== undefined && max !== null) {
    if (Number(min) === 0 && Number(max) === 0) return '신입';
    if (Number(min) === 0) return `~${max}년`;
    return `${min}~${max}년`;
  }
  return ({ ANY: '전체', NEWCOMER: '신입', JUNIOR: '신입~3년', MID: '3~7년', SENIOR: '시니어', LEAD: '리드' }[career] || career || '경력 무관');
};

const roleLabel = (role, detail) => detail || ({ BACKEND: 'Backend Engineer', FRONTEND: 'Frontend Engineer', FULLSTACK: 'Full Stack Engineer', DEVOPS: 'DevOps Engineer', DATA_ENGINEER: 'Data Engineer', AI_ENGINEER: 'AI Engineer', ML_ENGINEER: 'ML Engineer', SECURITY: 'Security Engineer' }[role] || role || 'Software Engineer');

const tagLabel = (code) => ({
  EVENT_DRIVEN: '이벤트 드리븐', CACHE_STRATEGY: '캐시 전략', BATCH_PROCESSING: '배치 처리',
  HIGH_TRAFFIC: '대용량 트래픽', DISTRIBUTED_SYSTEM: '분산 시스템', CI_CD: 'CI/CD',
  TESTING: '테스트', AUTH: '인증/인가', OBSERVABILITY: '모니터링', IDEMPOTENCY: '멱등 처리',
}[code] || code);

const skillNames = (skills) => (skills || []).map((s) => s.skillName || s.name || s).filter(Boolean);
const tagCodes = (tags) => (tags || []).map((t) => t.tagCode || t.code || t).filter(Boolean);
const scoreOf = (job, fallback = 72) => Math.round(Number(job?.score?.totalScore ?? job?.score?.skillMatchRate ?? job?.matchScore ?? job?.score ?? fallback));

const parseDdayNumber = (value) => {
  const n = Number(String(value || '').replace(/\D/g, ''));
  return Number.isFinite(n) && n > 0 ? n : 999;
};

export function toPrototypeJob(job, index = 0) {
  const rawJob = job.job || job;
  const companyKo = rawJob.companyName || rawJob.companyKo || job.companyName || '회사명';
  const skills = skillNames(rawJob.skills || job.skills);
  const matched = [
    ...(job.matchedRequiredSkills || []),
    ...(job.matchedPreferredSkills || []),
  ];
  const missing = [
    ...(job.missingRequiredSkills || []),
    ...(job.missingPreferredSkills || []),
  ];
  const visibleSkills = matched.length || missing.length ? matched : skills;
  const score = scoreOf(job, Math.max(58, 92 - index * 4));
  return {
    id: rawJob.id || rawJob.jobId || job.jobId || job.id,
    jobId: rawJob.jobId || rawJob.id || job.jobId || job.id,
    sourceId: rawJob.sourceId || job.sourceId,
    company: rawJob.company || job.company || companyKo,
    companyKo,
    logo: rawJob.logo || job.logo || initials(companyKo),
    fullTitle: rawJob.fullTitle || rawJob.title || job.jobTitle || job.title || `${companyKo} 백엔드 엔지니어 채용`,
    title: rawJob.title || job.jobTitle || job.title || roleLabel(rawJob.role || job.role, rawJob.roleDetail || job.roleDetail),
    role: roleLabel(rawJob.role || job.role, rawJob.roleDetail || job.roleDetail),
    level: careerLabel(rawJob.careerLevel || job.careerLevel, rawJob.minExperienceYears ?? job.minExperienceYears, rawJob.maxExperienceYears ?? job.maxExperienceYears),
    location: [rawJob.locationRegion || job.locationRegion, rawJob.locationCity || job.locationCity].filter(Boolean).join(' ') || '위치 협의',
    score,
    required: Math.round(Number(job.score?.requiredSkillRate ?? job.requiredMatchRate ?? score)),
    preferred: Math.round(Number(job.score?.preferredSkillRate ?? job.preferredMatchRate ?? Math.max(40, score - 24))),
    applicants: job.applicants || Math.max(8, 64 - index * 3),
    matched: [...new Set(visibleSkills)].slice(0, 5),
    missing: [...new Set(missing.length ? missing : skills.slice(4, 6))].slice(0, 3),
    requiredSkills: skillNames(rawJob.skills?.filter?.((s) => s.requirementType === 'REQUIRED')) || skillNames(job.skills?.filter?.((s) => s.requirementType === 'REQUIRED')) || visibleSkills,
    preferredSkills: skillNames(rawJob.skills?.filter?.((s) => s.requirementType === 'PREFERRED')) || skillNames(job.skills?.filter?.((s) => s.requirementType === 'PREFERRED')) || [],
    tags: tagCodes(rawJob.experienceTags).length ? tagCodes(rawJob.experienceTags) : tagCodes(job.experienceTags).length ? tagCodes(job.experienceTags) : tagCodes(job.matchedExperienceTags).concat(tagCodes(job.missingExperienceTags)).slice(0, 3),
    deadline: dday(rawJob.deadlineAt || job.deadlineAt) || job.deadline || '상시',
    views: job.views || Math.max(90, 326 - index * 17),
    companyIntro: rawJob.companyIntro || job.companyIntro || `${companyKo}의 제품 개발 조직입니다.`,
    desc: rawJob.description || job.description || '수집된 공고 원문을 기반으로 주요 업무와 요구 기술을 확인합니다.',
  };
}

const toTrend = (trend, index = 0, ownedSkills = new Set()) => {
  const required = Number(trend.requiredCount || 0);
  const total = Number(trend.jobCount || trend.totalJobs || 1284);
  const rate = trend.rate || Math.min(92, Math.round((required / Math.max(1, total)) * 100) || trend.trendScore || 40 + index * 6);
  const name = trend.skillName || trend.name;
  return {
    name,
    rate,
    growth: trend.growth || Math.max(2, 12 - index * 2),
    owned: ownedSkills.has(name) || Boolean(trend.owned),
    insight: trend.insight || `${name} 요구가 최근 공고에서 꾸준히 등장하고 있습니다.`,
  };
};

const toApplication = (app) => ({
  id: app.id || app.applicationId,
  jobId: app.jobId,
  company: app.companyName || app.company || '회사명',
  title: app.jobTitle || app.title || '공고명',
  status: app.status || 'APPLIED',
  appliedAt: app.appliedAt,
  updatedAt: app.updatedAt,
});

const toSkill = (skill) => ({
  name: skill.skillName || skill.name,
  conf: Math.round(Number(skill.confidence || skill.conf || 70)),
  level: skill.source === 'LLM' ? '분석 신뢰도' : '근거 강도',
  commits: skill.commits || 0,
  files: skill.files || 0,
  reason: skill.evidence || skill.reason || '프로젝트 분석 결과에서 검출되었습니다.',
});

const toExpTag = (tag) => ({
  code: tag.tagCode || tag.code,
  label: tag.tagName || tag.label || tagLabel(tag.tagCode || tag.code),
  sentence: tag.evidence || tag.sentence || tag.description || '프로젝트 분석 결과에서 관련 경험 태그로 추출되었습니다.',
});

const toUserJobCard = (item, index = 0) => toPrototypeJob({
  id: item.jobId,
  jobId: item.jobId,
  title: item.jobTitle || item.title,
  companyName: item.companyName,
  deadlineAt: item.deadlineAt,
  score: 78 - index * 2,
}, index);

const buildPipeline = (applications = []) => {
  const steps = [
    ['APPLIED', '지원'],
    ['DOCUMENT_PASSED', '서류'],
    ['CODING_TEST', '코테'],
    ['INTERVIEW', '면접'],
    ['OFFER', '오퍼'],
  ];
  return steps.map(([key, label]) => ({
    key,
    label,
    count: applications.filter((app) => app.status === key).length,
  }));
};

const toGapSkills = (gapResponse, fallback = []) => {
  const bySkill = new Map();
  asList(gapResponse?.jobMatches).forEach((match) => {
    const missing = [
      ...(match.missingRequiredSkills || []),
      ...(match.missingPreferredSkills || []),
    ];
    const evidenceAdded = Number(match.evidence?.addedJobs || 0);
    missing.forEach((skill) => {
      const current = bySkill.get(skill) || {
        skill,
        addedJobs: 0,
        demand: 20,
        owned: false,
        unlocked: [],
        reason: '',
      };
      current.addedJobs += Math.max(1, evidenceAdded || 1);
      current.demand = Math.min(95, current.demand + 8);
      if (match.companyName && !current.unlocked.includes(match.companyName)) current.unlocked.push(match.companyName);
      const learning = asList(match.evidence?.learningConnections).find((x) => x.missingSkillName === skill);
      const tag = asList(match.evidence?.relatedTags).find((x) => x.skillName === skill);
      current.reason = learning?.reason || tag?.tagDescription || `${skill} 요구 공고에서 반복적으로 부족 신호가 감지됐습니다.`;
      bySkill.set(skill, current);
    });
  });
  const rows = [...bySkill.values()]
    .map((row) => ({ ...row, unlocked: row.unlocked.slice(0, 4) }))
    .sort((a, b) => b.addedJobs - a.addedJobs);
  return rows.length ? rows : fallback;
};

async function settle(key, loader) {
  try {
    return { key, ok: true, data: await loader() };
  } catch (error) {
    return { key, ok: false, error };
  }
}

async function hydrateJobDetails(jobs, limit = 24) {
  const target = jobs.slice(0, limit);
  const details = await Promise.all(target.map((job) => settle(`job-${job.id}`, () => api.job(job.id))));
  const byId = new Map();
  details.forEach((result) => {
    if (result.ok && result.data?.id) byId.set(result.data.id, result.data);
  });
  return jobs.map((job) => byId.get(job.id) || job);
}

function attachLookup(next) {
  const everyJob = [
    ...(next.listings || []),
    ...(next.matches || []),
    ...(next.popular || []),
    ...(next.closing || []),
    ...(next.userJobs?.saved || []),
    ...(next.userJobs?.viewed || []),
    ...(next.userJobs?.ignored || []),
  ];
  next.__jobIdByCompany = Object.fromEntries(everyJob.filter((job) => job.companyKo && (job.jobId || job.id)).map((job) => [job.companyKo, job.jobId || job.id]));
  next.__jobByCompany = Object.fromEntries(everyJob.filter((job) => job.companyKo).map((job) => [job.companyKo, job]));
  next.__jobCompanyById = Object.fromEntries(everyJob.filter((job) => job.companyKo && (job.jobId || job.id)).map((job) => [String(job.jobId || job.id), job.companyKo]));
  return next;
}

export async function loadJobFlowData(baseJF) {
  const next = clone(baseJF);
  const token = authStore.getToken();
  const publicResults = await Promise.all([
    settle('jobs', () => api.jobs()),
    settle('trends', () => api.skillTrends({ limit: 8 })),
    settle('market', () => api.market({ role: 'BACKEND', limit: 5 })),
  ]);
  next.__apiStatus = Object.fromEntries(publicResults.map((result) => [result.key, result.ok ? 'ok' : 'mock']));

  const jobs = publicResults.find((x) => x.key === 'jobs');
  const jobRows = asList(jobs?.data);
  if (jobs?.ok && jobRows.length) {
    const detailedJobs = await hydrateJobDetails(jobRows);
    const mapped = detailedJobs.map(toPrototypeJob);
    next.listings = mapped;
    next.popular = mapped.slice(0, 4);
    next.closing = mapped.slice().sort((a, b) => parseDdayNumber(a.deadline) - parseDdayNumber(b.deadline)).slice(0, 3);
    next.market.totalCount = Math.max(next.market.totalCount || 0, mapped.length);
  }

  const trends = publicResults.find((x) => x.key === 'trends');
  const trendRows = asList(trends?.data);
  if (trends?.ok && trendRows.length) {
    const owned = new Set(next.skills.map((s) => s.name));
    next.trends = trendRows.map((t, i) => toTrend(t, i, owned));
  }

  const market = publicResults.find((x) => x.key === 'market');
  const marketRows = asList(market?.data);
  if (market?.ok && marketRows.length) {
    const total = marketRows.reduce((sum, row) => sum + Number(row.jobCount || 0), 0);
    const open = marketRows.reduce((sum, row) => sum + Number(row.openJobCount || 0), 0);
    next.market.totalCount = total || next.market.totalCount;
    next.market.avgOpenDays = marketRows[0]?.avgOpenDays || next.market.avgOpenDays || 18;
    next.market.openJobCount = open || undefined;
  }

  if (!token) return attachLookup(next);

  const authResults = await Promise.all([
    settle('me', () => api.me()),
    settle('applications', () => api.applications()),
    settle('recommendations', () => api.recommendations(1, { targetRoles: ['BACKEND'], limit: 12 })),
    settle('matches', () => api.projectJobMatches(1, { targetRoles: ['BACKEND'], targetCareerLevel: 'NEWCOMER', limit: 12 })),
    settle('skills', () => api.projectSkills(1)),
    settle('tags', () => api.projectExperienceTags(1)),
    settle('gap', () => api.gapAnalysis(1, { targetRoles: ['BACKEND'], limit: 20 })),
    settle('saved', () => api.savedJobs()),
    settle('viewed', () => api.viewedJobs()),
    settle('ignored', () => api.ignoredJobs()),
  ]);
  next.__apiStatus = {
    ...(next.__apiStatus || {}),
    ...Object.fromEntries(authResults.map((result) => [result.key, result.ok ? 'ok' : 'mock'])),
  };

  const me = authResults.find((x) => x.key === 'me');
  if (me?.ok && me.data) {
    next.user = {
      ...next.user,
      name: me.data.name || next.user.name,
      role: me.data.role || next.user.role,
      github: true,
      email: me.data.email,
    };
  }

  const skillsResult = authResults.find((x) => x.key === 'skills');
  const skillRows = asList(skillsResult?.data);
  if (skillsResult?.ok && skillRows.length) {
    next.skills = skillRows.map(toSkill).filter((x) => x.name);
  }

  const tagsResult = authResults.find((x) => x.key === 'tags');
  const tagRows = asList(tagsResult?.data);
  if (tagsResult?.ok && tagRows.length) {
    next.expTags = tagRows.map(toExpTag).filter((x) => x.code);
  }

  const matches = authResults.find((x) => x.key === 'matches');
  const recommendations = authResults.find((x) => x.key === 'recommendations');
  const matchRows = asList(matches?.data);
  const recommendationRows = asList(recommendations?.data);
  const matchSource = matches?.ok && matchRows.length ? matchRows : recommendations?.ok && recommendationRows.length ? recommendationRows : null;
  if (matchSource) {
    next.matches = matchSource.map(toPrototypeJob).slice(0, 8);
  }

  const apps = authResults.find((x) => x.key === 'applications');
  const appRows = asList(apps?.data);
  if (apps?.ok && appRows.length) {
    next.applications = appRows.map(toApplication);
    next.pipeline = buildPipeline(next.applications);
  }

  const gap = authResults.find((x) => x.key === 'gap');
  if (gap?.ok && gap.data) {
    next.gapSkills = toGapSkills(gap.data, next.gapSkills);
    const gapMatches = asList(gap.data.jobMatches);
    if (gapMatches.length && !matchSource) next.matches = gapMatches.map(toPrototypeJob).slice(0, 8);
  }

  const saved = authResults.find((x) => x.key === 'saved');
  const savedRows = asList(saved?.data);
  if (saved?.ok) next.saved = savedRows.length;
  const viewed = authResults.find((x) => x.key === 'viewed');
  const viewedRows = asList(viewed?.data);
  if (viewed?.ok) next.viewed = viewedRows.length;
  const ignored = authResults.find((x) => x.key === 'ignored');
  const ignoredRows = asList(ignored?.data);
  if (saved?.ok || viewed?.ok || ignored?.ok) {
    next.userJobs = {
      saved: savedRows.map(toUserJobCard),
      viewed: viewedRows.map(toUserJobCard),
      ignored: ignoredRows.map(toUserJobCard),
    };
    next.ignored = ignoredRows.length;
  }

  return attachLookup(next);
}
