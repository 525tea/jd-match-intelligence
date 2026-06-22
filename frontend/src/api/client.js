const trimTrailingSlash = (value) => String(value || '').replace(/\/+$/, '');

export const API_BASE_URL = trimTrailingSlash(import.meta.env.VITE_API_BASE_URL || '/api');
const API_BASE_URL_IS_ABSOLUTE = /^https?:\/\//i.test(API_BASE_URL);
const AUTH_HINT_KEY = 'jobflow.authenticated';
const AUTH_TOKEN_KEY = 'jobflow.accessToken';
const PROJECT_ID_KEY = 'jobflow.userProjectId';
const PROJECT_META_KEY = 'jobflow.userProjectMeta';
let memoryAccessToken = '';

const getStorage = (type) => {
  try {
    return typeof window !== 'undefined' ? window[type] : null;
  } catch (error) {
    return null;
  }
};

const readStorage = (type, key) => {
  try {
    return getStorage(type)?.getItem(key) || '';
  } catch (error) {
    return '';
  }
};

const writeStorage = (type, key, value) => {
  try {
    getStorage(type)?.setItem(key, value);
  } catch (error) {
    // Memory token remains authoritative when browser storage is unavailable.
  }
};

const removeStorage = (type, key) => {
  try {
    getStorage(type)?.removeItem(key);
  } catch (error) {
    // Ignore storage cleanup failures in restricted browser contexts.
  }
};

export const authStore = {
  getToken: () => memoryAccessToken || readStorage('sessionStorage', AUTH_TOKEN_KEY) || (readStorage('localStorage', AUTH_HINT_KEY) === 'true' ? 'cookie' : ''),
  getAccessToken: () => memoryAccessToken || readStorage('sessionStorage', AUTH_TOKEN_KEY) || '',
  hasMemoryToken: () => Boolean(memoryAccessToken || readStorage('sessionStorage', AUTH_TOKEN_KEY)),
  clearAccessToken: () => {
    memoryAccessToken = '';
    removeStorage('sessionStorage', AUTH_TOKEN_KEY);
  },
  markAuthenticated: () => {
    writeStorage('localStorage', AUTH_HINT_KEY, 'true');
  },
  setToken: (token) => {
    if (token) {
      memoryAccessToken = token;
      writeStorage('sessionStorage', AUTH_TOKEN_KEY, token);
    }
    writeStorage('localStorage', AUTH_HINT_KEY, 'true');
  },
  clear: () => {
    memoryAccessToken = '';
    removeStorage('sessionStorage', AUTH_TOKEN_KEY);
    removeStorage('localStorage', AUTH_HINT_KEY);
  },
};

export const projectStore = {
  getProjectId: () => readStorage('localStorage', PROJECT_ID_KEY) || import.meta.env.VITE_DEFAULT_USER_PROJECT_ID || '',
  setProjectId: (projectId) => projectId ? writeStorage('localStorage', PROJECT_ID_KEY, String(projectId)) : removeStorage('localStorage', PROJECT_ID_KEY),
  getProjectMeta: () => {
    try {
      return JSON.parse(readStorage('localStorage', PROJECT_META_KEY) || '{}');
    } catch (error) {
      return {};
    }
  },
  setProjectMeta: (meta = {}) => {
    const clean = Object.fromEntries(
      Object.entries(meta).filter(([, value]) => value !== undefined && value !== null && value !== '')
    );
    if (Object.keys(clean).length) {
      writeStorage('localStorage', PROJECT_META_KEY, JSON.stringify(clean));
    } else {
      removeStorage('localStorage', PROJECT_META_KEY);
    }
  },
  clear: () => {
    removeStorage('localStorage', PROJECT_ID_KEY);
    removeStorage('localStorage', PROJECT_META_KEY);
  },
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

async function requestUrl(url, options = {}) {
  const response = await fetch(url, { ...options, credentials: 'include' });
  const text = await response.text();
  const payload = parseResponsePayload(text);
  if (!response.ok) {
    const message = payload?.error?.message || payload?.message || payload?.error || `요청 실패 (${response.status})`;
    throw new ApiError(message, response.status, payload);
  }
  if (payload && Object.prototype.hasOwnProperty.call(payload, 'data')) return payload.data;
  return payload;
}

async function request(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  const accessToken = memoryAccessToken || readStorage('sessionStorage', AUTH_TOKEN_KEY);
  if (accessToken && !headers.has('Authorization')) headers.set('Authorization', `Bearer ${accessToken}`);
  const url = buildUrl(path, options.params);

  return requestUrl(url, { ...options, headers });
}

export const api = {
  login: (body) => request('/auth/login', { method: 'POST', body: JSON.stringify(body) }),
  demoLogin: () => request('/auth/demo-login', { method: 'POST' }),
  signup: (body) => request('/auth/signup', { method: 'POST', body: JSON.stringify(body) }),
  oauthToken: (body) => request('/auth/oauth2/token', { method: 'POST', body: JSON.stringify(body) }),
  logout: () => request('/auth/logout', { method: 'POST' }),
  me: (accessToken = '') => request('/auth/me', {
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
  }),

  jobs: (params = {}) => request('/jobs', { params: { page: 0, size: 50, ...params } }),
  searchJobs: (keyword, limit = 30) => request('/jobs/search', { params: { keyword, limit } }),
  job: (jobId) => request(`/jobs/${jobId}`),
  canonicalGroup: (jobId) => request(`/jobs/${jobId}/canonical-group`),

  saveJob: (jobId) => request(`/user/jobs/${jobId}/save`, { method: 'POST' }),
  viewJob: (jobId) => request(`/user/jobs/${jobId}/view`, { method: 'POST' }),
  ignoreJob: (jobId) => request(`/user/jobs/${jobId}/ignore`, { method: 'POST' }),
  savedJobs: () => request('/user/jobs/saved'),
  viewedJobs: () => request('/user/jobs/viewed'),
  ignoredJobs: () => request('/user/jobs/ignored'),

  applications: () => request('/applications'),
  createApplication: (jobId) => request('/applications', { method: 'POST', body: JSON.stringify({ jobId }) }),
  updateApplicationStatus: (applicationId, status) => request(`/applications/${applicationId}/status`, { method: 'PATCH', body: JSON.stringify({ status }) }),

  userProjects: () => request('/projects'),
  projectSkills: (projectId) => request(`/projects/${projectId}/skills`),
  projectSummary: (projectId) => request(`/projects/${projectId}`),
  projectExperienceTags: (projectId) => request(`/projects/${projectId}/experience-tags`),
  projectJobMatches: (projectId, params = {}) => request(`/projects/${projectId}/job-matches`, { params }),
  githubRepositories: () => request('/github/repositories'),
  importGithubRepository: (body) => request('/projects/github-import', { method: 'POST', body: JSON.stringify(body) }),
  recommendations: (projectId, params = {}) => request('/recommendations/jobs', { params: { userProjectId: projectId, ...params } }),
  gapAnalysis: (projectId, params = {}) => request(`/gap-analysis/projects/${projectId}`, { params }),

  skillTrends: (params = {}) => request('/trends/skills', { params }),
  market: (params = {}) => request('/trends/market', { params }),
  cooccurrences: (skillId, params = {}) => request(`/trends/skills/${skillId}/cooccurrences`, { params }),
  skillExperienceTags: (skillId, params = {}) => request(`/trends/skills/${skillId}/experience-tags`, { params }),
};
