import React from 'react';
import { JF as baseJF } from './prototype/jobflow-data.js';
import { JobFlowHome } from './prototype/HomePrototype.jsx';
import { JobDetail } from './prototype/JobDetailPrototype.jsx';
import { Onboarding } from './prototype/OnboardingPrototype.jsx';
import { JobFlowScreens } from './prototype/ScreensPrototype.jsx';
import { loadJobFlowData, toPrototypeJob } from './prototype/adapters.js';
import { api, authStore } from './api/client.js';
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
  const [jf, setJf] = React.useState(baseJF);
  const [route, setRoute] = React.useState(parseRoute);
  const [authenticated, setAuthenticated] = React.useState(Boolean(authStore.getToken()));
  const t = React.useMemo(() => ({
    ...initialTweaks,
    state: authenticated ? '로그인' : '비로그인',
  }), [authenticated]);
  window.JF = jf;

  const refreshData = React.useCallback(async () => {
    const data = await loadJobFlowData(baseJF);
    setAuthenticated(Boolean(authStore.getToken()));
    setJf(data);
    return data;
  }, []);

  React.useEffect(() => {
    let alive = true;
    loadJobFlowData(baseJF).then((data) => {
      if (!alive) return;
      setAuthenticated(Boolean(authStore.getToken()));
      setJf(data);
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
    window.__jobflowApi = {
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
        const rows = await api.searchJobs(keyword || '');
        const mapped = rows.map(toPrototypeJob);
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
      logout() {
        authStore.clear();
        setAuthenticated(false);
        refreshData();
      },
    };
    return () => { delete window.__jobflowApi; };
  }, [jf, refreshData, resolveJobId]);

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
  if (!authenticated && protectedScreens.has(route.name)) {
    return <JobFlowScreens t={t} go={go} screen="error-401" />;
  }

  if (route.name === 'login') return <ConnectedLogin go={go} onAuthenticated={refreshData} />;
  if (route.name === 'oauth') return <ConnectedOAuth go={go} onAuthenticated={refreshData} />;
  if (route.name === 'home') return <JobFlowHome t={t} go={go} />;
  if (route.name === 'detail') return <JobDetail t={t} go={go} jobId={route.params.jobId} company={route.params.company || resolveCompanyFromJobId(route.params.jobId)} />;
  if (route.name === 'onboarding') return <Onboarding t={t} go={go} />;
  return <JobFlowScreens t={t} go={go} screen={route.name} />;
}
