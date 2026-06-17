import React from 'react';
import { api, useApiResource } from '../api/client.js';
import { mockMarket, mockTrends } from '../data/mock.js';
import { ApiState, Badge, ProgressBar, Section } from '../components/ui.jsx';
import { compactNumber } from '../utils/format.js';

export function TrendsPage() {
  const [skill, setSkill] = React.useState(mockTrends[0]);
  const trends = useApiResource(() => api.skillTrends({ limit: 10 }), mockTrends, []);
  const market = useApiResource(() => api.market({ role: 'BACKEND', limit: 5 }), mockMarket, []);
  const co = useApiResource(() => api.cooccurrences(skill.skillId || 1, { limit: 5 }), [], [skill.skillId]);
  const tags = useApiResource(() => api.skillExperienceTags(skill.skillId || 1, { limit: 5 }), [], [skill.skillId]);
  const rows = trends.data.length ? trends.data.map((t) => ({ ...t, trendScore: t.trendScore || Math.min(100, Math.round((t.requiredCount || 1) / 12)) })) : mockTrends;
  const marketRow = market.data?.[0] || mockMarket[0];
  return <main className="page">
    <div className="page-head"><div><h1>트렌드</h1><p>직무 기반 시장 리포트입니다. 내 미보유 스킬과 연결해 다음 학습 결정을 돕습니다.</p></div><ApiState {...trends} /></div>
    <div className="trend-grid">
      <section className="grid">{rows.map((t) => <button key={t.skillName} className={`tile trend-card ${skill.skillName === t.skillName ? 'active' : ''}`} onClick={() => setSkill(t)}><div style={{ display: 'flex', justifyContent: 'space-between' }}><h2 style={{ margin: 0 }}>{t.skillName}</h2><Badge tone={t.owned ? 'skill' : 'coral'}>{t.owned ? '보유' : '미보유'}</Badge></div><ProgressBar value={t.trendScore} tone={skill.skillName === t.skillName ? 'purple' : 'lime'} /><p style={{ color: skill.skillName === t.skillName ? 'rgba(255,255,255,.72)' : 'var(--muted)', lineHeight: 1.55 }}>{t.insight || `${t.skillName}는 최근 공고에서 중요한 요구 스킬로 등장합니다.`}</p></button>)}</section>
      <aside className="grid"><section className="tile side-card"><p className="eyebrow">MARKET</p><h2>{compactNumber(marketRow.jobCount)}개 공고</h2><p style={{ color: 'var(--muted)' }}>백엔드 주니어 기준 · 열린 공고 {compactNumber(marketRow.openJobCount)}</p></section><section className="tile side-card"><p className="eyebrow">SKILL DETAIL</p><h2>{skill.skillName}</h2><div className="filter-group"><h4>함께 등장하는 스킬</h4>{(co.data.length ? co.data : [{ coSkillName: 'Spring Boot', liftScore: 82 }, { coSkillName: 'Redis', liftScore: 66 }]).map((x) => <div className="bar-row" key={x.coSkillName}><b>{x.coSkillName}</b><ProgressBar value={x.liftScore || 50} /><span>{Math.round(x.liftScore || 50)}</span></div>)}</div><div className="filter-group"><h4>관련 경험 태그</h4><div className="tag-row">{(tags.data.length ? tags.data : [{ tagName: '이벤트 드리븐' }, { tagName: '대용량 트래픽' }]).map((x) => <Badge key={x.tagName || x.tagCode} tone="tag">#{x.tagName || x.tagCode}</Badge>)}</div></div></section></aside>
    </div>
  </main>;
}
