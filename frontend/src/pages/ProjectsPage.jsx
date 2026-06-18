import React from 'react';
import { GitBranch, Pencil } from 'lucide-react';
import { api, useApiResource } from '../api/client.js';
import { mockExperienceTags, mockJobs, mockProjects, mockSkills } from '../data/mock.js';
import { ApiState, Badge, Button, GithubConnectedIcon, JobCard, Modal, ProgressBar, Section } from '../components/ui.jsx';

export function ProjectsPage({ go }) {
  const [preview, setPreview] = React.useState(mockProjects[0]);
  const [edit, setEdit] = React.useState(null);
  const recommendations = useApiResource(() => api.recommendations(preview.userProjectId, { targetRoles: ['BACKEND'], limit: 6 }), mockJobs, [preview.userProjectId]);
  return <main className="page">
    <div className="page-head"><div><h1>프로젝트</h1><p>레포지토리에서 추출한 스킬 인벤토리와 연결 가능한 공고를 확인합니다.</p></div><Button variant="dark" onClick={() => go('project-new')}>+ 새 레포 분석</Button></div>
    <div className="grid grid-2">
      {mockProjects.map((p) => <article key={p.name} className="tile project-card" onClick={() => { setPreview(p); }}>
        <div className="repo-visual"><div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}><span style={{ display: 'flex', gap: 8, alignItems: 'center', fontWeight: 900 }}><GitBranch size={17} /> GITHUB REPOSITORY</span><button className="edit-mini" onClick={(e) => { e.stopPropagation(); setEdit(p); }}><Pencil size={15} /></button></div><strong>{p.connected ? `${p.name} / main` : 'connect GitHub repository'}</strong></div>
        <div className="project-body"><div style={{ display: 'flex', gap: 12, alignItems: 'center' }}><div className="company-mark">{p.name.slice(0, 2).toUpperCase()}</div><div><h2 style={{ margin: 0 }}>{p.name}</h2><p style={{ margin: 0, color: 'var(--muted)', display: 'flex', alignItems: 'center', gap: 6 }}>{p.connected && <GithubConnectedIcon size={20} />}{p.repo}</p></div></div><p style={{ color: 'var(--muted)', lineHeight: 1.6 }}>{p.summary}</p><div className="skill-row">{p.previewSkills.map((s) => <Badge key={s} tone="skill">{s}</Badge>)}</div><div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 16 }}><Button variant="lime" onClick={(e) => { e.stopPropagation(); setPreview(p); }}> {p.matchedJobs}개 매칭 공고 미리보기</Button><span style={{ color: 'var(--faint)', fontSize: 13 }}>마지막 분석 · {p.analyzedAt}</span></div></div>
      </article>)}
    </div>
    <Section title="내 스택과 매칭률이 높은 공고" action={<ApiState {...recommendations} />}>
      <div className="job-scroller">{recommendations.data.slice(0, 6).map((job) => <JobCard key={job.id || job.jobId} job={job} onOpen={(id) => go('detail', id)} />)}</div>
    </Section>
    {edit && <Modal title="프로젝트 수정" onClose={() => setEdit(null)}><label>프로젝트 이름<input defaultValue={edit.name} /></label><label>레포 주소<input defaultValue={edit.repo} /></label><label>썸네일 메모<input defaultValue={edit.summary} /></label><div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}><Button variant="light" onClick={() => setEdit(null)}>취소</Button><Button variant="lime" onClick={() => setEdit(null)}>저장</Button></div></Modal>}
  </main>;
}

