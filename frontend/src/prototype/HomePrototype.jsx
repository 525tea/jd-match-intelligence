import React from 'react';

// JobFlow Home — large job inventory + repository-based technical matching.
// Lime = matching/owned, charcoal = insight surface, coral = urgency/missing.
export function JobFlowHome({ t, go }) {
  const JF = window.JF;
  const [toast, setToast] = React.useState(null);
  const [matchPage, setMatchPage] = React.useState(0);
  const [popularPage, setPopularPage] = React.useState(0);
  const [closingPage, setClosingPage] = React.useState(0);
  const fire = (label) => { setToast(label); clearTimeout(window.__jfT); window.__jfT = setTimeout(() => setToast(null), 1900); };
  const openJob = (job) => go('detail', { jobId: job.jobId || job.id, company: job.companyKo });

  const ink = '#14151a', muted = '#5b616e', faint = '#9aa1ad';
  const page = '#ffffff', card = '#ffffff', line = '#e7eaf0', soft = '#f1f3f7';
  const green = t.green || '#b9ec2a';
  const greenInk = '#3f5c08', greenTint = '#eef8cf', greenTintBd = '#dbeca8';
  const coralDeep = '#c2391f', coralTint = '#ffe8e1', coralTintBd = '#f6c9bc';
  const v2 = t.ver === 'v2';
  const v3 = t.ver === 'v3';
  const tagTone = '보라';
  const scoreColor = (s) => ink;
  const login = t.state !== '비로그인';
  const font = "'Space Grotesk', 'Pretendard', 'Apple SD Gothic Neo', system-ui, sans-serif";
  const shadow = '0 1px 2px rgba(20,21,26,0.04), 0 8px 22px rgba(20,21,26,0.06)';
  const narrow = window.innerWidth < 980;
  const num = { fontFeatureSettings: '"tnum"', fontVariantNumeric: 'tabular-nums' };

  const nav = [['홈', 'home'], ['공고', 'jobs'], ['프로젝트', 'projects'], ['갭 분석', 'gap'], ['트렌드', 'trends']];
  const primaryProject = JF.projectList?.[0] || {};
  const primaryProjectName = primaryProject.name || '내 프로젝트';
  const primaryProjectSummary = primaryProject.summary || '최근 분석된 프로젝트';
  const allMatchJobs = JF.matches.concat(JF.listings.filter((j) => !JF.matches.some((m) => m.companyKo === j.companyKo)).slice(0, 6));
  const popularJobs = JF.popular.concat(JF.listings.slice(0, 4));
  const deadlineOrder = (deadline) => {
    const text = String(deadline || '');
    if (text.includes('오늘')) return 0;
    const number = Number(text.replace(/[^\d]/g, ''));
    return Number.isFinite(number) && number > 0 ? number : 999;
  };
  const closingJobs = JF.listings.slice().sort((a, b) => deadlineOrder(a.deadline) - deadlineOrder(b.deadline));
  const topMatch = JF.matches?.[0];
  const topGap = JF.gapSkills?.[0];
  const topClosing = closingJobs?.[0];
  const topGapName = topGap?.skill || topGap?.name;
  const topGapCount = topGap?.addedJobs ?? topGap?.count ?? 0;
  const heroStats = [
    ['추천 1순위', topMatch ? `${topMatch.companyKo} ${topMatch.score}%` : '추천 대기', topMatch ? `필수 ${topMatch.required ?? 0}% 충족` : '프로젝트 분석 후 표시'],
    ['부족하면 열림', topGapName ? `${topGapName} +${topGapCount}` : '갭 분석 대기', topGapName ? '관련 공고 증가' : '분석 결과 없음'],
    ['마감 임박', topClosing?.deadline || '마감 정보 없음', topClosing ? '오늘 먼저 볼 공고' : '실시간 공고 기준 표시'],
  ];

  const pageSlice = (items, page, size) => items.slice(page * size, page * size + size);
  const nextPage = (setter, items, page, size) => setter((page + 1) * size >= items.length ? 0 : page + 1);
  const prevPage = (setter, items, page, size) => setter(page === 0 ? Math.max(0, Math.ceil(items.length / size) - 1) : page - 1);

  const Logo = ({ text, dark }) => <div style={{ width: 44, height: 44, borderRadius: 14, background: dark ? 'rgba(255,255,255,0.12)' : ink, color: dark ? green : '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 13, fontWeight: 900, flexShrink: 0 }}>{text || 'JF'}</div>;
  const GithubMark = ({ size = 20 }) => <svg width={size} height={size} viewBox="0 0 16 16" fill="currentColor"><path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38v-1.34c-2.23.49-2.7-1.07-2.7-1.07-.36-.93-.89-1.18-.89-1.18-.73-.5.05-.49.05-.49.8.06 1.23.83 1.23.83.72 1.23 1.88.87 2.34.67.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.96 0-.87.31-1.59.83-2.15-.08-.2-.36-1.02.08-2.13 0 0 .67-.21 2.2.82a7.6 7.6 0 0 1 4 0c1.53-1.03 2.2-.82 2.2-.82.44 1.11.16 1.93.08 2.13.52.56.82 1.28.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48v2.2c0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8z"/></svg>;
  const Chip = ({ children, missing, dark }) => <span style={{ fontSize: 12, fontWeight: 780, padding: '4px 9px', borderRadius: 8, background: missing ? (dark ? 'rgba(255,255,255,0.08)' : soft) : dark ? 'rgba(185,236,42,0.14)' : greenTint, color: missing ? (dark ? 'rgba(255,255,255,0.56)' : muted) : dark ? green : greenInk, border: '1px solid ' + (missing ? (dark ? 'rgba(255,255,255,0.12)' : line) : dark ? 'rgba(185,236,42,0.3)' : greenTintBd), whiteSpace: 'nowrap' }}>{missing ? '' : '✓ '}{children}</span>;
  const Tag = ({ children, muted: dull, dark }) => { const tone = tagTone; const P = ({ '보라': { bg: '#efeaff', fg: '#5b3fd6', bd: '#dcd2fb', dbg: 'rgba(123,97,255,0.2)', dfg: '#d1c5ff', dbd: 'rgba(123,97,255,0.42)' }, '라임': { bg: '#eef8cf', fg: '#3f5c08', bd: '#dbeca8', dbg: 'rgba(185,236,42,0.16)', dfg: green, dbd: 'rgba(185,236,42,0.34)' }, '검정': { bg: '#14151a', fg: '#ffffff', bd: '#14151a', dbg: '#ffffff', dfg: '#14151a', dbd: '#ffffff' } })[tone] || { bg: '#efeaff', fg: '#5b3fd6', bd: '#dcd2fb', dbg: 'rgba(123,97,255,0.2)', dfg: '#d1c5ff', dbd: 'rgba(123,97,255,0.42)' }; return <span style={{ display: 'inline-flex', alignItems: 'center', lineHeight: 1.1, fontSize: 11.5, fontWeight: 760, padding: '4px 9px', borderRadius: 8, background: dark ? P.dbg : P.bg, color: dark ? P.dfg : P.fg, border: '1px solid ' + (dark ? P.dbd : P.bd), whiteSpace: 'nowrap', opacity: dull ? 0.82 : 1 }}>#{children}</span>; };
  const MoreChip = ({ count, dark }) => <span style={{ fontSize: 12, fontWeight: 900, padding: '4px 9px', borderRadius: 8, background: dark ? 'rgba(255,255,255,0.08)' : soft, color: dark ? 'rgba(255,255,255,0.68)' : muted, border: '1px solid ' + (dark ? 'rgba(255,255,255,0.14)' : line), whiteSpace: 'nowrap' }}>+{count}</span>;
  const Eye = ({ c }) => <svg width="13" height="13" viewBox="0 0 16 16" fill="none" stroke={c || 'currentColor'} strokeWidth="1.5" style={{ flexShrink: 0, marginRight: 3, verticalAlign: '-2px' }}><path d="M1 8s2.5-5 7-5 7 5 7 5-2.5 5-7 5-7-5-7-5z"/><circle cx="8" cy="8" r="2"/></svg>;
  const ArrowButton = ({ children, onClick, dark }) => <button onClick={onClick} style={{ font: 'inherit', cursor: 'pointer', width: 34, height: 34, borderRadius: 17, border: '1px solid ' + (dark ? 'rgba(255,255,255,0.16)' : line), background: dark ? 'rgba(255,255,255,0.08)' : '#fff', color: dark ? '#fff' : ink, fontWeight: 900 }}>{children}</button>;
  const FloatingStat = ({ label, value, desc }) => <div style={{ background: 'rgba(255,255,255,0.92)', border: '1px solid ' + line, borderRadius: 17, padding: '13px 15px', boxShadow: '0 14px 34px rgba(20,21,26,0.12)', minWidth: 138 }}><b style={{ fontSize: 23, letterSpacing: -0.8, ...num }}>{value}</b><div style={{ fontSize: 12, color: ink, fontWeight: 800, marginTop: 1 }}>{label}</div><div style={{ fontSize: 11.5, color: faint, marginTop: 3, whiteSpace: 'nowrap' }}>{desc}</div></div>;

  const JobCard = ({ job, featured, darkSection, urgent }) => {
    const score = job.score || (JF.matches.find((m) => m.companyKo === job.companyKo) || {}).score || 0;
    const tags = (job.tags || []).map((c) => JF.tagLabel[c] || c);
    const ownedSkills = job.matched || job.skills || [];
    const missingSkills = job.missing || [];
    const darkCard = darkSection && !featured;
    const visibleSkills = missingSkills.length
      ? ownedSkills.slice(0, 3).map((skill) => ({ skill })).concat([{ skill: missingSkills[0], missing: true }])
      : ownedSkills.slice(0, 3).map((skill) => ({ skill }));
    const hiddenSkills = missingSkills.length > 1 ? missingSkills.length - 1 : (!missingSkills.length && ownedSkills.length > 3 ? ownedSkills.length - 3 : 0);
    const maxTags = 2;
    const visibleTags = tags.length > maxTags ? tags.slice(0, maxTags - 1) : tags.slice(0, maxTags);
    const hiddenTags = Math.max(0, tags.length - visibleTags.length);
    return (
      <div className="jf-jobcard" onClick={() => openJob(job)} style={{ cursor: 'pointer', background: v2 && featured ? green : darkCard ? '#171a20' : card, color: darkCard ? '#fff' : ink, border: '1px solid ' + (featured ? (v3 ? greenTintBd : green) : urgent ? coralTintBd : darkCard ? 'rgba(255,255,255,0.1)' : '#edf0f3'), borderRadius: 18, padding: v3 ? 18 : 20, display: 'flex', flexDirection: 'column', minHeight: 252, boxShadow: urgent ? '0 3px 16px rgba(240,96,63,0.13)' : featured && !v2 ? (v3 ? '0 8px 22px rgba(20,21,26,0.055)' : '0 0 0 2px rgba(185,236,42,0.16), 0 8px 24px rgba(20,21,26,0.06)') : shadow }}>
        <div style={{ display: 'flex', gap: 13, alignItems: 'flex-start' }}>
          <Logo text={job.logo || job.companyKo.slice(0, 2)} />
          <div style={{ minWidth: 0, flex: 1 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}><span style={{ fontSize: 13, fontWeight: v3 ? 560 : 600, color: featured ? ink : darkCard ? 'rgba(255,255,255,0.78)' : ink }}>{job.companyKo}</span><span style={{ marginLeft: 'auto', color: coralDeep, background: coralTint, border: '1px solid ' + coralTintBd, borderRadius: 12, padding: '3px 8px', fontSize: 11.5, fontWeight: 900 }}>{job.deadline}</span></div>
            <div style={{ fontSize: 17, lineHeight: 1.34, fontWeight: 760, letterSpacing: -0.45, marginTop: 6, minHeight: 46, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', wordBreak: 'keep-all' }}>{job.fullTitle || job.title || '공고명 없음'}</div>
            <div style={{ fontSize: 12.5, color: featured ? 'rgba(20,21,26,0.58)' : darkCard ? 'rgba(255,255,255,0.62)' : muted, marginTop: 7, height: 18, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{job.role || job.title} · {job.level}</div>
          </div>
        </div>
        <div style={{ marginTop: 15, display: 'flex', flexWrap: 'nowrap', gap: 6, alignItems: 'center', height: 26, overflow: 'hidden' }}>
          {visibleSkills.map(({ skill, missing }) => <Chip key={skill} missing={missing} dark={darkCard}>{skill}</Chip>)}{hiddenSkills > 0 && <MoreChip count={hiddenSkills} dark={darkCard} />}
        </div>
        <div style={{ display: 'flex', flexWrap: 'nowrap', gap: 6, marginTop: 10, height: 25, overflow: 'hidden' }}>{visibleTags.map((tag, idx) => <Tag key={tag} muted={idx > 0 && missingSkills.length > 0} dark={darkCard}>{tag}</Tag>)}{hiddenTags > 0 && <MoreChip count={hiddenTags} dark={darkCard} />}</div>
        <div style={{ marginTop: 'auto', paddingTop: 16, display: 'flex', alignItems: 'flex-end', gap: 14 }}>
          {login ? (v2 ? <div><div style={{ display: 'inline-flex', alignItems: 'baseline', gap: 2, background: featured ? 'rgba(20,21,26,0.12)' : greenTint, border: '1px solid ' + (featured ? 'transparent' : greenTintBd), borderRadius: 10, padding: '4px 10px' }}><b style={{ fontSize: 26, letterSpacing: -1, lineHeight: 1, color: ink, ...num }}>{score}</b><span style={{ color: featured ? 'rgba(20,21,26,0.66)' : greenInk, fontWeight: 900, fontSize: 14 }}>%</span></div><div style={{ fontSize: 11, fontWeight: 800, color: featured ? 'rgba(20,21,26,0.6)' : '#9aa1ad', marginTop: 5 }}>레포 매칭</div></div> : <div><div style={{ display: 'flex', alignItems: 'baseline', gap: 3 }}><b style={{ fontSize: 32, letterSpacing: -1.5, lineHeight: 1, color: ink, ...num }}>{score}</b><span style={{ color: featured ? 'rgba(20,21,26,0.7)' : faint, fontWeight: 900 }}>%</span></div><div style={{ fontSize: 11, fontWeight: 800, color: '#9aa1ad' }}>레포 매칭</div></div>) : <div style={{ display: 'inline-flex', alignItems: 'center', gap: 6, color: darkCard ? 'rgba(255,255,255,0.66)' : muted, background: darkCard ? 'rgba(255,255,255,0.08)' : soft, border: '1px solid ' + (darkCard ? 'rgba(255,255,255,0.14)' : line), borderRadius: 10, padding: '6px 10px', fontSize: 11.5, fontWeight: 800 }}><svg width="12" height="12" viewBox="0 0 14 14" fill="none" stroke="currentColor" strokeWidth="1.6"><rect x="2.5" y="6" width="9" height="6.5" rx="1.2"/><path d="M4.5 6V4.3a2.5 2.5 0 0 1 5 0V6"/></svg>로그인하고 매칭률 확인</div>}
          <div style={{ marginLeft: 'auto', display: narrow ? 'none' : 'flex', alignItems: 'center', gap: 8, textAlign: 'right', color: featured ? 'rgba(20,21,26,0.55)' : darkCard ? 'rgba(255,255,255,0.5)' : faint, fontSize: 11.5, fontWeight: 700, whiteSpace: 'nowrap' }}><span style={{ display: 'inline-flex', alignItems: 'center' }}><Eye />{job.views || 0}</span></div>
        </div>
      </div>
    );
  };

  const CarouselSection = ({ title, sub, items, page, setPage, size = 3, dark, hero, type, urgent }) => {
    const shown = pageSlice(items, page, size);
    return (
      <section style={{ background: dark ? ink : 'transparent', color: dark ? '#fff' : ink, borderRadius: dark ? 24 : 0, padding: dark ? (narrow ? 22 : 26) : 0, boxShadow: dark ? '0 14px 34px rgba(20,21,26,0.18)' : 'none' }}>
        <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 14, marginBottom: 14 }}>
          <div><div style={{ fontSize: narrow ? 20 : 22, fontWeight: 900, letterSpacing: -0.5 }}>{title}</div>{sub && <div style={{ fontSize: 13.5, color: dark ? 'rgba(255,255,255,0.62)' : muted, marginTop: 4 }}>{sub}</div>}</div>
          <div style={{ display: 'flex', gap: 7 }}><ArrowButton dark={dark} onClick={() => prevPage(setPage, items, page, size)}>‹</ArrowButton><ArrowButton dark={dark} onClick={() => nextPage(setPage, items, page, size)}>›</ArrowButton></div>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : `repeat(${size}, minmax(0, 1fr))`, gap: 14 }}>{shown.map((job, i) => type === 'compact' ? <JobCard key={job.companyKo + i} job={job} urgent={urgent} /> : <JobCard key={job.companyKo + i} job={job} featured={(dark || hero) && i === 0} darkSection={dark} />)}</div>
      </section>
    );
  };

  return (
    <div style={{ minHeight: '100vh', background: page, fontFamily: font, color: ink, paddingBottom: 0 }}>
      <div style={{ height: 64, borderBottom: '1px solid ' + line, background: 'rgba(255,255,255,0.84)', backdropFilter: 'blur(8px)', display: 'flex', alignItems: 'center', padding: narrow ? '0 18px' : '0 40px', gap: narrow ? 10 : 32, position: 'sticky', top: 0, zIndex: 20, overflow: 'hidden' }}>
        <div style={{ fontSize: narrow ? 20 : 22, fontWeight: 900, letterSpacing: -1, cursor: 'pointer', flexShrink: 0 }} onClick={() => go('home')}>jobflow<span style={{ color: green, WebkitTextStroke: '0.5px ' + greenInk }}>.</span></div>
        <div style={{ display: 'flex', gap: 4, whiteSpace: 'nowrap', flex: 1, minWidth: 0, overflowX: 'auto', scrollbarWidth: 'none' }}>{nav.map(([n, route]) => <span key={route} onClick={() => go(route)} className="jf-navitem" style={{ fontSize: narrow ? 12.5 : 14, fontWeight: route === 'home' ? 900 : 600, background: route === 'home' ? green : 'transparent', color: route === 'home' ? ink : muted, padding: narrow ? '7px 10px' : '7px 14px', borderRadius: 20, cursor: 'pointer', flexShrink: 0 }}>{n}</span>)}</div>
        <div style={{ marginLeft: 'auto', display: narrow ? 'none' : 'flex', alignItems: 'center', gap: 12, flexShrink: 0 }}>{login ? <><span style={{ display: 'flex', alignItems: 'center', gap: 7, fontSize: 12.5, fontWeight: 800, color: greenInk, background: greenTint, border: '1px solid ' + greenTintBd, padding: '6px 12px', borderRadius: 20 }}><span style={{ width: 7, height: 7, borderRadius: 4, background: greenInk }} />GitHub 연동됨</span><div style={{ width: 36, height: 36, borderRadius: 18, background: ink, color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 14, fontWeight: 900, cursor: 'pointer' }} onClick={() => go('mypage')}>사</div></> : <><span onClick={() => go('login')} style={{ fontSize: 14, fontWeight: 700, cursor: 'pointer' }}>로그인</span><span onClick={() => go('login')} style={{ fontSize: 14, fontWeight: 900, background: ink, color: '#fff', padding: '9px 18px', borderRadius: 22, cursor: 'pointer' }}>회원가입</span></>}</div>
      </div>

      <main style={{ maxWidth: 1520, margin: '0 auto', padding: narrow ? '28px 18px 54px' : '36px 48px 64px' }}>
        <section style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : (v3 ? '0.92fr 1.08fr' : '1.05fr 0.82fr'), gap: 18, alignItems: 'stretch', marginBottom: 34 }}>
          <div style={{ background: green, borderRadius: 28, padding: narrow ? 26 : (v3 ? 28 : 34), minHeight: v3 ? 220 : 254, display: 'flex', flexDirection: 'column' }}>
            {login ? <>
            <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, alignSelf: 'flex-start', background: 'rgba(255,255,255,0.55)', border: '1px solid rgba(255,255,255,0.55)', borderRadius: 20, padding: '7px 11px', fontSize: 12, fontWeight: 900 }}>매칭률 높은 공고 {JF.matches.length}개</div>
            <h1 style={{ fontSize: narrow ? 30 : (v3 ? 34 : 38), lineHeight: 1.08, letterSpacing: -1.6, margin: v3 ? '18px 0 10px' : '24px 0 12px', maxWidth: 620 }}>{v3 ? <>매칭률 높은 공고 {JF.matches.length}개를 발견했어요.</> : <>{JF.user.name}님, 매칭률 높은<br />공고 {JF.matches.length}개를 발견했어요.</>}</h1>
            <p style={{ color: 'rgba(20,21,26,0.65)', fontSize: 15.5, lineHeight: 1.6, maxWidth: 560 }}><b>{primaryProjectName}</b> 분석 결과와 {JF.market.totalCount.toLocaleString()}개 통합 공고를 비교해, 오늘 먼저 볼 후보를 추렸어요.</p>
            <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : 'repeat(3, 1fr)', gap: 8, margin: '12px 0 18px' }}>{heroStats.map(([a,b,c]) => <div key={a} style={{ background: 'rgba(255,255,255,0.36)', border: '1px solid rgba(255,255,255,0.46)', borderRadius: 15, padding: '10px 12px' }}><div style={{ fontSize: 11, fontWeight: 900, color: 'rgba(20,21,26,0.56)' }}>{a}</div><div style={{ fontSize: 15, fontWeight: 850, marginTop: 3 }}>{b}</div><div style={{ fontSize: 11.5, color: 'rgba(20,21,26,0.58)', marginTop: 2 }}>{c}</div></div>)}</div>
            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginTop: 'auto' }}><button onClick={() => go('jobs')} style={{ font: 'inherit', cursor: 'pointer', border: 'none', background: ink, color: '#fff', borderRadius: 24, padding: '12px 18px', fontWeight: 900 }}>추천 공고 보러가기</button><button onClick={() => go('projects')} style={{ font: 'inherit', cursor: 'pointer', border: '1px solid rgba(20,21,26,0.18)', background: '#fff', color: ink, borderRadius: 24, padding: '12px 18px', fontWeight: 900 }}>스택 추출하기</button></div>
            </> : <>
            <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, alignSelf: 'flex-start', background: 'rgba(255,255,255,0.55)', border: '1px solid rgba(255,255,255,0.55)', borderRadius: 20, padding: '7px 11px', fontSize: 12, fontWeight: 900 }}>실시간 수집 공고 {JF.market.totalCount.toLocaleString()}개</div>
            <h1 style={{ fontSize: narrow ? 34 : 50, lineHeight: 1.04, letterSpacing: -2, margin: '38px 0 14px', maxWidth: 760 }}>대량 공고 중 내 레포와<br />맞는 공고만 <span style={{ background: ink, color: green, borderRadius: 12, padding: '0 14px', display: 'inline-block' }}>골라서.</span></h1>
            <p style={{ color: 'rgba(20,21,26,0.65)', fontSize: 16, lineHeight: 1.65, maxWidth: 620 }}>지금 <b>{JF.market.totalCount.toLocaleString()}개</b> 공고를 모아 기술 스택·경험 태그·경력으로 좁힌 뒤, GitHub 레포 기준 매칭률로 다시 정렬해드려요.</p>
            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginTop: 'auto' }}><button onClick={() => go('jobs')} style={{ font: 'inherit', cursor: 'pointer', border: 'none', background: ink, color: '#fff', borderRadius: 24, padding: '13px 20px', fontWeight: 900 }}>전체 공고 탐색</button><button onClick={() => go('login')} style={{ font: 'inherit', cursor: 'pointer', border: '1px solid rgba(20,21,26,0.18)', background: '#fff', color: ink, borderRadius: 24, padding: '13px 20px', fontWeight: 900 }}>시작하기</button></div>
            </>}
          </div>
          <div style={{ display: 'flex' }}>
            <div style={{ background: ink, color: '#fff', borderRadius: 28, padding: v3 ? 24 : 28, display: 'flex', flexDirection: 'column', boxShadow: v3 ? '0 10px 28px rgba(20,21,26,0.14)' : '0 18px 44px rgba(20,21,26,0.2)', flex: 1, minHeight: v3 ? 220 : 254, boxSizing: 'border-box' }}>
            <span style={{ fontSize: 11, fontWeight: 900, letterSpacing: 1, color: green }}>레포 매칭</span>
            {login ? <>
            <div style={{ marginTop: v3 ? 18 : 24, display: 'flex', alignItems: 'center', gap: 12 }}><div style={{ width: 44, height: 44, borderRadius: 14, background: 'rgba(255,255,255,0.12)', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><GithubMark /></div><div><b>{primaryProjectName}</b><div style={{ color: 'rgba(255,255,255,0.58)', fontSize: 13 }}>{primaryProjectSummary} · 매칭 후보 {JF.matches.length}개</div></div></div>
            <div style={{ marginTop: v3 ? 18 : 26, display: 'grid', gridTemplateColumns: v3 && !narrow ? '1fr 1fr' : '1fr', gap: v3 ? 14 : 18 }}>
              <div><div style={{ color: 'rgba(255,255,255,0.45)', fontSize: 11, fontWeight: 900, letterSpacing: 0.6, marginBottom: 9 }}>추출 스킬</div><div style={{ display: 'flex', flexWrap: 'wrap', gap: 7 }}>{JF.skills.slice(0, 7).map((skill) => <Chip key={skill.name} dark>{skill.name}</Chip>)}</div></div>
              <div><div style={{ color: 'rgba(255,255,255,0.45)', fontSize: 11, fontWeight: 900, letterSpacing: 0.6, marginBottom: 9 }}>경험 태그</div><div style={{ display: 'grid', gap: 7, alignItems: 'start' }}>{JF.expTags.slice(0, 4).map((tag, idx) => <div key={tag.code} style={{ lineHeight: 1.1 }}><Tag muted={idx > 2} dark>{tag.label}</Tag></div>)}</div></div>
            </div>
            <button onClick={() => go('project-analysis')} style={{ marginTop: 'auto', font: 'inherit', cursor: 'pointer', border: '1px solid rgba(255,255,255,0.16)', background: 'rgba(255,255,255,0.08)', color: '#fff', borderRadius: 16, padding: 14, fontWeight: 900 }}>분석 근거 보기 →</button>
            </> : <>
            <div style={{ marginTop: 22, fontSize: 22, fontWeight: 900, lineHeight: 1.32, letterSpacing: -0.6 }}>GitHub 레포를 연결하면<br /><span style={{ color: green }}>공고마다 내 매칭률</span>이 채워져요.</div>
            <div style={{ marginTop: 20, display: 'grid', gap: 11 }}>{[['1', '레포 연결 또는 스킬 입력'], ['2', '코드에서 스킬·경험 자동 추출'], ['3', '공고와 매칭률로 정렬']].map(([n, txt]) => <div key={n} style={{ display: 'flex', alignItems: 'center', gap: 11 }}><span style={{ width: 26, height: 26, borderRadius: 13, background: 'rgba(185,236,42,0.16)', color: green, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 13, fontWeight: 900, flexShrink: 0 }}>{n}</span><span style={{ fontSize: 14, fontWeight: 700, color: 'rgba(255,255,255,0.85)' }}>{txt}</span></div>)}</div>
            <div style={{ marginTop: 'auto', display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1fr 1fr', gap: 9 }}><button onClick={() => go('login')} style={{ font: 'inherit', cursor: 'pointer', border: 'none', background: green, color: ink, borderRadius: 16, padding: 14, fontWeight: 900, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}><GithubMark size={16} />GitHub 연결하기</button><button onClick={() => go('onboarding')} style={{ font: 'inherit', cursor: 'pointer', border: '1px solid rgba(255,255,255,0.16)', background: 'rgba(255,255,255,0.08)', color: '#fff', borderRadius: 16, padding: 14, fontWeight: 900 }}>스킬 입력하기</button></div>
            </>}
            </div>
          </div>
        </section>

        <div style={{ display: 'grid', gap: 34, marginTop: 0 }}>
          <CarouselSection title="매칭된 공고" items={allMatchJobs} page={matchPage} setPage={setMatchPage} size={narrow ? 1 : 3} hero />
          <CarouselSection title="인기 공고" items={popularJobs} page={popularPage} setPage={setPopularPage} size={narrow ? 1 : 3} type="compact" />
          <CarouselSection title="마감 임박 공고" items={closingJobs} page={closingPage} setPage={setClosingPage} size={narrow ? 1 : 3} type="compact" urgent />
        </div>
      </main>

      {toast && <div style={{ position: 'fixed', bottom: 28, left: '50%', transform: 'translateX(-50%)', background: ink, color: '#fff', padding: '12px 20px', borderRadius: 24, fontSize: 14, fontWeight: 700, boxShadow: '0 8px 28px rgba(0,0,0,0.25)', zIndex: 200 }}><span style={{ color: green }}>● </span>{toast}</div>}
    </div>
  );
}
