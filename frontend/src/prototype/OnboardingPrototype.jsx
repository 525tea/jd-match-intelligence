import React from 'react';

// JobFlow — 온보딩 플로우 (스킬 직접 입력). 4스텝, 선택 즉시 자동 전환.
// Step1 직무 → Step2 경력 → Step3 스킬(복수) → Step4 결과(매칭 공고 + GitHub 유도)
export function Onboarding({ t, go }) {
  const JF = window.JF;
  const [step, setStep] = React.useState(1);
  const [role, setRole] = React.useState(null);
  const [career, setCareer] = React.useState(null);
  const [skills, setSkills] = React.useState([]);
  const [custom, setCustom] = React.useState('');

  const ink = '#14151a', muted = '#5d626d', faint = '#9aa0ac';
  const card = '#ffffff', line = '#e7eaf0', soft = '#f1f3f7', page = '#ffffff';
  const green = t.green || '#b9ec2a';
  const greenInk = '#3f5c08', greenTint = '#eef8cf', greenTintBd = '#dbeca8';
  const font = "'Space Grotesk', 'Pretendard', 'Apple SD Gothic Neo', system-ui, sans-serif";

  const skillPool = (JF.onboarding.skills[role] || JF.onboarding.skills['default']);
  const toggleSkill = (s) => setSkills((xs) => xs.includes(s) ? xs.filter((x) => x !== s) : [...xs, s]);
  const addCustom = () => { const v = custom.trim(); if (v && !skills.includes(v)) setSkills((xs) => [...xs, v]); setCustom(''); };

  // result: listings overlapping selected skills (fallback: all)
  const sel = new Set(skills);
  const matched = JF.listings
    .map((j) => ({ j, hit: j.skills.filter((s) => sel.has(s)).length }))
    .sort((a, b) => b.hit - a.hit)
    .filter((x, i) => x.hit > 0 || i < 6)
    .slice(0, 6);

  const advance = (setter, v, next) => { setter(v); setTimeout(() => setStep(next), 180); };

  const Opt = ({ label, active, onClick }) => (
    <button className="jf-cta" onClick={onClick}
      style={{ font: 'inherit', cursor: 'pointer', textAlign: 'left', width: '100%', padding: '20px 22px', borderRadius: 14, fontSize: 17, fontWeight: 600,
        background: active ? green : card, color: ink, border: '1px solid ' + (active ? green : line),
        boxShadow: active ? 'none' : '0 1px 2px rgba(20,21,26,0.04)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
      {label}<span style={{ fontSize: 16, color: active ? ink : faint }}>→</span>
    </button>
  );

  const QTitle = ({ children, sub }) => (
    <div style={{ marginBottom: 26 }}>
      <div style={{ fontSize: 27, fontWeight: 700, letterSpacing: -0.6 }}>{children}</div>
      {sub && <div style={{ fontSize: 14.5, color: muted, marginTop: 8 }}>{sub}</div>}
    </div>
  );

  const pct = step === 4 ? 100 : (step - 1) / 3 * 100 + 8;

  return (
    <div style={{ minHeight: '100vh', background: page, fontFamily: font, color: ink }}>
      <div style={{ height: 64, display: 'flex', alignItems: 'center', padding: '0 40px', gap: 16 }}>
        <span className="jf-link" style={{ fontSize: 14, fontWeight: 600, color: muted, cursor: 'pointer' }} onClick={() => (step > 1 && step < 4 ? setStep(step - 1) : go('home'))}>← {step > 1 && step < 4 ? '이전' : '홈으로'}</span>
        <div style={{ marginLeft: 'auto', fontSize: 20, fontWeight: 700, letterSpacing: -1 }}>jobflow<span style={{ color: green, WebkitTextStroke: '0.5px ' + greenInk }}>.</span></div>
      </div>

      {/* progress */}
      <div style={{ maxWidth: 620, margin: '0 auto', padding: '0 32px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, fontWeight: 700, color: faint, marginBottom: 8 }}>
          <span>STEP {Math.min(step, 4)} / 4</span>
          <span>{['직무', '경력', '기술 스택', '결과'][Math.min(step, 4) - 1]}</span>
        </div>
        <div style={{ height: 6, background: soft, borderRadius: 4, overflow: 'hidden' }}>
          <div style={{ width: pct + '%', height: '100%', background: green, borderRadius: 4 }} />
        </div>
      </div>

      <div style={{ maxWidth: 620, margin: '0 auto', padding: '40px 32px 64px' }}>
        {step === 1 && (
          <>
            <QTitle sub="역할에 맞는 채용 공고와 스킬을 추천해드려요.">어떤 개발자예요?</QTitle>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {JF.onboarding.roles.map((r) => <Opt key={r} label={r} active={role === r} onClick={() => advance(setRole, r, 2)} />)}
            </div>
          </>
        )}

        {step === 2 && (
          <>
            <QTitle sub="경력에 맞춰 신입/주니어 공고를 우선 보여드려요.">경력은 어느 정도예요?</QTitle>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {JF.onboarding.careers.map((c) => <Opt key={c} label={c} active={career === c} onClick={() => advance(setCareer, c, 3)} />)}
            </div>
          </>
        )}

        {step === 3 && (
          <>
            <QTitle sub="써본 기술을 모두 골라주세요. 직접 입력도 돼요.">어떤 기술을 쓰세요?</QTitle>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 9, marginBottom: 18 }}>
              {[...new Set([...skillPool, ...skills])].map((s) => {
                const on = skills.includes(s);
                return (
                  <button key={s} className="jf-cta" onClick={() => toggleSkill(s)}
                    style={{ font: 'inherit', cursor: 'pointer', fontSize: 14.5, fontWeight: 600, padding: '9px 16px', borderRadius: 22,
                      background: on ? green : card, color: ink, border: '1px solid ' + (on ? green : line) }}>
                    {on ? '✓ ' : ''}{s}
                  </button>
                );
              })}
            </div>
            <div style={{ display: 'flex', gap: 8, marginBottom: 24 }}>
              <input value={custom} onChange={(e) => setCustom(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && addCustom()}
                placeholder="다른 기술 직접 입력 (예: Elasticsearch)"
                style={{ font: 'inherit', flex: 1, fontSize: 14, padding: '11px 14px', borderRadius: 10, border: '1px solid ' + line, outline: 'none', background: card, color: ink }} />
              <button className="jf-cta" onClick={addCustom} style={{ font: 'inherit', cursor: 'pointer', fontSize: 14, fontWeight: 700, padding: '0 18px', borderRadius: 10, border: '1px solid ' + line, background: card, color: ink }}>추가</button>
            </div>
            <button className="jf-cta" onClick={() => setStep(4)} disabled={!skills.length}
              style={{ font: 'inherit', cursor: skills.length ? 'pointer' : 'not-allowed', width: '100%', padding: '15px', borderRadius: 12, fontSize: 15.5, fontWeight: 700, border: 'none',
                background: skills.length ? green : soft, color: skills.length ? ink : faint }}>
              {skills.length ? `${skills.length}개 스킬로 매칭 공고 보기 →` : '기술을 1개 이상 골라주세요'}
            </button>
          </>
        )}

        {step === 4 && (
          <>
            <QTitle sub={`${role || '백엔드'} · ${career || '신입'} · ${skills.length}개 스킬 기준`}>
              <span style={{ color: greenInk }}>{matched.length}개</span> 공고가 맞아요
            </QTitle>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 22 }}>
              {skills.map((s) => <span key={s} style={{ fontSize: 12.5, fontWeight: 600, padding: '4px 11px', borderRadius: 20, background: greenTint, color: greenInk, border: '1px solid ' + greenTintBd }}>{s}</span>)}
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 24 }}>
              {matched.map(({ j, hit }) => (
                <div key={j.companyKo} className="jf-jobcard" onClick={() => go('detail', { company: j.companyKo })}
                  style={{ cursor: 'pointer', background: card, border: '1px solid ' + line, borderRadius: 14, padding: '18px 20px', boxShadow: '0 1px 2px rgba(20,21,26,0.04)', display: 'flex', alignItems: 'center', gap: 14 }}>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
                      <span style={{ fontSize: 17, fontWeight: 700 }}>{j.companyKo}</span>
                      <span style={{ fontSize: 12.5, color: muted, whiteSpace: 'nowrap' }}>{j.title} · {j.level}</span>
                      <span style={{ marginLeft: 'auto', fontSize: 11.5, fontWeight: 700, color: ink }}>{j.deadline}</span>
                    </div>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 5, marginTop: 9 }}>
                      {j.skills.map((s) => <span key={s} style={{ fontSize: 11.5, fontWeight: 600, padding: '3px 9px', borderRadius: 16, background: sel.has(s) ? greenTint : soft, color: sel.has(s) ? greenInk : muted, border: '1px solid ' + (sel.has(s) ? greenTintBd : line) }}>{sel.has(s) ? '✓ ' : ''}{s}</span>)}
                    </div>
                  </div>
                </div>
              ))}
            </div>

            <div style={{ background: ink, color: '#fff', borderRadius: 16, padding: '22px 24px', display: 'flex', alignItems: 'center', gap: 18, flexWrap: 'wrap' }}>
              <div style={{ flex: 1, minWidth: 220 }}>
                <div style={{ fontSize: 16, fontWeight: 700, marginBottom: 4 }}>레포지토리 연결하면 더 정확해져요</div>
                <div style={{ fontSize: 13.5, color: 'rgba(255,255,255,0.7)' }}>GitHub 레포지토리에서 스킬을 자동 추출해 매칭률까지 계산해드려요.</div>
              </div>
              <button className="jf-cta" onClick={() => go('home')} style={{ font: 'inherit', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8, background: green, color: ink, border: 'none', fontSize: 14.5, fontWeight: 700, padding: '12px 20px', borderRadius: 24 }}>
                <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor"><path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38v-1.34c-2.23.49-2.7-1.07-2.7-1.07-.36-.93-.89-1.18-.89-1.18-.73-.5.05-.49.05-.49.8.06 1.23.83 1.23.83.72 1.23 1.88.87 2.34.67.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.96 0-.87.31-1.59.83-2.15-.08-.2-.36-1.02.08-2.13 0 0 .67-.21 2.2.82a7.6 7.6 0 0 1 4 0c1.53-1.03 2.2-.82 2.2-.82.44 1.11.16 1.93.08 2.13.52.56.82 1.28.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48v2.2c0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8z"/></svg>
                레포지토리 연결하기
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
