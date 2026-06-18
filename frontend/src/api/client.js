import React from 'react';

const trimTrailingSlash = (value) => String(value || '').replace(/\/+$/, '');

export const API_BASE_URL = trimTrailingSlash(import.meta.env.VITE_API_BASE_URL || '/api');
const TOKEN_KEY = 'jobflow.accessToken';
const PROJECT_ID_KEY = 'jobflow.userProjectId';

export const authStore = {
  getToken: () => localStorage.getItem(TOKEN_KEY),
  setToken: (token) => token ? localStorage.setItem(TOKEN_KEY, token) : localStorage.removeItem(TOKEN_KEY),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

export const projectStore = {
  getProjectId: () => localStorage.getItem(PROJECT_ID_KEY) || import.meta.env.VITE_DEFAULT_USER_PROJECT_ID || '1',
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
  return url.pathname + url.search;
};

export const unwrapList = (value) => {
  if (Array.isArray(value)) return value;
  if (Array.isArray(value?.content)) return value.content;
  if (Array.isArray(value?.items)) return value.items;
  if (Array.isArray(value?.data)) return value.data;
  return [];
};

async function request(path, options = {}) {
  const token = authStore.getToken();
  const headers = new Headers(options.headers || {});
  if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  if (token) headers.set('Authorization', `Bearer ${token}`);

  const response = await fetch(buildUrl(path, options.params), { ...options, headers });
  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;
  if (!response.ok) {
    const message = payload?.error?.message || payload?.message || payload?.error || `API 요청 실패 (${response.status})`;
    throw new ApiError(message, response.status, payload);
  }
  if (payload && Object.prototype.hasOwnProperty.call(payload, 'data')) return payload.data;
  return payload;
}

export const api = {
  login: (body) => request('/auth/login', { method: 'POST', body: JSON.stringify(body) }),
  signup: (body) => request('/auth/signup', { method: 'POST', body: JSON.stringify(body) }),
  oauthToken: (body) => request('/auth/oauth2/token', { method: 'POST', body: JSON.stringify(body) }),
  me: () => request('/auth/me'),

  jobs: () => request('/jobs'),
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

export function useApiResource(loader, fallback, deps = []) {
  const [state, setState] = React.useState({ data: fallback, loading: true, error: null, fromMock: false });
  React.useEffect(() => {
    let alive = true;
    setState((prev) => ({ ...prev, loading: true, error: null }));
    loader()
      .then((data) => alive && setState({ data: data ?? fallback, loading: false, error: null, fromMock: false }))
      .catch((error) => alive && setState({ data: fallback, loading: false, error, fromMock: true }));
    return () => { alive = false; };
  }, deps);
  return state;
}