export function ProjectNewPage({ go }) {
  return <main className="page"><div className="page-head"><div><h1>새 레포 분석</h1><p>GitHub 레포 URL을 등록하면 스킬, 경험 태그, 추천 공고 플로우로 이어집니다.</p></div></div><section className="tile" style={{ padding: 28 }}><div className="grid grid-2"><label>Repository URL<input placeholder="https://github.com/example-org/sample-repo" /></label><label>Branch<input placeholder="main" /></label><label>프로젝트 이름<input placeholder="sample-api" /></label><label>분석 목적<select><option>공고 매칭</option><option>포트폴리오 문장 생성</option></select></label></div><div className="grid grid-3" style={{ marginTop: 22 }}>{['레포 접근 확인', '스킬 후보 추출', '경험 태그 요약'].map((x, i) => <div className="tile" style={{ padding: 18 }} key={x}><Badge tone="lime">{i + 1}</Badge><h3>{x}</h3><p style={{ color: 'var(--muted)' }}>분석 트리거 API 확정 후 연결 예정입니다.</p></div>)}</div><div style={{ marginTop: 20, display: 'flex', gap: 8 }}><Button variant="lime" onClick={() => go('project-analysis', 1)}>분석 시작</Button><Button variant="light" onClick={() => go('projects')}>URL만 저장</Button></div></section></main>;
}

export function ProjectAnalysisPage({ go, projectId = 1 }) {
  const project = mockProjects.find((p) => String(p.userProjectId) === String(projectId)) || mockProjects[0];
  const skills = useApiResource(() => api.projectSkills(project.userProjectId), mockSkills, [project.userProjectId]);
  const tags = useApiResource(() => api.projectExperienceTags(project.userProjectId), mockExperienceTags, [project.userProjectId]);
  return <main className="page">
    <div className="page-head"><div><h1>{project.name}</h1><p><GithubConnectedIcon size={24} /> {project.repo} · 마지막 분석 {project.analyzedAt}</p></div><div style={{ display: 'flex', gap: 8 }}><Button variant="light" onClick={() => go('projects')}>레포 변경</Button><Button variant="dark" onClick={() => go('project-new')}>재분석 받기</Button></div></div>
    <div className="analysis-grid">
      <section className="tile" style={{ padding: 28 }}><p className="eyebrow">OVERVIEW</p><p style={{ fontSize: 20, lineHeight: 1.8 }}>{project.overview}</p><div className="repo-tags"><div className="line"><strong>도메인</strong>{project.domain?.map((d) => <Badge key={d}>{d}</Badge>)}</div><div className="line"><strong>아키텍처</strong><Badge tone="skill">{project.architecture}</Badge></div><div className="line"><strong>Tech Stack</strong>{skills.data.slice(0, 8).map((s) => <Badge key={s.skillName} tone="skill">{s.skillName} {Math.round(s.confidence || 0)}</Badge>)}</div></div></section>
      <section className="tile side-card"><p className="eyebrow">CODE STATS</p><div className="grid grid-2">{Object.entries(project.stats || {}).map(([k, v]) => <div key={k}><h2 style={{ margin: 0 }}>{v}</h2><p style={{ color: 'var(--muted)', marginTop: 2 }}>{k}</p></div>)}</div>{project.dirs?.map(([path, pct]) => <div className="bar-row" key={path}><b>{path}</b><ProgressBar value={pct} /><span>{pct}%</span></div>)}</section>
    </div>
    <Section title="추출 스킬과 분석 근거" action={<ApiState {...skills} />}><div className="skill-evidence">{skills.data.map((s) => <div className="skill-evidence-row" key={s.skillName}><div><b>{s.skillName}</b><p style={{ margin: 0, color: 'var(--faint)', fontSize: 13 }}>{s.category} · {s.source}</p></div><div><ProgressBar value={s.confidence} /><p style={{ color: 'var(--muted)' }}>{s.evidence}</p></div></div>)}</div></Section>
    <Section title="경험 태그 문장" action={<ApiState {...tags} />}><div className="grid grid-2">{tags.data.map((t) => <div className="tile" style={{ padding: 20 }} key={t.tagCode}><Badge tone="tag">#{t.tagName || t.tagCode}</Badge><p style={{ lineHeight: 1.7 }}>{t.evidence}</p></div>)}</div></Section>
  </main>;
}
