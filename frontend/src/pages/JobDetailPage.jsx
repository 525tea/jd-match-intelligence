import React from 'react';
import { Bookmark, Eye } from 'lucide-react';
import { api, useApiResource } from '../api/client.js';
import { mockJobs, tagLabels } from '../data/mock.js';
import { ApiState, Badge, Button, JobCard, ProgressBar, Section } from '../components/ui.jsx';
import { careerLabel, companyLogo, dday, roleLabel, scoreOf } from '../utils/format.js';

export function JobDetailPage({ go, jobId }) {
  const fallback = mockJobs.find((j) => String(j.id) === String(jobId)) || mockJobs[0];
  const detail = useApiResource(() => api.job(jobId || fallback.id), fallback, [jobId]);
  const job = detail.data || fallback;
  React.useEffect(() => { if (jobId) api.viewJob(jobId).catch(() => {}); }, [jobId]);
  const skills = (job.skills || []).map((s) => s.skillName || s.name).filter(Boolean);
  const matched = [...(job.matchedRequiredSkills || []), ...(job.matchedPreferredSkills || [])];
  const missing = [...(job.missingRequiredSkills || []), ...(job.missingPreferredSkills || [])];
  const tags = (job.experienceTags || []).map((t) => t.tagName || tagLabels[t.tagCode] || t.tagCode).filter(Boolean);
  const matchedTags = (job.matchedExperienceTags || []).map((t) => t.tagName || tagLabels[t.tagCode] || t.tagCode);
  const score = scoreOf(job) || 94;
  return <main className="page">
    <div className="page-head"><div><h1>공고 상세</h1><p>공고 원문과 내 레포 기준 매칭 근거를 함께 봅니다.</p></div><ApiState {...detail} /></div>
    <div className="detail-layout">
      <section className="tile detail-hero">
        <div className="detail-title"><div className="company-mark">{job.logo || companyLogo(job.companyName)}</div><div><div className="company-name">{job.companyName} <span style={{ color: 'var(--muted)', fontWeight: 500 }}>{job.companyIntro}</span></div><h1>{job.title}</h1><p>{roleLabel(job.role)} · {careerLabel(job.careerLevel, job.minExperienceYears, job.maxExperienceYears)} · {job.locationRegion || '서울'} {job.locationCity || ''}</p></div><div className="score"><b>{score}</b><span>%</span><small>레포 매칭</small></div></div>
        <div className="detail-meta"><Badge tone="coral">마감 {dday(job.deadlineAt)}</Badge><Badge tone="neutral">경력 {careerLabel(job.careerLevel, job.minExperienceYears, job.maxExperienceYears)}</Badge><span className="views"><Eye size={16} />{job.views || 326}</span></div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 20 }}><Button variant="light" onClick={() => api.saveJob(job.id || job.jobId).catch(() => {})}><Bookmark size={17} />저장</Button><Button variant="lime" onClick={() => api.createApplication(job.id || job.jobId).catch(() => {})}>지원 기록 추가</Button></div>
        <Section title="공고 원문"><p className="description">{job.description || '상세 원문이 아직 수집되지 않았습니다.'}</p><div className="filter-group"><h4>기술 스택</h4><div className="skill-row">{skills.map((s) => <Badge key={s} tone={matched.includes(s) ? 'skill' : missing.includes(s) ? 'muted' : 'skill'}>{matched.includes(s) ? '✓ ' : ''}{s}</Badge>)}</div></div><div className="filter-group"><h4>경험 태그</h4><div className="tag-row">{tags.map((t) => <Badge key={t} tone={matchedTags.includes(t) ? 'tag' : 'tag-muted'}>#{t}</Badge>)}</div></div></Section>
      </section>
      <aside className="grid">
        <section className="tile side-card"><p className="eyebrow">MATCH REPORT</p><h2 style={{ margin: 0 }}>sample-api 기준 {score}%</h2><ProgressBar value={score} /><div className="filter-group"><h4>보유 스킬</h4><div className="skill-row">{matched.map((s) => <Badge key={s} tone="skill">✓ {s}</Badge>)}</div></div><div className="filter-group"><h4>미보유 스킬</h4><div className="skill-row">{missing.map((s) => <Badge key={s} tone="muted">{s}</Badge>)}</div></div><Button variant="dark" onClick={() => go('projects')}>다른 프로젝트로 다시 매칭</Button></section>
        <section className="tile side-card applicant-card"><p className="eyebrow">APPLICANT INSIGHT</p><h3>이 공고 지원자들 스펙</h3>{[['Java', 82], ['Spring Boot', 78], ['Redis', 51], ['Kafka', 44]].map(([l, v]) => <div className="bar-row" key={l}><b>{l}</b><ProgressBar value={v} /><span>{v}%</span></div>)}<div className="filter-group"><h4>연차 분포</h4>{[['신입', 48], ['1~3년', 36], ['3년+', 16]].map(([l, v]) => <div className="bar-row" key={l}><b>{l}</b><ProgressBar value={v} tone="purple" /><span>{v}%</span></div>)}</div></section>
        <section className="tile side-card"><p className="eyebrow">SIMILAR JOBS</p><div className="grid">{mockJobs.filter((m) => m.id !== job.id).slice(0, 2).map((m) => <JobCard key={m.id} job={m} compact onOpen={(id) => go('detail', id)} />)}</div></section>
      </aside>
    </div>
  </main>;
}
