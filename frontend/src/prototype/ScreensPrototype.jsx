import React from 'react';

// JobFlow additional screens — final IA: home · jobs · projects · gap · trends.
// Palette: lime = matching/owned, charcoal = analysis surface, coral = urgent/missing, gray = structure.
export function JobFlowScreens({ t, go, screen }) {
  const JF = window.JF;
  const isV3 = t.ver === 'v3' && ['gap', 'trends'].includes(screen);
  const [sort, setSort] = React.useState('매칭순');
  const [query, setQuery] = React.useState('');
  const [filterOpen, setFilterOpen] = React.useState(false);
  const [activeFilter, setActiveFilter] = React.useState({ role: '전체', career: '전체', skill: '전체' });
  const [previewProject, setPreviewProject] = React.useState(() => window.JF?.projectList?.[0]?.name || '내 프로젝트');
  const [previewOpen, setPreviewOpen] = React.useState(false);
  const [editingProject, setEditingProject] = React.useState(null);
  const [addingApplication, setAddingApplication] = React.useState(false);
  const [settingsModal, setSettingsModal] = React.useState(null);
  const [screenNotice, setScreenNotice] = React.useState(null);
  const [trendSkill, setTrendSkill] = React.useState('Kafka');
  const [openGroups, setOpenGroups] = React.useState(isV3 ? { role: true, career: true, skill: false, employment: false, remote: false, region: false, tag: false, deadline: false } : { role: true, career: true, skill: true, employment: false, remote: false, region: false, tag: false, deadline: false });
  const toggleGroup = (id) => setOpenGroups((p) => ({ ...p, [id]: !p[id] }));
  const notify = (message) => {
    setScreenNotice(message);
    clearTimeout(window.__jfScreenNoticeT);
    window.__jfScreenNoticeT = setTimeout(() => setScreenNotice(null), 2200);
  };
  const ink = '#14151a', muted = '#5b616e', faint = '#9aa1ad';
  const card = '#ffffff', line = '#e7eaf0', soft = '#f1f3f7';
  const green = t.green || '#b9ec2a';
  const greenStrong = green, greenInk = '#3f5c08', greenTint = '#eef8cf', greenTintBd = '#dbeca8';
  const coral = '#f0603f', coralDeep = '#c2391f', coralTint = '#ffe8e1', coralTintBd = '#f6c9bc';
  const font = "'Space Grotesk', 'Pretendard', 'Apple SD Gothic Neo', system-ui, sans-serif";
  const shadow = '0 1px 2px rgba(20,21,26,0.04), 0 8px 22px rgba(20,21,26,0.06)';
  const login = t.state !== '비로그인';
  const v2 = t.ver === 'v2';
  const tagTone = '보라';
  const num = { fontVariantNumeric: 'tabular-nums' };
  const narrow = window.innerWidth < 980;
  const tile = { background: card, border: '1px solid ' + line, borderRadius: 18, padding: 22, boxShadow: shadow };
  const navItems = [['home', '홈'], ['jobs', '공고'], ['projects', '프로젝트'], ['gap', '갭 분석'], ['trends', '트렌드']];
  const navOn = (key) => screen === key || (key === 'projects' && ['project-analysis', 'project-new'].includes(screen));
  const labelMap = {
    BACKEND: '백엔드', FRONTEND: '프론트엔드', FULLSTACK: '풀스택', ANDROID: '안드로이드', IOS: 'iOS',
    DEVOPS: 'DevOps', SRE: 'SRE', DBA: 'DBA', SECURITY: '보안', DATA_ENGINEER: '데이터 엔지니어',
    ML_ENGINEER: 'ML 엔지니어', AI_ENGINEER: 'AI 엔지니어', QA: 'QA', PM: 'PM',
    CROSS_PLATFORM: '크로스플랫폼', SYSTEM_NETWORK: '시스템/네트워크', SYSTEM_SOFTWARE: '시스템 SW',
    SOFTWARE_ENGINEER: '소프트웨어 엔지니어', EMBEDDED_SOFTWARE: '임베디드', ROBOT_SOFTWARE: '로봇 SW',
    IOT: 'IoT', APPLICATION_SOFTWARE: '응용 SW', BLOCKCHAIN: '블록체인', WEB_PUBLISHING: '웹 퍼블리싱',
    VR_AR_3D: 'VR/AR/3D', ERP_SAP: 'ERP/SAP', GRAPHICS: '그래픽스', HARDWARE_ENGINEER: '하드웨어',
    IT_ETC: 'IT 기타', DATA_ANALYST: '데이터 분석가', DATA_SCIENTIST: '데이터 사이언티스트',
    MULTIMODAL_ENGINEER: '멀티모달', GENERATIVE_AI: '생성형 AI', VISION_AUDIO_AI: '비전/오디오 AI',
    AUTONOMOUS_DRIVING: '자율주행', COMPUTER_VISION: '컴퓨터 비전', AI_BUSINESS: 'AI 비즈니스',
    AI_SERVICE_PLANNING: 'AI 서비스 기획', AI_RESEARCHER: 'AI 리서처', NLP: 'NLP', LLM: 'LLM',
    MLOPS: 'MLOps', RAG: 'RAG', GAME_PM: '게임 PM', GAME_OPERATION: '게임 운영', GAME_QA: '게임 QA',
    GAME_CLIENT: '게임 클라이언트', GAME_SERVER: '게임 서버', GAME_MOBILE: '게임 모바일',
    TECHNICAL_ARTIST: '테크니컬 아티스트', GAME_ART: '게임 아트', GAME_3D_MODELING: '3D 모델링',
    GAME_ANIMATION: '게임 애니메이션', GAME_EFFECT: '게임 이펙트', GAME_INTERFACE: '게임 UI',
    GAME_DIRECTING_VIDEO: '게임 영상', GAME_SOUND: '게임 사운드', GAME_ETC: '게임 기타', ETC: '기타',
    ANY: '전체', NEWCOMER: '신입', JUNIOR: '주니어', MID: '미드레벨', SENIOR: '시니어', LEAD: '리드',
    FULL_TIME: '정규직', CONTRACT: '계약직', INTERN: '인턴', PART_TIME: '파트타임', FREELANCE: '프리랜서',
    MILITARY_SERVICE: '병역특례', ONSITE: '출근', REMOTE: '원격', HYBRID: '하이브리드', FLEXIBLE: '유연근무',
    OPEN: '진행중', CLOSED: '마감', EXPIRED: '만료', HIDDEN: '숨김',
    EVENT_DRIVEN: '이벤트 드리븐', CACHE_STRATEGY: '캐시 전략', CI_CD: 'CI/CD', TESTING: '테스트',
    AUTH: '인증/인가', OBSERVABILITY: '모니터링', HIGH_TRAFFIC: '대용량 트래픽', DISTRIBUTED_SYSTEM: '분산 시스템',
  };
  const display = (value) => labelMap[value] || value;
  const options = (values) => ['전체'].concat(values.filter((x) => display(x) !== '전체').map(display));
  const selectedFilters = Object.values(activeFilter).filter((x) => x && x !== '전체');
  const primaryProject = JF.projectList?.[0] || {};
  const currentProjectName = primaryProject.name || previewProject || '내 프로젝트';
  const currentProjectRepo = primaryProject.repo || '연결된 GitHub repository';
  const currentProjectSkillSummary = (primaryProject.previewSkills || JF.skills?.map((skill) => skill.name) || []).slice(0, 3).join(' · ') || '스킬 분석 대기';

  const Shell = ({ title, sub, children, actions }) => (
    <div style={{ minHeight: '100vh', background: '#fff', fontFamily: font, color: ink }}>
      <div style={{ height: 64, borderBottom: '1px solid ' + line, background: 'rgba(255,255,255,0.84)', backdropFilter: 'blur(8px)', display: 'flex', alignItems: 'center', padding: narrow ? '0 18px' : '0 40px', gap: narrow ? 10 : 28, position: 'sticky', top: 0, zIndex: 20, overflow: 'hidden' }}>
        <div style={{ fontSize: narrow ? 20 : 22, fontWeight: 900, letterSpacing: -1, cursor: 'pointer', flexShrink: 0 }} onClick={() => go('home')}>jobflow<span style={{ color: green, WebkitTextStroke: '0.5px ' + greenInk }}>.</span></div>
        <div style={{ display: 'flex', gap: 4, whiteSpace: 'nowrap', flex: 1, minWidth: 0, overflowX: 'auto', scrollbarWidth: 'none' }}>{navItems.map(([key, label]) => { const on = navOn(key); return <button key={key} onClick={() => go(key)} style={{ font: 'inherit', cursor: 'pointer', border: 'none', borderRadius: 20, padding: narrow ? '7px 10px' : '7px 13px', fontSize: narrow ? 12.5 : 13.5, fontWeight: on ? 900 : 600, background: on ? green : 'transparent', color: on ? ink : muted, whiteSpace: 'nowrap', flexShrink: 0 }}>{label}</button>; })}</div>
        <div style={{ marginLeft: 'auto', display: narrow ? 'none' : 'flex', alignItems: 'center', gap: 10, flexShrink: 0 }}><span style={{ display: 'flex', alignItems: 'center', gap: 7, fontSize: 12.5, fontWeight: 800, color: login ? greenInk : muted, background: login ? greenTint : soft, border: '1px solid ' + (login ? greenTintBd : line), padding: '6px 11px', borderRadius: 20, whiteSpace: 'nowrap' }}>{login && <span style={{ width: 7, height: 7, borderRadius: 4, background: greenInk }} />}{login ? 'GitHub 연동됨' : '비로그인 탐색 중'}</span><button onClick={() => go('mypage')} style={{ font: 'inherit', cursor: 'pointer', width: 34, height: 34, borderRadius: 17, background: ink, color: '#fff', border: 'none', fontSize: 13, fontWeight: 900 }}>사</button></div>
      </div>
      <main style={{ maxWidth: 1240, margin: '0 auto', padding: narrow ? '28px 18px 64px' : '36px 40px 72px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', gap: 24, marginBottom: 26, flexWrap: 'wrap' }}><div><div style={{ fontSize: narrow ? 27 : 32, fontWeight: 900, letterSpacing: -0.8 }}>{title}</div>{sub && <div style={{ fontSize: 14.5, color: muted, marginTop: 8, maxWidth: 760, lineHeight: 1.58 }}>{sub}</div>}</div>{actions}</div>
        {children}
      </main>
      {screenNotice && <div style={{ position: 'fixed', bottom: 28, left: '50%', transform: 'translateX(-50%)', background: ink, color: '#fff', padding: '12px 18px', borderRadius: 24, fontSize: 14, fontWeight: 800, boxShadow: '0 8px 28px rgba(0,0,0,0.25)', zIndex: 900, display: 'flex', alignItems: 'center', gap: 8 }}><span style={{ width: 7, height: 7, borderRadius: 4, background: green }} />{screenNotice}</div>}
    </div>
  );

  const H = ({ children }) => <div style={{ fontSize: 17, fontWeight: 900, marginBottom: 14 }}>{children}</div>;
  const SecTitle = ({ children, color }) => <div style={{ fontSize: 11, fontWeight: 900, letterSpacing: 1, color: color || faint, textTransform: 'uppercase', marginBottom: 14 }}>{children}</div>;
  const Logo = ({ text, dark, size = 42 }) => <div style={{ width: size, height: size, borderRadius: Math.round(size / 3), background: dark ? 'rgba(255,255,255,0.12)' : ink, color: dark ? green : '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: size > 48 ? 16 : 13, fontWeight: 900, flexShrink: 0 }}>{text || 'JF'}</div>;
  const GithubMark = ({ dark, size = 16 }) => <svg width={size} height={size} viewBox="0 0 16 16" fill={dark ? '#fff' : ink} style={{ flexShrink: 0 }}><path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38v-1.34c-2.23.49-2.7-1.07-2.7-1.07-.36-.93-.89-1.18-.89-1.18-.73-.5.05-.49.05-.49.8.06 1.23.83 1.23.83.72 1.23 1.88.87 2.34.67.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.96 0-.87.31-1.59.83-2.15-.08-.2-.36-1.02.08-2.13 0 0 .67-.21 2.2.82a7.6 7.6 0 0 1 4 0c1.53-1.03 2.2-.82 2.2-.82.44 1.11.16 1.93.08 2.13.52.56.82 1.28.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48v2.2c0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8z"/></svg>;
  const EyeIcon = ({ size = 14 }) => <svg width={size} height={size} viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" style={{ flexShrink: 0 }}><path d="M1 8s2.5-5 7-5 7 5 7 5-2.5 5-7 5-7-5-7-5z"/><circle cx="8" cy="8" r="2"/></svg>;
  const BookmarkIcon = ({ size = 15 }) => <svg width={size} height={size} viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round" style={{ flexShrink: 0 }}><path d="M4 2.5h8v11L8 11l-4 2.5v-11z"/></svg>;
  const EditIcon = () => <svg width="15" height="15" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M9.8 3.2l3 3L6 13H3v-3l6.8-6.8z"/><path d="M8.7 4.3l3 3"/></svg>;
  const SkillChip = ({ s, owned = true, miss, dark }) => <span style={{ fontSize: 12, fontWeight: 780, padding: '4px 10px', borderRadius: 8, background: miss ? (dark ? 'rgba(255,255,255,0.08)' : soft) : dark ? 'rgba(185,236,42,0.13)' : greenTint, color: miss ? (dark ? 'rgba(255,255,255,0.58)' : muted) : dark ? green : greenInk, border: '1px solid ' + (miss ? (dark ? 'rgba(255,255,255,0.14)' : line) : dark ? 'rgba(185,236,42,0.25)' : greenTintBd), whiteSpace: 'nowrap' }}>{miss ? '' : '✓ '}{s}</span>;
  const TagChip = ({ code, muted: dull }) => { const tone = tagTone; const P = ({ '보라': { bg: '#efeaff', fg: '#5b3fd6', bd: '#dcd2fb' }, '라임': { bg: '#eef8cf', fg: '#3f5c08', bd: '#dbeca8' }, '검정': { bg: '#14151a', fg: '#ffffff', bd: '#14151a' } })[tone] || { bg: '#efeaff', fg: '#5b3fd6', bd: '#dcd2fb' }; return <span style={{ fontSize: 11.5, fontWeight: 750, padding: '4px 8px', borderRadius: 8, background: P.bg, color: P.fg, border: '1px solid ' + P.bd, whiteSpace: 'nowrap', opacity: dull ? 0.82 : 1 }}>#{JF.tagLabel[code] || code}</span>; };
  const MoreChip = ({ count }) => <span style={{ fontSize: 12, fontWeight: 900, padding: '4px 9px', borderRadius: 8, background: soft, color: muted, border: '1px solid ' + line, whiteSpace: 'nowrap' }}>+{count}</span>;

  const JobThumbnail = ({ job, score, compact, locked }) => {
    const ownedSkills = job.matched || job.skills || [];
    const missingSkills = job.missing || [];
    const tags = job.tags || ['EVENT_DRIVEN', 'CI_CD'];
    const matchScore = score || job.score || 72;
    const visibleSkills = missingSkills.length
      ? ownedSkills.slice(0, compact ? 2 : 3).map((skill) => ({ skill })).concat([{ skill: missingSkills[0], miss: true }])
      : ownedSkills.slice(0, compact ? 3 : 4).map((skill) => ({ skill }));
    const hiddenSkills = missingSkills.length > 1 ? missingSkills.length - 1 : (!missingSkills.length && ownedSkills.length > (compact ? 3 : 4) ? ownedSkills.length - (compact ? 3 : 4) : 0);
    const maxTags = 2;
    const visibleTags = tags.length > maxTags ? tags.slice(0, maxTags - 1) : tags.slice(0, maxTags);
    const hiddenTags = Math.max(0, tags.length - visibleTags.length);
    return <div className="jf-jobcard" onClick={() => go('detail', { jobId: job.jobId || job.id, company: job.companyKo })} style={{ cursor: 'pointer', ...tile, border: '1px solid ' + (isV3 ? '#edf0f3' : '#edf0f3'), boxShadow: isV3 ? '0 1px 2px rgba(20,21,26,0.025), 0 8px 20px rgba(20,21,26,0.035)' : '0 1px 2px rgba(20,21,26,0.03), 0 7px 18px rgba(20,21,26,0.045)', padding: isV3 ? 17 : 18, display: 'flex', flexDirection: 'column', minHeight: compact ? 186 : 208 }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
        <Logo text={job.logo || job.companyKo.slice(0, 2)} size={44} />
        <div style={{ minWidth: 0, flex: 1 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ fontSize: 14, fontWeight: isV3 ? 560 : 650, whiteSpace: 'nowrap' }}>{job.companyKo}</span>
            <span style={{ fontSize: 12, color: faint, fontWeight: 500, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{job.role || job.title} · {job.level}</span>
            <span style={{ marginLeft: 'auto', fontSize: 11.5, fontWeight: 850, color: coralDeep, background: coralTint, border: '1px solid ' + coralTintBd, padding: '3px 8px', borderRadius: 12, whiteSpace: 'nowrap', flexShrink: 0 }}>{job.deadline}</span>
          </div>
          <div style={{ fontSize: compact ? 15 : 16.5, fontWeight: isV3 ? 540 : 600, letterSpacing: -0.25, marginTop: 8, lineHeight: 1.38, minHeight: compact ? 42 : 46, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{job.fullTitle || `${job.companyKo} ${job.title} 채용`}</div>
        </div>
      </div>
      <div style={{ display: 'flex', flexWrap: 'nowrap', gap: 6, marginTop: 14, height: 26, overflow: 'hidden' }}>{visibleSkills.map(({ skill, miss }) => <SkillChip key={skill} s={skill} miss={miss} />)}{hiddenSkills > 0 && <MoreChip count={hiddenSkills} />}</div>
      <div style={{ display: 'flex', flexWrap: 'nowrap', gap: 6, marginTop: 9, height: 25, overflow: 'hidden' }}>{visibleTags.map((c, idx) => <TagChip key={c} code={c} muted={idx > 0 && missingSkills.length > 0} />)}{hiddenTags > 0 && <MoreChip count={hiddenTags} />}</div>
      <div style={{ marginTop: 'auto', paddingTop: 15, display: 'flex', alignItems: 'center', gap: 10, color: faint, fontSize: 11.5, fontWeight: 650 }}>
        {login && !locked ? <span style={{ color: ink, fontWeight: compact ? 950 : 850, fontSize: compact ? 17 : 11.5, letterSpacing: compact ? -0.5 : 0, ...num }}>{matchScore}% <span style={{ fontSize: compact ? 11.5 : 11.5, fontWeight: 850, color: '#9aa1ad' }}>레포 매칭</span></span> : <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, color: muted, fontWeight: 700 }}><svg width="12" height="12" viewBox="0 0 14 14" fill="none" stroke="currentColor" strokeWidth="1.6"><rect x="2.5" y="6" width="9" height="6.5" rx="1.2"/><path d="M4.5 6V4.3a2.5 2.5 0 0 1 5 0V6"/></svg>로그인하고 매칭률 확인하기</span>}
        <span style={{ marginLeft: 'auto', display: 'inline-flex', alignItems: 'center', gap: 4 }}><EyeIcon />{job.views}</span>
        <button onClick={async (e) => { e.stopPropagation(); try { if (!login) throw new Error('로그인 후 공고를 저장할 수 있어요.'); await window.__jobflowApi?.saveJobByCompany?.(job.companyKo); notify('저장한 공고에 추가했어요.'); } catch (error) { notify(error.message || '공고 저장에 실패했어요.'); } }} aria-label="공고 저장" style={{ width: 28, height: 28, borderRadius: 10, border: '1px solid ' + line, background: '#fff', color: muted, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer' }}><BookmarkIcon /></button>
      </div>
    </div>;
  };

  const FilterGroup = ({ id, label, items, maxHeight }) => {
    const displayItems = options(items);
    const open = openGroups[id] !== false;
    const current = activeFilter[id] || '전체';
    return <div style={{ borderBottom: '1px solid ' + line, padding: open ? '14px 0' : '13px 0' }}>
      <button onClick={() => toggleGroup(id)} style={{ font: 'inherit', cursor: 'pointer', border: 'none', background: 'transparent', width: '100%', display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: 0 }}>
        <span style={{ display: 'flex', alignItems: 'center', gap: 7 }}><b style={{ fontSize: 14, fontWeight: 800 }}>{label}</b>{!open && current !== '전체' && <span style={{ fontSize: 11.5, fontWeight: 800, color: greenInk, background: greenTint, border: '1px solid ' + greenTintBd, borderRadius: 10, padding: '1px 7px' }}>{current}</span>}</span>
        <span style={{ width: 20, height: 20, borderRadius: 6, border: '1px solid ' + line, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 14, fontWeight: 700, color: muted, lineHeight: 1 }}>{open ? '−' : '+'}</span>
      </button>
      {open && <div style={{ display: 'flex', flexWrap: 'wrap', gap: 7, maxHeight: maxHeight || 116, overflow: 'auto', paddingRight: 2, marginTop: 11 }}>{displayItems.map((x) => { const on = current === x; return <button key={x} onClick={() => setActiveFilter((prev) => ({ ...prev, [id]: x }))} style={{ font: 'inherit', cursor: 'pointer', border: '1px solid ' + (on ? greenStrong : line), background: on ? greenStrong : '#fff', color: on ? ink : muted, borderRadius: 18, padding: '7px 10px', fontWeight: on ? 900 : 700, fontSize: 12.5, whiteSpace: 'nowrap' }}>{x}</button>; })}</div>}
    </div>;
  };

  const ActionButton = ({ children, onClick, primary, danger }) => <button onClick={onClick} style={{ font: 'inherit', cursor: 'pointer', border: primary ? 'none' : '1px solid ' + (danger ? coralTintBd : line), background: primary ? green : danger ? coralTint : '#fff', color: danger ? coralDeep : ink, borderRadius: 13, padding: '10px 14px', fontSize: 13, fontWeight: 900, whiteSpace: 'nowrap' }}>{children}</button>;
  const StatePanel = ({ type = 'empty', title, desc, action, secondary }) => {
    const palette = type === 'error' || type === 'forbidden' ? { bg: coralTint, bd: coralTintBd, fg: coralDeep, icon: '!' } : type === 'loading' ? { bg: '#f8f9fb', bd: line, fg: muted, icon: '…' } : type === 'unauthorized' ? { bg: '#f8f9fb', bd: line, fg: ink, icon: '↗' } : { bg: greenTint, bd: greenTintBd, fg: greenInk, icon: '✓' };
    return <div style={{ background: palette.bg, border: '1px solid ' + palette.bd, borderRadius: 18, padding: 18, display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap' }}><div style={{ width: 36, height: 36, borderRadius: 13, background: '#fff', color: palette.fg, display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 1000, boxShadow: '0 1px 2px rgba(20,21,26,0.06)' }}>{palette.icon}</div><div style={{ flex: 1, minWidth: 220 }}><b style={{ fontSize: 15 }}>{title}</b><div style={{ color: muted, fontSize: 13, lineHeight: 1.55, marginTop: 3 }}>{desc}</div></div>{action && <ActionButton primary>{action}</ActionButton>}{secondary && <ActionButton>{secondary}</ActionButton>}</div>;
  };
  const Field = ({ label, value, placeholder, textarea }) => <label style={{ display: 'block', marginBottom: 12 }}><div style={{ fontSize: 12, color: muted, fontWeight: 850, marginBottom: 6 }}>{label}</div>{textarea ? <textarea defaultValue={value} placeholder={placeholder} rows={4} style={{ width: '100%', boxSizing: 'border-box', resize: 'vertical', font: 'inherit', border: '1px solid ' + line, borderRadius: 13, padding: '12px 13px', outline: 'none' }} /> : <input defaultValue={value} placeholder={placeholder} style={{ width: '100%', boxSizing: 'border-box', font: 'inherit', border: '1px solid ' + line, borderRadius: 13, padding: '12px 13px', outline: 'none' }} />}</label>;
  const Modal = ({ title, sub, children, onClose, width = 460 }) => <div style={{ position: 'fixed', inset: 0, zIndex: 700, background: 'rgba(20,21,26,0.38)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20 }} onClick={onClose}><div onClick={(e) => e.stopPropagation()} style={{ width, maxWidth: '100%', maxHeight: '92vh', overflow: 'auto', background: '#fff', borderRadius: 22, padding: 24, boxShadow: '0 24px 80px rgba(0,0,0,0.28)' }}><div style={{ display: 'flex', alignItems: 'flex-start', gap: 12, marginBottom: 18 }}><div style={{ flex: 1 }}><b style={{ fontSize: 20, letterSpacing: -0.4 }}>{title}</b>{sub && <div style={{ color: muted, fontSize: 13, lineHeight: 1.55, marginTop: 4 }}>{sub}</div>}</div><button onClick={onClose} style={{ border: 'none', background: soft, borderRadius: 14, width: 30, height: 30, cursor: 'pointer', fontWeight: 900 }}>×</button></div>{children}</div></div>;
  const TimelineStep = ({ done, active, title, desc }) => <div style={{ display: 'grid', gridTemplateColumns: '30px 1fr', gap: 10, padding: '10px 0', borderTop: '1px solid ' + line }}><span style={{ width: 24, height: 24, borderRadius: 12, background: done ? green : active ? ink : soft, color: done ? ink : active ? '#fff' : faint, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 11, fontWeight: 1000 }}>{done ? '✓' : active ? '…' : ''}</span><div><b style={{ fontSize: 13.5 }}>{title}</b><div style={{ color: muted, fontSize: 12.5, lineHeight: 1.45, marginTop: 2 }}>{desc}</div></div></div>;

  const loginScreen = () => <Shell title="로그인 / 회원가입" sub="이메일, GitHub, Google 중 편한 방식으로 시작합니다. 레포 연결은 로그인 후에도 할 수 있어요."><div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1.05fr 0.95fr', gap: 22, alignItems: 'stretch' }}><section style={{ ...tile, padding: 28 }}><div style={{ display: 'flex', gap: 8, marginBottom: 22 }}><button style={{ font: 'inherit', border: 'none', background: green, borderRadius: 20, padding: '8px 16px', fontWeight: 900 }}>로그인</button><button style={{ font: 'inherit', border: '1px solid ' + line, background: '#fff', color: muted, borderRadius: 20, padding: '8px 16px', fontWeight: 800 }}>회원가입</button></div>{['이름 (회원가입 시)', '이메일', '비밀번호'].map((x, i) => <input key={x} placeholder={x} type={x === '비밀번호' ? 'password' : 'text'} style={{ width: '100%', font: 'inherit', fontSize: 14, padding: '13px 15px', borderRadius: 12, border: '1px solid ' + line, marginBottom: 10, outline: 'none', boxSizing: 'border-box', background: i === 0 ? '#fbfcfd' : '#fff' }} />)}<button onClick={() => go('home')} style={{ font: 'inherit', cursor: 'pointer', width: '100%', border: 'none', background: ink, color: '#fff', borderRadius: 12, padding: 14, fontWeight: 900 }}>이메일로 계속하기</button><button onClick={() => go('home')} style={{ font: 'inherit', cursor: 'pointer', width: '100%', border: '1px solid ' + line, background: greenTint, color: greenInk, borderRadius: 12, padding: 13, fontWeight: 900, marginTop: 10 }}>데모 계정으로 둘러보기</button><div style={{ color: faint, fontSize: 12, lineHeight: 1.5, marginTop: 7 }}>시연용 shortcut입니다. 실제 구현에서는 /auth/login을 demo 계정으로 호출해 JWT를 저장합니다.</div><div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1fr 1fr', gap: 10, marginTop: 12 }}><button onClick={() => go('oauth')} style={{ font: 'inherit', cursor: 'pointer', border: '1px solid ' + line, background: '#fff', borderRadius: 12, padding: 13, fontWeight: 850, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}><GithubMark />GitHub</button><button onClick={() => go('oauth')} style={{ font: 'inherit', cursor: 'pointer', border: '1px solid ' + line, background: '#fff', borderRadius: 12, padding: 13, fontWeight: 850 }}>Google</button></div><div style={{ marginTop: 18, display: 'grid', gap: 10 }}><StatePanel type="error" title="로그인 실패 상태" desc="이메일 또는 비밀번호가 맞지 않을 때 입력 아래에 짧게 노출합니다." secondary="비밀번호 재설정" /><StatePanel type="loading" title="처리 중 상태" desc="OAuth 리다이렉트나 이메일 로그인 요청 중 버튼 영역에 스피너와 함께 표시합니다." /></div></section><section style={{ background: ink, color: '#fff', borderRadius: 24, padding: 30, minHeight: 440, display: 'flex', flexDirection: 'column', overflow: 'hidden', position: 'relative' }}><span style={{ fontSize: 11, fontWeight: 900, letterSpacing: 1, color: green }}>REPOSITORY MATCHING</span><div style={{ marginTop: 42, fontSize: narrow ? 30 : 40, fontWeight: 900, letterSpacing: -1.4, lineHeight: 1.08 }}>레포지토리에서<br />스킬 근거를 읽고<br /><span style={{ color: green }}>공고를 정렬합니다.</span></div><div style={{ marginTop: 'auto', display: 'grid', gap: 10 }}>{['코드 기반 스킬 추출', '경험 태그 자동 요약', '공고별 매칭률 계산'].map((x, i) => <div key={x} style={{ display: 'flex', alignItems: 'center', gap: 10, color: 'rgba(255,255,255,0.82)', fontSize: 14, fontWeight: 800 }}><span style={{ width: 26, height: 26, borderRadius: 13, background: 'rgba(185,236,42,0.16)', color: green, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 1000 }}>{i + 1}</span>{x}</div>)}</div></section></div></Shell>;
  const oauthScreen = () => <Shell title="OAuth 콜백" sub="GitHub 또는 Google 로그인 후 돌아오는 처리 화면입니다. 성공, 실패, 재시도 상태를 모두 같은 톤으로 보여줍니다."><div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1.15fr 0.85fr', gap: 18 }}><section style={{ ...tile, padding: 28 }}><SecTitle>SUCCESS</SecTitle><div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 18 }}><div style={{ width: 54, height: 54, borderRadius: 18, background: green, color: ink, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 24, fontWeight: 1000 }}>✓</div><div><H>로그인 처리 중</H><div style={{ color: muted, fontSize: 13.5 }}>authorization code를 토큰으로 교환하고 사용자 정보를 불러옵니다.</div></div></div><div style={{ display: 'grid', gap: 10 }}>{['code 검증', 'accessToken 저장', '내 정보 조회', '홈으로 이동'].map((x, i) => <div key={x} style={{ display: 'grid', gridTemplateColumns: '28px 1fr 80px', gap: 10, alignItems: 'center', padding: '10px 0', borderTop: '1px solid ' + line }}><span style={{ width: 24, height: 24, borderRadius: 12, background: i < 3 ? greenTint : soft, color: i < 3 ? greenInk : muted, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 1000 }}>{i < 3 ? '✓' : '…'}</span><b style={{ fontSize: 13.5 }}>{x}</b><span style={{ color: i < 3 ? greenInk : faint, fontSize: 12, fontWeight: 800, textAlign: 'right' }}>{i < 3 ? '완료' : '진행중'}</span></div>)}</div><button onClick={() => go('home')} style={{ marginTop: 20, font: 'inherit', border: 'none', background: ink, color: '#fff', borderRadius: 13, padding: '13px 18px', fontWeight: 900 }}>홈으로 이동</button></section><aside style={{ display: 'grid', gap: 12 }}><StatePanel type="error" title="로그인이 완료되지 않았습니다" desc="사용자가 권한 승인을 취소했거나 code가 만료된 상태입니다." action="다시 시도" secondary="로그인으로" /><StatePanel type="forbidden" title="권한이 부족합니다" desc="레포 분석에 필요한 scope가 없으면 GitHub 재연동을 안내합니다." action="GitHub 재연동" /><StatePanel type="unauthorized" title="세션 만료" desc="토큰 저장에 실패했거나 만료되면 로그인 화면으로 돌려보냅니다." action="로그인으로 이동" /></aside></div></Shell>;

  const jobsScreen = () => {
    const resetFilters = () => setActiveFilter({ role: '전체', career: '전체', employment: '전체', remote: '전체', region: '전체', skill: '전체', tag: '전체', deadline: '전체' });
    const normalizedQuery = query.trim().toLowerCase();
    const selected = (key) => activeFilter[key] || '전체';
    const includesText = (value, needle) => String(value || '').toLowerCase().includes(String(needle || '').toLowerCase());
    const isRoleMatch = (job, value) => {
      if (value === '전체') return true;
      const roleText = `${job.role || ''} ${job.title || ''} ${job.fullTitle || ''}`;
      if (value === '백엔드') return /backend|server|백엔드|서버/i.test(roleText);
      if (value === '프론트엔드') return /frontend|front|프론트/i.test(roleText);
      if (value === '풀스택') return /full.?stack|풀스택/i.test(roleText);
      return includesText(roleText, value);
    };
    const isDeadlineMatch = (job, value) => {
      if (value === '전체') return true;
      const d = Number(String(job.deadline || '').replace(/\D/g, '') || 999);
      if (value === '오늘 마감') return String(job.deadline).includes('DAY') || d === 0;
      if (value === '3일 이내') return d <= 3;
      if (value === '7일 이내') return d <= 7;
      if (value === '마감일 없음') return job.deadline === '상시';
      return true;
    };
    const filteredListings = JF.listings
      .filter((job) => {
        const skills = [...new Set([...(job.skills || []), ...(job.matched || []), ...(job.missing || []), ...(job.requiredSkills || []), ...(job.preferredSkills || [])])];
        const text = `${job.companyKo || ''} ${job.fullTitle || ''} ${job.title || ''} ${job.role || ''} ${skills.join(' ')} ${(job.tags || []).map((c) => JF.tagLabel?.[c] || c).join(' ')}`.toLowerCase();
        const queryOk = !normalizedQuery || text.includes(normalizedQuery);
        const roleOk = isRoleMatch(job, selected('role'));
        const careerOk = selected('career') === '전체' || includesText(job.level, selected('career'));
        const regionOk = selected('region') === '전체' || includesText(job.location, selected('region'));
        const skillOk = selected('skill') === '전체' || skills.some((skill) => skill === selected('skill'));
        const tagOk = selected('tag') === '전체' || (job.tags || []).some((tag) => (JF.tagLabel?.[tag] || tag) === selected('tag'));
        const deadlineOk = isDeadlineMatch(job, selected('deadline'));
        return queryOk && roleOk && careerOk && regionOk && skillOk && tagOk && deadlineOk;
      })
      .sort((a, b) => {
        if (sort === '인기순') return (b.views || 0) - (a.views || 0);
        if (sort === '마감순') return parseInt(String(a.deadline).replace(/\D/g, '') || '999', 10) - parseInt(String(b.deadline).replace(/\D/g, '') || '999', 10);
        if (sort === '최신순') return (b.jobId || b.id || 0) - (a.jobId || a.id || 0);
        return (b.score || 0) - (a.score || 0);
      });
    const filterPanel = (
      <aside style={{ ...tile, position: narrow ? 'static' : 'sticky', top: 86, padding: '18px 20px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <b style={{ fontSize: 18 }}>필터</b>
          <span style={{ color: greenInk, fontWeight: 900 }}>ON</span>
          <button onClick={resetFilters} style={{ marginLeft: 'auto', border: 'none', background: 'transparent', color: faint, font: 'inherit', fontSize: 12, fontWeight: 800, cursor: 'pointer' }}>초기화</button>
        </div>
        <FilterGroup id="role" label="직무" items={JF.filterOptions.roles} maxHeight={150} />
        <FilterGroup id="career" label="경력" items={JF.filterOptions.careers} />
        <FilterGroup id="employment" label="채용 유형" items={JF.filterOptions.employmentTypes} />
        <FilterGroup id="remote" label="근무 방식" items={JF.filterOptions.remoteTypes} />
        <FilterGroup id="region" label="지역" items={JF.filterOptions.regions} />
        <FilterGroup id="skill" label="기술 스택" items={JF.filterOptions.skills} maxHeight={170} />
        <FilterGroup id="tag" label="경험 태그" items={JF.filterOptions.experienceTags} />
        <FilterGroup id="deadline" label="마감" items={JF.filterOptions.deadlines} />
      </aside>
    );

    const filteredCount = filteredListings.length;
    return (
      <Shell title="공고" sub={isV3 ? "직무·경력·스택·지역 조건으로 먼저 좁힌 뒤 레포 매칭순으로 확인합니다." : (login ? "검색어를 먼저 입력하고, 직무·경력·스킬·경험 태그로 좁힌 뒤 레포 매칭률로 다시 정렬합니다." : "로그인 없이도 전체 공고와 필터를 먼저 탐색할 수 있어요. 레포를 연결하면 같은 목록이 매칭순으로 재정렬됩니다.")}>
        <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '292px 1fr', gap: 22, alignItems: 'start' }}>
          {!narrow && filterPanel}
          <section>
            {!login && <div style={{ background: greenTint, border: '1px solid ' + greenTintBd, borderRadius: 18, padding: '15px 18px', marginBottom: 14, display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap' }}><div style={{ flex: 1, minWidth: 240 }}><b>레포를 연결하면 공고가 내 스택 기준으로 정렬돼요.</b><div style={{ color: muted, fontSize: 13, marginTop: 3 }}>지금은 전체 공고 기준으로 탐색 중입니다.</div></div><button onClick={() => go('login')} style={{ font: 'inherit', border: 'none', background: ink, color: '#fff', borderRadius: 20, padding: '10px 15px', fontWeight: 850 }}>로그인하고 매칭 보기</button></div>}
            {JF.__apiStatus?.jobs === 'mock' && <div style={{ marginBottom: 14 }}><StatePanel type="error" title="실시간 공고 API를 불러오지 못했습니다" desc="현재는 디자인용 샘플 공고를 보여주고 있어요. 백엔드가 켜지면 실제 공고로 자동 갱신됩니다." action="다시 시도" /></div>}
            {isV3 && <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr 1fr' : 'repeat(3, 1fr)', gap: 10, marginBottom: 14 }}>{[['전체 공고', JF.market.totalCount.toLocaleString()], ['오늘 신규', '37'], ['7일 내 마감', '112']].map(([l, v]) => <div key={l} style={{ background: '#fff', border: '1px solid ' + line, borderRadius: 16, padding: '12px 14px' }}><b style={{ fontSize: 20, ...num }}>{v}</b><div style={{ fontSize: 12, color: muted, fontWeight: 800, marginTop: 2 }}>{l}</div></div>)}</div>}
            <div style={{ ...tile, padding: narrow ? 16 : 18, marginBottom: 14, display: 'grid', gap: 14 }}>
              <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr auto' : '1fr auto', gap: 8 }}>
	                <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder={isV3 ? "검색어는 선택 사항이에요. 먼저 필터로 좁혀보세요." : "예: 백엔드 Spring, Kubernetes 플랫폼, C++ 개발자"} style={{ width: '100%', minWidth: 0, border: '1px solid ' + line, background: soft, color: ink, borderRadius: 14, padding: '13px 15px', font: 'inherit', fontWeight: isV3 ? 600 : 700, outline: 'none' }} />
                <button onClick={async () => {
                  if (narrow) {
                    setFilterOpen(!filterOpen);
                    return;
                  }
                  try {
                    const rows = await window.__jobflowApi?.searchJobs?.(query);
                    notify(`${rows?.length || 0}개 공고를 불러왔어요.`);
                  } catch (error) {
                    notify(error.message || '공고 검색에 실패했어요.');
                  }
                }} style={{ font: 'inherit', cursor: 'pointer', border: 'none', background: ink, color: '#fff', borderRadius: 14, padding: '0 20px', fontWeight: 800 }}>{narrow ? (filterOpen ? '닫기' : '필터') : '검색'}</button>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap' }}>
                <div style={{ fontSize: 15, fontWeight: 700, letterSpacing: -0.3 }}>조건에 맞는 공고 <span style={{ color: greenInk, fontWeight: 800 }}>{filteredCount.toLocaleString()}개</span> <span style={{ fontSize: 12.5, color: faint, fontWeight: 600 }}>· 현재 목록 {JF.listings.length.toLocaleString()}개</span></div>
                <div style={{ marginLeft: 'auto', display: 'flex', gap: 7, flexWrap: 'wrap' }}>{['매칭순', '인기순', '마감순', '최신순'].map((x) => <button key={x} onClick={() => setSort(x)} style={{ font: 'inherit', cursor: 'pointer', border: '1px solid ' + (sort === x ? greenStrong : line), background: sort === x ? greenStrong : '#fff', color: ink, borderRadius: 20, padding: '7px 13px', fontWeight: sort === x ? 800 : 600, fontSize: 13 }}>{x}</button>)}</div>
              </div>
	              {(isV3 || selectedFilters.length > 0) && <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, alignItems: 'center' }}><span style={{ color: faint, fontSize: 12, fontWeight: 900 }}>선택 조건</span>{Object.entries(activeFilter).filter(([, v]) => v && v !== '전체').length ? Object.entries(activeFilter).filter(([, v]) => v && v !== '전체').map(([id, v]) => <span key={id} style={{ display: 'inline-flex', alignItems: 'center', gap: 5, background: greenTint, color: greenInk, border: '1px solid ' + greenTintBd, borderRadius: 14, padding: '4px 10px', fontSize: 12, fontWeight: 800 }}>{v}<span onClick={() => setActiveFilter((p) => ({ ...p, [id]: '전체' }))} style={{ cursor: 'pointer', opacity: 0.55 }}>✕</span></span>) : <span style={{ background: soft, color: muted, border: '1px solid ' + line, borderRadius: 14, padding: '4px 10px', fontSize: 12, fontWeight: 800 }}>전체</span>}</div>}
            </div>
            {narrow && <div style={{ marginBottom: 14 }}>{filterOpen ? filterPanel : <div style={{ ...tile, padding: 14, display: 'flex', alignItems: 'center', gap: 8, overflowX: 'auto' }}><b style={{ whiteSpace: 'nowrap' }}>선택 필터</b>{(selectedFilters.length ? selectedFilters : ['필터 없음']).map((x) => <span key={x} style={{ background: x === '필터 없음' ? soft : greenTint, color: x === '필터 없음' ? muted : greenInk, border: '1px solid ' + (x === '필터 없음' ? line : greenTintBd), borderRadius: 16, padding: '6px 10px', fontSize: 12, fontWeight: 900, whiteSpace: 'nowrap' }}>{x}</span>)}</div>}</div>}
            {filteredListings.length ? <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : 'repeat(2, 1fr)', gap: 14 }}>{filteredListings.map((j) => <JobThumbnail key={(j.jobId || j.id || '') + j.companyKo + j.fullTitle} job={j} locked={!login} />)}</div> : <StatePanel type="empty" title="조건에 맞는 공고가 없습니다" desc="선택한 필터를 줄이거나 검색어를 지우면 더 많은 공고를 볼 수 있어요." action="필터 초기화" /> }
          </section>
        </div>
      </Shell>
    );
  };

  const projectsScreen = () => {
    const selected = JF.projectList.find((p) => p.name === previewProject) || JF.projectList[0];
    return <Shell title="프로젝트" sub="전체 프로젝트와 추출 스킬 목록입니다. 프로젝트를 누르면 상세 분석으로 들어가고, 매칭 공고는 우측에서 미리 봅니다." actions={<button onClick={() => go('project-new')} style={{ font: 'inherit', cursor: 'pointer', border: 'none', background: ink, color: '#fff', borderRadius: 22, padding: '12px 18px', fontWeight: 900 }}>+ 새 레포 분석</button>}>
      <div style={{ display: 'grid', gap: 14, marginBottom: 18 }}><StatePanel type="empty" title="프로젝트 없음 상태" desc="등록된 레포가 없으면 GitHub 프로젝트 분석 시작 CTA를 먼저 보여줍니다." action="GitHub 프로젝트 분석 시작" /><StatePanel type="error" title="분석 실패 상태" desc="레포 접근 실패, 지원하지 않는 언어, 토큰 만료를 구분해 재분석 또는 재연동을 안내합니다." action="재분석" secondary="GitHub 재연동" /></div>
      <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1fr 390px', gap: 18, alignItems: 'start' }}>
        <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : 'repeat(2, 1fr)', gap: 16 }}>
          {JF.projectList.map((p) => (
            <div key={p.name} className="jf-jobcard" style={{ ...tile, cursor: 'pointer', borderColor: previewProject === p.name ? greenStrong : line, padding: 0, overflow: 'hidden' }} onClick={() => go('project-analysis')}>
              <div style={{ height: 132, background: p.connected ? 'linear-gradient(135deg, #14151a, #283018 55%, #b9ec2a)' : 'linear-gradient(135deg, #f2f3f5, #dde2e8)', color: p.connected ? '#fff' : muted, padding: 18, display: 'flex', flexDirection: 'column' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 7, fontSize: 11, fontWeight: 900, letterSpacing: 1 }}><GithubMark dark={p.connected} />GITHUB REPOSITORY</span>
                  <button title="프로젝트 수정" onClick={(e) => { e.stopPropagation(); setEditingProject(p); }} style={{ width: 30, height: 30, borderRadius: 15, border: '1px solid ' + (p.connected ? 'rgba(255,255,255,0.18)' : line), background: p.connected ? 'rgba(255,255,255,0.10)' : 'rgba(255,255,255,0.78)', color: p.connected ? 'rgba(255,255,255,0.82)' : muted, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 0 }}><EditIcon /></button>
                </div>
                <div style={{ marginTop: 'auto', fontSize: 13, fontWeight: 900 }}>{p.repoVisual}</div>
              </div>
              <div style={{ padding: 18 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}><Logo text={p.name.slice(0,2).toUpperCase()} /><div><div style={{ fontSize: 18, fontWeight: 900 }}>{p.name}</div><div style={{ color: faint, fontSize: 12.5 }}>{p.repo}</div></div></div>
                <p style={{ color: muted, fontSize: 13.5, lineHeight: 1.55 }}>{p.summary}</p>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>{p.previewSkills.map((skill) => <SkillChip key={skill} s={skill} />)}</div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 9, marginTop: 15, flexWrap: 'wrap' }}>
                  <button onClick={(e) => { e.stopPropagation(); setPreviewProject(p.name); setPreviewOpen(true); }} style={{ font: 'inherit', border: 'none', background: green, color: ink, borderRadius: 16, padding: '8px 12px', fontSize: 12.5, fontWeight: 900 }}>{p.matchedJobs}개 매칭 공고 미리보기</button>
                  <span style={{ color: faint, fontSize: 12 }}>마지막 분석 · {p.analyzedAt}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
        <aside style={{ ...tile, position: narrow ? 'static' : 'sticky', top: 86 }}>
          <SecTitle>TOP MATCHES</SecTitle>
          <H>내 스택과 매칭률 높은 공고</H>
          <div style={{ color: muted, fontSize: 12.5, lineHeight: 1.55, marginBottom: 12 }}>현재 미리보기 기준: <b style={{ color: ink }}>{selected.name}</b>. 공고를 누르면 상세 공고로 이동합니다.</div>
          <div style={{ display: 'grid', gap: 10 }}>{JF.matches.map((m) => <div key={m.companyKo} onClick={() => go('detail', { company: m.companyKo })} style={{ border: '1px solid ' + line, borderRadius: 14, padding: 13, cursor: 'pointer' }}><div style={{ display: 'flex', gap: 10, alignItems: 'center' }}><Logo text={m.logo} size={36} /><div style={{ flex: 1 }}><b>{m.companyKo}</b><div style={{ color: muted, fontSize: 12.5 }}>{m.fullTitle}</div></div><b style={{ color: greenInk, ...num }}>{m.score}%</b></div><div style={{ display: 'flex', flexWrap: 'wrap', gap: 5, marginTop: 10 }}>{m.matched.slice(0,3).map((skill) => <SkillChip key={skill} s={skill} owned />)}{m.missing.slice(0,1).map((skill) => <SkillChip key={skill} s={skill} miss />)}</div><div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 8, marginTop: 11, color: faint, fontSize: 12, fontWeight: 800 }}><span style={{ background: soft, border: '1px solid ' + line, borderRadius: 10, padding: '6px 8px', textAlign: 'center', whiteSpace: 'nowrap' }}>{m.level}</span><span style={{ background: coralTint, border: '1px solid ' + coralTintBd, color: coralDeep, borderRadius: 10, padding: '6px 8px', textAlign: 'center', whiteSpace: 'nowrap' }}>{m.deadline}</span><span style={{ background: soft, border: '1px solid ' + line, borderRadius: 10, padding: '6px 8px', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 4, whiteSpace: 'nowrap', ...num }}><EyeIcon size={13} />{m.views}</span></div></div>)}</div>
        </aside>
      </div>
      {previewOpen && <div style={{ position: 'fixed', inset: 0, zIndex: 560, background: 'rgba(20,21,26,0.32)', display: 'flex', justifyContent: 'flex-end' }} onClick={() => setPreviewOpen(false)}><div onClick={(e) => e.stopPropagation()} style={{ width: 520, maxWidth: '100%', height: '100%', background: '#fff', padding: 26, boxShadow: '-18px 0 60px rgba(20,21,26,0.2)', overflow: 'auto' }}><div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 18 }}><div><SecTitle>PROJECT MATCH PREVIEW</SecTitle><H>{selected.name} 기준 추천 공고</H><div style={{ color: muted, fontSize: 13 }}>카드를 누르면 공고 상세로 이동합니다.</div></div><button onClick={() => setPreviewOpen(false)} style={{ marginLeft: 'auto', border: 'none', background: soft, borderRadius: 14, width: 32, height: 32, cursor: 'pointer' }}>×</button></div><div style={{ display: 'grid', gap: 12 }}>{JF.matches.map((m, i) => <JobThumbnail key={m.companyKo + i} job={m} score={m.score} compact />)}</div></div></div>}
      {editingProject && <div style={{ position: 'fixed', inset: 0, zIndex: 600, background: 'rgba(20,21,26,0.38)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20 }} onClick={() => setEditingProject(null)}><div onClick={(e) => e.stopPropagation()} style={{ width: 430, maxWidth: '100%', background: '#fff', borderRadius: 22, padding: 24, boxShadow: '0 24px 80px rgba(0,0,0,0.28)' }}><div style={{ display: 'flex', alignItems: 'center', gap: 9, marginBottom: 16 }}><GithubMark /><b style={{ fontSize: 19 }}>프로젝트 수정</b><button onClick={() => setEditingProject(null)} style={{ marginLeft: 'auto', border: 'none', background: soft, borderRadius: 14, width: 28, height: 28, cursor: 'pointer' }}>×</button></div>{[['프로젝트 이름', editingProject.name], ['레포 주소', editingProject.repo], ['썸네일 메모', editingProject.repoVisual]].map(([label, value]) => <label key={label} style={{ display: 'block', marginBottom: 12 }}><div style={{ fontSize: 12, color: muted, fontWeight: 800, marginBottom: 6 }}>{label}</div><input defaultValue={value} style={{ width: '100%', boxSizing: 'border-box', font: 'inherit', border: '1px solid ' + line, borderRadius: 12, padding: '12px 13px' }} /></label>)}<button onClick={() => setEditingProject(null)} style={{ font: 'inherit', width: '100%', border: 'none', background: green, color: ink, borderRadius: 14, padding: 13, fontWeight: 900 }}>저장하기</button></div></div>}
    </Shell>;
  };

  const projectNewScreen = () => <Shell title="새 레포 분석" sub="GitHub 레포 URL을 등록하고 분석이 끝나면 프로젝트 상세와 추천 공고로 이어집니다." actions={<ActionButton onClick={() => go('projects')}>프로젝트 목록</ActionButton>}>
    <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1fr 0.86fr', gap: 18, alignItems: 'start' }}>
      <section style={{ ...tile, padding: 26 }}>
        <SecTitle>REPOSITORY INPUT</SecTitle>
        <H>분석할 레포지토리</H>
        <Field label="프로젝트 이름" value="sample-repo" />
        <Field label="GitHub 레포 URL" value="https://github.com/example-org/sample-repo" />
        <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1fr 1fr', gap: 12 }}>
          <label><div style={{ fontSize: 12, color: muted, fontWeight: 850, marginBottom: 6 }}>기준 브랜치</div><select defaultValue="main" style={{ width: '100%', font: 'inherit', border: '1px solid ' + line, borderRadius: 13, padding: '12px 13px', background: '#fff' }}><option>main</option><option>develop</option><option>직접 입력</option></select></label>
          <label><div style={{ fontSize: 12, color: muted, fontWeight: 850, marginBottom: 6 }}>분석 목적</div><select defaultValue="추천 공고 매칭" style={{ width: '100%', font: 'inherit', border: '1px solid ' + line, borderRadius: 13, padding: '12px 13px', background: '#fff' }}><option>추천 공고 매칭</option><option>갭 분석</option><option>포트폴리오 문장 생성</option></select></label>
        </div>
        <div style={{ marginTop: 14, display: 'flex', flexWrap: 'wrap', gap: 8 }}><ActionButton primary onClick={() => go('project-analysis')}>분석 시작</ActionButton><ActionButton onClick={() => go('projects')}>URL만 저장</ActionButton></div>
        <div style={{ borderTop: '1px solid ' + line, marginTop: 22, paddingTop: 18 }}>
          <SecTitle>VALIDATION STATES</SecTitle>
          <div style={{ display: 'grid', gap: 10 }}><StatePanel type="forbidden" title="private 레포 권한 필요" desc="GitHub scope가 부족하면 권한 재연동 버튼을 보여줍니다." action="GitHub 재연동" /><StatePanel type="error" title="레포를 찾을 수 없습니다" desc="URL 오타, 삭제된 레포, 접근 불가를 구분해 입력 필드 아래에 연결합니다." secondary="URL 다시 확인" /></div>
        </div>
      </section>
      <aside style={{ display: 'grid', gap: 14 }}>
        <section style={{ ...tile, background: ink, color: '#fff' }}>
          <SecTitle color={green}>ANALYSIS PIPELINE</SecTitle>
          <div style={{ fontSize: 22, fontWeight: 900, letterSpacing: -0.5, lineHeight: 1.25 }}>분석은 저장 후 백그라운드로 진행돼도 이해돼야 해요.</div>
          <div style={{ marginTop: 18, background: '#fff', color: ink, borderRadius: 16, padding: 16 }}><TimelineStep done title="레포 접근 확인" desc="provider token과 repository URL을 검증합니다." /><TimelineStep active title="스킬 추출" desc="정적 분석으로 언어, 프레임워크, 인프라 근거를 읽습니다." /><TimelineStep title="경험 태그 요약" desc="코드 근거에서 이벤트, 캐시, CI/CD 같은 경험을 추출합니다." /><TimelineStep title="공고 매칭 계산" desc="프로젝트 스킬과 공고 필수/우대 스킬을 비교합니다." /></div>
        </section>
        <section style={tile}>
          <SecTitle>AFTER ANALYSIS</SecTitle>
          <div style={{ display: 'grid', gap: 10 }}>{[['추출 스킬', 'Java · Spring Boot · Kafka'], ['생성되는 화면', '프로젝트 상세 · 추천 피드 · 갭 분석'], ['다음 CTA', '매칭 공고 보기 / 부족 스킬 보기']].map(([l, v]) => <div key={l} style={{ display: 'grid', gridTemplateColumns: '110px 1fr', gap: 10, borderTop: '1px solid ' + line, paddingTop: 10 }}><span style={{ color: faint, fontSize: 12, fontWeight: 900 }}>{l}</span><b style={{ fontSize: 13.5 }}>{v}</b></div>)}</div>
        </section>
      </aside>
    </div>
  </Shell>;

  const projectAnalysisScreen = () => {
    const p = JF.projectList.find((x) => x.name === previewProject && x.overview) || JF.projectList[0];
    const detailTags = p.detailTags || JF.expTags;
    const stat = (v, l) => <div><b style={{ fontSize: 25, letterSpacing: -1, ...num }}>{v}</b><div style={{ fontSize: 11.5, color: muted, fontWeight: 700, marginTop: 2 }}>{l}</div></div>;
    return <Shell title={p.name}
      sub={<span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}><span style={{ position: 'relative', display: 'inline-flex', width: 18, height: 18, alignItems: 'center', justifyContent: 'center' }}><GithubMark size={17} /><span style={{ position: 'absolute', right: -5, bottom: -5, width: 13, height: 13, borderRadius: 7, background: green, border: '2px solid #fff', color: ink, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 9, fontWeight: 1000, lineHeight: 1 }}>✓</span></span><span style={{ whiteSpace: 'nowrap' }}>{p.repo}</span><span style={{ whiteSpace: 'nowrap' }}>· 마지막 분석 {p.analyzedAt}</span></span>}
      actions={<div style={{ display: 'flex', gap: 8 }}>{p.connected ? <><button onClick={() => go('projects')} style={{ font: 'inherit', cursor: 'pointer', border: '1px solid ' + line, background: '#fff', color: ink, borderRadius: 22, padding: '12px 16px', fontWeight: 800 }}>레포 변경</button><button onClick={() => go('project-new')} style={{ font: 'inherit', cursor: 'pointer', border: 'none', background: ink, color: '#fff', borderRadius: 22, padding: '12px 18px', fontWeight: 800 }}>재분석 받기</button></> : <button onClick={() => go('login')} style={{ font: 'inherit', cursor: 'pointer', border: 'none', background: green, color: ink, borderRadius: 22, padding: '12px 18px', fontWeight: 900, display: 'flex', alignItems: 'center', gap: 7 }}><GithubMark size={15} />GitHub 연결하기</button>}</div>}>
      <div style={{ display: 'grid', gap: 18 }}>
        <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : 'repeat(3, 1fr)', gap: 12 }}>
          <StatePanel type="loading" title="분석 중" desc="레포를 클론하고 정적 분석과 LLM 요약을 순서대로 처리합니다." />
          <StatePanel type="error" title="분석 실패" desc="레포 접근 권한, 브랜치 없음, 파일 파싱 실패 사유를 보여줍니다." action="재분석" />
          <StatePanel type="unauthorized" title="GitHub 미연결" desc="URL만 입력된 프로젝트는 스킬 추출 전 GitHub 연결을 요청합니다." action="레포지토리 연결" />
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1.4fr 1fr', gap: 18, alignItems: 'stretch' }}>
          <div style={tile}>
            <SecTitle>OVERVIEW</SecTitle>
            <p style={{ fontSize: 15, lineHeight: 1.75, color: '#33363e', margin: '0 0 16px' }}>{p.overview}</p>
            <div style={{ display: 'flex', gap: 22, flexWrap: 'wrap' }}>
              <div><div style={{ fontSize: 11.5, color: faint, fontWeight: 700, marginBottom: 6 }}>도메인</div><div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>{p.domain.map((d) => <span key={d} style={{ fontSize: 12.5, fontWeight: 700, color: ink, background: soft, borderRadius: 8, padding: '5px 10px' }}>{d}</span>)}</div></div>
              <div><div style={{ fontSize: 11.5, color: faint, fontWeight: 700, marginBottom: 6 }}>아키텍처</div><span style={{ fontSize: 12.5, fontWeight: 700, color: greenInk, background: greenTint, border: '1px solid ' + greenTintBd, borderRadius: 8, padding: '5px 10px' }}>{p.architecture}</span></div>
            </div>
            <div style={{ borderTop: '1px solid ' + line, marginTop: 22, paddingTop: 18 }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10, marginBottom: 10 }}>
                <div style={{ fontSize: 11.5, color: faint, fontWeight: 900, letterSpacing: 0.6 }}>TECH STACK</div>
                <span style={{ fontSize: 11.5, color: greenInk, fontWeight: 900, background: greenTint, border: '1px solid ' + greenTintBd, borderRadius: 14, padding: '3px 8px' }}>{p.skillsTotal || 24}개 스킬 추출</span>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: 10 }}>
                {p.stackGroups.map((grp) => <div key={grp.label}>
                  <div style={{ fontSize: 12, fontWeight: 900, color: muted, marginBottom: 9 }}>{grp.label}</div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>{grp.items.slice(0, 4).map((it) => <span key={it.n} style={{ fontSize: 12.5, fontWeight: 760, color: ink, background: it.pct ? greenTint : soft, border: '1px solid ' + (it.pct ? greenTintBd : line), borderRadius: 8, padding: '5px 9px', whiteSpace: 'nowrap' }}>{it.n}{it.pct ? <b style={{ color: greenInk, marginLeft: 5, ...num }}>{it.pct}%</b> : null}</span>)}</div>
                </div>)}
              </div>
            </div>
          </div>
          <div style={tile}>
            <SecTitle>CODE STATS</SecTitle>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 16, marginBottom: 18 }}>{stat(p.stats.commits, '커밋')}{stat(p.stats.files, '파일')}{stat(p.stats.tests + '%', '테스트 커버리지')}{stat(p.stats.contributors, '기여자')}</div>
            <div style={{ fontSize: 12, fontWeight: 800, color: muted, marginBottom: 9 }}>주요 디렉토리</div>
            {p.dirs.map((d) => <div key={d.path} style={{ display: 'grid', gridTemplateColumns: '1fr 76px 34px', gap: 8, alignItems: 'center', marginBottom: 8 }}><span style={{ fontSize: 12.5, fontWeight: 600, fontFamily: 'monospace', color: ink, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{d.path}</span><div style={{ height: 6, background: soft, borderRadius: 3, overflow: 'hidden' }}><div style={{ width: d.share + '%', height: '100%', background: green, borderRadius: 3 }} /></div><span style={{ fontSize: 11.5, color: muted, fontWeight: 700, textAlign: 'right', ...num }}>{d.share}%</span></div>)}
          </div>
        </div>

        <div style={tile}>
          <H>추출 스킬과 분석 근거</H>
          <div style={{ fontSize: 12.5, color: muted, marginBottom: 6 }}>근거 강도는 숙련도가 아니라 코드 검출 빈도·신뢰도를 나타내요 (0–100).</div>
          <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1fr 1fr', gap: '0 28px' }}>{JF.skills.map((sk) => <div key={sk.name} style={{ display: 'grid', gridTemplateColumns: '120px 1fr', gap: 12, padding: '13px 0', borderTop: '1px solid ' + line }}><div><b>{sk.name}</b><div style={{ fontSize: 12, color: faint, marginTop: 3 }}>{sk.level} {sk.conf}</div></div><div><div style={{ height: 7, background: soft, borderRadius: 4, overflow: 'hidden' }}><div style={{ width: sk.conf + '%', height: '100%', background: green, borderRadius: 4 }} /></div><div style={{ fontSize: 12, color: muted, marginTop: 6, lineHeight: 1.5 }}>{sk.reason}</div></div></div>)}</div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1fr 1fr', gap: 18, alignItems: 'stretch' }}>
          <div style={{ background: ink, color: '#fff', borderRadius: 20, padding: 22 }}>
            <SecTitle color={green}>EXPERIENCE TAGS</SecTitle>
            {detailTags.map((tag) => <div key={tag.code} style={{ marginBottom: 14 }}><span style={{ fontSize: 12.5, fontWeight: 800, color: green, background: 'rgba(185,236,42,0.14)', borderRadius: 8, padding: '4px 9px' }}>#{tag.label}</span><p style={{ color: 'rgba(255,255,255,0.78)', fontSize: 13, lineHeight: 1.62, margin: '8px 0 0' }}>{tag.sentence}</p></div>)}
          </div>
          <div style={tile}>
            <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between' }}><H>이 레포와 잘 맞는 공고</H><span style={{ fontSize: 12.5, fontWeight: 800, color: greenInk }}>{p.matchedJobs}개</span></div>
            {JF.matches.map((m) => <div key={m.companyKo} onClick={() => go('detail', { company: m.companyKo })} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '11px 0', borderTop: '1px solid ' + line, cursor: 'pointer' }}><Logo text={m.logo} size={34} /><div style={{ flex: 1, minWidth: 0 }}><b style={{ fontSize: 14 }}>{m.companyKo}</b><div style={{ color: muted, fontSize: 12, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{m.title} · {m.level}</div></div><b style={{ color: ink, ...num }}>{m.score}%</b></div>)}
            <button onClick={() => go('jobs')} style={{ font: 'inherit', cursor: 'pointer', width: '100%', marginTop: 14, border: '1px solid ' + line, background: '#fff', color: ink, borderRadius: 12, padding: 12, fontWeight: 800 }}>매칭 공고 전체 보기 →</button>
          </div>
        </div>
      </div>
    </Shell>;
  };

  const LockIcon = ({ size = 15 }) => <svg width={size} height={size} viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0 }}><rect x="3" y="7" width="10" height="7" rx="1.5"/><path d="M5 7V5a3 3 0 0 1 6 0v2"/></svg>;
  const gapScreen = () => {
    const UnlockJobs = ({ jobs }) => <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '132px 1fr', gap: 10, alignItems: 'start', marginTop: 14 }}><div style={{ display: 'inline-flex', alignItems: 'center', gap: 6, color: faint, fontSize: 11.5, fontWeight: 900, paddingTop: 6 }}><LockIcon size={13} />잠금 해제 공고</div><div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>{jobs.map((x) => <span key={x} onClick={() => go('detail', { company: x })} style={{ background: greenTint, color: greenInk, border: '1px solid ' + greenTintBd, borderRadius: 16, padding: '6px 11px', fontSize: 12.5, fontWeight: 900, cursor: 'pointer' }}>{x}</span>)}</div></div>;
    return <Shell title="갭 분석" sub="공고가 아니라 스킬이 주인공입니다. 무엇을 보강하면 열리는 공고가 얼마나 늘어나는지 보여줍니다."><div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '0.95fr 1.25fr', gap: 18 }}><div style={{ background: ink, color: '#fff', borderRadius: 22, padding: 26, boxShadow: '0 14px 30px rgba(20,21,26,0.16)', display: 'flex', flexDirection: 'column' }}><SecTitle color={green}>GAP ANALYSIS</SecTitle><div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 18 }}><span style={{ background: 'rgba(255,255,255,0.12)', border: '1px solid rgba(255,255,255,0.18)', borderRadius: 18, padding: '6px 10px', fontSize: 12, fontWeight: 900 }}>{currentProjectName}</span><span style={{ background: 'rgba(255,255,255,0.12)', border: '1px solid rgba(255,255,255,0.18)', borderRadius: 18, padding: '6px 10px', fontSize: 12, fontWeight: 900 }}>백엔드</span><span style={{ background: 'rgba(255,255,255,0.12)', border: '1px solid rgba(255,255,255,0.18)', borderRadius: 18, padding: '6px 10px', fontSize: 12, fontWeight: 900 }}>주니어</span></div><div style={{ fontSize: 24, fontWeight: 900, lineHeight: 1.25, letterSpacing: -0.5 }}>내 프로젝트 스킬과 진행 중인 공고를 비교해 부족한 스킬을 집계합니다.</div><p style={{ color: 'rgba(255,255,255,0.65)', lineHeight: 1.65 }}>{currentProjectSkillSummary} 기준으로 필수 스킬과 우대 스킬을 나눠 매칭률을 계산하고, 어떤 스킬을 보강하면 더 많은 공고가 열리는지 우선순위로 보여줍니다.</p>{JF.trends.map((tr) => <div key={tr.name} style={{ display: 'grid', gridTemplateColumns: '104px 1fr 56px', gap: 10, alignItems: 'center', marginTop: 13 }}><span style={{ color: tr.owned ? green : '#fff', fontWeight: 800 }}>{tr.name}</span><div style={{ height: 8, background: 'rgba(255,255,255,0.12)', borderRadius: 4 }}><div style={{ width: tr.rate + '%', height: '100%', background: tr.owned ? green : coral, borderRadius: 4 }} /></div><span style={{ color: tr.owned ? green : '#ffb4a6', fontSize: 12, fontWeight: 900, textAlign: 'right' }}>{tr.owned ? '충족' : '부족'}</span></div>)}<button onClick={() => go('jobs')} style={{ marginTop: 22, font: 'inherit', cursor: 'pointer', border: 'none', background: green, color: ink, borderRadius: 14, padding: 13, fontWeight: 900 }}>부족 스킬 요구 공고 보기 →</button></div><div style={{ display: 'grid', gap: 12 }}>{JF.gapSkills.map((g, i) => <div key={g.skill} style={{ ...tile, borderColor: i === 0 ? greenStrong : line, padding: 24 }}><div style={{ display: 'flex', alignItems: 'center', gap: 12 }}><div style={{ width: 42, height: 42, borderRadius: 14, background: i === 0 ? green : greenTint, color: ink, display: 'flex', alignItems: 'center', justifyContent: 'center' }}><LockIcon size={18} /></div><div style={{ flex: 1, minWidth: 0 }}><div style={{ display: 'flex', alignItems: 'baseline', gap: 9, flexWrap: 'wrap' }}><b style={{ fontSize: 26, letterSpacing: -0.8 }}>{g.skill}</b><span style={{ color: muted, fontSize: 13, fontWeight: 700 }}>배우면 열리는 기회</span></div><div style={{ color: muted, fontSize: 13, lineHeight: 1.55, marginTop: 4 }}>{g.reason}</div></div><span style={{ display: 'inline-flex', alignItems: 'baseline', gap: 3, whiteSpace: 'nowrap' }}><span style={{ fontSize: 12.5, fontWeight: 700, color: muted }}>매칭 후보</span><b style={{ color: ink, fontWeight: 900, fontSize: 24, ...num }}>+{g.addedJobs}</b><span style={{ fontSize: 13, fontWeight: 800, color: muted }}>개</span></span></div><div style={{ color: ink, fontSize: 12.5, lineHeight: 1.55, marginTop: 14, background: soft, borderRadius: 12, padding: '10px 12px' }}>다음 액션: 미니 프로젝트나 기존 레포에 {g.skill} 사용 근거를 만들고 재분석하면 추천 후보가 늘어납니다.</div><UnlockJobs jobs={g.unlocked} /></div>)}</div></div></Shell>;
  };


  const recommendationsScreen = () => {
    const feed = JF.matches.concat(JF.listings.slice(0, 5)).map((m, i) => ({ ...m, score: (m.score || 74) - Math.min(i, 6) }));
    return <Shell title="추천 피드" sub="프로젝트 기준 추천 공고를 계속 넘겨 보며 저장하거나 상세로 들어갑니다." actions={<div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}><ActionButton primary onClick={() => go('projects')}>{currentProjectName} 기준</ActionButton><ActionButton onClick={() => go('jobs')}>백엔드</ActionButton><ActionButton onClick={() => go('jobs')}>신입·주니어</ActionButton></div>}><div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '320px 1fr', gap: 18, alignItems: 'start' }}><aside style={{ display: 'grid', gap: 12 }}><div style={{ background: ink, color: '#fff', borderRadius: 22, padding: 22 }}><SecTitle color={green}>RECOMMENDATION</SecTitle><div style={{ fontSize: 28, fontWeight: 900, letterSpacing: -1, lineHeight: 1.15 }}>매칭률과 마감 신호를 섞어<br /><span style={{ color: green }}>먼저 볼 공고</span>를 정렬합니다.</div><div style={{ color: 'rgba(255,255,255,0.58)', fontSize: 12.5, marginTop: 10, lineHeight: 1.5 }}>{currentProjectRepo}</div><div style={{ display: 'grid', gap: 10, marginTop: 20 }}>{[['스킬 매칭', '70%'], ['신선도', '15%'], ['행동 신호', '10%'], ['인기', '5%']].map(([l, v]) => <div key={l} style={{ display: 'grid', gridTemplateColumns: '1fr 52px', gap: 10, alignItems: 'center' }}><span style={{ color: 'rgba(255,255,255,0.72)', fontSize: 13, fontWeight: 800 }}>{l}</span><b style={{ color: green, textAlign: 'right', ...num }}>{v}</b></div>)}</div></div><StatePanel type="empty" title="추천이 비어 있을 때" desc="프로젝트를 먼저 분석하면 추천 공고가 표시됩니다." action="프로젝트 분석" /><StatePanel type="error" title="추천 계산 실패" desc="추천 API가 실패하면 이전 추천을 보여주고 재시도 버튼을 제공합니다." action="다시 계산" /></aside><section><div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : 'repeat(2,1fr)', gap: 14 }}>{feed.map((m, i) => <JobThumbnail key={(m.companyKo || m.company) + i} job={{ ...m, skills: m.matched || m.skills }} score={m.score} compact />)}</div></section></div></Shell>;
  };
  const trendsScreen = () => <Shell title="트렌드" sub={isV3 ? "시장 리포트에서 끝나지 않고, 내 미보유 스킬과 바로 볼 수 있는 공고로 연결합니다." : "사용자 직무 설정 기반 맞춤 리포트입니다. 데이터 바보다 먼저 해석을 보여줍니다."} actions={<select style={{ font: 'inherit', border: '1px solid ' + line, background: '#fff', borderRadius: 12, padding: '11px 14px', fontWeight: 900 }}><option>백엔드 · 주니어</option><option>프론트엔드 · 주니어</option><option>AI · 주니어</option><option>보안 · 주니어</option></select>}>
    <div style={{ display: 'grid', gap: 18 }}>
      <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1.4fr 1fr', gap: 0, background: ink, color: '#fff', borderRadius: 22, overflow: 'hidden', boxShadow: '0 14px 30px rgba(20,21,26,0.16)' }}>
        <div style={{ padding: 28 }}>
          <SecTitle color={green}>CUSTOM REPORT</SecTitle>
          <div style={{ fontSize: narrow ? 25 : 30, fontWeight: 900, lineHeight: 1.28, letterSpacing: -0.7 }}>백엔드 주니어 공고에서 <span style={{ color: green }}>Kafka 수요가 12%↑</span> 신호를 보입니다.</div>
          <p style={{ color: 'rgba(255,255,255,0.68)', lineHeight: 1.7 }}>아직 Kafka 근거가 부족하고, 커머스·결제 도메인 공고에서 Redis·Spring Boot와 함께 요구되는 빈도가 높습니다.</p>
        </div>
        <div style={{ padding: 28, borderLeft: narrow ? 'none' : '1px solid rgba(255,255,255,0.1)', borderTop: narrow ? '1px solid rgba(255,255,255,0.1)' : 'none', display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
          {JF.trends.slice().sort((a,b)=>b.growth-a.growth).map((tr) => <div key={tr.name} style={{ display: 'grid', gridTemplateColumns: '96px 1fr 48px', gap: 12, alignItems: 'center', marginBottom: 13 }}><b style={{ color: tr.owned ? green : '#fff', fontSize: 13.5 }}>{tr.name}</b><div style={{ height: 8, background: 'rgba(255,255,255,0.12)', borderRadius: 4 }}><div style={{ width: tr.rate + '%', height: '100%', background: tr.owned ? green : coral, borderRadius: 4 }} /></div><span style={{ color: tr.owned ? green : '#ffb4a6', textAlign: 'right', fontWeight: 900, fontSize: 13 }}>↑{tr.growth}%</span></div>)}
        </div>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : 'repeat(3, 1fr)', gap: 16 }}>
        <div style={tile}><H>내 스킬과의 차이</H>{JF.trends.filter((x) => !x.owned).map((tr) => <div key={tr.name} onClick={() => setTrendSkill(tr.name)} style={{ padding: '11px 0', borderTop: '1px solid ' + line, cursor: 'pointer' }}><b style={{ color: coralDeep }}>{tr.name} 근거 부족</b><div style={{ color: muted, fontSize: 13, lineHeight: 1.55, marginTop: 4 }}>{tr.insight}</div>{isV3 && <button onClick={(e) => { e.stopPropagation(); go('jobs'); }} style={{ marginTop: 8, font: 'inherit', border: '1px solid ' + line, background: '#fff', borderRadius: 12, padding: '7px 10px', fontSize: 12, fontWeight: 850, cursor: 'pointer' }}>{tr.name} 요구 공고 보기</button>}</div>)}</div>
        <div style={tile}><H>함께 뜨는 조합</H><div style={{ color: muted, fontSize: 13, lineHeight: 1.55, marginBottom: 12 }}>Kafka 공고에서 자주 함께 등장하는 스택이에요.</div><div style={{ display: 'flex', flexWrap: 'wrap', gap: 7 }}>{['Spring Boot', 'Redis', 'Docker', 'gRPC', 'Kubernetes'].map((x) => <SkillChip key={x} s={x} />)}</div></div>
        <div style={{ ...tile, background: isV3 ? '#fff' : '#14151a', color: isV3 ? ink : '#fff', border: '1px solid ' + (isV3 ? line : '#14151a') }}><SecTitle color={greenInk}>MARKET SNAPSHOT</SecTitle><div style={{ display: 'grid', gap: 14 }}>{[['이번 달 공고', JF.market.totalCount.toLocaleString() + '개'], ['평균 공고 노출', (JF.market.avgOpenDays || 18) + '일'], ['내 보유 스킬', JF.trends.filter((x)=>x.owned).length + ' / ' + JF.trends.length]].map(([l, v]) => <div key={l} style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', borderTop: '1px solid ' + (isV3 ? line : 'rgba(255,255,255,0.12)'), paddingTop: 12 }}><span style={{ color: isV3 ? muted : 'rgba(255,255,255,0.6)', fontSize: 13, fontWeight: 700 }}>{l}</span><b style={{ fontSize: 20, ...num }}>{v}</b></div>)}</div>{isV3 && <button onClick={() => go('gap')} style={{ marginTop: 16, font: 'inherit', width: '100%', cursor: 'pointer', border: 'none', background: ink, color: '#fff', borderRadius: 13, padding: 12, fontWeight: 900 }}>내 갭 우선순위 보기 →</button>}</div>
      </div>
      {isV3 && <div style={{ ...tile, display: 'grid', gridTemplateColumns: narrow ? '1fr' : '0.78fr 1.22fr', gap: 18, alignItems: 'stretch' }}><div style={{ background: ink, color: '#fff', borderRadius: 18, padding: 22 }}><SecTitle color={green}>SKILL DETAIL</SecTitle><div style={{ fontSize: 30, fontWeight: 900, letterSpacing: -1, color: green }}>{trendSkill}</div><p style={{ color: 'rgba(255,255,255,0.68)', fontSize: 13.5, lineHeight: 1.65 }}>선택한 스킬이 어떤 공고와 경험 태그에서 함께 등장하는지 확인하고, 바로 갭 분석이나 공고 목록으로 이동합니다.</p><div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginTop: 16 }}>{[['등장률', (JF.trends.find((x) => x.name === trendSkill) || JF.trends[0]).rate + '%'], ['증가율', '↑' + (JF.trends.find((x) => x.name === trendSkill) || JF.trends[0]).growth + '%'], ['관련 공고', '42개'], ['내 보유', (JF.trends.find((x) => x.name === trendSkill) || {}).owned ? '보유' : '미보유']].map(([l, v]) => <div key={l} style={{ background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.12)', borderRadius: 14, padding: 12 }}><b style={{ color: green, fontSize: 18, ...num }}>{v}</b><div style={{ color: 'rgba(255,255,255,0.56)', fontSize: 11.5, fontWeight: 800, marginTop: 2 }}>{l}</div></div>)}</div></div><div style={{ display: 'grid', gap: 12 }}><div><SecTitle>CO-OCCURRENCE</SecTitle><div style={{ display: 'flex', flexWrap: 'wrap', gap: 7 }}>{['Spring Boot', 'Redis', 'Docker', 'Kubernetes', 'MySQL', 'AWS'].map((x) => <button key={x} onClick={() => setTrendSkill(x)} style={{ font: 'inherit', cursor: 'pointer', border: '1px solid ' + (x === trendSkill ? greenStrong : line), background: x === trendSkill ? green : '#fff', color: ink, borderRadius: 18, padding: '7px 10px', fontSize: 12.5, fontWeight: 850 }}>{x}</button>)}</div></div><div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1fr 1fr', gap: 12 }}><div style={{ background: greenTint, border: '1px solid ' + greenTintBd, borderRadius: 16, padding: 16 }}><b>관련 경험 태그</b><div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 10 }}>{['EVENT_DRIVEN', 'HIGH_TRAFFIC', 'OBSERVABILITY'].map((c) => <TagChip key={c} code={c} />)}</div></div><div style={{ background: soft, border: '1px solid ' + line, borderRadius: 16, padding: 16 }}><b>다음 액션</b><div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginTop: 10 }}><ActionButton primary onClick={() => go('gap')}>갭 분석 보기</ActionButton><ActionButton onClick={() => go('jobs')}>관련 공고 보기</ActionButton></div></div></div></div></div>}
    </div>
  </Shell>;
  const mypageScreen = () => <Shell title="설정" sub="계정 정보, GitHub 연동, 로그아웃을 관리합니다." actions={<ActionButton danger onClick={() => setSettingsModal('logout')}>로그아웃</ActionButton>}><div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1fr 1fr', gap: 18, alignItems: 'start' }}><section style={{ ...tile }}><SecTitle>ACCOUNT</SecTitle><div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 18 }}><div style={{ width: 52, height: 52, borderRadius: 18, background: ink, color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 18, fontWeight: 900 }}>사</div><div><H>사용자</H><div style={{ color: muted, fontSize: 13 }}>user@example.com · GitHub 로그인</div></div></div><div style={{ display: 'grid', gap: 10 }}>{[['이름', '사용자'], ['이메일', 'user@example.com'], ['권한', '일반 사용자'], ['가입일', '2026.06.12']].map(([l, v]) => <div key={l} style={{ display: 'grid', gridTemplateColumns: '100px 1fr', gap: 12, padding: '10px 0', borderTop: '1px solid ' + line }}><span style={{ color: faint, fontSize: 12, fontWeight: 900 }}>{l}</span><b style={{ fontSize: 13.5 }}>{v}</b></div>)}</div><div style={{ marginTop: 18, display: 'flex', gap: 8, flexWrap: 'wrap' }}><ActionButton onClick={() => setSettingsModal('profile')}>프로필 수정</ActionButton><ActionButton danger onClick={() => setSettingsModal('security')}>계정 보안</ActionButton></div></section><section style={{ display: 'grid', gap: 12 }}><div style={{ ...tile, background: greenTint, borderColor: greenTintBd }}><SecTitle color={greenInk}>GITHUB</SecTitle><div style={{ display: 'flex', alignItems: 'center', gap: 12 }}><span style={{ position: 'relative', width: 38, height: 38, borderRadius: 14, background: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><GithubMark size={20} /><span style={{ position: 'absolute', right: -4, bottom: -4, width: 14, height: 14, borderRadius: 7, background: green, border: '2px solid #fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 9, fontWeight: 1000 }}>✓</span></span><div><H>GitHub 연동됨</H><div style={{ color: muted, fontSize: 13 }}>example-user · 3개 레포 분석 가능</div></div></div><div style={{ marginTop: 16, display: 'flex', gap: 8, flexWrap: 'wrap' }}><ActionButton primary onClick={() => go('projects')}>레포 관리</ActionButton><ActionButton onClick={() => setSettingsModal('reconnect')}>재연동</ActionButton><ActionButton danger onClick={() => setSettingsModal('disconnect')}>연동 해제</ActionButton></div></div><StatePanel type="forbidden" title="권한 부족 상태" desc="private repository 권한이 빠졌을 때 재연동 CTA를 보여줍니다." action="권한 다시 연결" /><StatePanel type="error" title="연동 실패 상태" desc="GitHub API 오류나 토큰 만료 시 레포 분석 대신 안내 카드를 노출합니다." action="다시 시도" /></section></div>{settingsModal && <Modal title={settingsModal === 'profile' ? '프로필 수정' : settingsModal === 'security' ? '계정 보안' : settingsModal === 'reconnect' ? 'GitHub 재연동' : settingsModal === 'disconnect' ? 'GitHub 연동 해제' : '로그아웃'} sub={settingsModal === 'disconnect' ? '연동을 해제하면 private 레포 분석과 재분석이 중단됩니다.' : settingsModal === 'logout' ? '현재 기기에서만 로그아웃됩니다.' : '설정 변경 전 사용자에게 영향 범위를 짧게 설명합니다.'} onClose={() => setSettingsModal(null)}>{settingsModal === 'profile' && <><Field label="표시 이름" value="사용자" /><Field label="이메일" value="user@example.com" /><div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}><ActionButton onClick={() => setSettingsModal(null)}>취소</ActionButton><ActionButton primary onClick={() => setSettingsModal(null)}>저장</ActionButton></div></>}{settingsModal === 'security' && <div style={{ display: 'grid', gap: 10 }}><StatePanel type="unauthorized" title="비밀번호 변경" desc="로컬 로그인 사용자는 비밀번호 변경 링크를 이메일로 받습니다." action="메일 보내기" /><StatePanel type="forbidden" title="로그인 세션 관리" desc="다른 기기의 세션을 만료시키는 액션을 제공합니다." secondary="전체 로그아웃" /></div>}{settingsModal === 'reconnect' && <><StatePanel type="loading" title="GitHub 권한 다시 확인" desc="repo read 권한을 다시 승인하면 private 레포 분석이 가능해집니다." /><div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 12 }}><ActionButton onClick={() => setSettingsModal(null)}>취소</ActionButton><ActionButton primary onClick={() => setSettingsModal(null)}>GitHub로 이동</ActionButton></div></>}{settingsModal === 'disconnect' && <><StatePanel type="forbidden" title="정말 해제할까요?" desc="이미 분석된 결과는 남지만, 새 분석과 재분석은 GitHub 재연동 전까지 불가능합니다." /><div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 12 }}><ActionButton onClick={() => setSettingsModal(null)}>취소</ActionButton><ActionButton danger onClick={() => setSettingsModal(null)}>연동 해제</ActionButton></div></>}{settingsModal === 'logout' && <><StatePanel type="empty" title="로그아웃하시겠어요?" desc="저장된 공고와 프로젝트 분석 결과는 계정에 그대로 남습니다." /><div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 12 }}><ActionButton onClick={() => setSettingsModal(null)}>취소</ActionButton><ActionButton danger onClick={() => { window.__jobflowApi?.logout?.(); setSettingsModal(null); go('login'); }}>로그아웃</ActionButton></div></>}</Modal>}</Shell>;
  const userJobsScreen = () => {
    const hasUserJobApi = ['saved', 'viewed', 'ignored'].some((key) => JF.__apiStatus?.[key] === 'ok');
    const savedJobs = hasUserJobApi ? (JF.userJobs?.saved || []) : JF.listings.slice(0, 4);
    const viewedJobs = hasUserJobApi ? (JF.userJobs?.viewed || []) : JF.listings.slice(4, 8);
    const hiddenJobs = hasUserJobApi ? (JF.userJobs?.ignored || []) : JF.listings.slice(8, 10);
    const ignoredCount = JF.ignored ?? hiddenJobs.length;
    const TabBlock = ({ title, count, desc, jobs, hidden }) => <section style={{ ...tile }}><div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 12, marginBottom: 14 }}><div><H>{title}</H><div style={{ color: muted, fontSize: 13 }}>{desc}</div></div><b style={{ color: hidden ? coralDeep : greenInk, ...num }}>{count}</b></div>{jobs.length ? <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : 'repeat(2,1fr)', gap: 12 }}>{jobs.map((j) => <JobThumbnail key={title + j.companyKo} job={j} compact />)}</div> : <StatePanel type="empty" title="아직 공고가 없습니다" desc="관심 있는 공고를 저장하면 여기에 쌓입니다." action="공고 보러가기" />}{hidden && <div style={{ marginTop: 12 }}><StatePanel type="empty" title="숨김 해제 가능" desc="숨긴 공고는 추천 피드에서 제외되며, 필요하면 다시 되돌릴 수 있어요." secondary="숨김 해제" /></div>}</section>;
    return <Shell title="저장 / 숨김 / 조회 공고" sub="관심 공고, 최근 본 공고, 숨긴 공고를 한 화면에서 관리합니다." actions={<ActionButton primary onClick={() => go('jobs')}>공고 더 보기</ActionButton>}><div style={{ display: 'grid', gap: 18 }}><div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : 'repeat(3, 1fr)', gap: 12 }}>{[['저장한 공고', JF.saved, '지원 전 다시 볼 후보'], ['최근 본 공고', JF.viewed, '읽었지만 저장하지 않은 공고'], ['숨긴 공고', ignoredCount, '추천에서 제외한 공고']].map(([l, v, d], i) => <div key={l} style={{ background: i === 2 ? coralTint : greenTint, border: '1px solid ' + (i === 2 ? coralTintBd : greenTintBd), borderRadius: 18, padding: 18 }}><b style={{ fontSize: 26, ...num }}>{v}</b><div style={{ fontWeight: 900, marginTop: 4 }}>{l}</div><div style={{ color: muted, fontSize: 12.5, marginTop: 3 }}>{d}</div></div>)}</div><TabBlock title="저장한 공고" count={JF.saved} desc="북마크한 공고입니다. 상세에서 지원 기록을 추가할 수 있어요." jobs={savedJobs} /><TabBlock title="최근 본 공고" count={JF.viewed} desc="조회 기록 기준으로 최근 열어본 공고입니다." jobs={viewedJobs} /><TabBlock title="숨긴 공고" count={ignoredCount} desc="관심 없다고 숨긴 공고입니다." jobs={hiddenJobs} hidden /></div></Shell>;
  };
  const demoStatusScreen = () => {
    const mono = "ui-monospace, 'SF Mono', 'JetBrains Mono', Menlo, monospace";
    const services = [['api-gateway', 'UP', 8080, 42, 99.98], ['mysql-primary', 'UP', 3306, 8, 99.99], ['redis-cache', 'UP', 6379, 1, 100], ['elasticsearch', 'UP', 9200, 23, 99.95], ['prometheus', 'UP', 9090, 12, 99.97], ['grafana', 'UP', 3000, 18, 99.96], ['zipkin', 'UP', 9411, 31, 99.9]];
    const smokes = [['search.precision@5', 'PASS', '0.9000', '212ms'], ['redis.cache.smoke', 'PASS', 'hit/miss ok', '9ms'], ['jd.match.smoke', 'PASS', 'top 79.95', '146ms'], ['gap.analysis.v2', 'PASS', 'evidence 8+', '188ms'], ['recommendation.api', 'PASS', 'excluded ok', '94ms'], ['deadline.reminder', 'PASS', '10k / retry', '1.2s'], ['daily.digest.smoke', 'PASS', 'retry ok', '420ms'], ['grafana.scrape', 'PASS', 'target up', '15ms']];
    const spark = (seed) => { const pts = Array.from({length:24},(_,k)=>40+Math.round(28*Math.abs(Math.sin(seed*1.3+k*0.55))+ (k%5===0?10:0))); const w=120,h=34,mx=Math.max(...pts); const d=pts.map((p,k)=>(k/(pts.length-1))*w+','+(h-(p/mx)*h)).join(' '); return <svg width={w} height={h} viewBox={'0 0 '+w+' '+h} preserveAspectRatio="none" style={{ display:'block' }}><polyline points={d} fill="none" stroke={green} strokeWidth="1.6" /><polyline points={'0,'+h+' '+d+' '+w+','+h} fill={green} opacity="0.08" stroke="none" /></svg>; };
    const Dot = ({ ok }) => <span style={{ position:'relative', width: 8, height: 8, flexShrink: 0 }}><span style={{ position:'absolute', inset: 0, borderRadius: 5, background: ok ? green : coral }} /><span className="jf-pulse" style={{ position:'absolute', inset: -3, borderRadius: 8, border: '1.5px solid ' + (ok ? green : coral) }} /></span>;
    const KPI = ({ label, value, unit, sub, seed }) => <div style={{ background: '#0e1116', border: '1px solid #1c2230', borderRadius: 16, padding: 18, position: 'relative', overflow: 'hidden' }}><div style={{ fontFamily: mono, color: '#5f6b7d', fontSize: 11, fontWeight: 700, letterSpacing: 1 }}>{label}</div><div style={{ display: 'flex', alignItems: 'baseline', gap: 4, marginTop: 8 }}><b style={{ fontFamily: mono, fontSize: 30, letterSpacing: -1, color: '#eef2f7' }}>{value}</b>{unit && <span style={{ fontFamily: mono, color: green, fontSize: 14, fontWeight: 700 }}>{unit}</span>}</div><div style={{ color: '#5f6b7d', fontSize: 11.5, marginTop: 6, fontFamily: mono }}>{sub}</div><div style={{ marginTop: 12, opacity: 0.9 }}>{spark(seed)}</div></div>;
    return <Shell title="Demo Status" sub="포트폴리오 시연용 운영 콘솔 — 서비스 헬스, 파이프라인, smoke/CI, 품질 지표를 실시간 대시보드로 보여줍니다." actions={<div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}><ActionButton onClick={() => go('home')}>사용자 홈</ActionButton><ActionButton primary onClick={() => go('project-analysis')}>핵심 플로우 보기</ActionButton></div>}>
      <div style={{ background: '#0a0d12', borderRadius: 24, padding: narrow ? 16 : 24, boxShadow: '0 24px 60px rgba(10,13,18,0.4)', backgroundImage: 'linear-gradient(#11151c 1px, transparent 1px), linear-gradient(90deg, #11151c 1px, transparent 1px)', backgroundSize: '34px 34px' }}>
        {/* console header bar */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap', padding: '4px 4px 18px', borderBottom: '1px solid #1c2230', marginBottom: 18 }}>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontFamily: mono, color: green, fontSize: 13, fontWeight: 800 }}><Dot ok /> ALL SYSTEMS OPERATIONAL</span>
          <span style={{ fontFamily: mono, color: '#5f6b7d', fontSize: 12.5 }}>env=<span style={{ color: '#aeb7c4' }}>prod</span></span>
          <span style={{ fontFamily: mono, color: '#5f6b7d', fontSize: 12.5 }}>build=<span style={{ color: '#aeb7c4' }}>#1284</span></span>
          <span style={{ fontFamily: mono, color: '#5f6b7d', fontSize: 12.5 }}>commit=<span style={{ color: green }}>a3f9c1e</span></span>
          <span style={{ marginLeft: 'auto', fontFamily: mono, color: '#5f6b7d', fontSize: 12.5 }}>uptime <span style={{ color: '#aeb7c4' }}>99.97%</span> · 14d 06h</span>
        </div>
        {/* KPI row */}
        <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr 1fr' : 'repeat(4, 1fr)', gap: 12 }}>
          <KPI label="REAL JOBS INDEXED" value="314" sub="JUMPIT · WANTED 수집" seed={1} />
          <KPI label="SEARCH PRECISION@5" value="0.900" sub="real-source eval" seed={2} />
          <KPI label="REQ THROUGHPUT" value="1.2k" unit="rpm" sub="p99 212ms" seed={3} />
          <KPI label="BATCH PROCESSED" value="10k" unit="ok" sub="mock provider retry" seed={4} />
        </div>
        {/* health + architecture */}
        <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1.05fr 0.95fr', gap: 14, marginTop: 14 }}>
          <section style={{ background: '#0e1116', border: '1px solid #1c2230', borderRadius: 16, padding: 18 }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}><span style={{ fontFamily: mono, color: '#5f6b7d', fontSize: 11, fontWeight: 800, letterSpacing: 1 }}>SYSTEM HEALTH</span><span style={{ fontFamily: mono, color: green, fontSize: 11, fontWeight: 800 }}>7/7 UP</span></div>
            <div style={{ display: 'grid', gap: 0, marginTop: 8 }}>{services.map(([name, st, port, lat, up]) => <div key={name} style={{ display: 'grid', gridTemplateColumns: '16px 1fr 58px 64px', gap: 10, alignItems: 'center', padding: '11px 0', borderTop: '1px solid #161b24' }}><Dot ok={st==='UP'} /><span style={{ fontFamily: mono, fontSize: 13, color: '#dfe5ec', fontWeight: 600 }}>{name}<span style={{ color: '#4b5666' }}>:{port}</span></span><span style={{ fontFamily: mono, fontSize: 12, color: '#8b95a4', textAlign: 'right' }}>{lat}ms</span><span style={{ fontFamily: mono, fontSize: 12, color: green, textAlign: 'right', fontWeight: 700 }}>{up}%</span></div>)}</div>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 16 }}>{['Grafana', 'Swagger', 'Prometheus', 'Zipkin'].map((x) => <span key={x} style={{ fontFamily: mono, fontSize: 12, fontWeight: 700, color: '#aeb7c4', background: '#151a22', border: '1px solid #232a38', borderRadius: 9, padding: '7px 11px' }}>{x} ↗</span>)}</div>
          </section>
          <section style={{ background: '#0e1116', border: '1px solid #1c2230', borderRadius: 16, padding: 18 }}>
            <span style={{ fontFamily: mono, color: '#5f6b7d', fontSize: 11, fontWeight: 800, letterSpacing: 1 }}>DATA PIPELINE</span>
            <div style={{ display: 'grid', gap: 9, marginTop: 12 }}>{[['ingest', 'GitHub analysis → skill inventory', green], ['index', 'collector → normalize → ES index', green], ['serve', 'JD match → gap → recommendation', green], ['notify', 'outbox → batch → retry / idempotency', green]].map(([tag, txt], k) => <div key={tag} style={{ display: 'flex', alignItems: 'center', gap: 11, padding: '11px 13px', background: '#11151c', border: '1px solid #1c2230', borderRadius: 12 }}><span style={{ fontFamily: mono, fontSize: 10.5, fontWeight: 800, color: green, background: 'rgba(185,236,42,0.1)', border: '1px solid rgba(185,236,42,0.25)', borderRadius: 7, padding: '3px 7px', minWidth: 52, textAlign: 'center' }}>{tag}</span><span style={{ fontFamily: mono, fontSize: 12.5, color: '#cdd4dd', fontWeight: 600, lineHeight: 1.4 }}>{txt}</span><span style={{ marginLeft: 'auto', color: green, fontSize: 13 }}>✓</span></div>)}</div>
          </section>
        </div>
        {/* smoke + quality */}
        <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1.25fr 0.75fr', gap: 14, marginTop: 14 }}>
          <section style={{ background: '#0e1116', border: '1px solid #1c2230', borderRadius: 16, padding: 18 }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}><span style={{ fontFamily: mono, color: '#5f6b7d', fontSize: 11, fontWeight: 800, letterSpacing: 1 }}>CI · SMOKE RESULTS</span><span style={{ fontFamily: mono, color: green, fontSize: 11, fontWeight: 800 }}>8 passed · 0 failed</span></div>
            <div>{smokes.map(([name, st, val, dur]) => <div key={name} style={{ display: 'grid', gridTemplateColumns: '16px 1fr auto 64px', gap: 10, alignItems: 'center', padding: '10px 0', borderTop: '1px solid #161b24' }}><span style={{ color: green, fontFamily: mono, fontWeight: 900, fontSize: 13 }}>✓</span><span style={{ fontFamily: mono, fontSize: 12.5, color: '#dfe5ec' }}>{name}</span><span style={{ fontFamily: mono, fontSize: 11.5, color: '#8b95a4' }}>{val}</span><span style={{ fontFamily: mono, fontSize: 11.5, color: '#5f6b7d', textAlign: 'right' }}>{dur}</span></div>)}</div>
          </section>
          <section style={{ display: 'grid', gap: 12, gridAutoRows: 'min-content' }}>
            <div style={{ background: '#0e1116', border: '1px solid #1c2230', borderRadius: 16, padding: 18 }}><div style={{ fontFamily: mono, color: '#5f6b7d', fontSize: 11, fontWeight: 800, letterSpacing: 1 }}>SKILL MAPPING MISS</div><div style={{ display: 'flex', alignItems: 'baseline', gap: 6, marginTop: 8 }}><b style={{ fontFamily: mono, fontSize: 30, color: green, letterSpacing: -1 }}>0.0</b><span style={{ fontFamily: mono, color: green, fontSize: 13, fontWeight: 700 }}>%</span><span style={{ marginLeft: 'auto', fontFamily: mono, fontSize: 11, color: green, background: 'rgba(185,236,42,0.1)', border: '1px solid rgba(185,236,42,0.25)', borderRadius: 7, padding: '3px 8px', fontWeight: 800 }}>HEALTHY</span></div><div style={{ color: '#5f6b7d', fontSize: 11.5, marginTop: 6, fontFamily: mono }}>alias backfill 후 미매핑 0건</div></div>
            <div style={{ background: '#0e1116', border: '1px solid #1c2230', borderRadius: 16, padding: 18 }}><div style={{ fontFamily: mono, color: '#5f6b7d', fontSize: 11, fontWeight: 800, letterSpacing: 1 }}>INDEXED SKILLS</div><b style={{ display: 'block', fontFamily: mono, fontSize: 30, color: '#eef2f7', letterSpacing: -1, marginTop: 8 }}>1,367</b><div style={{ color: '#5f6b7d', fontSize: 11.5, marginTop: 6, fontFamily: mono }}>job_skill_index rows</div></div>
            <div style={{ background: 'linear-gradient(135deg, #16200a, #0e1116)', border: '1px solid rgba(185,236,42,0.25)', borderRadius: 16, padding: 18 }}><div style={{ display: 'flex', alignItems: 'center', gap: 8 }}><Dot ok /><span style={{ fontFamily: mono, color: green, fontSize: 11, fontWeight: 800, letterSpacing: 1 }}>OBSERVABILITY</span></div><b style={{ display: 'block', fontFamily: mono, fontSize: 22, color: '#eef2f7', marginTop: 10 }}>UP</b><div style={{ color: '#8b95a4', fontSize: 11.5, marginTop: 4, fontFamily: mono }}>Prometheus + Grafana 연동</div></div>
          </section>
        </div>
      </div>
    </Shell>;
  };

    const errorScreen = (code) => {
    const info = ({ '401': ['로그인이 필요합니다', '보호된 API를 호출하려면 JobFlow JWT가 필요해요.', '로그인으로 이동'], '403': ['접근 권한이 없습니다', '이 프로젝트나 레포는 현재 계정의 소유가 아니에요.', '프로젝트 목록으로'], '404': ['페이지를 찾을 수 없습니다', '주소가 바뀌었거나 삭제된 화면입니다.', '홈으로 이동'], '500': ['서버 오류가 발생했습니다', '잠시 후 다시 시도하거나 Demo Status에서 시스템 상태를 확인하세요.', 'Demo Status 보기'], oauth: ['OAuth 로그인이 완료되지 않았습니다', '승인을 취소했거나 authorization code가 만료됐습니다.', '다시 로그인'] })[code] || ['문제가 발생했습니다', '요청을 처리하지 못했습니다.', '홈으로 이동'];
    const action = code === '401' || code === 'oauth' ? 'login' : code === '403' ? 'projects' : code === '500' ? 'demo-status' : 'home';
    return <Shell title={code === 'oauth' ? 'OAuth Failure' : code} sub="공통 에러 페이지 세트입니다. FE 구현에서는 라우터와 API client 에러 핸들러에서 이 컴포넌트로 연결합니다." actions={<ActionButton onClick={() => go('demo-status')}>Demo Status</ActionButton>}><div style={{ minHeight: 520, display: 'grid', placeItems: 'center' }}><div style={{ width: 620, maxWidth: '100%', textAlign: 'center', background: '#fff', border: '1px solid ' + line, borderRadius: 28, padding: narrow ? 28 : 44, boxShadow: shadow }}><div style={{ margin: '0 auto 20px', width: 72, height: 72, borderRadius: 24, background: code === '500' || code === 'oauth' ? coralTint : greenTint, color: code === '500' || code === 'oauth' ? coralDeep : greenInk, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 28, fontWeight: 1000 }}>{code === '404' ? '?' : code === '401' ? '↗' : code === '403' ? '!' : '×'}</div><div style={{ fontSize: narrow ? 28 : 38, fontWeight: 900, letterSpacing: -1.2 }}>{info[0]}</div><p style={{ color: muted, fontSize: 15, lineHeight: 1.7, margin: '12px auto 22px', maxWidth: 440 }}>{info[1]}</p><div style={{ display: 'flex', gap: 8, justifyContent: 'center', flexWrap: 'wrap' }}><ActionButton primary onClick={() => go(action)}>{info[2]}</ActionButton><ActionButton onClick={() => go('home')}>홈</ActionButton></div></div></div></Shell>;
  };

  const applicationsScreen = () => {
    const stageColor = { APPLIED: { bg: '#f4f5f0', bd: '#e3e6db', fg: '#69705d' }, DOCUMENT_PASSED: { bg: '#f3f9d8', bd: '#e2efb0', fg: '#5c7012' }, CODING_TEST: { bg: '#e8f5b0', bd: '#d3e985', fg: '#46600a' }, INTERVIEW: { bg: '#d6ee70', bd: '#c4e34d', fg: '#2e4400' }, OFFER: { bg: green, bd: greenStrong, fg: ink }, REJECTED: { bg: coralTint, bd: coralTintBd, fg: coralDeep }, WITHDRAWN: { bg: soft, bd: line, fg: muted } };
    const statusLabel = { APPLIED: '지원 완료', DOCUMENT_PASSED: '서류 합격', CODING_TEST: '코딩테스트', INTERVIEW: '면접 진행', OFFER: '오퍼', REJECTED: '불합격', WITHDRAWN: '지원 취소' };
    return <Shell title="지원 현황" sub="외부에서 지원한 공고를 단계별로 추적하고, 상태를 바로 변경합니다." actions={<ActionButton primary onClick={() => setAddingApplication(true)}>지원 기록 추가</ActionButton>}>
      <div style={{ display: 'grid', gridTemplateColumns: narrow ? 'repeat(2,1fr)' : 'repeat(5,1fr)', gap: 10, marginBottom: 22 }}>{JF.pipeline.map((p, i) => { const c = stageColor[p.key]; return <div key={p.key} style={{ background: c.bg, border: '1px solid ' + c.bd, borderRadius: 16, padding: '16px 18px' }}><div style={{ fontSize: 10.5, fontWeight: 800, color: c.fg, letterSpacing: 0.5, marginBottom: 4 }}>STEP {i + 1}</div><b style={{ fontSize: 28, color: p.key === 'OFFER' ? ink : ink, ...num }}>{p.count}</b><div style={{ color: c.fg, fontWeight: 800, fontSize: 13, marginTop: 2 }}>{p.label}</div></div>; })}</div>
      {JF.__apiStatus?.applications === 'mock' && <div style={{ marginBottom: 18 }}><StatePanel type="error" title="지원 현황 API를 불러오지 못했습니다" desc="현재는 디자인용 샘플 지원 내역을 보여주고 있어요." action="다시 시도" /></div>}
      {JF.__apiStatus?.applications === 'ok' && !JF.applications.length && <div style={{ marginBottom: 18 }}><StatePanel type="empty" title="지원 내역 없음" desc="외부에서 지원한 공고를 기록하면 단계별 현황이 채워집니다." action="첫 지원 기록 추가" /></div>}
      <div style={{ ...tile, padding: 0, overflow: 'hidden' }}>
        <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr 116px' : '1fr 120px 150px 128px', gap: 14, padding: '13px 22px', borderBottom: '1px solid ' + line, fontSize: 11.5, fontWeight: 800, color: faint, letterSpacing: 0.5 }}><span>회사 · 공고</span>{!narrow && <><span>지원일</span><span>다음 액션</span></>}<span style={{ textAlign: 'right' }}>현재 단계</span></div>
        {JF.applications.map((a, i) => { const c = stageColor[a.status] || stageColor.APPLIED; return <div key={a.id || a.company} style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr 116px' : '1fr 120px 150px 128px', gap: 14, padding: '15px 22px', borderTop: i ? '1px solid ' + line : 'none', alignItems: 'center' }}><div onClick={() => go('detail', { jobId: a.jobId, company: a.company })} style={{ display: 'flex', alignItems: 'center', gap: 11, minWidth: 0, cursor: 'pointer' }}><div style={{ width: 36, height: 36, borderRadius: 11, background: ink, color: green, display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 900, fontSize: 13, flexShrink: 0 }}>{a.company.slice(0, 2)}</div><div style={{ minWidth: 0 }}><b style={{ fontSize: 14.5 }}>{a.company}</b><div style={{ color: muted, fontSize: 12.5, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{a.title}</div></div></div>{!narrow && <><span style={{ color: muted, fontSize: 13, ...num }}>{a.appliedAt ? String(a.appliedAt).slice(0, 10).replaceAll('-', '.') : `2026.06.${String(9 + i).padStart(2, '0')}`}</span><select defaultValue={a.status} onChange={async (e) => { try { await window.__jobflowApi?.updateApplicationStatus?.(a.id, e.target.value); notify('지원 상태를 변경했어요.'); } catch (error) { notify(error.message || '지원 상태 변경에 실패했어요.'); } }} style={{ font: 'inherit', border: '1px solid ' + line, background: '#fff', borderRadius: 12, padding: '8px 10px', fontSize: 12.5, fontWeight: 800 }}><option value="APPLIED">지원 완료</option><option value="DOCUMENT_PASSED">서류 합격</option><option value="CODING_TEST">코딩테스트</option><option value="INTERVIEW">면접 진행</option><option value="OFFER">오퍼</option><option value="REJECTED">불합격</option><option value="WITHDRAWN">지원 취소</option></select></>}<span style={{ justifySelf: 'end', fontSize: 12, fontWeight: 800, color: c.fg, background: c.bg, border: '1px solid ' + c.bd, borderRadius: 14, padding: '5px 11px', whiteSpace: 'nowrap' }}>{statusLabel[a.status] || a.status}</span></div>; })}
      </div>
      {addingApplication && <Modal title="지원 기록 추가" sub="외부 채용 사이트에서 지원한 공고도 JobFlow에서 단계별로 추적합니다." onClose={() => setAddingApplication(false)}><Field label="회사명" value="코어페이" /><Field label="공고명" value="코어페이 결제 플랫폼 백엔드 엔지니어 신입 채용" /><div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1fr 1fr', gap: 10 }}><Field label="지원일" value="2026.06.17" /><label><div style={{ fontSize: 12, color: muted, fontWeight: 850, marginBottom: 6 }}>현재 단계</div><select defaultValue="APPLIED" style={{ width: '100%', font: 'inherit', border: '1px solid ' + line, borderRadius: 13, padding: '12px 13px', background: '#fff' }}><option value="APPLIED">지원 완료</option><option value="DOCUMENT_PASSED">서류 합격</option><option value="CODING_TEST">코딩테스트</option><option value="INTERVIEW">면접 진행</option><option value="OFFER">오퍼</option></select></label></div><Field label="메모" value="지원 후 포트폴리오 링크와 프로젝트 분석 내용을 보강할 것" textarea /><div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 12 }}><ActionButton onClick={() => setAddingApplication(false)}>취소</ActionButton><ActionButton primary onClick={async () => { try { await window.__jobflowApi?.createApplicationByCompany?.('코어페이'); notify('지원 기록을 저장했어요.'); } catch (error) { notify(error.message || '지원 기록 저장에 실패했어요.'); } setAddingApplication(false); }}>기록 저장</ActionButton></div></Modal>}
    </Shell>;
  };

  const map = { login: loginScreen, oauth: oauthScreen, jobs: jobsScreen, userJobs: userJobsScreen, applications: applicationsScreen, projects: projectsScreen, 'project-new': projectNewScreen, 'project-analysis': projectAnalysisScreen, gap: gapScreen, recommendations: recommendationsScreen, trends: trendsScreen, mypage: mypageScreen, 'demo-status': demoStatusScreen, 'error-401': () => errorScreen('401'), 'error-403': () => errorScreen('403'), 'error-404': () => errorScreen('404'), 'error-500': () => errorScreen('500'), 'oauth-failure': () => errorScreen('oauth') };
  return (map[screen] || jobsScreen)();
}
