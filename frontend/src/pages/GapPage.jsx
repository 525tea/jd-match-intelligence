import React from 'react';
import { LockKeyhole } from 'lucide-react';
import { api, useApiResource } from '../api/client.js';
import { mockGapSkills, mockJobs, mockProjects } from '../data/mock.js';
import { ApiState, Badge, Button, JobCard, ProgressBar, Section } from '../components/ui.jsx';

export function GapPage({ go }) {
  const [selected, setSelected] = React.useState(mockGapSkills[0]);
  const project = mockProjects[0];
  const gap = useApiResource(() => api.gapAnalysis(project.userProjectId, { targetRoles: ['BACKEND'], limit: 20 }), { userProjectId: project.userProjectId, jobMatches: mockJobs }, []);
  const jobs = gap.data?.jobMatches?.length ? gap.data.jobMatches : mockJobs;
  return <main className="page">
    <div className="page-head"><div><h1>갭 분석</h1><p>공고가 아니라 스킬이 주인공입니다. 무엇을 배우면 기회가 얼마나 늘어나는지 봅니다.</p></div><ApiState {...gap} /></div>
    <div className="gap-layout">
      <aside className="grid">{mockGapSkills.map((item) => <button key={item.skill} className={`gap-skill ${selected.skill === item.skill ? 'active' : ''}`} onClick={() => setSelected(item)}><div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}><span className="unlock"><LockKeyhole size={18} /></span><Badge tone="lime">+{item.addedJobs} 공고</Badge></div><h3>{item.skill}</h3><p style={{ color: 'var(--muted)', textAlign: 'left', lineHeight: 1.5 }}>{item.reason}</p><ProgressBar value={item.demand} /></button>)}</aside>
      <section className="tile" style={{ padding: 26 }}><p className="eyebrow">UNLOCKED BY {selected.skill}</p><h2 style={{ marginTop: 0 }}>{selected.skill}를 배우면 {selected.addedJobs}개 공고를 더 열 수 있어요</h2><p style={{ color: 'var(--muted)' }}>{selected.unlocked.join(', ')} 같은 공고에서 반복적으로 요구됩니다.</p><div className="grid" style={{ marginTop: 18 }}>{jobs.slice(0, 4).map((job) => <JobCard key={job.id || job.jobId} job={job} compact onOpen={(id) => go('detail', id)} />)}</div></section>
    </div>
  </main>;
}
