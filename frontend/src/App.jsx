import React from 'react';
import { JF as baseJF } from './prototype/jobflow-data.js';
import { JobFlowHome } from './prototype/HomePrototype.jsx';
import { JobDetail } from './prototype/JobDetailPrototype.jsx';
import { Onboarding } from './prototype/OnboardingPrototype.jsx';
import { JobFlowScreens } from './prototype/ScreensPrototype.jsx';
import { createEmptyJobFlowState, dedupeJobs, fetchUserFacingJobs, hydrateUserFacingJobs, isUserFacingJob, loadJobFlowData, mergePrototypeJobIntoState, toPrototypeJob } from './prototype/adapters.js';
import { setJobflowState } from './prototype/jobflowState.js';
import { API_BASE_URL, api, authStore, projectStore } from './api/client.js';
import { clearJobflowActions, setJobflowActions } from './api/jobflowActions.js';
import { ConnectedLogin, ConnectedOAuth } from './pages/ConnectedAuth.jsx';

const initialTweaks = {
  state: '로그인',
  accent: '라임 · 차콜 · 코랄',
  green: '#b9ec2a',
  tagTone: '보라',
  ver: 'v3',
};

function parseRoute() {
  const path = window.location.pathname;
  if (path.endsWith('/oauth2/success')) return { name: 'oauth', params: {} };
  if (path.endsWith('/oauth2/failure')) return { name: 'oauth-failure', params: {} };
  const raw = window.location.hash.replace(/^#\/?/, '') || 'home';
  const [name, rest] = raw.split('/');
  const params = {};
  if (name === 'detail' && rest) {
    const decoded = decodeURIComponent(rest);
    if (/^\d+$/.test(decoded)) params.jobId = Number(decoded);
    else params.company = decoded;
  }
  return { name, params };
}

export default function App() {
  const [jf, setJf] = React.useState(() => createEmptyJobFlowState(baseJF));
  const [dataLoading, setDataLoading] = React.useState(true);
  const [route, setRoute] = React.useState(parseRoute);
  const [authenticated, setAuthenticated] = React.useState(Boolean(authStore.getToken()));
  const [authChecked, setAuthChecked] = React.useState(false);
  const fetchedJobDetailIds = React.useRef(new Set());
  const t = React.useMemo(() => ({
    ...initialTweaks,
    state: authenticated ? '로그인' : '비로그인',
  }), [authenticated]);
  setJobflowState(jf);

  const refreshData = React.useCallback(async () => {
    setDataLoading(true);
    try {
      const data = await loadJobFlowData(baseJF);
      setAuthenticated(Boolean(data.__authenticated || authStore.getToken()));
      setJf(data);
      setAuthChecked(true);
      return data;
    } finally {
      setDataLoading(false);
    }
  }, []);

  const completeAuthentication = React.useCallback(async (context = {}) => {
    const data = await refreshData();
    if (context.user?.id) {
      setAuthenticated(true);
      setJf((prev) => ({
        ...prev,
        __authenticated: true,
        __userProjectId: context.userProjectId || prev.__userProjectId || projectStore.getProjectId() || null,
        user: {
          ...(prev.user || {}),
          name: context.user.name || prev.user?.name || '사용자',
          email: context.user.email || prev.user?.email || '',
          role: context.user.role || prev.user?.role || 'USER',
          github: Boolean(context.github),
        },
      }));
    } else {
      setAuthenticated(Boolean(data.__authenticated || authStore.getToken()));
    }
    return data;
  }, [refreshData]);

  React.useEffect(() => {
    let alive = true;
    setDataLoading(true);
    loadJobFlowData(baseJF).then((data) => {
      if (!alive) return;
      setAuthenticated(Boolean(data.__authenticated || authStore.getToken()));
      setJf(data);
    }).finally(() => {
      if (alive) {
        setAuthChecked(true);
        setDataLoading(false);
      }
    });
    return () => { alive = false; };
  }, []);


  const resolveJobId = React.useCallback((company) => {
    if (!company) return null;
    return jf.__jobIdByCompany?.[company]
      || jf.matches?.find((job) => job.companyKo === company)?.jobId
      || jf.listings?.find((job) => job.companyKo === company)?.jobId
      || null;
  }, [jf]);

  const resolveCompanyFromJobId = React.useCallback((jobId) => {
    if (!jobId) return null;
    return jf.__jobCompanyById?.[String(jobId)]
      || jf.matches?.find((job) => String(job.jobId || job.id) === String(jobId))?.companyKo
      || jf.listings?.find((job) => String(job.jobId || job.id) === String(jobId))?.companyKo
      || null;
  }, [jf]);

  React.useEffect(() => {
    setJobflowActions({
      refreshData,
      resolveJobId,
      async saveJobById(jobId) {
        if (!jobId) throw new Error('공고 ID가 없습니다.');
        const result = await api.saveJob(jobId);
        await refreshData();
        return result;
      },
      async saveJobByCompany(company) {
        const jobId = resolveJobId(company);
        if (!jobId) throw new Error('공고 ID를 찾을 수 없습니다.');
        return this.saveJobById(jobId);
      },
      async viewJobById(jobId) {
        if (!jobId) return null;
        return api.viewJob(jobId).catch(() => null);
      },
      async viewJobByCompany(company) {
        const jobId = resolveJobId(company);
        if (!jobId) return null;
        return this.viewJobById(jobId);
      },
      async createApplicationByJobId(jobId) {
        if (!jobId) throw new Error('공고 ID가 없습니다.');
        const result = await api.createApplication(jobId);
        await refreshData();
        return result;
      },
      async createApplicationByCompany(company) {
        const jobId = resolveJobId(company);
        if (!jobId) throw new Error('공고 ID를 찾을 수 없습니다.');
        return this.createApplicationByJobId(jobId);
      },
      async ignoreJobById(jobId) {
        if (!jobId) throw new Error('공고 ID가 없습니다.');
        const result = await api.ignoreJob(jobId);
        await refreshData();
        return result;
      },
      async updateApplicationStatus(applicationId, status) {
        if (!applicationId) throw new Error('지원 기록 ID가 없습니다.');
        const result = await api.updateApplicationStatus(applicationId, status);
        await refreshData();
        return result;
      },
      async searchJobs(keyword) {
        const normalizedKeyword = String(keyword || '').trim();
        const rows = normalizedKeyword
          ? await hydrateUserFacingJobs(await api.searchJobs(normalizedKeyword, 100), 100)
          : await fetchUserFacingJobs({}, { hydrate: true, hydrateLimit: 120 });
        const mapped = dedupeJobs(rows.filter(isUserFacingJob)).map(toPrototypeJob);
        const everyJob = mapped.filter((job) => job.companyKo && (job.jobId || job.id));
        setJf((prev) => ({
          ...prev,
          listings: mapped,
          popular: mapped.slice(0, 4),
          closing: mapped.slice().sort((a, b) => Number(String(a.deadline).replace(/\D/g, '') || 999) - Number(String(b.deadline).replace(/\D/g, '') || 999)).slice(0, 3),
          __jobIdByCompany: { ...(prev.__jobIdByCompany || {}), ...Object.fromEntries(everyJob.map((job) => [job.companyKo, job.jobId || job.id])) },
          __jobCompanyById: { ...(prev.__jobCompanyById || {}), ...Object.fromEntries(everyJob.map((job) => [String(job.jobId || job.id), job.companyKo])) },
          __jobByCompany: { ...(prev.__jobByCompany || {}), ...Object.fromEntries(mapped.filter((job) => job.companyKo).map((job) => [job.companyKo, job])) },
        }));
        return mapped;
      },
      async listJobs(filters = {}) {
        const rows = await fetchUserFacingJobs(filters, { hydrate: true, hydrateLimit: 120 });
        const mapped = dedupeJobs(rows.filter(isUserFacingJob)).map(toPrototypeJob);
        const everyJob = mapped.filter((job) => job.companyKo && (job.jobId || job.id));
        setJf((prev) => ({
          ...prev,
          listings: mapped,
          popular: mapped.slice(0, 4),
          closing: mapped.slice().sort((a, b) => Number(String(a.deadline).replace(/\D/g, '') || 999) - Number(String(b.deadline).replace(/\D/g, '') || 999)).slice(0, 3),
          __jobIdByCompany: { ...(prev.__jobIdByCompany || {}), ...Object.fromEntries(everyJob.map((job) => [job.companyKo, job.jobId || job.id])) },
          __jobCompanyById: { ...(prev.__jobCompanyById || {}), ...Object.fromEntries(everyJob.map((job) => [String(job.jobId || job.id), job.companyKo])) },
          __jobByCompany: { ...(prev.__jobByCompany || {}), ...Object.fromEntries(mapped.filter((job) => job.companyKo).map((job) => [job.companyKo, job])) },
        }));
        return mapped;
      },
      status() {
        return {
          authenticated: Boolean(jf.__authenticated || authStore.getToken()),
          apiStatus: jf.__apiStatus || {},
          userProjectId: jf.__userProjectId,
          listingCount: jf.listings?.length || 0,
          matchCount: jf.matches?.length || 0,
          skillCount: jf.skills?.length || 0,
          experienceTagCount: jf.expTags?.length || 0,
        };
      },
      async logout() {
        await api.logout().catch(() => null);
        authStore.clear();
        projectStore.clear();
        setAuthenticated(false);
        refreshData();
      },
      async startGithubOAuth() {
        authStore.clear();
        await api.logout().catch(() => null);
        window.location.href = `${API_BASE_URL}/oauth2/authorization/github`;
      },
    });
    return clearJobflowActions;
  }, [jf, refreshData, resolveJobId]);

  React.useEffect(() => {
    if (route.name !== 'detail') return;

    const requestedJobId = route.params.jobId || resolveJobId(route.params.company);
    if (!requestedJobId) return;

    const cacheKey = `${requestedJobId}:${route.params.company || ''}`;
    if (fetchedJobDetailIds.current.has(cacheKey)) return;
    fetchedJobDetailIds.current.add(cacheKey);

    let alive = true;
    setDataLoading(true);
    api.job(requestedJobId)
      .then((detail) => {
        if (!alive || !detail) return;
        setJf((prev) => mergePrototypeJobIntoState(prev, toPrototypeJob(detail)));
      })
      .catch(() => {
        fetchedJobDetailIds.current.delete(cacheKey);
      })
      .finally(() => {
        if (alive) setDataLoading(false);
      });

    return () => { alive = false; };
  }, [route.name, route.params.jobId, route.params.company, resolveJobId]);

  React.useEffect(() => {
    const onHash = () => setRoute(parseRoute());
    window.addEventListener('hashchange', onHash);
    return () => window.removeEventListener('hashchange', onHash);
  }, []);

  const go = (name, params = {}) => {
    if (name === 'detail' && (params.jobId || params.id)) window.location.hash = `detail/${encodeURIComponent(params.jobId || params.id)}`;
    else if (name === 'detail' && params.company) window.location.hash = `detail/${encodeURIComponent(params.company)}`;
    else window.location.hash = name;
    setRoute({ name, params });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const protectedScreens = new Set(['userJobs', 'applications', 'projects', 'project-new', 'project-analysis', 'gap', 'recommendations', 'mypage', 'demo-status']);
  if (!authChecked && dataLoading && protectedScreens.has(route.name)) {
    return <JobFlowScreens t={t} go={go} screen="loading" />;
  }
  if (!authenticated && protectedScreens.has(route.name)) {
    return <JobFlowScreens t={t} go={go} screen="error-401" />;
  }

  if (route.name === 'login') return <ConnectedLogin go={go} onAuthenticated={completeAuthentication} />;
  if (route.name === 'oauth') return <ConnectedOAuth go={go} onAuthenticated={completeAuthentication} />;
  if (route.name === 'home') return <JobFlowHome t={t} go={go} />;
  if (route.name === 'detail') return <JobDetail t={t} go={go} jobId={route.params.jobId} company={route.params.company || resolveCompanyFromJobId(route.params.jobId)} loading={dataLoading} />;
  if (route.name === 'onboarding') return <Onboarding t={t} go={go} />;
  return <JobFlowScreens t={t} go={go} screen={route.name} />;
}
