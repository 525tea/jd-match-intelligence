const trimTrailingSlash = (value) => String(value || '').replace(/\/+$/, '');

export const API_BASE_URL = trimTrailingSlash(import.meta.env.VITE_API_BASE_URL || '/api');
const API_BASE_URL_IS_ABSOLUTE = /^https?:\/\//i.test(API_BASE_URL);
const AUTH_HINT_KEY = 'jobflow.authenticated';
const PROJECT_ID_KEY = 'jobflow.userProjectId';
let memoryAccessToken = '';

export const authStore = {
  getToken: () => memoryAccessToken || (localStorage.getItem(AUTH_HINT_KEY) === 'true' ? 'cookie' : ''),
  hasMemoryToken: () => Boolean(memoryAccessToken),
  setToken: (token) => {
    if (token) {
      memoryAccessToken = token;
    }
    localStorage.setItem(AUTH_HINT_KEY, 'true');
  },
  clear: () => {
    memoryAccessToken = '';
    localStorage.removeItem(AUTH_HINT_KEY);
  },
};

export const projectStore = {
  getProjectId: () => localStorage.getItem(PROJECT_ID_KEY) || import.meta.env.VITE_DEFAULT_USER_PROJECT_ID || '',
  setProjectId: (projectId) => projectId ? localStorage.setItem(PROJECT_ID_KEY, String(projectId)) : localStorage.removeItem(PROJECT_ID_KEY),
  clear: () => localStorage.removeItem(PROJECT_ID_KEY),
};

export class ApiError extends Error {
  constructor(message, status, payload) {
    super(message);
    this.status = status;
    this.payload = payload;
  }
}

const buildUrl = (path, params) => {
  const url = new URL(`${API_BASE_URL}${path}`, window.location.origin);
  Object.entries(params || {}).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '' || value === 'ALL') return;
    if (Array.isArray(value)) value.forEach((v) => url.searchParams.append(key, v));
    else url.searchParams.set(key, value);
  });
  if (API_BASE_URL_IS_ABSOLUTE) return url.toString();
  return url.pathname + url.search;
};

const parseResponsePayload = (text) => {
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch (error) {
    return { message: text };
  }
};

export const unwrapList = (value) => {
  if (Array.isArray(value)) return value;
  if (Array.isArray(value?.content)) return value.content;
  if (Array.isArray(value?.items)) return value.items;
  if (Array.isArray(value?.data)) return value.data;
  return [];
};

async function request(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  if (memoryAccessToken && !headers.has('Authorization')) headers.set('Authorization', `Bearer ${memoryAccessToken}`);

  const response = await fetch(buildUrl(path, options.params), { ...options, headers, credentials: 'include' });
  const text = await response.text();
  const payload = parseResponsePayload(text);
  if (!response.ok) {
    const message = payload?.error?.message || payload?.message || payload?.error || `요청 실패 (${response.status})`;
    throw new ApiError(message, response.status, payload);
  }
  if (payload && Object.prototype.hasOwnProperty.call(payload, 'data')) return payload.data;
  return payload;
}

export const api = {
  login: (body) => request('/auth/login', { method: 'POST', body: JSON.stringify(body) }),
  demoLogin: () => request('/auth/demo-login', { method: 'POST' }),
  signup: (body) => request('/auth/signup', { method: 'POST', body: JSON.stringify(body) }),
  oauthToken: (body) => request('/auth/oauth2/token', { method: 'POST', body: JSON.stringify(body) }),
  logout: () => request('/auth/logout', { method: 'POST' }),
  me: () => request('/auth/me'),

  jobs: (params = {}) => request('/jobs', { params: { limit: 50, ...params } }),
  searchJobs: (keyword, limit = 30) => request('/jobs/search', { params: { keyword, limit } }),
  job: (jobId) => request(`/jobs/${jobId}`),

  saveJob: (jobId) => request(`/user/jobs/${jobId}/save`, { method: 'POST' }),
  viewJob: (jobId) => request(`/user/jobs/${jobId}/view`, { method: 'POST' }),
  ignoreJob: (jobId) => request(`/user/jobs/${jobId}/ignore`, { method: 'POST' }),
  savedJobs: () => request('/user/jobs/saved'),
  viewedJobs: () => request('/user/jobs/viewed'),
  ignoredJobs: () => request('/user/jobs/ignored'),

  applications: () => request('/applications'),
  createApplication: (jobId) => request('/applications', { method: 'POST', body: JSON.stringify({ jobId }) }),
  updateApplicationStatus: (applicationId, status) => request(`/applications/${applicationId}/status`, { method: 'PATCH', body: JSON.stringify({ status }) }),

  projectSkills: (projectId) => request(`/projects/${projectId}/skills`),
  projectExperienceTags: (projectId) => request(`/projects/${projectId}/experience-tags`),
  projectJobMatches: (projectId, params = {}) => request(`/projects/${projectId}/job-matches`, { params }),
  recommendations: (projectId, params = {}) => request('/recommendations/jobs', { params: { userProjectId: projectId, ...params } }),
  gapAnalysis: (projectId, params = {}) => request(`/gap-analysis/projects/${projectId}`, { params }),

  skillTrends: (params = {}) => request('/trends/skills', { params }),
  market: (params = {}) => request('/trends/market', { params }),
  cooccurrences: (skillId, params = {}) => request(`/trends/skills/${skillId}/cooccurrences`, { params }),
  skillExperienceTags: (skillId, params = {}) => request(`/trends/skills/${skillId}/experience-tags`, { params }),
};
