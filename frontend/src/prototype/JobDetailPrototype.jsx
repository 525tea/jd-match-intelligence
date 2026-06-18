import React from 'react';

// JobFlow — 공고 상세. JD + matching report.
// Palette: lime = owned/match, charcoal = analysis, coral = urgent/missing.
export function JobDetail({ t, go, company, jobId }) {
  const JF = window.JF;
  const [toast, setToast] = React.useState(null);
  const [saved, setSaved] = React.useState(false);
  const [applied, setApplied] = React.useState(false);
  const [hidden, setHidden] = React.useState(false);
  const fire = (label) => { setToast(label); clearTimeout(window.__jdT); window.__jdT = setTimeout(() => setToast(null), 1900); };

  const ink = '#14151a', muted = '#5b616e', faint = '#9aa1ad';
  const card = '#ffffff', line = '#e7eaf0', soft = '#f1f3f7';
  const green = t.green || '#b9ec2a';
  const greenInk = '#3f5c08', greenTint = '#eef8cf', greenTintBd = '#dbeca8';
  const coralDeep = '#c2391f', coralTint = '#ffe8e1', coralTintBd = '#f6c9bc';
  const login = t.state !== '비로그인';
  const v2 = t.ver === 'v2';
  const v3 = t.ver === 'v3';
  const tagTone = '보라';
  const font = "'Space Grotesk', 'Pretendard', 'Apple SD Gothic Neo', system-ui, sans-serif";
  const shadow = '0 1px 2px rgba(20,21,26,0.04), 0 6px 18px rgba(20,21,26,0.05)';
  const num = { fontVariantNumeric: 'tabular-nums' };
  const narrow = window.innerWidth < 980;

  const allJobs = [
    ...(JF.matches || []),
    ...(JF.listings || []),
    ...(JF.popular || []),
    ...(JF.closing || []),
    ...(JF.userJobs?.saved || []),
    ...(JF.userJobs?.viewed || []),
    ...(JF.userJobs?.ignored || []),
  ];
  const targetById = jobId ? allJobs.find((x) => String(x.jobId || x.id) === String(jobId)) : null;
  const targetCompany = company || targetById?.companyKo || '코어페이';
  const primaryProject = JF.projectList?.[0] || {};
  const currentProjectName = primaryProject.name || '내 프로젝트';
  const currentProjectSkillSummary = (primaryProject.previewSkills || JF.skills?.map((skill) => skill.name) || []).slice(0, 4).join(', ') || '프로젝트 분석 스킬';
  const userSkills = new Set(JF.skills.map((s) => s.name));
  const m = targetById || JF.matches.find((x) => x.companyKo === targetCompany);
  const l = !m && (JF.listings.find((x) => x.companyKo === targetCompany) || JF.popular.find((x) => x.companyKo === targetCompany) || JF.closing.find((x) => x.companyKo === targetCompany));
  const reqList = m ? m.requiredSkills : (l && l.skills) || ['Java', 'Spring Boot', 'JPA'];
  const prefList = m ? m.preferredSkills : ['Docker', 'AWS'];
  const tags = m ? m.tags : ['CI_CD', 'TESTING'];
  const job = {
    companyKo: targetCompany,
    id: (m && (m.id || m.jobId)) || (l && (l.id || l.jobId)) || jobId,
    jobId: (m && (m.jobId || m.id)) || (l && (l.jobId || l.id)) || jobId,
    company: (m && m.company) || 'JobFlow Partner',
    logo: (m && m.logo) || (l && l.logo) || targetCompany.slice(0, 2),
    fullTitle: (m && m.fullTitle) || (l && l.fullTitle) || `${targetCompany} 백엔드 엔지니어 채용`,
    title: (m && m.title) || (l && l.title) || '백엔드 엔지니어',
    role: (m && m.role) || (l && l.role) || 'Backend Engineer',
    level: (m && m.level) || (l && l.level) || '신입',
    location: (m && m.location) || (l && l.location) || '서울',
    deadline: (m && m.deadline) || (l && l.deadline) || 'D-7',
    views: (m && m.views) || (l && l.views) || 172,
    applicants: (m && m.applicants) || (l && l.applicants) || 58,
    companyIntro: (m && m.companyIntro) || '성장 중인 제품 조직에서 안정적인 백엔드 시스템을 만드는 팀입니다.',
    desc: (m && m.desc) || '서비스의 핵심 백엔드 시스템을 함께 설계·운영할 엔지니어를 찾습니다. 안정적인 API와 데이터 처리, 그리고 트래픽을 견디는 구조를 함께 만들어갑니다.',
  };
  const has = (s) => userSkills.has(s);
  const reqMatched = reqList.filter(has), reqMissing = reqList.filter((s) => !has(s));
  const prefMatched = prefList.filter(has), prefMissing = prefList.filter((s) => !has(s));
  const requiredRate = m ? m.required : Math.round((reqMatched.length / Math.max(1, reqList.length)) * 100);
  const preferredRate = m ? m.preferred : Math.round((prefMatched.length / Math.max(1, prefList.length)) * 100);
  const score = m ? m.score : Math.round(requiredRate * 0.7 + preferredRate * 0.3);
  const ownedSkills = [...new Set(reqMatched.concat(prefMatched))];
  const missingSkills = [...new Set(reqMissing.concat(prefMissing))];

  const Logo = ({ size = 54 }) => <div style={{ width: size, height: size, borderRadius: Math.round(size / 3), background: ink, color: green, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: size > 50 ? 16 : 13, fontWeight: 900, flexShrink: 0 }}>{job.logo}</div>;
  const EyeIcon = ({ size = 14 }) => <svg width={size} height={size} viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" style={{ flexShrink: 0 }}><path d="M1 8s2.5-5 7-5 7 5 7 5-2.5 5-7 5-7-5-7-5z"/><circle cx="8" cy="8" r="2"/></svg>;
  const BookmarkIcon = ({ filled }) => <svg width="16" height="16" viewBox="0 0 16 16" fill={filled ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round"><path d="M4 2.5h8v11L8 11l-4 2.5v-11z"/></svg>;
  const Pill = ({ children, miss, owned }) => <span style={{ fontSize: 12.5, fontWeight: 760, padding: '5px 11px', borderRadius: 8, background: miss ? soft : owned ? greenTint : soft, color: miss ? muted : owned ? greenInk : muted, border: '1px solid ' + (miss ? line : owned ? greenTintBd : line), whiteSpace: 'nowrap' }}>{owned ? '✓ ' : miss ? '+ ' : ''}{children}</span>;
  const TagPill = ({ children, muted: dull }) => { const tone = tagTone; const P = ({ '보라': { bg: '#efeaff', fg: '#5b3fd6', bd: '#dcd2fb' }, '라임': { bg: '#eef8cf', fg: '#3f5c08', bd: '#dbeca8' }, '검정': { bg: '#14151a', fg: '#ffffff', bd: '#14151a' } })[tone] || { bg: '#efeaff', fg: '#5b3fd6', bd: '#dcd2fb' }; return <span style={{ fontSize: 12.5, fontWeight: 720, padding: '5px 11px', borderRadius: 8, background: P.bg, color: P.fg, border: '1px solid ' + P.bd, whiteSpace: 'nowrap', opacity: dull ? 0.82 : 1 }}>#{children}</span>; };
  const Stat = ({ label, value, tone }) => <div style={{ background: tone === 'green' ? greenTint : tone === 'coral' ? coralTint : soft, border: '1px solid ' + (tone === 'green' ? greenTintBd : tone === 'coral' ? coralTintBd : line), borderRadius: 14, padding: '13px 14px' }}><b style={{ fontSize: 23, color: tone === 'coral' ? coralDeep : ink, ...num }}>{value}</b><div style={{ color: tone === 'green' ? greenInk : muted, fontSize: 11.5, fontWeight: 800, marginTop: 2 }}>{label}</div></div>;
  const Bar = ({ label, value }) => <div style={{ marginBottom: 12 }}><div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12.5, fontWeight: 700, color: 'rgba(255,255,255,0.65)', marginBottom: 5 }}><span>{label}</span><span style={{ color: '#fff', ...num }}>{value}%</span></div><div style={{ height: 9, background: 'rgba(255,255,255,0.12)', borderRadius: 5, overflow: 'hidden' }}><div style={{ width: value + '%', height: '100%', background: green, borderRadius: 5 }} /></div></div>;
  const btn = (label, active, onClick, primary, icon) => <button className="jf-cta" onClick={onClick} disabled={active} style={{ font: 'inherit', flex: 1, cursor: active ? 'default' : 'pointer', borderRadius: 12, padding: '13px', fontSize: 14.5, fontWeight: 760, border: primary ? 'none' : '1px solid ' + line, background: active ? soft : primary ? green : card, color: active ? muted : ink, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 7 }}>{icon}{label}</button>;
  const JDSection = ({ title, children }) => <div><div style={{ fontSize: 12.5, fontWeight: 850, marginBottom: 8, color: muted }}>{title}</div>{children}</div>;
  const MiniBar = ({ label, value, tone }) => <div style={{ marginTop: 11 }}><div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, fontSize: 12.5, fontWeight: 800, color: muted, marginBottom: 6 }}><span>{label}</span><span style={{ color: tone === 'coral' ? coralDeep : ink, ...num }}>{value}%</span></div><div style={{ height: 8, background: soft, borderRadius: 5, overflow: 'hidden' }}><div style={{ width: value + '%', height: '100%', background: tone === 'coral' ? '#f06b50' : green, borderRadius: 5 }} /></div></div>;
  const ApplicantSpecCard = () => <div style={{ background: card, border: '1px solid ' + line, borderRadius: 18, padding: 22, boxShadow: shadow }}>
    <div style={{ fontSize: 11, fontWeight: 900, color: greenInk, letterSpacing: 1 }}>APPLICANT SIGNAL</div>
    <div style={{ fontSize: 16, fontWeight: 800, margin: '8px 0 6px' }}>이 공고 지원자들 스펙</div>
    <div style={{ color: muted, fontSize: 12.5, lineHeight: 1.55 }}>JobFlow에서 이 공고를 저장·지원한 사용자 기준 상위 신호입니다.</div>
    <div style={{ borderTop: '1px solid ' + line, marginTop: 14, paddingTop: 12 }}>
      <div style={{ fontSize: 12.5, fontWeight: 850, color: muted, marginBottom: 8 }}>상위 스킬</div>
      <MiniBar label="Java" value={82} />
      <MiniBar label="Spring Boot" value={76} />
      <MiniBar label="JPA" value={63} />
      <MiniBar label="Kafka" value={41} tone="coral" />
    </div>
    <div style={{ borderTop: '1px solid ' + line, marginTop: 14, paddingTop: 12 }}>
      <div style={{ fontSize: 12.5, fontWeight: 850, color: muted, marginBottom: 8 }}>연차 분포</div>
      <MiniBar label="신입" value={48} />
      <MiniBar label="1~3년" value={36} />
      <MiniBar label="3년 이상" value={16} />
    </div>
    <div style={{ borderTop: '1px solid ' + line, marginTop: 14, paddingTop: 14 }}><div style={{ fontSize: 12.5, fontWeight: 850, color: muted, marginBottom: 9 }}>상위 경험 태그</div><div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>{tags.map((c, i) => <TagPill key={c} muted={i > 1}>{(JF.tagLabel && JF.tagLabel[c]) || c}</TagPill>)}</div></div>
  </div>;
  React.useEffect(() => {
    if (job.jobId) window.__jobflowApi?.viewJobById?.(job.jobId);
    else window.__jobflowApi?.viewJobByCompany?.(job.companyKo);
  }, [job.companyKo, job.jobId]);

  const bullets = {
    work: ['결제 승인 API와 정산 배치의 안정성을 개선합니다.', '주문·결제 이벤트를 비동기 처리하는 메시징 구조를 운영합니다.', '장애 상황에서도 거래 데이터가 보존되도록 모니터링과 재시도 정책을 설계합니다.'],
    required: ['Java/Spring Boot 기반 백엔드 서비스 개발 경험 또는 학습 경험', 'RDBMS를 활용한 API와 도메인 모델링 이해', '테스트 코드와 코드 리뷰를 통한 품질 개선 경험'],
    preferred: ['Redis, Kafka, AWS 등 운영 환경 기술 경험', '대용량 트래픽 또는 결제/커머스 도메인에 대한 관심', 'CI/CD 파이프라인이나 컨테이너 기반 배포 경험'],
    process: ['서류 검토', '직무 인터뷰', '과제 또는 코딩테스트', '최종 인터뷰'],
  };

  return (
    <div style={{ minHeight: '100vh', background: '#ffffff', fontFamily: font, color: ink }}>
      <div style={{ height: 64, borderBottom: '1px solid ' + line, background: 'rgba(255,255,255,0.82)', backdropFilter: 'blur(8px)', display: 'flex', alignItems: 'center', padding: narrow ? '0 18px' : '0 40px', gap: narrow ? 10 : 18, position: 'sticky', top: 0, zIndex: 20, overflow: 'hidden' }}>
        <div style={{ fontSize: narrow ? 20 : 22, fontWeight: 700, letterSpacing: -1, cursor: 'pointer', flexShrink: 0 }} onClick={() => go('home')}>jobflow<span style={{ color: green, WebkitTextStroke: '0.5px ' + greenInk }}>.</span></div>
        <div style={{ display: 'flex', gap: 4, whiteSpace: 'nowrap', flex: 1, minWidth: 0, overflowX: 'auto', scrollbarWidth: 'none' }}>{[['홈', 'home'], ['공고', 'jobs'], ['프로젝트', 'projects'], ['갭 분석', 'gap'], ['트렌드', 'trends']].map(([n, route]) => <button key={n} onClick={() => go(route)} style={{ font: 'inherit', cursor: 'pointer', border: 'none', borderRadius: 20, padding: narrow ? '7px 10px' : '7px 13px', fontSize: narrow ? 12.5 : 13.5, fontWeight: route === 'jobs' ? 800 : 500, background: route === 'jobs' ? green : 'transparent', color: route === 'jobs' ? ink : muted, whiteSpace: 'nowrap', flexShrink: 0 }}>{n}</button>)}</div>
        <span className="jf-link" style={{ marginLeft: 'auto', display: narrow ? 'none' : 'inline', fontSize: 13.5, fontWeight: 700, color: muted, cursor: 'pointer', whiteSpace: 'nowrap' }} onClick={() => go('jobs')}>← 공고 목록</span>
      </div>

      <div style={{ maxWidth: 1160, margin: '0 auto', padding: narrow ? '28px 18px 64px' : '36px 40px 64px' }}>
        <section style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1fr 360px', gap: 22, alignItems: 'start' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: 16 }}>
              <Logo />
              <div style={{ minWidth: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 9, flexWrap: 'wrap' }}><span style={{ fontSize: 18, fontWeight: 650 }}>{job.companyKo}</span><span style={{ color: muted, fontSize: 13.5 }}>{job.companyIntro}</span></div>
                <h1 style={{ fontSize: narrow ? 22 : (v3 ? 25 : 32), lineHeight: 1.16, margin: '10px 0 8px', letterSpacing: -0.7, fontWeight: v3 ? 600 : 660, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: '100%' }}>{job.fullTitle}</h1>
                <div style={{ color: muted, fontSize: 14.5 }}>{job.role} · {job.level} · {job.location}</div>
              </div>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginTop: 20, flexWrap: 'wrap' }}>
              <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, background: coralTint, border: '1px solid ' + coralTintBd, borderRadius: 12, padding: '9px 14px', whiteSpace: 'nowrap' }}><span style={{ fontSize: 11.5, fontWeight: 800, color: coralDeep }}>마감</span><b style={{ fontSize: 16, color: coralDeep, ...num }}>{job.deadline}</b></div>
              <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, background: soft, border: '1px solid ' + line, borderRadius: 12, padding: '9px 14px', whiteSpace: 'nowrap' }}><span style={{ fontSize: 11.5, fontWeight: 800, color: muted }}>경력</span><b style={{ fontSize: 16 }}>{job.level}</b></div>
              <span style={{ color: faint, fontSize: 12.5, fontWeight: 650, ...num, display: 'inline-flex', alignItems: 'center', gap: 5 }}><EyeIcon />{job.views}</span>
            </div>
            {login && <div style={{ display: 'flex', gap: 10, marginTop: 18, flexWrap: narrow ? 'wrap' : 'nowrap' }}>{btn(saved ? '저장됨' : '저장', saved, async () => { try { if (job.jobId) await window.__jobflowApi?.saveJobById?.(job.jobId); else await window.__jobflowApi?.saveJobByCompany?.(job.companyKo); setSaved(true); fire('저장한 공고에 추가했어요'); } catch (e) { fire(e.message || '저장에 실패했어요'); } }, false, <BookmarkIcon filled={saved} />)}{btn(hidden ? '숨김 처리됨' : '숨김', hidden, async () => { try { if (job.jobId) await window.__jobflowApi?.ignoreJobById?.(job.jobId); setHidden(true); fire('추천에서 숨겼어요'); } catch (e) { fire(e.message || '숨김 처리에 실패했어요'); } }, false)}{btn(applied ? '지원 완료 ✓' : '지원 기록 추가', applied, async () => { try { if (job.jobId) await window.__jobflowApi?.createApplicationByJobId?.(job.jobId); else await window.__jobflowApi?.createApplicationByCompany?.(job.companyKo); setApplied(true); fire('지원 기록을 만들었어요'); } catch (e) { fire(e.message || '지원 기록 생성에 실패했어요'); } }, true)}</div>}
            <div style={{ background: card, border: '1px solid ' + line, borderRadius: 18, padding: 24, boxShadow: shadow, marginTop: 16 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 9, marginBottom: 12, flexWrap: 'wrap' }}><div style={{ fontSize: 13, fontWeight: 900, letterSpacing: 0.5, color: faint, textTransform: 'uppercase' }}>공고 원문</div></div>
              <p style={{ fontSize: 15, lineHeight: 1.8, color: '#33363e', margin: '0 0 18px' }}>{job.desc}</p>
              <div style={{ borderTop: '1px solid ' + line, paddingTop: 16, display: 'grid', gap: 18 }}>
                <JDSection title="주요 업무"><ul style={{ margin: 0, paddingLeft: 19, color: '#33363e', lineHeight: 1.72, fontSize: 14.5 }}>{bullets.work.map((x) => <li key={x}>{x}</li>)}</ul></JDSection>
                {v3 && <JDSection title="자격 요건"><ul style={{ margin: 0, paddingLeft: 19, color: '#33363e', lineHeight: 1.72, fontSize: 14.5 }}>{bullets.required.map((x) => <li key={x}>{x}</li>)}</ul></JDSection>}
                {v3 && <JDSection title="우대 사항"><ul style={{ margin: 0, paddingLeft: 19, color: '#33363e', lineHeight: 1.72, fontSize: 14.5 }}>{bullets.preferred.map((x) => <li key={x}>{x}</li>)}</ul></JDSection>}
                <JDSection title="필수 스킬"><div style={{ display: 'flex', flexWrap: 'wrap', gap: 7 }}>{reqList.map((s) => <Pill key={s} owned={has(s)} miss={!has(s)}>{s}</Pill>)}</div></JDSection>
                <JDSection title="우대 스킬"><div style={{ display: 'flex', flexWrap: 'wrap', gap: 7 }}>{prefList.map((s) => <Pill key={s} owned={has(s)} miss={!has(s)}>{s}</Pill>)}</div></JDSection>
                <JDSection title="경험 태그"><div style={{ display: 'flex', flexWrap: 'wrap', gap: 7 }}>{tags.map((c, i) => <TagPill key={c} muted={i > 0}>{(JF.tagLabel && JF.tagLabel[c]) || c}</TagPill>)}</div></JDSection>
                {v3 && <JDSection title="채용 절차"><div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>{bullets.process.map((x, i) => <span key={x} style={{ background: soft, border: '1px solid ' + line, borderRadius: 12, padding: '7px 10px', fontSize: 12.5, fontWeight: 800 }}>{i + 1}. {x}</span>)}</div></JDSection>}
              </div>
            </div>
          </div>

          {login ? (
            <aside style={{ display: 'grid', gap: 14, position: narrow ? 'static' : 'sticky', top: 86 }}>
              <div style={{ background: ink, color: '#fff', borderRadius: 22, padding: 24, boxShadow: '0 14px 30px rgba(20,21,26,0.16)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}><span style={{ fontSize: 11, fontWeight: 900, letterSpacing: 1, color: green }}>MATCH REPORT</span><select style={{ font: 'inherit', border: '1px solid rgba(255,255,255,0.18)', background: 'rgba(255,255,255,0.08)', color: '#fff', borderRadius: 10, padding: '7px 9px', fontSize: 12, fontWeight: 800 }}><option>{currentProjectName} 기준</option></select></div>
                <div style={{ marginTop: 16, color: 'rgba(255,255,255,0.72)', fontSize: 13 }}>{currentProjectName} 프로젝트 기준 매칭률</div>
                <div style={{ display: 'flex', alignItems: 'baseline', gap: 5, margin: '2px 0 16px' }}><span style={{ fontSize: 62, fontWeight: 900, letterSpacing: -3, color: green, lineHeight: 1, ...num }}>{score}</span><span style={{ fontSize: 22, fontWeight: 800, color: 'rgba(255,255,255,0.72)' }}>%</span></div>
                <Bar label={`필수 스킬 충족 · ${reqMatched.length}/${reqList.length}`} value={requiredRate} />
                <Bar label={`우대 스킬 충족 · ${prefMatched.length}/${prefList.length}`} value={preferredRate} />
                <div style={{ color: 'rgba(255,255,255,0.5)', fontSize: 11.5, fontWeight: 700, marginTop: 2 }}>필수 70% + 우대 30% 가중치 기준</div>
                {v3 && <div style={{ borderTop: '1px solid rgba(255,255,255,0.12)', marginTop: 14, paddingTop: 14, color: 'rgba(255,255,255,0.76)', fontSize: 12.5, lineHeight: 1.6 }}><b style={{ color: green }}>왜 {score}%인가요?</b><br />{currentProjectName}에서 {currentProjectSkillSummary} 근거를 확인했고, 부족 스킬은 갭 분석에서 우선순위로 볼 수 있어요.</div>}
                <div style={{ borderTop: '1px solid rgba(255,255,255,0.12)', marginTop: 16, paddingTop: 15, display: 'grid', gap: 12 }}>
                  <div><div style={{ fontSize: 11.5, fontWeight: 800, color: green, marginBottom: 7 }}>이미 충족한 스킬</div><div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>{ownedSkills.length ? ownedSkills.map((s) => <span key={s} style={{ fontSize: 12, fontWeight: 800, padding: '4px 9px', borderRadius: 8, background: 'rgba(185,236,42,0.14)', color: green, border: '1px solid rgba(185,236,42,0.28)', whiteSpace: 'nowrap' }}>✓ {s}</span>) : <span style={{ color: 'rgba(255,255,255,0.5)', fontSize: 12.5 }}>없음</span>}</div></div>
                  <div><div style={{ fontSize: 11.5, fontWeight: 800, color: 'rgba(255,255,255,0.62)', marginBottom: 7 }}>보강하면 좋은 스킬</div><div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>{missingSkills.length ? missingSkills.map((s) => <span key={s} style={{ fontSize: 12, fontWeight: 800, padding: '4px 9px', borderRadius: 8, background: 'rgba(255,255,255,0.08)', color: 'rgba(255,255,255,0.62)', border: '1px solid rgba(255,255,255,0.14)', whiteSpace: 'nowrap' }}>+ {s}</span>) : <span style={{ color: green, fontSize: 12.5, fontWeight: 700 }}>모두 충족했어요</span>}</div></div>
                </div>
                <button onClick={() => go('gap')} style={{ font: 'inherit', cursor: 'pointer', width: '100%', marginTop: 16, border: 'none', background: green, color: ink, borderRadius: 12, padding: 12, fontWeight: 900 }}>부족 스킬 갭 분석 보기 →</button>
              </div>

              <ApplicantSpecCard />

              <div style={{ background: card, border: '1px solid ' + line, borderRadius: 18, padding: 22, boxShadow: shadow }}>
                <div style={{ fontSize: 16, fontWeight: 800, marginBottom: 10 }}>비슷한 추천 공고</div>
                {JF.matches.filter((x) => x.companyKo !== job.companyKo).map((r) => <div key={r.companyKo} onClick={() => go('detail', { company: r.companyKo })} style={{ display: 'flex', alignItems: 'center', gap: 11, padding: '11px 0', borderTop: '1px solid ' + line, cursor: 'pointer' }}><div style={{ width: 36, height: 36, borderRadius: 11, background: ink, color: green, display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 900, fontSize: 13, flexShrink: 0 }}>{r.logo}</div><div style={{ flex: 1, minWidth: 0 }}><span style={{ fontSize: 14, fontWeight: 650 }}>{r.companyKo}</span><div style={{ color: muted, fontSize: 12, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{r.title} · {r.level}</div></div><b style={{ color: greenInk, ...num }}>{r.score}%</b></div>)}
              </div>
            </aside>
          ) : (
            <aside style={{ display: 'grid', gap: 14, position: narrow ? 'static' : 'sticky', top: 86 }}>
              <div style={{ background: ink, color: '#fff', borderRadius: 22, padding: 24, boxShadow: '0 14px 30px rgba(20,21,26,0.16)' }}>
                <span style={{ fontSize: 11, fontWeight: 900, letterSpacing: 1, color: green }}>MATCH REPORT</span>
                <div style={{ fontSize: 19, fontWeight: 900, margin: '12px 0 8px', lineHeight: 1.3 }}>내 스킬과 이 공고의<br />매칭률이 궁금하다면</div>
                <p style={{ color: 'rgba(255,255,255,0.62)', fontSize: 13.5, lineHeight: 1.6, margin: '0 0 16px' }}>GitHub 레포를 연결하거나 스킬을 입력하면 필수·우대 스킬 충족률이 여기에 채워져요.</p>
                <button onClick={() => go('login')} style={{ font: 'inherit', cursor: 'pointer', width: '100%', border: 'none', background: green, color: ink, borderRadius: 12, padding: 12, fontWeight: 900 }}>GitHub 연결하고 매칭 보기</button>
              </div>

              <ApplicantSpecCard />

              <div style={{ background: card, border: '1px solid ' + line, borderRadius: 18, padding: 22, boxShadow: shadow }}>
                <div style={{ fontSize: 16, fontWeight: 800, marginBottom: 10 }}>비슷한 추천 공고</div>
                {JF.matches.filter((x) => x.companyKo !== job.companyKo).map((r) => <div key={r.companyKo} onClick={() => go('detail', { company: r.companyKo })} style={{ display: 'flex', alignItems: 'center', gap: 11, padding: '11px 0', borderTop: '1px solid ' + line, cursor: 'pointer' }}><div style={{ width: 36, height: 36, borderRadius: 11, background: ink, color: green, display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 900, fontSize: 13, flexShrink: 0 }}>{r.logo}</div><div style={{ flex: 1, minWidth: 0 }}><span style={{ fontSize: 14, fontWeight: 650 }}>{r.companyKo}</span><div style={{ color: muted, fontSize: 12, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{r.title} · {r.level}</div></div></div>)}
              </div>
            </aside>
          )}
        </section>
      </div>

      {toast && <div style={{ position: 'fixed', bottom: 28, left: '50%', transform: 'translateX(-50%)', background: ink, color: '#fff', padding: '12px 20px', borderRadius: 24, fontSize: 14, fontWeight: 700, boxShadow: '0 8px 28px rgba(0,0,0,0.25)', zIndex: 200, display: 'flex', alignItems: 'center', gap: 9 }}><span style={{ width: 7, height: 7, borderRadius: 4, background: green }} />{toast}</div>}
    </div>
  );
}
