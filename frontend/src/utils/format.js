export const roleLabel = (role) => ({
  BACKEND: '백엔드', FRONTEND: '프론트엔드', FULLSTACK: '풀스택', DEVOPS: 'DevOps', DATA_ENGINEER: '데이터', AI_ENGINEER: 'AI', ML_ENGINEER: 'ML', SECURITY: '보안', QA: 'QA', PM: 'PM', MLOPS: 'MLOps', LLM: 'LLM', ETC: '기타',
}[role] || role || '직무');

export const careerLabel = (career, min, max) => {
  if (min !== undefined && min !== null && max !== undefined && max !== null) {
    if (Number(min) === 0 && Number(max) === 0) return '신입';
    if (Number(min) === 0) return `~${max}년차`;
    return `${min}~${max}년차`;
  }
  return ({ ANY: '전체', NEWCOMER: '신입', JUNIOR: '~3년차', MID: '3~7년차', SENIOR: '시니어', LEAD: '리드' }[career] || career || '경력 무관');
};

export const statusLabel = (status) => ({ APPLIED: '지원', DOCUMENT_PASSED: '서류', CODING_TEST: '코테', INTERVIEW: '면접', OFFER: '오퍼', REJECTED: '불합격', WITHDRAWN: '취소' }[status] || status);

export const dday = (deadlineAt) => {
  if (!deadlineAt) return '마감 정보 없음';
  const today = new Date();
  const due = new Date(deadlineAt);
  const diff = Math.ceil((due.setHours(0, 0, 0, 0) - today.setHours(0, 0, 0, 0)) / 86400000);
  if (Number.isNaN(diff)) return '마감 정보 없음';
  if (diff < 0) return '마감';
  if (diff === 0) return 'D-DAY';
  return `D-${diff}`;
};

export const scoreOf = (job) => Math.round(Number(job?.score?.totalScore ?? job?.score?.skillMatchRate ?? job?.matchScore ?? job?.totalScore ?? 0));
export const jobIdOf = (job) => job?.jobId ?? job?.id;
export const companyLogo = (name = '') => name.split(/\s+/).map((x) => x[0]).join('').slice(0, 2).toUpperCase() || 'JF';
export const compactNumber = (n) => new Intl.NumberFormat('ko-KR').format(Number(n || 0));
export const toPercent = (n) => `${Math.round(Number(n || 0))}%`;
