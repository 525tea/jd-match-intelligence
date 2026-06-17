import React from 'react';
import { api, useApiResource } from '../api/client.js';
import { mockApplications, mockJobs, mockMarket, mockProjects, mockTrends } from '../data/mock.js';
import { ApiState, Badge, Button, GithubConnectedIcon, JobCard, ProgressBar, Section } from '../components/ui.jsx';
import { compactNumber } from '../utils/format.js';

export function HomePage({ go, user }) {
  const project = mockProjects[0];
  const matches = useApiResource(() => api.projectJobMatches(project.userProjectId, { targetRoles: ['BACKEND'], targetCareerLevel: 'NEWCOMER', limit: 8 }), mockJobs, []);
  const trends = useApiResource(() => api.skillTrends({ limit: 5 }), mockTrends, []);
  const apps = useApiResource(() => api.applications(), mockApplications, []);
  const jobs = matches.data?.length ? matches.data : mockJobs;
  const topCount = jobs.filter((j) => Number(j.score?.totalScore ?? j.matchScore ?? 0) >= 80).length || 3;
  return <main className="page">
    <div className="hero">
      <section className="hero-main">
        <p className="eyebrow">DAILY MATCH DIGEST</p>
        <h1>{user.name}님,<br /><span className="number">매칭률 80%+</span> 공고 {topCount}개를 발견했어요.</h1>
        <p>GitHub 레포에서 추출한 기술 스택과 경험 태그를 기준으로, 지금 지원 가능성이 높은 공고부터 정렬했습니다.</p>
        <div className="hero-actions"><Button variant="lime" onClick={() => go('recommendations')}>추천 공고 보러가기</Button><Button variant="light" onClick={() => go('projects')}>스택 추출하기</Button></div>
        <div className="float-stats"><div className="float-stat"><b>{project.name}</b><span>분석 레포</span></div><div className="float-stat"><b>{apps.data.length}</b><span>지원 중</span></div><div className="float-stat"><b>{compactNumber(mockMarket[0].jobCount)}</b><span>이번 달 공고</span></div></div>
      </section>
      <aside className="repo-panel">
        <p className="eyebrow">REPOSITORY MATCHING</p>
        <div className="repo-title"><GithubConnectedIcon size={52} /><div><h3>{project.name}</h3><p>최근 분석 · {project.analyzedAt}</p></div></div>
        <div className="repo-tags">
          <div className="line"><strong>Skills</strong>{project.previewSkills.map((s) => <Badge key={s} tone="skill">{s}</Badge>)}</div>
          <div className="line"><strong>Experience</strong>{['이벤트 드리븐', 'CI/CD', '멱등 처리'].map((s) => <Badge key={s} tone="tag">#{s}</Badge>)}</div>
        </div>
        <Button variant="light" onClick={() => go('project-analysis', project.userProjectId)}>분석 근거 보기</Button>
      </aside>
    </div>

    <Section eyebrow={<ApiState {...matches} />} title="매칭된 공고" action={<Button variant="light" onClick={() => go('recommendations')}>계속 넘겨보기</Button>}>
      <div className="job-scroller">{jobs.map((job) => <JobCard key={job.jobId || job.id} job={job} onOpen={(id) => go('detail', id)} onSave={(id) => api.saveJob(id).catch(() => {})} />)}</div>
    </Section>

    <div className="grid grid-2">
      <Section title="인기 공고" action={<Button variant="light" onClick={() => go('jobs')}>전체 보기</Button>}>
        <div className="grid">{mockJobs.slice(0, 3).map((job) => <JobCard key={job.id} job={job} compact onOpen={(id) => go('detail', id)} />)}</div>
      </Section>
      <Section title="스킬 트렌드" action={<ApiState {...trends} />}>
        <div className="grid">{trends.data.slice(0, 5).map((t) => <div key={t.skillName} className="trend-card"><div style={{ display: 'flex', justifyContent: 'space-between' }}><b>{t.skillName}</b><Badge tone={t.owned ? 'skill' : 'muted'}>{t.owned ? '보유' : '미보유'}</Badge></div><ProgressBar value={t.trendScore || t.requiredCount / 12} /><p style={{ margin: 0, color: 'var(--muted)', lineHeight: 1.5 }}>{t.insight || `${t.skillName} 요구가 최근 공고에서 증가하고 있습니다.`}</p></div>)}</div>
      </Section>
    </div>
  </main>;
}
