import React from 'react';
import { api, useApiResource } from '../api/client.js';
import { mockJobs, mockProjects } from '../data/mock.js';
import { ApiState, Badge, Button, JobCard, ProgressBar } from '../components/ui.jsx';

export function RecommendationsPage({ go }) {
  const project = mockProjects[0];
  const rec = useApiResource(() => api.recommendations(project.userProjectId, { targetRoles: ['BACKEND'], limit: 20 }), mockJobs, []);
  return <main className="page">
    <div className="page-head"><div><h1>추천 피드</h1><p>매칭률, 신선도, 행동 신호를 섞어 먼저 볼 공고를 계속 넘겨봅니다.</p></div><ApiState {...rec} /></div>
    <div className="filter-layout">
      <aside className="tile filter-panel"><p className="eyebrow">RECOMMENDATION</p><h2>commerce-api 기준</h2>{[['스킬 매칭', 70], ['신선도', 15], ['행동 신호', 10], ['인기', 5]].map(([l, v]) => <div className="bar-row" key={l}><b>{l}</b><ProgressBar value={v} /><span>{v}%</span></div>)}<Button variant="lime" onClick={() => go('projects')}>프로젝트 바꾸기</Button></aside>
      <section><div className="job-grid">{rec.data.map((job, i) => <article key={job.id || job.jobId || i} style={{ position: 'relative' }}><Badge tone="dark" className="rank-badge">#{i + 1}</Badge><JobCard job={job} onOpen={(id) => go('detail', id)} onSave={(id) => api.saveJob(id).catch(() => {})} /></article>)}</div></section>
    </div>
  </main>;
}
