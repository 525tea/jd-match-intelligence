import { api, authStore, projectStore, unwrapList } from '../api/client.js';


const clone = (value) => JSON.parse(JSON.stringify(value));
const asList = unwrapList;

const initials = (text = '') => String(text).replace(/[^A-Za-z0-9가-힣]/g, ' ').trim().split(/\s+/).map((x) => x[0]).join('').slice(0, 2).toUpperCase() || 'JF';

const dday = (deadlineAt) => {
  if (!deadlineAt) return '마감 정보 없음';
  const now = new Date();
  const due = new Date(deadlineAt);
  if (Number.isNaN(due.getTime())) return '마감 정보 없음';
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

const roleLabel = (role, detail) => detail || ({
  BACKEND: '백엔드',
  FRONTEND: '프론트엔드',
  FULLSTACK: '풀스택',
  DEVOPS: 'DevOps',
  DATA_ENGINEER: '데이터 엔지니어',
  AI_ENGINEER: 'AI 엔지니어',
  ML_ENGINEER: 'ML 엔지니어',
  SECURITY: '보안 엔지니어',
}[role] || role || '소프트웨어 엔지니어');

const compactDetail = (value) => {
  const text = String(value || '').trim();
  if (!text) return '';
  if (text.length > 36) return '';
  if (text.split(/\s+/).length > 4) return '';
  return text;
};

const tagLabel = (code) => ({
  EVENT_DRIVEN: '이벤트 드리븐', CACHE_STRATEGY: '캐시 전략', BATCH_PROCESSING: '배치 처리',
  HIGH_TRAFFIC: '대용량 트래픽', DISTRIBUTED_SYSTEM: '분산 시스템', CI_CD: 'CI/CD',
  TESTING: '테스트', AUTH: '인증/인가', OBSERVABILITY: '모니터링', IDEMPOTENCY: '멱등 처리',
  CLOUD_INFRA: '클라우드/인프라', MONITORING: '모니터링', PERFORMANCE: '성능 최적화',
  RELIABILITY: '안정성',
}[code] || code);

const skillNames = (skills) => (skills || []).map((s) => s.skillName || s.name || s).filter(Boolean);
const tagCodes = (tags) => (tags || []).map((t) => t.tagCode || t.code || t).filter(Boolean);
const firstNonEmpty = (...items) => items.find((item) => Array.isArray(item) ? item.length : item !== undefined && item !== null && item !== '');
const scoreOf = (job, fallback = 0) => Math.round(Number(
  job?.score?.totalScore
  ?? job?.score?.skillMatchScore
  ?? job?.score?.matchScore
  ?? job?.score?.requiredSkillRate
  ?? job?.matchScore
  ?? job?.totalScore
  ?? job?.score
  ?? fallback
));

const parseDdayNumber = (value) => {
  const n = Number(String(value || '').replace(/\D/g, ''));
  return Number.isFinite(n) && n > 0 ? n : 999;
};

const USER_FACING_JOB_SOURCES = new Set(['WANTED', 'JUMPIT']);

const canonicalApplyUrl = (source, externalId, fallback = '') => {
  const id = String(externalId || '').trim();
  if (!id) return fallback || '';
  if (source === 'WANTED') return `https://www.wanted.co.kr/wd/${encodeURIComponent(id)}`;
  if (source === 'JUMPIT') return `https://jumpit.saramin.co.kr/position/${encodeURIComponent(id)}`;
  return fallback || '';
};

export const isUserFacingJob = (job = {}) => {
  const rawJob = job.job || job;
  const source = rawJob.source || job.source;
  const status = rawJob.status || job.status;
  const text = `${rawJob.title || job.title || ''} ${rawJob.companyName || job.companyName || ''} ${source || ''}`.toLowerCase();
  const fixtureLike = /smoke|mock|baseline|example|jobflow|manual|daily digest/.test(text);
  const visibleSource = !source || USER_FACING_JOB_SOURCES.has(source);
  const visibleStatus = !status || status === 'OPEN';
  return visibleSource && visibleStatus && !fixtureLike;
};

export function toPrototypeJob(job, index = 0) {
  const rawJob = job.job || job;
  const displayRole = roleLabel(rawJob.role || job.role, compactDetail(rawJob.roleDetail || job.roleDetail));
  const companyKo = rawJob.companyName || rawJob.companyKo || job.companyName || '회사명';
  const source = rawJob.source || job.source;
  const externalId = rawJob.externalId || rawJob.external_id || rawJob.sourceId || job.externalId || job.external_id || job.sourceId;
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
  const score = scoreOf(job);
  return {
    id: rawJob.id || rawJob.jobId || job.jobId || job.id,
    jobId: rawJob.jobId || rawJob.id || job.jobId || job.id,
    source,
    externalId,
    status: rawJob.status || job.status,
    sourceId: externalId,
    originalUrl: canonicalApplyUrl(source, externalId, rawJob.originalUrl || rawJob.original_url || rawJob.url || job.originalUrl || job.original_url || job.url),
    company: rawJob.company || job.company || companyKo,
    companyKo,
    logo: rawJob.logo || job.logo || initials(companyKo),
    fullTitle: rawJob.fullTitle || rawJob.title || job.jobTitle || job.title || '제목 없음',
    title: rawJob.title || job.jobTitle || job.title || roleLabel(rawJob.role || job.role, rawJob.roleDetail || job.roleDetail),
    role: displayRole,
    level: careerLabel(rawJob.careerLevel || job.careerLevel, rawJob.minExperienceYears ?? job.minExperienceYears, rawJob.maxExperienceYears ?? job.maxExperienceYears),
    location: [rawJob.locationRegion || job.locationRegion, rawJob.locationCity || job.locationCity].filter(Boolean).join(' ') || '위치 협의',
    score,
    required: Math.round(Number(job.score?.requiredSkillRate ?? job.requiredMatchRate ?? score)),
    preferred: Math.round(Number(job.score?.preferredSkillRate ?? job.preferredMatchRate ?? 0)),
    applicants: job.applicants || 0,
    matched: [...new Set(visibleSkills)].slice(0, 5),
    missing: [...new Set(missing)].slice(0, 3),
    requiredSkills: firstNonEmpty(skillNames(rawJob.skills?.filter?.((s) => s.requirementType === 'REQUIRED')), skillNames(job.skills?.filter?.((s) => s.requirementType === 'REQUIRED')), [...new Set([...(job.matchedRequiredSkills || []), ...(job.missingRequiredSkills || [])])], visibleSkills),
    preferredSkills: firstNonEmpty(skillNames(rawJob.skills?.filter?.((s) => s.requirementType === 'PREFERRED')), skillNames(job.skills?.filter?.((s) => s.requirementType === 'PREFERRED')), [...new Set([...(job.matchedPreferredSkills || []), ...(job.missingPreferredSkills || [])])], []),
    tags: tagCodes(rawJob.experienceTags).length ? tagCodes(rawJob.experienceTags) : tagCodes(job.experienceTags).length ? tagCodes(job.experienceTags) : tagCodes(job.matchedExperienceTags).concat(tagCodes(job.missingExperienceTags)).slice(0, 3),
    deadline: dday(rawJob.deadlineAt || job.deadlineAt) || job.deadline || '마감 정보 없음',
    views: job.views || 0,
    companyIntro: rawJob.companyIntro || job.companyIntro || '',
    desc: rawJob.description || job.description || '공고 원문이 제공되지 않았습니다.',
    descriptionSections: rawJob.descriptionSections || job.descriptionSections || [],
  };
}

const toTrend = (trend, index = 0, ownedSkills = new Set()) => {
  const required = Number(trend.requiredCount || 0);
  const preferred = Number(trend.preferredCount || 0);
  const total = Number(trend.jobCount || trend.totalJobs || 0);
  const trendScore = Number(trend.trendScore ?? trend.score ?? 0);
  const rate = Math.min(100, Math.round(Number(trend.rate ?? (total ? (required / total) * 100 : trendScore))));
  const name = trend.skillName || trend.name;
  return {
    id: trend.skillId || trend.id || index,
    name,
    rate,
    growth: Number(trend.growth || 0),
    owned: ownedSkills.has(name) || Boolean(trend.owned),
    jobCount: total,
    requiredCount: required,
    preferredCount: preferred,
    trendScore,
    category: trend.skillCategory || trend.category,
    insight: trend.insight || (name ? `${name} 포함 공고 ${total.toLocaleString()}건이 집계되었습니다. 필수 요구 ${required.toLocaleString()}건, 우대 언급 ${preferred.toLocaleString()}건 기준입니다.` : '이번 달 공고 집계 결과입니다.'),
  };
};

const toApplication = (app) => ({
  id: app.id || app.applicationId,
  jobId: app.jobId || app.job?.id,
  company: app.companyName || app.job?.companyName || app.company || '회사명',
  title: app.jobTitle || app.job?.title || app.title || '공고명',
  status: app.status || 'APPLIED',
  appliedAt: app.appliedAt,
  updatedAt: app.updatedAt,
});

const userFacingEvidence = (value, fallback) => {
  const text = String(value || '').trim();
  if (!text) return fallback;
  if (/smoke|fixture|mock|test data|sample data/i.test(text)) return fallback;
  return text;
};

const toSkill = (skill) => ({
  name: skill.skillName || skill.name,
  conf: Math.round(Number(skill.confidence || skill.conf || 70)),
  level: skill.source === 'LLM' ? '분석 신뢰도' : '근거 강도',
  commits: skill.commits || 0,
  files: skill.files || 0,
  reason: userFacingEvidence(skill.evidence || skill.reason, '프로젝트 분석 결과에서 검출되었습니다.'),
});

const toExpTag = (tag) => ({
  code: tag.tagCode || tag.code,
  label: tag.tagName || tag.label || tagLabel(tag.tagCode || tag.code),
  sentence: userFacingEvidence(tag.evidence || tag.sentence, tag.description || '프로젝트 분석 결과에서 관련 경험 태그로 추출되었습니다.'),
});

const toUserJobCard = (item, index = 0) => {
  const rawJob = item.job || {};
  return toPrototypeJob({
    ...rawJob,
    id: item.jobId || rawJob.id || item.id,
    jobId: item.jobId || rawJob.id,
    source: item.source || rawJob.source,
    status: item.status || rawJob.status,
    originalUrl: item.originalUrl || rawJob.originalUrl,
    title: item.jobTitle || rawJob.title || item.title,
    companyName: item.companyName || rawJob.companyName,
    deadlineAt: item.deadlineAt || rawJob.deadlineAt,
    careerLevel: item.careerLevel || rawJob.careerLevel,
    role: item.role || rawJob.role,
    score: item.score ?? rawJob.score ?? 0,
  }, index);
};

const buildAnalyzedProject = (baseProject = {}, userProjectId, skills, tags, matches) => {
  const topSkills = skills.map((skill) => skill.name).filter(Boolean);
  const topTags = tags.map((tag) => tag.label).filter(Boolean);
  const projectName = userProjectId ? `분석 프로젝트 ${userProjectId}` : baseProject.name || '내 프로젝트';
  const baseStats = baseProject.stats || {};
  const baseSkillTotal = baseProject.skillsTotal || 0;
  const baseTagTotal = baseProject.tagsTotal || 0;
  const baseMatchedJobs = baseProject.matchedJobs || 0;
  const basePreviewSkills = baseProject.previewSkills || [];
  const baseDetailTags = baseProject.detailTags || [];
  return {
    ...baseProject,
    name: projectName,
    repo: userProjectId ? `프로젝트 ID ${userProjectId}` : '연결된 프로젝트',
    connected: true,
    analyzedAt: '최근 분석',
    skillsTotal: skills.length || baseSkillTotal,
    tagsTotal: tags.length || baseTagTotal,
    matchedJobs: matches.length || baseMatchedJobs,
    previewSkills: topSkills.slice(0, 5).length ? topSkills.slice(0, 5) : basePreviewSkills,
    repoVisual: `${projectName} · ${topSkills.slice(0, 3).join(' / ') || '분석 완료'}`,
    summary: topSkills.length
      ? `${topSkills.slice(0, 4).join(', ')} 기반으로 분석된 사용자 프로젝트`
      : baseProject.summary || '프로젝트 분석 결과를 불러왔습니다.',
    overview: [
      '연결된 프로젝트의 기술 스택과 경험 태그를 바탕으로 구성한 분석 카드입니다.',
      topSkills.length ? `추출 스킬은 ${topSkills.slice(0, 6).join(', ')} 중심입니다.` : '',
      topTags.length ? `경험 태그는 ${topTags.slice(0, 4).join(', ')} 신호가 확인됩니다.` : '',
    ].filter(Boolean).join(' '),
    domain: topTags.slice(0, 3).length ? topTags.slice(0, 3) : baseProject.domain || [],
    architecture: topTags.length ? '코드 기반 스킬 분석' : baseProject.architecture || '스킬 분석',
    stackGroups: [
      { label: '감지된 스킬', items: topSkills.slice(0, 8).map((name, index) => ({ n: name, pct: Math.max(52, 96 - index * 6) })) },
      ...(baseProject.stackGroups || []).slice(1),
    ],
    stats: {
      ...baseStats,
      files: Math.max(baseStats.files || 0, skills.length),
      tests: Math.max(baseStats.tests || 0, Math.round((tags.length / Math.max(1, skills.length)) * 100)),
    },
    detailTags: tags.length ? tags.map((tag) => ({
      code: tag.code,
      label: tag.label,
      sentence: tag.sentence,
    })) : baseDetailTags,
  };
};

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
  return jobs.map((job) => {
    const detail = byId.get(job.id);
    return detail ? { ...detail, score: job.score ?? detail.score, source: job.source ?? detail.source, status: job.status ?? detail.status } : job;
  });
}

