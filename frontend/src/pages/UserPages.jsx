import React from 'react';
import { api, authStore, useApiResource } from '../api/client.js';
import { mockApplications, mockJobs, mockUser } from '../data/mock.js';
import { ApiState, Badge, Button, EmptyState, GithubConnectedIcon, JobCard, Modal } from '../components/ui.jsx';
import { statusLabel } from '../utils/format.js';

export function UserJobsPage({ go }) {
  const [tab, setTab] = React.useState('saved');
  const loaders = { saved: api.savedJobs, viewed: api.viewedJobs, ignored: api.ignoredJobs };
  const resource = useApiResource(() => loaders[tab](), mockJobs.map((j) => ({ ...j, jobTitle: j.title })), [tab]);
  return <main className="page"><div className="page-head"><div><h1>내 공고</h1><p>저장, 최근 본 공고, 숨긴 공고를 다시 확인합니다.</p></div><ApiState {...resource} /></div><div className="filter-options" style={{ marginBottom: 18 }}>{[['saved', '저장'], ['viewed', '최근 본'], ['ignored', '숨김']].map(([k, l]) => <button key={k} className={`filter-chip ${tab === k ? 'active' : ''}`} onClick={() => setTab(k)}>{l}</button>)}</div><div className="job-grid">{resource.data.map((x) => <JobCard key={x.jobId || x.id} job={{ ...x, id: x.jobId || x.id, title: x.jobTitle || x.title, companyName: x.companyName }} onOpen={(id) => go('detail', id)} />)}</div></main>;
}

export function ApplicationsPage() {
  const apps = useApiResource(() => api.applications(), mockApplications, []);
  const statuses = ['APPLIED', 'DOCUMENT_PASSED', 'CODING_TEST', 'INTERVIEW', 'OFFER'];
  const update = (id, status) => api.updateApplicationStatus(id, status).catch(() => {});
  return <main className="page"><div className="page-head"><div><h1>지원 현황</h1><p>외부에서 지원한 공고도 단계별로 기록합니다.</p></div><ApiState {...apps} /></div><section className="section"><div className="pipeline">{statuses.map((s, i) => <div key={s} className={`pipeline-step ${i > 1 ? 'active' : ''} ${s === 'OFFER' ? 'strong' : ''}`}><h2>{apps.data.filter((a) => a.status === s).length}</h2><p>{statusLabel(s)}</p></div>)}</div></section><section className="section"><div className="grid">{apps.data.map((a) => <div className="tile" style={{ padding: 18, display: 'grid', gridTemplateColumns: '1fr 180px', gap: 12, alignItems: 'center' }} key={a.id}><div><Badge tone="neutral">{statusLabel(a.status)}</Badge><h3>{a.companyName} · {a.jobTitle}</h3><p style={{ color: 'var(--muted)' }}>지원일 {a.appliedAt?.slice(0, 10)}</p></div><select className="status-select" defaultValue={a.status} onChange={(e) => update(a.id, e.target.value)}>{['APPLIED', 'DOCUMENT_PASSED', 'CODING_TEST', 'INTERVIEW', 'OFFER', 'REJECTED', 'WITHDRAWN'].map((s) => <option key={s} value={s}>{statusLabel(s)}</option>)}</select></div>)}</div></section></main>;
}

export function MyPage({ go, setUser }) {
  const [modal, setModal] = React.useState(null);
  const me = useApiResource(() => api.me(), mockUser, []);
  const logout = () => { authStore.clear(); localStorage.removeItem('jobflow.demo'); setUser(mockUser); go('login'); };
  return <main className="page"><div className="page-head"><div><h1>설정</h1><p>계정 정보와 GitHub 연결 상태를 관리합니다.</p></div><ApiState {...me} /></div><div className="grid grid-2"><section className="tile side-card"><p className="eyebrow">ACCOUNT</p><div style={{ display: 'flex', gap: 14, alignItems: 'center' }}><div className="avatar">사</div><div><h2>{me.data.name || '사용자'}</h2><p>{me.data.email}</p></div></div><Button variant="light" onClick={() => setModal('profile')}>프로필 수정</Button></section><section className="tile side-card" style={{ background: 'var(--lime-soft)' }}><p className="eyebrow">GITHUB</p><div style={{ display: 'flex', gap: 12, alignItems: 'center' }}><GithubConnectedIcon size={42} /><div><h2>GitHub 연동됨</h2><p>example-user · 3개 레포 분석 가능</p></div></div><div style={{ display: 'flex', gap: 8, marginTop: 14 }}><Button variant="lime" onClick={() => go('projects')}>레포 관리</Button><Button variant="light" onClick={() => setModal('reconnect')}>재연동</Button></div></section></div><section className="section"><Button variant="danger" onClick={logout}>로그아웃</Button></section>{modal && <Modal title={modal === 'profile' ? '프로필 수정' : 'GitHub 재연동'} onClose={() => setModal(null)}><p>실제 API가 확정되면 설정 변경 요청을 연결합니다.</p><Button variant="lime" onClick={() => setModal(null)}>확인</Button></Modal>}</main>;
}
