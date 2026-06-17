import React from 'react';
import { api, useApiResource } from '../api/client.js';
import { filters, mockListings } from '../data/mock.js';
import { ApiState, Button, EmptyState, JobCard } from '../components/ui.jsx';
import { Search } from 'lucide-react';

export function JobsPage({ go }) {
  const [query, setQuery] = React.useState('Spring Boot');
  const [selected, setSelected] = React.useState({ roles: ['BACKEND'], careers: ['ANY'], skills: ['Java', 'Spring Boot'], sort: 'match' });
  const jobs = useApiResource(() => query ? api.searchJobs(query, 30) : api.jobs(), mockListings, [query]);
  const toggle = (group, value) => setSelected((prev) => ({ ...prev, [group]: prev[group]?.includes(value) ? prev[group].filter((x) => x !== value) : [...(prev[group] || []), value] }));
  const data = jobs.data?.length ? jobs.data : mockListings;
  const sorted = [...data].sort((a, b) => selected.sort === 'deadline' ? String(a.deadlineAt).localeCompare(String(b.deadlineAt)) : selected.sort === 'latest' ? Number(b.id) - Number(a.id) : Number(b.matchScore || b.score?.totalScore || 0) - Number(a.matchScore || a.score?.totalScore || 0));

  return <main className="page">
    <div className="page-head"><div><h1>공고</h1><p>검색보다 필터를 먼저 열어두고, 기술 스택 기준으로 빠르게 좁혀봅니다.</p></div><ApiState {...jobs} /></div>
    <div className="filter-layout">
      <aside className="tile filter-panel">
        <div className="search-box"><Search size={18} /><input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="공고, 기술, 회사 검색" /></div>
        <FilterGroup title="직무" options={filters.roles} selected={selected.roles} onToggle={(v) => toggle('roles', v)} />
        <FilterGroup title="경력" options={filters.careers} selected={selected.careers} onToggle={(v) => toggle('careers', v)} />
        <FilterGroup title="기술 스택" options={filters.skills.map((x) => [x, x])} selected={selected.skills} onToggle={(v) => toggle('skills', v)} />
        <FilterGroup title="근무 형태" options={filters.remoteTypes} selected={selected.remoteTypes || []} onToggle={(v) => toggle('remoteTypes', v)} />
        <FilterGroup title="마감" options={filters.deadlines} selected={selected.deadlines || ['ALL']} onToggle={(v) => toggle('deadlines', v)} />
      </aside>
      <section>
        <div className="sort-bar"><strong>해당 공고 {sorted.length}개</strong><div className="filter-options">{[['match', '매칭순'], ['popular', '인기순'], ['deadline', '마감순'], ['latest', '최신순']].map(([key, label]) => <button key={key} className={`filter-chip ${selected.sort === key ? 'active' : ''}`} onClick={() => setSelected((p) => ({ ...p, sort: key }))}>{label}</button>)}</div></div>
        {sorted.length ? <div className="job-grid">{sorted.map((job) => <JobCard key={job.id || job.jobId} job={job} onOpen={(id) => go('detail', id)} onSave={(id) => api.saveJob(id).catch(() => {})} />)}</div> : <EmptyState title="조건에 맞는 공고가 없어요" desc="필터를 조금 줄이거나 다른 기술 스택을 선택해보세요." />}
      </section>
    </div>
  </main>;
}

function FilterGroup({ title, options, selected = [], onToggle }) {
  return <div className="filter-group"><h4>{title}</h4><div className="filter-options">{options.map((item) => { const [value, label] = Array.isArray(item) ? item : [item, item]; return <button key={value} className={`filter-chip ${selected.includes(value) ? 'active' : ''}`} onClick={() => onToggle(value)}>{label}</button>; })}</div></div>;
}