export const dedupeJobs = (jobs = []) => {
  const seen = new Set();
  return jobs.filter((job) => {
    const rawJob = job.job || job;
    const key = [
      String(rawJob.companyName || job.companyName || '').trim().toLowerCase(),
      String(rawJob.title || job.title || '').trim().toLowerCase(),
      String(rawJob.deadlineAt || job.deadlineAt || ''),
    ].join('|');
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
};

function attachLookup(next) {
  const everyJob = [
    ...(next.listings || []),
    ...(next.matches || []),
    ...(next.recommendations || []),
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

const resetUserFacingData = (state) => {
  Object.assign(state, {
    listings: [],
    matches: [],
    recommendations: [],
    popular: [],
    closing: [],
    skills: [],
    expTags: [],
    gapSkills: [],
    projectList: [],
    applications: [],
    pipeline: [],
    saved: 0,
    viewed: 0,
    ignored: 0,
    trends: [],
    userJobs: {
      saved: [],
      viewed: [],
      ignored: [],
    },
    market: {
      ...(state.market || {}),
      totalCount: 0,
      openJobCount: 0,
    },
  });
  return state;
};

export const createEmptyJobFlowState = (baseJF) => resetUserFacingData(clone(baseJF));

export function mergePrototypeJobIntoState(state, job) {
  if (!job || !(job.jobId || job.id || job.companyKo)) return state;

  const jobId = String(job.jobId || job.id || '');
  const company = job.companyKo;
  const sameJob = (candidate) => {
    const candidateId = String(candidate?.jobId || candidate?.id || '');
    return (jobId && candidateId === jobId) || (company && candidate?.companyKo === company);
  };
  const upsert = (list = [], appendWhenMissing = false) => {
    let found = false;
    const merged = list.map((candidate) => {
      if (!sameJob(candidate)) return candidate;
      found = true;
      return { ...candidate, ...job };
    });
    return found || !appendWhenMissing ? merged : [job, ...merged];
  };

  return attachLookup({
    ...state,
    listings: upsert(state.listings || [], true),
    matches: upsert(state.matches || []),
    recommendations: upsert(state.recommendations || []),
    popular: upsert(state.popular || []),
    closing: upsert(state.closing || []),
    userJobs: {
      ...(state.userJobs || {}),
      saved: upsert(state.userJobs?.saved || []),
      viewed: upsert(state.userJobs?.viewed || []),
      ignored: upsert(state.userJobs?.ignored || []),
    },
  });
}

export async function loadJobFlowData(baseJF) {
  const next = createEmptyJobFlowState(baseJF);
  let userProjectId = projectStore.getProjectId();
  next.tagLabel = {
    ...(next.tagLabel || {}),
    CLOUD_INFRA: '클라우드/인프라',
    MONITORING: '모니터링',
    PERFORMANCE: '성능 최적화',
    RELIABILITY: '안정성',
  };
  const publicResults = await Promise.all([
    settle('jobs', () => api.searchJobs('백엔드', 40)),
    settle('trends', () => api.skillTrends({ limit: 8 })),
    settle('market', () => api.market({ role: 'BACKEND', limit: 5 })),
  ]);
  next.__apiStatus = Object.fromEntries(publicResults.map((result) => [result.key, result.ok ? 'ok' : 'unavailable']));

  const jobs = publicResults.find((x) => x.key === 'jobs');
  const jobRows = asList(jobs?.data).filter(isUserFacingJob);
  if (jobs?.ok && jobRows.length) {
    const detailedJobs = await hydrateJobDetails(jobRows);
    const mapped = dedupeJobs(detailedJobs.filter(isUserFacingJob)).map(toPrototypeJob);
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
    next.market.avgOpenDays = marketRows[0]?.avgOpenDays || next.market.avgOpenDays || 0;
    next.market.openJobCount = open || undefined;
  }

  const meResult = await settle('me', () => api.me());
  if (!meResult.ok) {
    if (!authStore.hasMemoryToken()) {
      authStore.clear();
    }
    return attachLookup(next);
  }

  authStore.setToken();
  next.__authenticated = true;

  if (!userProjectId && meResult.data?.userProjectId) {
    userProjectId = String(meResult.data.userProjectId);
    projectStore.setProjectId(userProjectId);
  }

  const hasUserProjectId = Boolean(userProjectId);

  const authLoaders = [
    settle('applications', () => api.applications()),
    settle('saved', () => api.savedJobs()),
    settle('viewed', () => api.viewedJobs()),
    settle('ignored', () => api.ignoredJobs()),
  ];

  if (hasUserProjectId) {
    authLoaders.push(
      settle('recommendations', () => api.recommendations(userProjectId, { targetRoles: ['BACKEND', 'FULLSTACK'], limit: 12 })),
      settle('matches', () => api.projectJobMatches(userProjectId, { targetRoles: ['BACKEND', 'FULLSTACK'], targetCareerLevel: 'MID', limit: 12 })),
      settle('skills', () => api.projectSkills(userProjectId)),
      settle('tags', () => api.projectExperienceTags(userProjectId)),
      settle('gap', () => api.gapAnalysis(userProjectId, { targetRoles: ['BACKEND'], limit: 20 })),
    );
  }

  const authResults = [meResult, ...(await Promise.all(authLoaders))];
  next.__apiStatus = {
    ...(next.__apiStatus || {}),
    ...Object.fromEntries(authResults.map((result) => [result.key, result.ok ? 'ok' : 'unavailable'])),
  };

  if (!hasUserProjectId) {
    next.__apiStatus = {
      ...(next.__apiStatus || {}),
      recommendations: 'missing-project',
      matches: 'missing-project',
      skills: 'missing-project',
      tags: 'missing-project',
      gap: 'missing-project',
    };
  }

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

  next.__userProjectId = userProjectId || null;

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
  const displayableMatches = dedupeJobs(matchRows.filter(isUserFacingJob));
  const displayableRecommendations = dedupeJobs(recommendationRows.filter(isUserFacingJob));
  if (recommendations?.ok) {
    next.recommendations = displayableRecommendations.map(toPrototypeJob).slice(0, 12);
  }
  const matchSource = matches?.ok && displayableMatches.length ? displayableMatches : recommendations?.ok && displayableRecommendations.length ? displayableRecommendations : null;
  if (matchSource) {
    next.matches = matchSource.map(toPrototypeJob).slice(0, 8);
  }

  if ((skillsResult?.ok && skillRows.length) || (tagsResult?.ok && tagRows.length) || matchSource) {
    const analyzed = buildAnalyzedProject(
      next.projectList[0],
      userProjectId,
      next.skills,
      next.expTags,
      next.matches || [],
    );
    next.projectList = [analyzed, ...next.projectList.slice(1)];
    next.projects = {
      ...next.projects,
      analyzed: Math.max(next.projects?.analyzed || 0, 1),
      skillsTotal: analyzed.skillsTotal,
      tagsTotal: analyzed.tagsTotal,
    };
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
    const gapMatches = dedupeJobs(asList(gap.data.jobMatches).filter(isUserFacingJob));
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
