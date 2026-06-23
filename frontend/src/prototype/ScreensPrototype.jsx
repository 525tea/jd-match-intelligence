import React from 'react';
import { jobflowActions } from '../api/jobflowActions.js';
import { getJobflowState } from './jobflowState.js';

// JobFlow additional screens — final IA: home · jobs · projects · gap · trends.
// Palette: lime = matching/owned, charcoal = analysis surface, coral = urgent/missing, gray = structure.
const DEFAULT_JOB_FILTER = {
  role: '전체',
  career: '전체',
  employment: '전체',
  remote: '전체',
  region: '전체',
  skill: '전체',
  tag: '전체',
  deadline: '전체',
};
const JOB_LIST_BATCH_SIZE = 40;

export function JobFlowScreens({ t, go, screen }) {
  const JF = getJobflowState();
  const isV3 = t.ver === 'v3' && ['gap', 'trends'].includes(screen);
  const [sort, setSort] = React.useState('매칭순');
  const [query, setQuery] = React.useState('');
  const [searchingJobs, setSearchingJobs] = React.useState(false);
  const [filterOpen, setFilterOpen] = React.useState(false);
  const [draftFilter, setDraftFilter] = React.useState(DEFAULT_JOB_FILTER);
  const [appliedFilter, setAppliedFilter] = React.useState(DEFAULT_JOB_FILTER);
  const [jobResultMode, setJobResultMode] = React.useState('전체 공고');
  const [visibleJobCount, setVisibleJobCount] = React.useState(JOB_LIST_BATCH_SIZE);
  const jobListSentinelRef = React.useRef(null);
  const [previewProject, setPreviewProject] = React.useState(() => getJobflowState()?.projectList?.[0]?.name || '내 프로젝트');
  const [previewOpen, setPreviewOpen] = React.useState(false);
  const [editingProject, setEditingProject] = React.useState(null);
  const [addingApplication, setAddingApplication] = React.useState(false);
  const [settingsModal, setSettingsModal] = React.useState(null);
  const [screenNotice, setScreenNotice] = React.useState(null);
  const [githubRepositories, setGithubRepositories] = React.useState([]);
  const [githubRepositoriesLoading, setGithubRepositoriesLoading] = React.useState(false);
  const [githubRepositoriesError, setGithubRepositoriesError] = React.useState('');
  const [selectedRepositoryFullName, setSelectedRepositoryFullName] = React.useState('');
  const [selectedRepositoryRef, setSelectedRepositoryRef] = React.useState('');
  const [importingRepository, setImportingRepository] = React.useState(false);
  const screenNoticeTimerRef = React.useRef(null);
  const [trendSkill, setTrendSkill] = React.useState(() => getJobflowState()?.trends?.[0]?.name || '');
  const [openGroups, setOpenGroups] = React.useState(isV3 ? { role: true, career: true, skill: false, employment: false, remote: false, region: false, tag: false, deadline: false } : { role: true, career: true, skill: true, employment: false, remote: false, region: false, tag: false, deadline: false });
  const toggleGroup = (id) => setOpenGroups((p) => ({ ...p, [id]: !p[id] }));
  const notify = (message) => {
    setScreenNotice(message);
    clearTimeout(screenNoticeTimerRef.current);
    screenNoticeTimerRef.current = setTimeout(() => setScreenNotice(null), 2200);
  };
  const ink = '#14151a', muted = '#5b616e', faint = '#9aa1ad';
  const card = '#ffffff', line = '#e7eaf0', soft = '#f1f3f7';
  const green = t.green || '#b9ec2a';
  const greenStrong = green, greenInk = '#3f5c08', greenTint = '#eef8cf', greenTintBd = '#dbeca8';
  const coral = '#f0603f', coralDeep = '#c2391f', coralTint = '#ffe8e1', coralTintBd = '#f6c9bc';
  const deadlineTone = { bg: '#f4f6f9', fg: '#5b616e', bd: '#e1e5ec' };
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
  React.useEffect(() => {
    if (screen === 'jobs') setVisibleJobCount(JOB_LIST_BATCH_SIZE);
  }, [screen, jobResultMode, query, appliedFilter, sort, JF.listings.length]);
  React.useEffect(() => {
    if (screen !== 'jobs' || !jobListSentinelRef.current || typeof IntersectionObserver === 'undefined') return undefined;
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        setVisibleJobCount((count) => count + JOB_LIST_BATCH_SIZE);
      }
    }, { rootMargin: '420px 0px' });
    observer.observe(jobListSentinelRef.current);
    return () => observer.disconnect();
  }, [screen, visibleJobCount]);
  React.useEffect(() => {
    if (screen !== 'project-new' || !login) return undefined;
    let alive = true;
    setGithubRepositoriesLoading(true);
    setGithubRepositoriesError('');
    jobflowActions.listGithubRepositories?.()
      ?.then((repositories) => {
        if (!alive) return;
        const rows = Array.isArray(repositories) ? repositories : [];
        setGithubRepositories(rows);
        const currentSelected = rows.find((repo) => repo.fullName === selectedRepositoryFullName);
        const nextSelected = currentSelected || rows[0] || null;
        if (nextSelected) {
          setSelectedRepositoryFullName(nextSelected.fullName);
          setSelectedRepositoryRef(nextSelected.defaultBranch || 'HEAD');
        }
      })
      ?.catch((error) => {
        if (!alive) return;
        setGithubRepositoriesError(error.message || 'GitHub 저장소 목록을 불러오지 못했습니다.');
      })
      ?.finally(() => {
        if (alive) setGithubRepositoriesLoading(false);
      });
    return () => { alive = false; };
  }, [screen, login]);
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
    Seoul: '서울', Gyeonggi: '경기', Incheon: '인천', Busan: '부산', Daejeon: '대전', Daegu: '대구', Gwangju: '광주',
    Gangnam: '강남', Seocho: '서초', Mapo: '마포', Bundang: '분당', Seongdong: '성동', Songpa: '송파',
    EVENT_DRIVEN: '이벤트 드리븐', CACHE_STRATEGY: '캐시 전략', CI_CD: 'CI/CD', TESTING: '테스트',
    AUTH: '인증/인가', OBSERVABILITY: '모니터링', HIGH_TRAFFIC: '대용량 트래픽', DISTRIBUTED_SYSTEM: '분산 시스템',
  };
  const display = (value) => labelMap[value] || value;
  const options = (values) => ['전체'].concat(values.filter((x) => display(x) !== '전체').map(display));
  const findRawByDisplay = (values, displayValue) => {
    if (!displayValue || displayValue === '전체') return '';
    return values.find((value) => value === displayValue || display(value) === displayValue) || displayValue;
  };
  const regionApiValue = (value) => ({
    '서울': 'Seoul',
    '경기': 'Gyeonggi',
    '인천': 'Incheon',
    '부산': 'Busan',
    '대전': 'Daejeon',
    '대구': 'Daegu',
    '광주': 'Gwangju',
  })[value] || value;
  const selectedFilters = Object.values(appliedFilter).filter((x) => x && x !== '전체');
  const primaryProject = JF.projectList?.[0] || {};
  const currentProjectName = primaryProject.name || previewProject || '내 프로젝트';
  const currentProjectRepo = primaryProject.repo || '연결된 GitHub repository';
  const currentProjectSkillSummary = (primaryProject.previewSkills || JF.skills?.map((skill) => skill.name) || []).slice(0, 3).join(' · ') || '스킬 분석 대기';
  const repositoryLogoText = (value = '') => {
    const repositoryName = String(value || '').split('/').filter(Boolean).pop() || value;
    return String(repositoryName).replace(/[^A-Za-z0-9가-힣]/g, ' ').trim().split(/\s+/).map((x) => x[0]).join('').slice(0, 2).toUpperCase() || 'GH';
  };
  const hasProjectContext = Boolean((JF.projectList?.length || 0) || (JF.skills?.length || 0) || (JF.matches?.length || 0) || JF.__userProjectId);
  const analyzedProjectCount = hasProjectContext ? Math.max(JF.projectList?.length || 0, 1) : 0;
  const fallbackAnalyzedProject = {
    name: currentProjectName,
    logo: repositoryLogoText(currentProjectName),
    repo: currentProjectRepo,
    connected: true,
    analyzedAt: '최근 분석',
    skillsTotal: JF.skills?.length || 0,
    tagsTotal: JF.expTags?.length || 0,
    matchedJobs: JF.matches?.length || 0,
    previewSkills: (JF.skills || []).map((skill) => skill.name).filter(Boolean).slice(0, 5),
    repoVisual: `${currentProjectName} · ${currentProjectSkillSummary}`,
    summary: `${currentProjectSkillSummary} 기반으로 분석된 사용자 프로젝트`,
    overview: '연결된 프로젝트의 기술 스택과 경험 태그를 바탕으로 구성한 분석 카드입니다.',
    domain: (JF.expTags || []).map((tag) => tag.label || tag.code).filter(Boolean).slice(0, 3),
    architecture: '코드 기반 스킬 분석',
    stackGroups: [
      { label: '감지된 스킬', items: (JF.skills || []).map((skill, index) => ({ n: skill.name, pct: skill.conf || Math.max(52, 96 - index * 6) })).filter((item) => item.n).slice(0, 8) },
    ],
    stats: { commits: '-', files: JF.skills?.length || '-', tests: JF.expTags?.length ? Math.min(100, Math.round((JF.expTags.length / Math.max(1, JF.skills?.length || 1)) * 100)) : '-', contributors: '-' },
    dirs: [],
    detailTags: JF.expTags || [],
  };
  const currentUserName = JF.user?.name || '사용자';
  const currentUserEmail = JF.user?.email || '이메일 정보 없음';
  const currentUserRole = JF.user?.role || 'USER';
  const currentUserInitial = currentUserName.replace(/[^A-Za-z0-9가-힣]/g, ' ').trim().slice(0, 1) || 'U';
  const selectedTrend = JF.trends.find((item) => item.name === trendSkill) || JF.trends[0] || { name: '트렌드 없음', rate: 0, growth: 0, owned: false, insight: '이번 달 공고 집계 결과가 없습니다.' };
  const trendCooccurrenceSkills = JF.trends.map((item) => item.name).filter(Boolean).slice(0, 6);
  const trendExperienceTags = (JF.expTags || []).map((tag) => tag.code).filter(Boolean).slice(0, 3);

  const Shell = ({ title, sub, children, actions }) => (
    <div style={{ minHeight: '100vh', background: screen === 'jobs' ? '#f7f8fa' : '#fff', fontFamily: font, color: ink }}>
      <div style={{ height: 64, borderBottom: '1px solid ' + line, background: 'rgba(255,255,255,0.88)', backdropFilter: 'blur(14px)', display: 'flex', alignItems: 'center', padding: narrow ? '0 18px' : '0 40px', gap: narrow ? 10 : 28, position: 'sticky', top: 0, zIndex: 20, overflow: 'hidden' }}>
        <div style={{ fontSize: narrow ? 20 : 22, fontWeight: 900, letterSpacing: -1, cursor: 'pointer', flexShrink: 0 }} onClick={() => go('home')}>jobflow<span style={{ color: green, WebkitTextStroke: '0.5px ' + greenInk }}>.</span></div>
        <div style={{ display: 'flex', gap: 4, whiteSpace: 'nowrap', flex: 1, minWidth: 0, overflowX: 'auto', scrollbarWidth: 'none' }}>{navItems.map(([key, label]) => { const on = navOn(key); return <button key={key} onClick={() => go(key)} style={{ font: 'inherit', cursor: 'pointer', border: 'none', borderRadius: 20, padding: narrow ? '7px 10px' : '7px 13px', fontSize: narrow ? 12.5 : 13.5, fontWeight: on ? 900 : 600, background: on ? green : 'transparent', color: on ? ink : muted, whiteSpace: 'nowrap', flexShrink: 0 }}>{label}</button>; })}</div>
        <div style={{ marginLeft: 'auto', display: narrow ? 'none' : 'flex', alignItems: 'center', gap: 10, flexShrink: 0 }}><span style={{ display: 'flex', alignItems: 'center', gap: 7, fontSize: 12.5, fontWeight: 800, color: login ? greenInk : muted, background: login ? greenTint : soft, border: '1px solid ' + (login ? greenTintBd : line), padding: '6px 11px', borderRadius: 20, whiteSpace: 'nowrap' }}>{login && <span style={{ width: 7, height: 7, borderRadius: 4, background: greenInk }} />}{login ? (JF.user?.github ? 'GitHub 연동됨' : '로그인됨') : '비로그인 탐색 중'}</span><button onClick={() => go('mypage')} style={{ font: 'inherit', cursor: 'pointer', width: 34, height: 34, borderRadius: 17, background: ink, color: '#fff', border: 'none', fontSize: 13, fontWeight: 900 }}>{currentUserInitial}</button></div>
      </div>
      <main style={{ maxWidth: 1520, margin: '0 auto', padding: narrow ? '28px 18px 64px' : '36px 48px 72px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', gap: 24, marginBottom: screen === 'jobs' ? 18 : 26, flexWrap: 'wrap' }}><div><div style={{ fontSize: narrow ? 29 : 36, fontWeight: 950, letterSpacing: -1.1 }}>{title}</div>{sub && <div style={{ fontSize: 14.5, color: muted, marginTop: 8, maxWidth: 760, lineHeight: 1.58 }}>{sub}</div>}</div>{actions}</div>
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

  const JobThumbnail = ({ job, score, compact, locked, scoreLabel = '프로젝트 매칭' }) => {
    const ownedSkills = [...new Set([
      ...(job.matched || []),
      ...(job.skills || []),
      ...(job.requiredSkills || []),
      ...(job.preferredSkills || []),
    ])];
    const missingSkills = job.missing || [];
    const tags = [...new Set(job.tags || [])];
    const matchScore = score || job.score || 0;
    const visibleSkills = missingSkills.length
      ? ownedSkills.slice(0, compact ? 2 : 3).map((skill) => ({ skill })).concat([{ skill: missingSkills[0], miss: true }])
      : ownedSkills.slice(0, compact ? 3 : 4).map((skill) => ({ skill }));
    const hiddenSkills = missingSkills.length > 1 ? missingSkills.length - 1 : (!missingSkills.length && ownedSkills.length > (compact ? 3 : 4) ? ownedSkills.length - (compact ? 3 : 4) : 0);
    const maxTags = 2;
    const visibleTags = tags.length > maxTags ? tags.slice(0, maxTags - 1) : tags.slice(0, maxTags);
    const hiddenTags = Math.max(0, tags.length - visibleTags.length);
    return <div className="jf-jobcard jf-jobcard-polished" onClick={() => go('detail', { jobId: job.jobId || job.id, company: job.companyKo })} style={{ cursor: 'pointer', background: card, border: '1px solid #e8ebf1', borderRadius: 24, boxShadow: '0 1px 2px rgba(20,21,26,0.025), 0 10px 28px rgba(20,21,26,0.045)', padding: compact ? 17 : 20, display: 'flex', flexDirection: 'column', minHeight: compact ? 180 : 214 }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
        <Logo text={job.logo || job.companyKo.slice(0, 2)} size={42} />
        <div style={{ minWidth: 0, flex: 1 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
            <span style={{ fontSize: 14.5, fontWeight: 780, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{job.companyKo}</span>
            <span style={{ fontSize: 12.2, color: faint, fontWeight: 700, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{job.level}</span>
          </div>
          <div style={{ fontSize: compact ? 15.5 : 18, fontWeight: 850, letterSpacing: -0.45, marginTop: 8, lineHeight: 1.34, minHeight: compact ? 42 : 50, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', wordBreak: 'keep-all' }}>{job.fullTitle || job.title || '공고명 없음'}</div>
        </div>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: 10, alignItems: 'center', marginTop: 14 }}>
        <div style={{ display: 'flex', flexWrap: 'nowrap', gap: 6, minWidth: 0, overflow: 'hidden' }}>{visibleSkills.map(({ skill, miss }) => <SkillChip key={skill} s={skill} miss={miss} />)}{hiddenSkills > 0 && <MoreChip count={hiddenSkills} />}</div>
        <span style={{ fontSize: 11.5, fontWeight: 900, color: deadlineTone.fg, background: deadlineTone.bg, border: '1px solid ' + deadlineTone.bd, padding: '4px 9px', borderRadius: 999, whiteSpace: 'nowrap' }}>{job.deadline}</span>
      </div>
      <div style={{ display: 'flex', flexWrap: 'nowrap', gap: 6, marginTop: 9, minHeight: 25, overflow: 'hidden' }}>{visibleTags.map((c, idx) => <TagChip key={c} code={c} muted={idx > 0 && missingSkills.length > 0} />)}{hiddenTags > 0 && <MoreChip count={hiddenTags} />}</div>
      <div style={{ marginTop: 'auto', paddingTop: 15, display: 'flex', alignItems: 'center', gap: 10, color: faint, fontSize: 11.5, fontWeight: 650 }}>
        {login && !locked ? <span style={{ color: ink, fontWeight: 950, fontSize: compact ? 16 : 18, letterSpacing: -0.55, ...num }}>{matchScore}% <span style={{ fontSize: 11.5, fontWeight: 850, color: '#9aa1ad' }}>{scoreLabel}</span></span> : <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, color: muted, background: soft, border: '1px solid ' + line, borderRadius: 999, padding: '4px 9px', fontWeight: 800 }}><svg width="12" height="12" viewBox="0 0 14 14" fill="none" stroke="currentColor" strokeWidth="1.6"><rect x="2.5" y="6" width="9" height="6.5" rx="1.2"/><path d="M4.5 6V4.3a2.5 2.5 0 0 1 5 0V6"/></svg>매칭률 잠금</span>}
        <span style={{ marginLeft: 'auto', display: 'inline-flex', alignItems: 'center', gap: 4 }}><EyeIcon />{job.views}</span>
        <button onClick={async (e) => { e.stopPropagation(); try { if (!login) throw new Error('로그인 후 공고를 저장할 수 있어요.'); await jobflowActions.saveJobByCompany?.(job.companyKo); notify('저장한 공고에 추가했어요.'); } catch (error) { notify(error.message || '공고 저장에 실패했어요.'); } }} aria-label="공고 저장" style={{ width: 28, height: 28, borderRadius: 10, border: '1px solid ' + line, background: '#fff', color: muted, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer' }}><BookmarkIcon /></button>
      </div>
    </div>;
  };

  const FilterGroup = ({ id, label, items, maxHeight }) => {
    const displayItems = options(items);
    const open = openGroups[id] !== false;
    const current = draftFilter[id] || '전체';
    return <div style={{ borderBottom: '1px solid ' + line, padding: open ? '14px 0' : '13px 0' }}>
      <button onClick={() => toggleGroup(id)} style={{ font: 'inherit', cursor: 'pointer', border: 'none', background: 'transparent', width: '100%', display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: 0 }}>
        <span style={{ display: 'flex', alignItems: 'center', gap: 7 }}><b style={{ fontSize: 14, fontWeight: 800 }}>{label}</b>{!open && current !== '전체' && <span style={{ fontSize: 11.5, fontWeight: 800, color: greenInk, background: greenTint, border: '1px solid ' + greenTintBd, borderRadius: 10, padding: '1px 7px' }}>{current}</span>}</span>
        <span style={{ width: 20, height: 20, borderRadius: 6, border: '1px solid ' + line, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 14, fontWeight: 700, color: muted, lineHeight: 1 }}>{open ? '−' : '+'}</span>
      </button>
      {open && <div style={{ display: 'flex', flexWrap: 'wrap', gap: 7, maxHeight: maxHeight || 116, overflow: 'auto', paddingRight: 2, marginTop: 11 }}>{displayItems.map((x) => { const on = current === x; return <button key={x} onClick={() => setDraftFilter((prev) => ({ ...prev, [id]: x }))} style={{ font: 'inherit', cursor: 'pointer', border: '1px solid ' + (on ? greenStrong : line), background: on ? greenStrong : '#fff', color: on ? ink : muted, borderRadius: 18, padding: '7px 10px', fontWeight: on ? 900 : 700, fontSize: 12.5, whiteSpace: 'nowrap' }}>{x}</button>; })}</div>}
    </div>;
  };

  const ActionButton = ({ children, onClick, primary, danger }) => <button onClick={onClick} style={{ font: 'inherit', cursor: 'pointer', border: primary ? 'none' : '1px solid ' + (danger ? coralTintBd : line), background: primary ? green : danger ? coralTint : '#fff', color: danger ? coralDeep : ink, borderRadius: 13, padding: '10px 14px', fontSize: 13, fontWeight: 900, whiteSpace: 'nowrap' }}>{children}</button>;
  const runStateAction = (label) => {
    const text = String(label || '');
    if (/GitHub|깃허브|연동|권한/.test(text)) {
      jobflowActions.startGithubOAuth?.();
      return;
    }
    if (/로그인|재연동/.test(text)) {
      go('login');
      return;
    }
    if (/프로젝트/.test(text)) {
      go('projects');
      return;
    }
    if (/공고|상세/.test(text)) {
      go('jobs');
      return;
    }
    if (/홈/.test(text)) {
      go('home');
      return;
    }
    jobflowActions.refreshData?.();
    notify('화면 데이터를 다시 불러오고 있어요.');
  };
  const StatePanel = ({ type = 'empty', title, desc, action, secondary }) => {
    const palette = type === 'error' || type === 'forbidden' ? { bg: coralTint, bd: coralTintBd, fg: coralDeep, icon: '!' } : type === 'loading' ? { bg: '#f8f9fb', bd: line, fg: muted, icon: '…' } : type === 'unauthorized' ? { bg: '#f8f9fb', bd: line, fg: ink, icon: '↗' } : { bg: greenTint, bd: greenTintBd, fg: greenInk, icon: '✓' };
    return <div style={{ background: palette.bg, border: '1px solid ' + palette.bd, borderRadius: 18, padding: 18, display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap' }}><div style={{ width: 36, height: 36, borderRadius: 13, background: '#fff', color: palette.fg, display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 1000, boxShadow: '0 1px 2px rgba(20,21,26,0.06)' }}>{palette.icon}</div><div style={{ flex: 1, minWidth: 220 }}><b style={{ fontSize: 15 }}>{title}</b><div style={{ color: muted, fontSize: 13, lineHeight: 1.55, marginTop: 3 }}>{desc}</div></div>{action && <ActionButton primary onClick={() => runStateAction(action)}>{action}</ActionButton>}{secondary && <ActionButton onClick={() => runStateAction(secondary)}>{secondary}</ActionButton>}</div>;
  };
  const Field = ({ label, value, placeholder, textarea }) => <label style={{ display: 'block', marginBottom: 12 }}><div style={{ fontSize: 12, color: muted, fontWeight: 850, marginBottom: 6 }}>{label}</div>{textarea ? <textarea defaultValue={value} placeholder={placeholder} rows={4} style={{ width: '100%', boxSizing: 'border-box', resize: 'vertical', font: 'inherit', border: '1px solid ' + line, borderRadius: 13, padding: '12px 13px', outline: 'none' }} /> : <input defaultValue={value} placeholder={placeholder} style={{ width: '100%', boxSizing: 'border-box', font: 'inherit', border: '1px solid ' + line, borderRadius: 13, padding: '12px 13px', outline: 'none' }} />}</label>;
  const Modal = ({ title, sub, children, onClose, width = 460 }) => <div style={{ position: 'fixed', inset: 0, zIndex: 700, background: 'rgba(20,21,26,0.38)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20 }} onClick={onClose}><div onClick={(e) => e.stopPropagation()} style={{ width, maxWidth: '100%', maxHeight: '92vh', overflow: 'auto', background: '#fff', borderRadius: 22, padding: 24, boxShadow: '0 24px 80px rgba(0,0,0,0.28)' }}><div style={{ display: 'flex', alignItems: 'flex-start', gap: 12, marginBottom: 18 }}><div style={{ flex: 1 }}><b style={{ fontSize: 20, letterSpacing: -0.4 }}>{title}</b>{sub && <div style={{ color: muted, fontSize: 13, lineHeight: 1.55, marginTop: 4 }}>{sub}</div>}</div><button onClick={onClose} style={{ border: 'none', background: soft, borderRadius: 14, width: 30, height: 30, cursor: 'pointer', fontWeight: 900 }}>×</button></div>{children}</div></div>;
  const TimelineStep = ({ done, active, title, desc }) => <div style={{ display: 'grid', gridTemplateColumns: '30px 1fr', gap: 10, padding: '10px 0', borderTop: '1px solid ' + line }}><span style={{ width: 24, height: 24, borderRadius: 12, background: done ? green : active ? ink : soft, color: done ? ink : active ? '#fff' : faint, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 11, fontWeight: 1000 }}>{done ? '✓' : active ? '…' : ''}</span><div><b style={{ fontSize: 13.5 }}>{title}</b><div style={{ color: muted, fontSize: 12.5, lineHeight: 1.45, marginTop: 2 }}>{desc}</div></div></div>;

  const loginScreen = () => <Shell title="로그인 / 회원가입" sub="로그인 화면으로 이동해 실제 인증 API로 계속합니다."><div style={{ ...tile, padding: 28, display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1fr auto', gap: 18, alignItems: 'center' }}><div><SecTitle>인증 필요</SecTitle><H>저장 공고, 프로젝트 분석, 추천 피드는 로그인 후 사용할 수 있어요.</H><p style={{ color: muted, fontSize: 14, lineHeight: 1.65, margin: '8px 0 0' }}>GitHub OAuth 또는 이메일 로그인을 사용합니다. 데모 계정 비밀번호는 브라우저 번들에 넣지 않습니다.</p></div><ActionButton primary onClick={() => { window.location.hash = 'login'; }}>로그인으로 이동</ActionButton></div></Shell>;
  const oauthScreen = () => <Shell title="로그인 확인" sub="GitHub 로그인 결과를 확인하고 계정 정보를 불러옵니다."><div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1.15fr 0.85fr', gap: 18 }}><section style={{ ...tile, padding: 28 }}><SecTitle>로그인 진행</SecTitle><div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 18 }}><div style={{ width: 54, height: 54, borderRadius: 18, background: green, color: ink, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 24, fontWeight: 1000 }}>✓</div><div><H>로그인 처리 중</H><div style={{ color: muted, fontSize: 13.5 }}>GitHub 로그인 결과를 확인하고 사용자 정보를 불러옵니다.</div></div></div><div style={{ display: 'grid', gap: 10 }}>{['로그인 결과 확인', '세션 저장', '내 정보 조회', '홈으로 이동'].map((x, i) => <div key={x} style={{ display: 'grid', gridTemplateColumns: '28px 1fr 80px', gap: 10, alignItems: 'center', padding: '10px 0', borderTop: '1px solid ' + line }}><span style={{ width: 24, height: 24, borderRadius: 12, background: i < 3 ? greenTint : soft, color: i < 3 ? greenInk : muted, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 1000 }}>{i < 3 ? '✓' : '…'}</span><b style={{ fontSize: 13.5 }}>{x}</b><span style={{ color: i < 3 ? greenInk : faint, fontSize: 12, fontWeight: 800, textAlign: 'right' }}>{i < 3 ? '완료' : '진행중'}</span></div>)}</div><button onClick={() => go('home')} style={{ marginTop: 20, font: 'inherit', border: 'none', background: ink, color: '#fff', borderRadius: 13, padding: '13px 18px', fontWeight: 900 }}>홈으로 이동</button></section><aside style={{ display: 'grid', gap: 12 }}><StatePanel type="error" title="로그인이 완료되지 않았습니다" desc="권한 승인을 취소했거나 로그인 요청이 만료된 상태입니다." action="다시 시도" secondary="로그인으로" /><StatePanel type="forbidden" title="권한이 부족합니다" desc="프로젝트 분석에 필요한 권한이 없으면 GitHub 재연동을 안내합니다." action="GitHub 재연동" /><StatePanel type="unauthorized" title="세션 만료" desc="로그인 세션이 만료되면 로그인 화면으로 이동합니다." action="로그인으로 이동" /></aside></div></Shell>;
  const loadingScreen = () => <Shell title="세션 확인" sub="브라우저의 HttpOnly 쿠키 세션을 확인하고 있습니다."><div style={{ ...tile, minHeight: 360, display: 'grid', placeItems: 'center', textAlign: 'center' }}><div><div style={{ width: 58, height: 58, borderRadius: 20, background: greenTint, color: greenInk, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontSize: 24, fontWeight: 1000, marginBottom: 16 }}>…</div><H>로그인 상태를 확인하는 중입니다</H><p style={{ color: muted, fontSize: 14, lineHeight: 1.65, margin: '8px auto 0', maxWidth: 420 }}>JWT는 HttpOnly 쿠키에 저장되어 있어 프론트에서 직접 읽지 않고, 서버의 내 정보 API로 세션을 확인합니다.</p></div></div></Shell>;

  const jobsScreen = () => {
    const resetFilters = async () => {
      const emptyFilter = { ...DEFAULT_JOB_FILTER };
      setDraftFilter(emptyFilter);
      setAppliedFilter(emptyFilter);
      setQuery('');
      setSearchingJobs(true);
      try {
        const rows = await jobflowActions.listJobs?.({});
        setJobResultMode('전체 공고');
        notify(`전체 공고 ${rows?.length || 0}개를 불러왔어요.`);
      } catch (error) {
        notify(error.message || '공고 목록 초기화에 실패했어요.');
      } finally {
        setSearchingJobs(false);
      }
    };
    const selected = (key) => appliedFilter[key] || '전체';
    const selectedDraftFilters = Object.values(draftFilter).filter((x) => x && x !== '전체');
    const buildApiFilterParams = (filter) => {
      const params = {};
      const role = findRawByDisplay(JF.filterOptions.roles, filter.role);
      const careerLevel = findRawByDisplay(JF.filterOptions.careers, filter.career);
      const employmentType = findRawByDisplay(JF.filterOptions.employmentTypes, filter.employment);
      const remoteType = findRawByDisplay(JF.filterOptions.remoteTypes, filter.remote);
      const locationRegion = filter.region && filter.region !== '전체' ? regionApiValue(filter.region) : '';
      if (role) params.role = role;
      if (careerLevel) params.careerLevel = careerLevel;
      if (employmentType) params.employmentType = employmentType;
      if (remoteType) params.remoteType = remoteType;
      if (locationRegion && !['원격', '전국'].includes(locationRegion)) params.locationRegion = locationRegion;
      return params;
    };
    const normalizeFilterText = (value) => String(value || '')
      .toLowerCase()
      .replace(/\s+/g, '')
      .replace(/[·/_.-]/g, '');
    const fieldMatches = (raw, value) => {
      if (!value || value === '전체') return true;
      const needle = normalizeFilterText(value);
      const candidates = [raw, display(raw)].map(normalizeFilterText).filter(Boolean);
      return candidates.some((candidate) => candidate.includes(needle) || needle.includes(candidate));
    };
    const anyFieldMatches = (values, value) => {
      if (!value || value === '전체') return true;
      return values.filter(Boolean).some((candidate) => fieldMatches(candidate, value));
    };
    const isRoleMatch = (job, value) => {
      if (value === '전체') return true;
      const roleText = `${job.role || ''} ${display(job.role) || ''} ${job.title || ''} ${job.fullTitle || ''}`;
      if (value === '백엔드') return /backend|server|백엔드|서버/i.test(roleText);
      if (value === '프론트엔드') return /frontend|front|프론트/i.test(roleText);
      if (value === '풀스택') return /full.?stack|풀스택/i.test(roleText);
      return anyFieldMatches([job.role, display(job.role), job.title, job.fullTitle], value);
    };
    const isDeadlineMatch = (job, value) => {
      if (value === '전체') return true;
      const d = Number(String(job.deadline || '').replace(/\D/g, '') || 999);
      if (value === '오늘 마감') return String(job.deadline).includes('DAY') || d === 0;
      if (value === '3일 이내') return d <= 3;
      if (value === '7일 이내') return d <= 7;
      if (value === '상시' || value === '마감일 없음') return job.deadline === '상시' || job.deadline === '마감 정보 없음';
      return true;
    };
    const filteredListings = JF.listings
      .filter((job) => {
        const skills = [...new Set([...(job.skills || []), ...(job.matched || []), ...(job.missing || []), ...(job.requiredSkills || []), ...(job.preferredSkills || [])])];
        const tagValues = (job.tags || []).flatMap((tag) => [tag, JF.tagLabel?.[tag]]);
        const roleOk = isRoleMatch(job, selected('role'));
        const careerOk = anyFieldMatches([job.level, job.careerLevel, display(job.level), display(job.careerLevel)], selected('career'));
        const employmentOk = anyFieldMatches([job.employmentType, display(job.employmentType)], selected('employment'));
        const remoteOk = anyFieldMatches([job.remoteType, display(job.remoteType)], selected('remote'));
        const regionOk = anyFieldMatches([job.location, job.locationRegion, job.locationCity, display(job.locationRegion), display(job.locationCity)], selected('region'));
        const skillOk = anyFieldMatches(skills, selected('skill'));
        const tagOk = anyFieldMatches(tagValues, selected('tag'));
        const deadlineOk = isDeadlineMatch(job, selected('deadline'));
        return roleOk && careerOk && employmentOk && remoteOk && regionOk && skillOk && tagOk && deadlineOk;
      })
      .sort((a, b) => {
        if (sort === '인기순') return (b.views || 0) - (a.views || 0);
        if (sort === '마감순') return parseInt(String(a.deadline).replace(/\D/g, '') || '999', 10) - parseInt(String(b.deadline).replace(/\D/g, '') || '999', 10);
        if (sort === '최신순') return (b.jobId || b.id || 0) - (a.jobId || a.id || 0);
        return (b.score || 0) - (a.score || 0);
      });
    const visibleListings = filteredListings.slice(0, visibleJobCount);
    const hasMoreVisibleJobs = visibleListings.length < filteredListings.length;
    const runKeywordSearch = async () => {
      setSearchingJobs(true);
      try {
        const rows = await jobflowActions.searchJobs?.(query);
        const emptyFilter = { ...DEFAULT_JOB_FILTER };
        setDraftFilter(emptyFilter);
        setAppliedFilter(emptyFilter);
        setJobResultMode(query.trim() ? '검색 결과' : '전체 공고');
        notify(`${rows?.length || 0}개 공고를 불러왔어요.`);
      } catch (error) {
        notify(error.message || '공고 검색에 실패했어요.');
      } finally {
        setSearchingJobs(false);
      }
    };
    const applyFilters = async () => {
      const nextFilter = { ...DEFAULT_JOB_FILTER, ...draftFilter };
      const apiParams = buildApiFilterParams(nextFilter);
      setSearchingJobs(true);
      try {
        const rows = Object.keys(apiParams).length
          ? await jobflowActions.listJobs?.(apiParams)
          : await jobflowActions.listJobs?.({});
        setAppliedFilter(nextFilter);
        setJobResultMode(Object.keys(apiParams).length ? '필터 결과' : '전체 공고');
        notify(`${rows?.length || 0}개 공고를 불러왔어요.`);
      } catch (error) {
        notify(error.message || '공고 필터 적용에 실패했어요.');
      } finally {
        setSearchingJobs(false);
      }
    };
    const removeAppliedFilter = async (id) => {
      const nextFilter = { ...appliedFilter, [id]: '전체' };
      const apiParams = buildApiFilterParams(nextFilter);
      setDraftFilter(nextFilter);
      setSearchingJobs(true);
      try {
        const rows = Object.keys(apiParams).length
          ? await jobflowActions.listJobs?.(apiParams)
          : await jobflowActions.listJobs?.({});
        setAppliedFilter(nextFilter);
        setJobResultMode(Object.keys(apiParams).length ? '필터 결과' : '전체 공고');
        notify(`${rows?.length || 0}개 공고를 불러왔어요.`);
      } catch (error) {
        notify(error.message || '필터 해제에 실패했어요.');
      } finally {
        setSearchingJobs(false);
      }
    };
    const filterPanel = (
      <aside style={{ ...tile, position: narrow ? 'static' : 'sticky', top: 86, padding: '18px 20px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <b style={{ fontSize: 18 }}>공고 필터</b>
          <span style={{ color: greenInk, fontWeight: 900 }}>{selectedDraftFilters.length ? `${selectedDraftFilters.length}개 선택` : '전체'}</span>
          <button onClick={resetFilters} disabled={searchingJobs} style={{ marginLeft: 'auto', border: 'none', background: 'transparent', color: faint, font: 'inherit', fontSize: 12, fontWeight: 800, cursor: searchingJobs ? 'default' : 'pointer' }}>초기화</button>
        </div>
        <div style={{ color: muted, fontSize: 12.5, lineHeight: 1.48, marginTop: 7 }}>조건을 고른 뒤 적용을 누르면 목록 API로 다시 불러옵니다.</div>
        <FilterGroup id="role" label="직무" items={JF.filterOptions.roles} maxHeight={150} />
        <FilterGroup id="career" label="경력" items={JF.filterOptions.careers} />
        <FilterGroup id="employment" label="채용 유형" items={JF.filterOptions.employmentTypes} />
        <FilterGroup id="remote" label="근무 방식" items={JF.filterOptions.remoteTypes} />
        <FilterGroup id="region" label="지역" items={JF.filterOptions.regions} />
        <FilterGroup id="skill" label="기술 스택" items={JF.filterOptions.skills} maxHeight={170} />
        <FilterGroup id="tag" label="경험 태그" items={JF.filterOptions.experienceTags} />
        <FilterGroup id="deadline" label="마감" items={JF.filterOptions.deadlines} />
        <button onClick={applyFilters} disabled={searchingJobs} style={{ marginTop: 16, width: '100%', font: 'inherit', cursor: searchingJobs ? 'default' : 'pointer', border: 'none', background: searchingJobs ? faint : green, color: ink, borderRadius: 16, padding: '12px 14px', fontWeight: 950 }}>{searchingJobs ? '적용중' : '필터 적용'}</button>
      </aside>
    );

    const filteredCount = filteredListings.length;
    return (
      <Shell title="공고" sub={isV3 ? "직무·경력·스택·지역 조건으로 먼저 좁힌 뒤 검색 점수와 상세 조건으로 확인합니다." : (login ? "검색어를 먼저 입력하고, 직무·경력·스킬·경험 태그로 좁힌 뒤 프로젝트 매칭 화면으로 이어갑니다." : "로그인 없이도 전체 공고와 필터를 먼저 탐색할 수 있어요. 프로젝트를 연결하면 추천 화면에서 매칭순으로 볼 수 있습니다.")}>
        <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '280px 1fr', gap: 22, alignItems: 'start' }}>
          {!narrow && filterPanel}
          <section>
            {!login && <div style={{ background: greenTint, border: '1px solid ' + greenTintBd, borderRadius: 18, padding: '15px 18px', marginBottom: 14, display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap' }}><div style={{ flex: 1, minWidth: 240 }}><b>프로젝트를 연결하면 공고가 내 스택 기준으로 정렬돼요.</b><div style={{ color: muted, fontSize: 13, marginTop: 3 }}>지금은 전체 공고 기준으로 탐색 중입니다.</div></div><button onClick={() => go('login')} style={{ font: 'inherit', border: 'none', background: ink, color: '#fff', borderRadius: 20, padding: '10px 15px', fontWeight: 850 }}>로그인하고 매칭 보기</button></div>}
            {JF.__apiStatus?.jobs === 'unavailable' && <div style={{ marginBottom: 14 }}><StatePanel type="error" title="공고 목록을 불러오지 못했습니다" desc="서비스 연결 상태를 확인한 뒤 다시 시도해주세요." action="다시 시도" /></div>}
            {isV3 && <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr 1fr' : 'repeat(3, 1fr)', gap: 10, marginBottom: 14 }}>{[['전체 공고', JF.market.totalCount.toLocaleString()], [jobResultMode, JF.listings.length.toLocaleString()], ['마감 정보 있음', JF.listings.filter((job) => job.deadline && job.deadline !== '마감 정보 없음').length.toLocaleString()]].map(([l, v]) => <div key={l} style={{ background: '#fff', border: '1px solid ' + line, borderRadius: 16, padding: '12px 14px' }}><b style={{ fontSize: 20, ...num }}>{v}</b><div style={{ fontSize: 12, color: muted, fontWeight: 800, marginTop: 2 }}>{l}</div></div>)}</div>}
            <div style={{ background: 'linear-gradient(135deg, #ffffff 0%, #fbfcfd 62%, #eef8cf 180%)', border: '1px solid ' + line, borderRadius: 26, padding: narrow ? 18 : 22, marginBottom: 16, display: 'grid', gap: 16, boxShadow: '0 14px 38px rgba(20,21,26,0.07)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 18, flexWrap: 'wrap' }}>
                <div>
                  <div style={{ fontSize: 12, color: greenInk, fontWeight: 950, letterSpacing: 0.8 }}>JOB SEARCH</div>
                  <div style={{ marginTop: 5, fontSize: narrow ? 20 : 24, fontWeight: 920, letterSpacing: -0.7 }}>원하는 공고만 빠르게 좁혀보세요</div>
                </div>
                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                  <span style={{ background: '#fff', border: '1px solid ' + line, borderRadius: 999, padding: '7px 11px', color: muted, fontSize: 12.5, fontWeight: 850 }}>전체 {JF.market.totalCount.toLocaleString()}개</span>
                  <span style={{ background: '#fff', border: '1px solid ' + line, borderRadius: 999, padding: '7px 11px', color: muted, fontSize: 12.5, fontWeight: 850 }}>현재 {filteredCount.toLocaleString()}개</span>
                </div>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr auto auto' : '1fr auto', gap: 8 }}>
	                <input value={query} onChange={(e) => setQuery(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') runKeywordSearch(); }} placeholder="예: 백엔드 Spring, Kubernetes 플랫폼, C++ 개발자" style={{ width: '100%', minWidth: 0, border: '1px solid #dfe3ea', background: '#fff', color: ink, borderRadius: 16, padding: '15px 16px', font: 'inherit', fontWeight: isV3 ? 600 : 750, outline: 'none', boxShadow: '0 1px 2px rgba(20,21,26,0.04)' }} />
                <button onClick={runKeywordSearch} disabled={searchingJobs} style={{ font: 'inherit', cursor: searchingJobs ? 'default' : 'pointer', border: 'none', background: searchingJobs ? faint : ink, color: '#fff', borderRadius: 16, padding: narrow ? '0 15px' : '0 22px', fontWeight: 900 }}>{searchingJobs ? '검색중' : '검색'}</button>
                {narrow && <button onClick={() => setFilterOpen(!filterOpen)} style={{ font: 'inherit', cursor: 'pointer', border: '1px solid ' + line, background: '#fff', color: ink, borderRadius: 16, padding: '0 14px', fontWeight: 900 }}>{filterOpen ? '닫기' : '필터'}</button>}
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap' }}>
                <div style={{ fontSize: 15, fontWeight: 700, letterSpacing: -0.3 }}><span style={{ color: greenInk, fontWeight: 800 }}>{jobResultMode}</span> 기준 <span style={{ color: greenInk, fontWeight: 800 }}>{filteredCount.toLocaleString()}개</span> 표시 <span style={{ fontSize: 12.5, color: faint, fontWeight: 600 }}>· 불러온 목록 {JF.listings.length.toLocaleString()}개</span></div>
                <div style={{ marginLeft: 'auto', display: 'flex', gap: 7, flexWrap: 'wrap' }}>{['검색순', '인기순', '마감순', '최신순'].map((x) => <button key={x} onClick={() => setSort(x === '검색순' ? '매칭순' : x)} style={{ font: 'inherit', cursor: 'pointer', border: '1px solid ' + ((sort === '매칭순' && x === '검색순') || sort === x ? greenStrong : line), background: (sort === '매칭순' && x === '검색순') || sort === x ? greenStrong : '#fff', color: ink, borderRadius: 20, padding: '7px 13px', fontWeight: (sort === '매칭순' && x === '검색순') || sort === x ? 800 : 600, fontSize: 13 }}>{x}</button>)}</div>
              </div>
	              {(isV3 || selectedFilters.length > 0) && <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, alignItems: 'center' }}><span style={{ color: faint, fontSize: 12, fontWeight: 900 }}>적용 조건</span>{Object.entries(appliedFilter).filter(([, v]) => v && v !== '전체').length ? Object.entries(appliedFilter).filter(([, v]) => v && v !== '전체').map(([id, v]) => <span key={id} style={{ display: 'inline-flex', alignItems: 'center', gap: 5, background: greenTint, color: greenInk, border: '1px solid ' + greenTintBd, borderRadius: 14, padding: '4px 10px', fontSize: 12, fontWeight: 800 }}>{v}<span onClick={() => removeAppliedFilter(id)} style={{ cursor: searchingJobs ? 'default' : 'pointer', opacity: 0.55 }}>✕</span></span>) : <span style={{ background: soft, color: muted, border: '1px solid ' + line, borderRadius: 14, padding: '4px 10px', fontSize: 12, fontWeight: 800 }}>전체</span>}</div>}
            </div>
            {narrow && <div style={{ marginBottom: 14 }}>{filterOpen ? filterPanel : <div style={{ ...tile, padding: 14, display: 'flex', alignItems: 'center', gap: 8, overflowX: 'auto' }}><b style={{ whiteSpace: 'nowrap' }}>선택 필터</b>{(selectedFilters.length ? selectedFilters : ['필터 없음']).map((x) => <span key={x} style={{ background: x === '필터 없음' ? soft : greenTint, color: x === '필터 없음' ? muted : greenInk, border: '1px solid ' + (x === '필터 없음' ? line : greenTintBd), borderRadius: 16, padding: '6px 10px', fontSize: 12, fontWeight: 900, whiteSpace: 'nowrap' }}>{x}</span>)}</div>}</div>}
            {filteredListings.length ? <>
              <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : 'repeat(2, 1fr)', gap: 14 }}>{visibleListings.map((j) => <JobThumbnail key={(j.jobId || j.id || '') + j.companyKo + j.fullTitle} job={j} locked={!login} scoreLabel="검색 점수" />)}</div>
              <div ref={jobListSentinelRef} style={{ minHeight: 80, display: 'flex', alignItems: 'center', justifyContent: 'center', paddingTop: 18 }}>
                {hasMoreVisibleJobs ? <button onClick={() => setVisibleJobCount((count) => count + JOB_LIST_BATCH_SIZE)} style={{ font: 'inherit', cursor: 'pointer', border: '1px solid ' + line, background: '#fff', color: ink, borderRadius: 999, padding: '11px 18px', fontWeight: 900, boxShadow: shadow }}>더 보기 · {visibleListings.length.toLocaleString()} / {filteredListings.length.toLocaleString()}</button> : <span style={{ color: faint, fontSize: 12.5, fontWeight: 800 }}>전체 {filteredListings.length.toLocaleString()}개를 모두 표시했어요.</span>}
              </div>
            </> : <div style={{ ...tile, textAlign: 'center', padding: narrow ? 28 : 38 }}>
              <div style={{ fontSize: 18, fontWeight: 900, letterSpacing: -0.35 }}>조건에 맞는 공고가 없습니다</div>
              <div style={{ color: muted, fontSize: 14, lineHeight: 1.6, marginTop: 8 }}>선택한 필터를 줄이거나 검색어를 지우면 더 많은 공고를 볼 수 있어요.</div>
              <button onClick={() => { setQuery(''); resetFilters(); }} style={{ marginTop: 16, font: 'inherit', cursor: 'pointer', border: 'none', background: ink, color: '#fff', borderRadius: 14, padding: '11px 16px', fontWeight: 900 }}>필터 초기화</button>
            </div> }
          </section>
        </div>
      </Shell>
    );
  };

  const projectsScreen = () => {
    const selected = JF.projectList.find((p) => p.name === previewProject) || JF.projectList[0] || fallbackAnalyzedProject;
    const projectCards = JF.projectList.length ? JF.projectList : hasProjectContext ? [fallbackAnalyzedProject] : [];
    const hasAnalyzedProject = hasProjectContext;
    const projectApiMissing = JF.__apiStatus?.skills === 'missing-project' || JF.__apiStatus?.matches === 'missing-project';
    if (!hasAnalyzedProject) {
      return <Shell title="프로젝트" sub="프로젝트 분석 결과가 연결되면 추출 스킬, 경험 태그, 매칭 공고가 이 화면에 표시됩니다." actions={<ActionButton onClick={() => go('jobs')}>공고 먼저 보기</ActionButton>}>
        <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1.05fr 0.95fr', gap: 18, alignItems: 'stretch' }}>
          <section style={{ ...tile, padding: 28 }}>
            <SecTitle>프로젝트 인벤토리</SecTitle>
            <div style={{ fontSize: narrow ? 28 : 34, fontWeight: 950, letterSpacing: -1, lineHeight: 1.16 }}>아직 연결된 프로젝트 분석 결과가 없습니다.</div>
            <p style={{ color: muted, fontSize: 15, lineHeight: 1.7, margin: '14px 0 0' }}>GitHub 권한을 확인한 뒤 분석된 프로젝트가 생기면 스킬, 경험 태그, 매칭 공고가 이 화면에 채워집니다.</p>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 18 }}>
              <ActionButton primary onClick={() => jobflowActions.startGithubOAuth?.()}>GitHub 저장소 연결</ActionButton>
              <ActionButton onClick={() => go('project-new')}>연결 흐름 보기</ActionButton>
            </div>
            <div style={{ marginTop: 18, display: 'grid', gap: 10 }}>
              <StatePanel type={projectApiMissing ? 'unauthorized' : 'empty'} title={projectApiMissing ? '프로젝트 분석 결과가 아직 없습니다' : '프로젝트 분석 결과 대기'} desc={projectApiMissing ? '현재 계정에 연결된 분석 결과가 없거나 프로젝트 등록 API가 아직 준비되지 않았습니다.' : '프로젝트 분석이 완료되면 스킬과 경험 태그가 카드로 표시됩니다.'} action="GitHub 권한 확인" />
              <StatePanel type="forbidden" title="새 프로젝트 분석 준비 중" desc="지금은 이미 분석된 프로젝트 결과를 기준으로 추천과 갭 분석을 확인할 수 있습니다." secondary="공개 공고 탐색" />
            </div>
          </section>
          <aside style={{ ...tile, background: ink, color: '#fff', display: 'flex', flexDirection: 'column' }}>
            <SecTitle color={green}>연결 후 흐름</SecTitle>
            <div style={{ fontSize: 24, fontWeight: 900, letterSpacing: -0.6, lineHeight: 1.25 }}>연결되면 여기서 바로 추천과 갭 분석으로 이어집니다.</div>
            <div style={{ display: 'grid', gap: 10, marginTop: 20 }}>
              {['프로젝트 스킬 인벤토리 조회', '경험 태그 조회', 'JD 매칭 상위 공고 조회', '갭 분석과 추천 피드 연결'].map((text, index) => <div key={text} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '11px 0', borderTop: '1px solid rgba(255,255,255,0.12)' }}><span style={{ width: 28, height: 28, borderRadius: 10, background: 'rgba(185,236,42,0.14)', color: green, display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 1000 }}>{index + 1}</span><b style={{ color: 'rgba(255,255,255,0.84)', fontSize: 13.5 }}>{text}</b></div>)}
            </div>
          </aside>
        </div>
      </Shell>;
    }
    const projectApiUnavailable = ['skills', 'tags', 'matches'].some((key) => JF.__apiStatus?.[key] === 'unavailable');
    return <Shell title="프로젝트" sub="전체 프로젝트와 추출 스킬 목록입니다. 프로젝트를 누르면 상세 분석으로 들어가고, 매칭 공고는 우측에서 확인합니다." actions={<button onClick={() => go('project-new')} style={{ font: 'inherit', cursor: 'pointer', border: 'none', background: ink, color: '#fff', borderRadius: 22, padding: '12px 18px', fontWeight: 900 }}>+ 새 프로젝트 분석</button>}>
      {projectApiUnavailable && <div style={{ display: 'grid', gap: 14, marginBottom: 18 }}><StatePanel type="error" title="프로젝트 정보를 일부 불러오지 못했습니다" desc="스킬, 경험 태그, 매칭 공고 중 일부를 불러오지 못했습니다. 현재 화면은 불러온 정보만 기준으로 표시합니다." action="새로고침 후 재시도" /></div>}
      <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1fr 390px', gap: 18, alignItems: 'start' }}>
        <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : 'repeat(2, 1fr)', gap: 16 }}>
          {projectCards.map((p) => (
            <div key={p.name} className="jf-jobcard" style={{ ...tile, cursor: 'pointer', borderColor: previewProject === p.name ? greenStrong : line, padding: 0, overflow: 'hidden' }} onClick={() => { setPreviewProject(p.name); if (p.id && p.id !== JF.__userProjectId) { jobflowActions.switchProject?.(p.id); } go('project-analysis'); }}>
              <div style={{ height: 132, background: p.connected ? 'linear-gradient(135deg, #14151a, #283018 55%, #b9ec2a)' : 'linear-gradient(135deg, #f2f3f5, #dde2e8)', color: p.connected ? '#fff' : muted, padding: 18, display: 'flex', flexDirection: 'column' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 7, fontSize: 11, fontWeight: 900, letterSpacing: 1 }}><GithubMark dark={p.connected} />연결된 프로젝트</span>
                  <button title="프로젝트 수정" onClick={(e) => { e.stopPropagation(); setEditingProject(p); }} style={{ width: 30, height: 30, borderRadius: 15, border: '1px solid ' + (p.connected ? 'rgba(255,255,255,0.18)' : line), background: p.connected ? 'rgba(255,255,255,0.10)' : 'rgba(255,255,255,0.78)', color: p.connected ? 'rgba(255,255,255,0.82)' : muted, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 0 }}><EditIcon /></button>
                </div>
                <div style={{ marginTop: 'auto', fontSize: 13, fontWeight: 900 }}>{p.repoVisual}</div>
              </div>
              <div style={{ padding: 18 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}><Logo text={p.logo || repositoryLogoText(p.name)} /><div><div style={{ fontSize: 18, fontWeight: 900 }}>{p.name}</div><div style={{ color: faint, fontSize: 12.5 }}>{p.repo}</div></div></div>
                <p style={{ color: muted, fontSize: 13.5, lineHeight: 1.55 }}>{p.summary}</p>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>{p.previewSkills.map((skill) => <SkillChip key={skill} s={skill} />)}</div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 9, marginTop: 15, flexWrap: 'wrap' }}>
                  <button onClick={(e) => { e.stopPropagation(); setPreviewProject(p.name); setPreviewOpen(true); }} style={{ font: 'inherit', border: 'none', background: green, color: ink, borderRadius: 16, padding: '8px 12px', fontSize: 12.5, fontWeight: 900 }}>{p.matchedJobs}개 매칭 공고 보기</button>
                  <span style={{ color: faint, fontSize: 12 }}>마지막 분석 · {p.analyzedAt}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
        <aside style={{ ...tile, position: narrow ? 'static' : 'sticky', top: 86 }}>
          <SecTitle>TOP MATCHES</SecTitle>
          <H>내 스택과 매칭률 높은 공고</H>
          <div style={{ color: muted, fontSize: 12.5, lineHeight: 1.55, marginBottom: 12 }}>현재 기준: <b style={{ color: ink }}>{selected.name}</b>. 공고를 누르면 상세 공고로 이동합니다.</div>
          <div style={{ display: 'grid', gap: 10 }}>{JF.matches.map((m) => <div key={(m.jobId || m.id || '') + m.companyKo} onClick={() => go('detail', { jobId: m.jobId || m.id, company: m.companyKo })} style={{ border: '1px solid ' + line, borderRadius: 14, padding: 13, cursor: 'pointer' }}><div style={{ display: 'flex', gap: 10, alignItems: 'center' }}><Logo text={m.logo} size={36} /><div style={{ flex: 1 }}><b>{m.companyKo}</b><div style={{ color: muted, fontSize: 12.5 }}>{m.fullTitle}</div></div><b style={{ color: greenInk, ...num }}>{m.score}%</b></div><div style={{ display: 'flex', flexWrap: 'wrap', gap: 5, marginTop: 10 }}>{m.matched.slice(0,3).map((skill) => <SkillChip key={skill} s={skill} owned />)}{m.missing.slice(0,1).map((skill) => <SkillChip key={skill} s={skill} miss />)}</div><div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 8, marginTop: 11, color: faint, fontSize: 12, fontWeight: 800 }}><span style={{ background: soft, border: '1px solid ' + line, borderRadius: 10, padding: '6px 8px', textAlign: 'center', whiteSpace: 'nowrap' }}>{m.level}</span><span style={{ background: coralTint, border: '1px solid ' + coralTintBd, color: coralDeep, borderRadius: 10, padding: '6px 8px', textAlign: 'center', whiteSpace: 'nowrap' }}>{m.deadline}</span><span style={{ background: soft, border: '1px solid ' + line, borderRadius: 10, padding: '6px 8px', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 4, whiteSpace: 'nowrap', ...num }}><EyeIcon size={13} />{m.views}</span></div></div>)}</div>
        </aside>
      </div>
      {previewOpen && <div style={{ position: 'fixed', inset: 0, zIndex: 560, background: 'rgba(20,21,26,0.32)', display: 'flex', justifyContent: 'flex-end' }} onClick={() => setPreviewOpen(false)}><div onClick={(e) => e.stopPropagation()} style={{ width: 520, maxWidth: '100%', height: '100%', background: '#fff', padding: 26, boxShadow: '-18px 0 60px rgba(20,21,26,0.2)', overflow: 'auto' }}><div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 18 }}><div><SecTitle>매칭 공고</SecTitle><H>{selected.name} 기준 추천 공고</H><div style={{ color: muted, fontSize: 13 }}>카드를 누르면 공고 상세로 이동합니다.</div></div><button onClick={() => setPreviewOpen(false)} style={{ marginLeft: 'auto', border: 'none', background: soft, borderRadius: 14, width: 32, height: 32, cursor: 'pointer' }}>×</button></div><div style={{ display: 'grid', gap: 12 }}>{JF.matches.map((m, i) => <JobThumbnail key={m.companyKo + i} job={m} score={m.score} compact />)}</div></div></div>}
      {editingProject && <div style={{ position: 'fixed', inset: 0, zIndex: 600, background: 'rgba(20,21,26,0.38)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20 }} onClick={() => setEditingProject(null)}><div onClick={(e) => e.stopPropagation()} style={{ width: 430, maxWidth: '100%', background: '#fff', borderRadius: 22, padding: 24, boxShadow: '0 24px 80px rgba(0,0,0,0.28)' }}><div style={{ display: 'flex', alignItems: 'center', gap: 9, marginBottom: 16 }}><GithubMark /><b style={{ fontSize: 19 }}>프로젝트 수정</b><button onClick={() => setEditingProject(null)} style={{ marginLeft: 'auto', border: 'none', background: soft, borderRadius: 14, width: 28, height: 28, cursor: 'pointer' }}>×</button></div>{[['프로젝트 이름', editingProject.name], ['저장소 주소', editingProject.repo], ['썸네일 메모', editingProject.repoVisual]].map(([label, value]) => <label key={label} style={{ display: 'block', marginBottom: 12 }}><div style={{ fontSize: 12, color: muted, fontWeight: 800, marginBottom: 6 }}>{label}</div><input defaultValue={value} style={{ width: '100%', boxSizing: 'border-box', font: 'inherit', border: '1px solid ' + line, borderRadius: 12, padding: '12px 13px' }} /></label>)}<button onClick={() => setEditingProject(null)} style={{ font: 'inherit', width: '100%', border: 'none', background: green, color: ink, borderRadius: 14, padding: 13, fontWeight: 900 }}>저장하기</button></div></div>}
    </Shell>;
  };

  const projectNewScreen = () => {
    const selectedRepository = githubRepositories.find((repo) => repo.fullName === selectedRepositoryFullName) || githubRepositories[0] || null;
    const selectedRef = selectedRepositoryRef || selectedRepository?.defaultBranch || 'HEAD';
    const canImportRepository = Boolean(selectedRepository && selectedRef && !importingRepository);
    const importSelectedRepository = async () => {
      if (!selectedRepository) {
        notify('분석할 GitHub 저장소를 먼저 선택해주세요.');
        return;
      }

      setImportingRepository(true);
      try {
        const result = await jobflowActions.importGithubRepository?.({
          owner: selectedRepository.owner,
          name: selectedRepository.name,
          ref: selectedRef,
          htmlUrl: selectedRepository.htmlUrl,
          description: selectedRepository.description,
        });
        notify(result?.skipped ? '이미 최신 분석 결과가 있어요.' : '프로젝트 분석이 완료됐어요.');
        go('project-analysis');
      } catch (error) {
        notify(error.message || '프로젝트 분석에 실패했어요.');
      } finally {
        setImportingRepository(false);
      }
    };

    return <Shell title="새 프로젝트 분석" sub="GitHub 로그인으로 저장소를 고르고, 기준 브랜치를 선택한 뒤 코드에서 스킬과 경험 태그를 추출합니다." actions={<ActionButton onClick={() => go('projects')}>프로젝트 목록</ActionButton>}>
      <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1.12fr 0.88fr', gap: 18, alignItems: 'start' }}>
        <section style={{ ...tile, padding: 28 }}>
          <SecTitle>GitHub 저장소 선택</SecTitle>
          <div style={{ fontSize: narrow ? 28 : 34, fontWeight: 950, letterSpacing: -1, lineHeight: 1.15 }}>분석할 저장소를 선택하세요.</div>
          <p style={{ color: muted, fontSize: 15, lineHeight: 1.72, margin: '14px 0 0' }}>선택한 저장소의 빌드 파일, Docker/설정 파일, GitHub Actions workflow를 읽어 프로젝트 스킬과 경험 태그를 갱신합니다.</p>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginTop: 18 }}>
            <ActionButton primary onClick={() => jobflowActions.startGithubOAuth?.()}>GitHub 재연동</ActionButton>
            <ActionButton onClick={() => {
              setGithubRepositories([]);
              setSelectedRepositoryFullName('');
              setSelectedRepositoryRef('');
              setGithubRepositoriesError('');
              setGithubRepositoriesLoading(true);
              jobflowActions.listGithubRepositories?.()
                ?.then((rows) => {
                  const repositories = Array.isArray(rows) ? rows : [];
                  setGithubRepositories(repositories);
                  const first = repositories[0];
                  if (first) {
                    setSelectedRepositoryFullName(first.fullName);
                    setSelectedRepositoryRef(first.defaultBranch || 'HEAD');
                  }
                })
                ?.catch((error) => setGithubRepositoriesError(error.message || 'GitHub 저장소 목록을 불러오지 못했습니다.'))
                ?.finally(() => setGithubRepositoriesLoading(false));
            }}>목록 새로고침</ActionButton>
          </div>

          {!login && <div style={{ marginTop: 18 }}><StatePanel type="unauthorized" title="로그인이 필요합니다" desc="GitHub 저장소 목록을 보려면 GitHub 로그인을 먼저 진행해주세요." action="GitHub 권한 확인" /></div>}
          {login && githubRepositoriesLoading && <div style={{ marginTop: 18 }}><StatePanel type="loading" title="저장소 목록을 불러오는 중입니다" desc="GitHub API에서 접근 가능한 저장소를 최신순으로 가져오고 있습니다." /></div>}
          {login && githubRepositoriesError && <div style={{ marginTop: 18 }}><StatePanel type="error" title="저장소 목록을 불러오지 못했습니다" desc={githubRepositoriesError} action="GitHub 재연동" /></div>}
          {login && !githubRepositoriesLoading && !githubRepositoriesError && !githubRepositories.length && <div style={{ marginTop: 18 }}><StatePanel type="empty" title="표시할 저장소가 없습니다" desc="GitHub 권한 범위에 읽을 수 있는 저장소가 없거나, 저장소 접근 권한 승인이 필요합니다." action="GitHub 재연동" /></div>}

          {githubRepositories.length > 0 && <div style={{ display: 'grid', gap: 10, marginTop: 20, maxHeight: 430, overflow: 'auto', paddingRight: 4 }}>
            {githubRepositories.map((repo) => {
              const selected = repo.fullName === selectedRepositoryFullName;
              return <button key={repo.fullName} onClick={() => {
                setSelectedRepositoryFullName(repo.fullName);
                setSelectedRepositoryRef(repo.defaultBranch || 'HEAD');
              }} style={{ font: 'inherit', cursor: 'pointer', textAlign: 'left', border: '1px solid ' + (selected ? greenStrong : line), background: selected ? greenTint : '#fff', borderRadius: 16, padding: 16, color: ink }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                  <GithubMark />
                  <b style={{ fontSize: 15.5 }}>{repo.fullName}</b>
                  <span style={{ color: repo.privateRepository ? coralDeep : greenInk, background: repo.privateRepository ? coralTint : greenTint, border: '1px solid ' + (repo.privateRepository ? coralTintBd : greenTintBd), borderRadius: 999, padding: '3px 8px', fontSize: 11, fontWeight: 900 }}>{repo.privateRepository ? 'private' : 'public'}</span>
                  <span style={{ marginLeft: 'auto', color: muted, fontSize: 12, fontWeight: 800 }}>{repo.defaultBranch || 'HEAD'}</span>
                </div>
                {repo.description && <div style={{ color: muted, fontSize: 13, lineHeight: 1.5, marginTop: 8 }}>{repo.description}</div>}
              </button>;
            })}
          </div>}
        </section>

        <aside style={{ display: 'grid', gap: 14 }}>
          <section style={{ ...tile, background: ink, color: '#fff' }}>
            <SecTitle color={green}>분석 실행</SecTitle>
            <div style={{ fontSize: 22, fontWeight: 900, letterSpacing: -0.5, lineHeight: 1.28 }}>{selectedRepository ? selectedRepository.fullName : '저장소를 선택해주세요.'}</div>
            <label style={{ display: 'block', marginTop: 18 }}>
              <div style={{ color: 'rgba(255,255,255,0.68)', fontSize: 12, fontWeight: 900, marginBottom: 7 }}>기준 브랜치 / ref</div>
              <input value={selectedRef} onChange={(event) => setSelectedRepositoryRef(event.target.value)} placeholder="main" style={{ width: '100%', boxSizing: 'border-box', font: 'inherit', border: '1px solid rgba(255,255,255,0.16)', background: 'rgba(255,255,255,0.08)', color: '#fff', borderRadius: 12, padding: '12px 13px', outline: 'none' }} />
            </label>
            <button disabled={!canImportRepository} onClick={importSelectedRepository} style={{ marginTop: 16, font: 'inherit', cursor: canImportRepository ? 'pointer' : 'not-allowed', width: '100%', border: 'none', background: canImportRepository ? green : 'rgba(255,255,255,0.10)', color: canImportRepository ? ink : 'rgba(255,255,255,0.42)', borderRadius: 14, padding: 14, fontWeight: 950 }}>
              {importingRepository ? '분석 중...' : '이 저장소 분석하기'}
            </button>
            <div style={{ display: 'grid', gap: 10, marginTop: 18 }}>
              {[
                ['1', '저장소 등록', '선택한 repository를 내 프로젝트로 저장합니다.'],
                ['2', '정적 분석', '빌드/인프라/workflow 파일에서 스킬과 경험 태그를 추출합니다.'],
                ['3', '매칭 연결', '프로젝트 ID를 저장하고 추천, 갭 분석, 매칭 화면에 반영합니다.'],
              ].map(([step, title, desc]) => <div key={step} style={{ borderTop: '1px solid rgba(255,255,255,0.13)', paddingTop: 12, display: 'grid', gridTemplateColumns: '30px 1fr', gap: 10 }}><span style={{ width: 26, height: 26, borderRadius: 10, background: 'rgba(185,236,42,0.14)', color: green, display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 1000 }}>{step}</span><div><b style={{ color: '#fff', fontSize: 13.5 }}>{title}</b><div style={{ color: 'rgba(255,255,255,0.65)', fontSize: 12.5, lineHeight: 1.5, marginTop: 3 }}>{desc}</div></div></div>)}
            </div>
          </section>
          <section style={tile}>
            <SecTitle>현재 프로젝트</SecTitle>
            <div style={{ display: 'grid', gap: 10 }}>
              {[
                ['프로젝트', currentProjectName],
                ['저장소', currentProjectRepo],
                ['스킬', currentProjectSkillSummary],
              ].map(([label, value]) => <div key={label} style={{ display: 'grid', gridTemplateColumns: '86px 1fr', gap: 10, borderTop: '1px solid ' + line, paddingTop: 10 }}><span style={{ color: faint, fontSize: 12, fontWeight: 900 }}>{label}</span><b style={{ fontSize: 13.5, lineHeight: 1.45 }}>{value}</b></div>)}
            </div>
          </section>
        </aside>
      </div>
    </Shell>;
  };

  const projectAnalysisScreen = () => {
    if (!hasProjectContext) {
      return <Shell title="프로젝트 분석" sub="프로젝트 분석 결과가 있어야 상세 리포트를 볼 수 있습니다." actions={<ActionButton onClick={() => go('projects')}>프로젝트 목록</ActionButton>}>
        <StatePanel type="empty" title="분석된 프로젝트가 없습니다" desc="분석 결과가 있는 프로젝트를 연결하면 스킬, 경험 태그, 매칭 공고가 이 화면에 표시됩니다." action="프로젝트 설정 확인" />
      </Shell>;
    }
    const p = JF.projectList.find((x) => x.name === previewProject && x.overview) || JF.projectList[0] || fallbackAnalyzedProject;
    const analysisApiUnavailable = ['skills', 'tags', 'matches'].some((key) => JF.__apiStatus?.[key] === 'unavailable');
    const detailTags = p.detailTags || JF.expTags;
    const projectDomain = p.domain || [];
    const projectStackGroups = p.stackGroups?.length ? p.stackGroups : fallbackAnalyzedProject.stackGroups;
    const projectStats = { commits: '-', files: '-', tests: '-', contributors: '-', ...(p.stats || {}) };
    const projectDirs = p.dirs || [];
    const stat = (v, l) => <div><b style={{ fontSize: 25, letterSpacing: -1, ...num }}>{v}</b><div style={{ fontSize: 11.5, color: muted, fontWeight: 700, marginTop: 2 }}>{l}</div></div>;
    return <Shell title={p.name}
      sub={<span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}><span style={{ position: 'relative', display: 'inline-flex', width: 18, height: 18, alignItems: 'center', justifyContent: 'center' }}><GithubMark size={17} /><span style={{ position: 'absolute', right: -5, bottom: -5, width: 13, height: 13, borderRadius: 7, background: green, border: '2px solid #fff', color: ink, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 9, fontWeight: 1000, lineHeight: 1 }}>✓</span></span><span style={{ whiteSpace: 'nowrap' }}>{p.repo}</span><span style={{ whiteSpace: 'nowrap' }}>· 마지막 분석 {p.analyzedAt}</span></span>}
      actions={<div style={{ display: 'flex', gap: 8 }}>{p.connected ? <><button onClick={() => go('projects')} style={{ font: 'inherit', cursor: 'pointer', border: '1px solid ' + line, background: '#fff', color: ink, borderRadius: 22, padding: '12px 16px', fontWeight: 800 }}>프로젝트 변경</button><button onClick={() => go('project-new')} style={{ font: 'inherit', cursor: 'pointer', border: 'none', background: ink, color: '#fff', borderRadius: 22, padding: '12px 18px', fontWeight: 800 }}>재분석 받기</button></> : <button onClick={() => go('login')} style={{ font: 'inherit', cursor: 'pointer', border: 'none', background: green, color: ink, borderRadius: 22, padding: '12px 18px', fontWeight: 900, display: 'flex', alignItems: 'center', gap: 7 }}><GithubMark size={15} />GitHub 연결하기</button>}</div>}>
      <div style={{ display: 'grid', gap: 18 }}>
        {analysisApiUnavailable && <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : 'repeat(3, 1fr)', gap: 12 }}>
          <StatePanel type="error" title="프로젝트 분석 정보를 일부 불러오지 못했습니다" desc="스킬, 경험 태그, 매칭 공고 중 일부를 불러오지 못했습니다. 현재 화면은 불러온 정보만 표시합니다." action="새로고침 후 재시도" />
        </div>}
        <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1.4fr 1fr', gap: 18, alignItems: 'stretch' }}>
          <div style={tile}>
            <SecTitle>OVERVIEW</SecTitle>
            <p style={{ fontSize: 15, lineHeight: 1.75, color: '#33363e', margin: '0 0 16px' }}>{p.overview}</p>
            <div style={{ display: 'flex', gap: 22, flexWrap: 'wrap' }}>
              <div><div style={{ fontSize: 11.5, color: faint, fontWeight: 700, marginBottom: 6 }}>도메인</div><div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>{projectDomain.length ? projectDomain.map((d) => <span key={d} style={{ fontSize: 12.5, fontWeight: 700, color: ink, background: soft, borderRadius: 8, padding: '5px 10px' }}>{d}</span>) : <span style={{ fontSize: 12.5, fontWeight: 700, color: muted, background: soft, borderRadius: 8, padding: '5px 10px' }}>태그 없음</span>}</div></div>
              <div><div style={{ fontSize: 11.5, color: faint, fontWeight: 700, marginBottom: 6 }}>아키텍처</div><span style={{ fontSize: 12.5, fontWeight: 700, color: greenInk, background: greenTint, border: '1px solid ' + greenTintBd, borderRadius: 8, padding: '5px 10px' }}>{p.architecture}</span></div>
            </div>
            <div style={{ borderTop: '1px solid ' + line, marginTop: 22, paddingTop: 18 }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10, marginBottom: 10 }}>
                <div style={{ fontSize: 11.5, color: faint, fontWeight: 900, letterSpacing: 0.6 }}>TECH STACK</div>
                <span style={{ fontSize: 11.5, color: greenInk, fontWeight: 900, background: greenTint, border: '1px solid ' + greenTintBd, borderRadius: 14, padding: '3px 8px' }}>{p.skillsTotal || 24}개 스킬 추출</span>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: 10 }}>
                {projectStackGroups.map((grp) => <div key={grp.label}>
                  <div style={{ fontSize: 12, fontWeight: 900, color: muted, marginBottom: 9 }}>{grp.label}</div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>{grp.items.slice(0, 4).map((it) => <span key={it.n} style={{ fontSize: 12.5, fontWeight: 760, color: ink, background: it.pct ? greenTint : soft, border: '1px solid ' + (it.pct ? greenTintBd : line), borderRadius: 8, padding: '5px 9px', whiteSpace: 'nowrap' }}>{it.n}{it.pct ? <b style={{ color: greenInk, marginLeft: 5, ...num }}>{it.pct}%</b> : null}</span>)}</div>
                </div>)}
              </div>
            </div>
          </div>
          <div style={tile}>
            <SecTitle>CODE STATS</SecTitle>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 16, marginBottom: 18 }}>{stat(projectStats.commits, '커밋')}{stat(projectStats.files, '파일')}{stat(projectStats.tests === '-' ? '-' : projectStats.tests + '%', '테스트 커버리지')}{stat(projectStats.contributors, '기여자')}</div>
            <div style={{ fontSize: 12, fontWeight: 800, color: muted, marginBottom: 9 }}>주요 디렉토리</div>
            {projectDirs.length ? projectDirs.map((d) => <div key={d.path} style={{ display: 'grid', gridTemplateColumns: '1fr 76px 34px', gap: 8, alignItems: 'center', marginBottom: 8 }}><span style={{ fontSize: 12.5, fontWeight: 600, fontFamily: 'monospace', color: ink, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{d.path}</span><div style={{ height: 6, background: soft, borderRadius: 3, overflow: 'hidden' }}><div style={{ width: d.share + '%', height: '100%', background: green, borderRadius: 3 }} /></div><span style={{ fontSize: 11.5, color: muted, fontWeight: 700, textAlign: 'right', ...num }}>{d.share}%</span></div>) : <div style={{ color: muted, fontSize: 12.5, lineHeight: 1.55 }}>기존 분석 결과입니다. 재분석을 실행하면 커밋, 전체 파일, 기여자, 주요 디렉토리 통계가 표시됩니다.</div>}
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
            <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between' }}><H>이 프로젝트와 잘 맞는 공고</H><span style={{ fontSize: 12.5, fontWeight: 800, color: greenInk }}>{p.matchedJobs}개</span></div>
            {JF.matches.map((m) => <div key={(m.jobId || m.id || '') + m.companyKo} onClick={() => go('detail', { jobId: m.jobId || m.id, company: m.companyKo })} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '11px 0', borderTop: '1px solid ' + line, cursor: 'pointer' }}><Logo text={m.logo} size={34} /><div style={{ flex: 1, minWidth: 0 }}><b style={{ fontSize: 14 }}>{m.companyKo}</b><div style={{ color: muted, fontSize: 12, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{m.title} · {m.level}</div></div><b style={{ color: ink, ...num }}>{m.score}%</b></div>)}
            <button onClick={() => go('jobs')} style={{ font: 'inherit', cursor: 'pointer', width: '100%', marginTop: 14, border: '1px solid ' + line, background: '#fff', color: ink, borderRadius: 12, padding: 12, fontWeight: 800 }}>매칭 공고 전체 보기 →</button>
          </div>
        </div>
      </div>
    </Shell>;
  };

  const LockIcon = ({ size = 15 }) => <svg width={size} height={size} viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0 }}><rect x="3" y="7" width="10" height="7" rx="1.5"/><path d="M5 7V5a3 3 0 0 1 6 0v2"/></svg>;
  const gapScreen = () => {
    const UnlockJobs = ({ jobs }) => <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '132px 1fr', gap: 10, alignItems: 'start', marginTop: 14 }}><div style={{ display: 'inline-flex', alignItems: 'center', gap: 6, color: faint, fontSize: 11.5, fontWeight: 900, paddingTop: 6 }}><LockIcon size={13} />잠금 해제 공고</div><div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>{jobs.map((x) => <span key={x} onClick={() => go('detail', { company: x })} style={{ background: greenTint, color: greenInk, border: '1px solid ' + greenTintBd, borderRadius: 16, padding: '6px 11px', fontSize: 12.5, fontWeight: 900, cursor: 'pointer' }}>{x}</span>)}</div></div>;
    const gapMissingProject = JF.__apiStatus?.gap === 'missing-project';
    const gapUnavailable = JF.__apiStatus?.gap === 'unavailable';
    const hasGapContext = Boolean(JF.__userProjectId && JF.projectList?.length && JF.gapSkills.length);
    const needsProjectForGap = !JF.__userProjectId || !JF.projectList?.length || gapMissingProject;
    const gapPreviewRows = hasGapContext
      ? JF.gapSkills.slice(0, 6).map((gap) => ({ name: gap.skill, rate: Math.min(100, Math.max(10, Number(gap.addedJobs || 0) * 8)), owned: false }))
      : [];
    return <Shell title="갭 분석" sub="공고가 아니라 스킬이 주인공입니다. 무엇을 보강하면 열리는 공고가 얼마나 늘어나는지 보여줍니다."><div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '0.95fr 1.25fr', gap: 18 }}><div style={{ background: ink, color: '#fff', borderRadius: 22, padding: 26, boxShadow: '0 14px 30px rgba(20,21,26,0.16)', display: 'flex', flexDirection: 'column' }}><SecTitle color={green}>스킬 갭 분석</SecTitle><div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 18 }}><span style={{ background: 'rgba(255,255,255,0.12)', border: '1px solid rgba(255,255,255,0.18)', borderRadius: 18, padding: '6px 10px', fontSize: 12, fontWeight: 900 }}>{currentProjectName}</span><span style={{ background: 'rgba(255,255,255,0.12)', border: '1px solid rgba(255,255,255,0.18)', borderRadius: 18, padding: '6px 10px', fontSize: 12, fontWeight: 900 }}>백엔드</span><span style={{ background: 'rgba(255,255,255,0.12)', border: '1px solid rgba(255,255,255,0.18)', borderRadius: 18, padding: '6px 10px', fontSize: 12, fontWeight: 900 }}>주니어</span></div><div style={{ fontSize: 24, fontWeight: 900, lineHeight: 1.25, letterSpacing: -0.5 }}>{hasGapContext ? '내 프로젝트 스킬과 진행 중인 공고를 비교해 부족한 스킬을 집계합니다.' : '프로젝트 분석 결과가 연결되면 부족 스킬을 계산합니다.'}</div><p style={{ color: 'rgba(255,255,255,0.65)', lineHeight: 1.65 }}>{hasGapContext ? `${currentProjectSkillSummary} 기준으로 필수 스킬과 우대 스킬을 나눠 매칭률을 계산하고, 어떤 스킬을 보강하면 더 많은 공고가 열리는지 우선순위로 보여줍니다.` : '스킬 인벤토리와 JD 매칭 결과가 준비되어야 부족 스킬, 열리는 공고 수, 보강 우선순위를 계산할 수 있습니다.'}</p>{gapPreviewRows.length ? gapPreviewRows.map((tr) => <div key={tr.name} style={{ display: 'grid', gridTemplateColumns: '104px 1fr 64px', gap: 10, alignItems: 'center', marginTop: 13 }}><span style={{ color: '#fff', fontWeight: 800 }}>{tr.name}</span><div style={{ height: 8, background: 'rgba(255,255,255,0.12)', borderRadius: 4 }}><div style={{ width: tr.rate + '%', height: '100%', background: coral, borderRadius: 4 }} /></div><span style={{ color: '#ffb4a6', fontSize: 12, fontWeight: 900, textAlign: 'right' }}>보강 필요</span></div>) : <div style={{ marginTop: 18, padding: 16, borderRadius: 16, background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.14)', color: 'rgba(255,255,255,0.72)', fontSize: 13.5, lineHeight: 1.6 }}>현재는 전체 트렌드가 아니라 프로젝트 분석 결과가 필요한 화면입니다. 로그인 후 분석된 프로젝트 ID를 연결하면 실제 부족 스킬이 표시됩니다.</div>}<button onClick={() => go(hasGapContext ? 'jobs' : 'projects')} style={{ marginTop: 22, font: 'inherit', cursor: 'pointer', border: 'none', background: green, color: ink, borderRadius: 14, padding: 13, fontWeight: 900 }}>{hasGapContext ? '부족 스킬 요구 공고 보기 →' : '프로젝트 설정 확인 →'}</button></div><div style={{ display: 'grid', gap: 12 }}>{JF.gapSkills.length ? JF.gapSkills.map((g, i) => <div key={g.skill} style={{ ...tile, borderColor: i === 0 ? greenStrong : line, padding: 24 }}><div style={{ display: 'flex', alignItems: 'center', gap: 12 }}><div style={{ width: 42, height: 42, borderRadius: 14, background: i === 0 ? green : greenTint, color: ink, display: 'flex', alignItems: 'center', justifyContent: 'center' }}><LockIcon size={18} /></div><div style={{ flex: 1, minWidth: 0 }}><div style={{ display: 'flex', alignItems: 'baseline', gap: 9, flexWrap: 'wrap' }}><b style={{ fontSize: 26, letterSpacing: -0.8 }}>{g.skill}</b><span style={{ color: muted, fontSize: 13, fontWeight: 700 }}>배우면 열리는 기회</span></div><div style={{ color: muted, fontSize: 13, lineHeight: 1.55, marginTop: 4 }}>{g.reason}</div></div><span style={{ display: 'inline-flex', alignItems: 'baseline', gap: 3, whiteSpace: 'nowrap' }}><span style={{ fontSize: 12.5, fontWeight: 700, color: muted }}>매칭 후보</span><b style={{ color: ink, fontWeight: 900, fontSize: 24, ...num }}>+{g.addedJobs}</b><span style={{ fontSize: 13, fontWeight: 800, color: muted }}>개</span></span></div><div style={{ color: ink, fontSize: 12.5, lineHeight: 1.55, marginTop: 14, background: soft, borderRadius: 12, padding: '10px 12px' }}>다음 액션: 미니 프로젝트나 기존 프로젝트에 {g.skill} 사용 근거를 만들고 재분석하면 추천 후보가 늘어납니다.</div><UnlockJobs jobs={g.unlocked || []} /></div>) : <StatePanel type={needsProjectForGap ? 'empty' : gapUnavailable ? 'error' : 'empty'} title={needsProjectForGap ? '프로젝트 분석 결과가 필요합니다' : gapUnavailable ? '갭 분석 결과를 불러오지 못했습니다' : '갭 분석 결과가 없습니다'} desc={needsProjectForGap ? '로그인 후 분석된 프로젝트 ID를 연결하면 부족 스킬과 열린 공고 후보를 조회합니다.' : gapUnavailable ? '잠시 후 다시 시도하거나 로그인과 프로젝트 설정을 확인해주세요.' : '현재 프로젝트 기준으로 표시할 부족 스킬이 없습니다.'} action="프로젝트 설정 확인" />}</div></div></Shell>;
  };


  const recommendationsScreen = () => {
    const recommendationApiMissing = JF.__apiStatus?.recommendations === 'missing-project';
    const recommendationApiUnavailable = JF.__apiStatus?.recommendations === 'unavailable';
    const feed = (JF.recommendations || []).map((m) => ({ ...m, score: m.score || 0 }));
    const recommendationState = recommendationApiMissing
      ? { type: 'empty', title: '프로젝트 ID가 필요합니다', desc: '분석된 프로젝트를 연결하면 추천 공고를 프로젝트 기준으로 정렬합니다.' }
      : recommendationApiUnavailable
        ? { type: 'error', title: '추천 공고를 불러오지 못했습니다', desc: '잠시 후 다시 시도하거나 로그인과 프로젝트 설정을 확인해주세요.' }
        : { type: 'empty', title: '추천 공고가 없습니다', desc: '현재 프로젝트 기준으로 추천할 공고가 없거나 아직 추천 응답이 비어 있습니다.' };
    return <Shell title="추천 피드" sub="프로젝트 기준 추천 공고를 계속 넘겨 보며 저장하거나 상세로 들어갑니다." actions={<div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}><ActionButton primary onClick={() => go('projects')}>{currentProjectName} 기준</ActionButton><ActionButton onClick={() => go('jobs')}>백엔드</ActionButton><ActionButton onClick={() => go('jobs')}>신입·주니어</ActionButton></div>}><div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '320px 1fr', gap: 18, alignItems: 'start' }}><aside style={{ display: 'grid', gap: 12 }}><div style={{ background: ink, color: '#fff', borderRadius: 22, padding: 22 }}><SecTitle color={green}>추천 기준</SecTitle><div style={{ fontSize: 28, fontWeight: 900, letterSpacing: -1, lineHeight: 1.15 }}>매칭률과 마감 신호를 섞어<br /><span style={{ color: green }}>먼저 볼 공고</span>를 정렬합니다.</div><div style={{ color: 'rgba(255,255,255,0.58)', fontSize: 12.5, marginTop: 10, lineHeight: 1.5 }}>{currentProjectRepo}</div><div style={{ display: 'grid', gap: 10, marginTop: 20 }}>{[['스킬 매칭', '70%'], ['신선도', '15%'], ['행동 신호', '10%'], ['인기', '5%']].map(([l, v]) => <div key={l} style={{ display: 'grid', gridTemplateColumns: '1fr 52px', gap: 10, alignItems: 'center' }}><span style={{ color: 'rgba(255,255,255,0.72)', fontSize: 13, fontWeight: 800 }}>{l}</span><b style={{ color: green, textAlign: 'right', ...num }}>{v}</b></div>)}</div></div>{feed.length ? <StatePanel type="empty" title="추천 공고를 불러왔습니다" desc={`${feed.length}개 공고를 프로젝트 기준으로 정렬했습니다.`} action="공고 상세 보기" /> : <StatePanel type={recommendationState.type} title={recommendationState.title} desc={recommendationState.desc} action="프로젝트 설정 확인" />}</aside><section>{feed.length ? <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : 'repeat(2, minmax(0, 1fr))', gap: 14 }}>{feed.map((m, i) => <JobThumbnail key={(m.jobId || m.id || '') + (m.companyKo || m.company) + i} job={{ ...m, skills: m.matched || m.skills }} score={m.score} compact />)}</div> : <StatePanel type={recommendationState.type} title={recommendationState.title} desc={recommendationState.desc} action="프로젝트 설정 확인" />}</section></div></Shell>;
  };
  const trendsScreen = () => {
    const trendRows = JF.trends.slice().sort((a, b) => (b.jobCount || 0) - (a.jobCount || 0));
    const topTrend = trendRows.find((item) => item.name === trendSkill) || trendRows[0] || selectedTrend;
    const maxJobCount = Math.max(1, ...trendRows.map((tr) => tr.jobCount || 0));
    const missingRows = trendRows.filter((tr) => !tr.owned);
    const selectedTrendDemand = `${(topTrend.jobCount || 0).toLocaleString()}개`;
    const BarRow = ({ tr }) => {
      const width = Math.max(3, Math.round(((tr.jobCount || 0) / maxJobCount) * 100));
      return <button onClick={() => setTrendSkill(tr.name)} style={{ font: 'inherit', cursor: 'pointer', border: 'none', background: 'transparent', color: '#fff', display: 'grid', gridTemplateColumns: narrow ? '86px 1fr 58px' : '112px 1fr 72px', gap: 12, alignItems: 'center', padding: '8px 0', width: '100%', textAlign: 'left' }}>
        <b style={{ color: tr.owned ? green : '#fff', fontSize: 13.5, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{tr.name}</b>
        <span style={{ height: 9, background: 'rgba(255,255,255,0.12)', borderRadius: 999, overflow: 'hidden' }}><span style={{ display: 'block', width: width + '%', height: '100%', background: tr.owned ? green : coral, borderRadius: 999 }} /></span>
        <span style={{ color: tr.owned ? green : '#ffb4a6', textAlign: 'right', fontWeight: 900, fontSize: 13, ...num }}>{(tr.jobCount || 0).toLocaleString()}개</span>
      </button>;
    };
    const CountCard = ({ label, value, tone }) => <div style={{ background: tone === 'dark' ? ink : '#fff', color: tone === 'dark' ? '#fff' : ink, border: '1px solid ' + (tone === 'dark' ? ink : line), borderRadius: 18, padding: 18 }}>
      <b style={{ fontSize: 26, letterSpacing: -0.7, ...num }}>{value}</b>
      <div style={{ color: tone === 'dark' ? 'rgba(255,255,255,0.58)' : muted, fontSize: 12.5, fontWeight: 850, marginTop: 5 }}>{label}</div>
    </div>;
    return <Shell title="트렌드" sub="이번 달 공고에서 많이 요구된 스킬을 집계하고, 내 프로젝트에 없는 스킬은 바로 공고·갭 분석으로 이어갑니다." actions={<span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, border: '1px solid ' + line, background: '#fff', borderRadius: 999, padding: '10px 14px', fontSize: 13, fontWeight: 900, color: muted }}><span style={{ width: 7, height: 7, borderRadius: 4, background: green }} />전체 공고 기준</span>}>
      <div style={{ display: 'grid', gap: 18 }}>
        <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1.2fr 1fr', gap: 0, background: ink, color: '#fff', borderRadius: 24, overflow: 'hidden', boxShadow: '0 14px 30px rgba(20,21,26,0.16)' }}>
          <div style={{ padding: narrow ? 22 : 30 }}>
            <SecTitle color={green}>이번 달 스킬 트렌드</SecTitle>
            <div style={{ fontSize: narrow ? 26 : 34, fontWeight: 950, lineHeight: 1.2, letterSpacing: -1 }}>이번 달 가장 많이 보인 스킬은 <span style={{ color: green }}>{topTrend.name}</span>입니다.</div>
            <p style={{ color: 'rgba(255,255,255,0.68)', lineHeight: 1.72, margin: '14px 0 0' }}>{topTrend.insight || '이번 달 공고 집계 결과를 기반으로 보유 스킬과 미보유 스킬의 차이를 보여줍니다.'}</p>
            <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : 'repeat(3, 1fr)', gap: 10, marginTop: 22 }}>
              {[['관련 공고', `${(topTrend.jobCount || 0).toLocaleString()}개`], ['필수 요구', `${(topTrend.requiredCount || 0).toLocaleString()}개`], ['우대 언급', `${(topTrend.preferredCount || 0).toLocaleString()}개`]].map(([label, value]) => <div key={label} style={{ background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.12)', borderRadius: 16, padding: 14 }}><b style={{ color: green, fontSize: 20, ...num }}>{value}</b><div style={{ color: 'rgba(255,255,255,0.56)', fontSize: 11.5, fontWeight: 850, marginTop: 3 }}>{label}</div></div>)}
            </div>
          </div>
          <div style={{ padding: narrow ? 22 : 30, borderLeft: narrow ? 'none' : '1px solid rgba(255,255,255,0.1)', borderTop: narrow ? '1px solid rgba(255,255,255,0.1)' : 'none', display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
            {trendRows.map((tr) => <BarRow key={tr.name} tr={tr} />)}
          </div>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : 'repeat(4, 1fr)', gap: 14 }}>
          <CountCard label="이번 달 집계 공고" value={`${(JF.market.totalCount || 0).toLocaleString()}개`} />
          <CountCard label="트렌드 스킬" value={`${trendRows.length.toLocaleString()}개`} />
          <CountCard label="내 보유 스킬" value={`${trendRows.filter((x) => x.owned).length}/${trendRows.length}`} />
          <CountCard label="선택 스킬 관련 공고" value={selectedTrendDemand} tone="dark" />
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1fr 1fr 1fr', gap: 16 }}>
          <div style={tile}><H>내 스킬과의 차이</H>{missingRows.length ? missingRows.map((tr) => <div key={tr.name} onClick={() => setTrendSkill(tr.name)} style={{ padding: '12px 0', borderTop: '1px solid ' + line, cursor: 'pointer' }}><b style={{ color: coralDeep }}>{tr.name} 근거 부족</b><div style={{ color: muted, fontSize: 13, lineHeight: 1.55, marginTop: 4 }}>{tr.insight}</div><button onClick={(e) => { e.stopPropagation(); go('jobs'); }} style={{ marginTop: 8, font: 'inherit', border: '1px solid ' + line, background: '#fff', borderRadius: 12, padding: '7px 10px', fontSize: 12, fontWeight: 850, cursor: 'pointer' }}>{tr.name} 요구 공고 보기</button></div>) : <div style={{ color: muted, fontSize: 13.5, lineHeight: 1.6 }}>현재 표시된 상위 스킬은 모두 프로젝트에 근거가 있습니다.</div>}</div>
          <div style={tile}><H>함께 확인할 스택</H><div style={{ color: muted, fontSize: 13, lineHeight: 1.55, marginBottom: 12 }}>상위 트렌드 스킬끼리 같이 검토하면 공고 탐색 범위를 잡기 좋아요.</div><div style={{ display: 'flex', flexWrap: 'wrap', gap: 7 }}>{trendCooccurrenceSkills.map((x) => <button key={x} onClick={() => setTrendSkill(x)} style={{ font: 'inherit', cursor: 'pointer', border: '1px solid ' + (x === topTrend.name ? greenStrong : line), background: x === topTrend.name ? green : '#fff', color: ink, borderRadius: 18, padding: '7px 10px', fontSize: 12.5, fontWeight: 850 }}>{x}</button>)}</div></div>
          <div style={tile}><H>다음 액션</H><div style={{ color: muted, fontSize: 13, lineHeight: 1.6 }}>트렌드는 읽고 끝나는 리포트가 아니라, 부족 스킬의 공고와 갭 분석으로 이어져야 합니다.</div><div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginTop: 14 }}><ActionButton primary onClick={() => go('gap')}>갭 분석 보기</ActionButton><ActionButton onClick={() => go('jobs')}>관련 공고 보기</ActionButton></div></div>
        </div>
      </div>
    </Shell>;
  };
  const mypageScreen = () => <Shell title="설정" sub="계정 정보, GitHub 연동, 로그아웃을 관리합니다." actions={<ActionButton danger onClick={() => setSettingsModal('logout')}>로그아웃</ActionButton>}><div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1fr 1fr', gap: 18, alignItems: 'start' }}><section style={{ ...tile }}><SecTitle>계정</SecTitle><div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 18 }}><div style={{ width: 52, height: 52, borderRadius: 18, background: ink, color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 18, fontWeight: 900 }}>{currentUserInitial}</div><div><H>{currentUserName}</H><div style={{ color: muted, fontSize: 13 }}>{currentUserEmail} · {JF.user?.github ? 'GitHub 로그인' : '로그인 상태'}</div></div></div><div style={{ display: 'grid', gap: 10 }}>{[['이름', currentUserName], ['이메일', currentUserEmail], ['권한', currentUserRole]].map(([l, v]) => <div key={l} style={{ display: 'grid', gridTemplateColumns: '100px 1fr', gap: 12, padding: '10px 0', borderTop: '1px solid ' + line }}><span style={{ color: faint, fontSize: 12, fontWeight: 900 }}>{l}</span><b style={{ fontSize: 13.5 }}>{v}</b></div>)}</div><div style={{ marginTop: 18, display: 'flex', gap: 8, flexWrap: 'wrap' }}><ActionButton onClick={() => setSettingsModal('profile')}>프로필 수정</ActionButton><ActionButton danger onClick={() => setSettingsModal('security')}>계정 보안</ActionButton></div></section><section style={{ display: 'grid', gap: 12 }}><div style={{ ...tile, background: greenTint, borderColor: greenTintBd }}><SecTitle color={greenInk}>GitHub 연동</SecTitle><div style={{ display: 'flex', alignItems: 'center', gap: 12 }}><span style={{ position: 'relative', width: 38, height: 38, borderRadius: 14, background: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><GithubMark size={20} /><span style={{ position: 'absolute', right: -4, bottom: -4, width: 14, height: 14, borderRadius: 7, background: green, border: '2px solid #fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 9, fontWeight: 1000 }}>✓</span></span><div><H>{JF.user?.github ? 'GitHub 연동됨' : 'GitHub 연동 정보 없음'}</H><div style={{ color: muted, fontSize: 13 }}>{JF.user?.github ? 'GitHub 연결 완료' : '연동 후 비공개 저장소 분석 가능'} · {analyzedProjectCount}개 프로젝트</div></div></div><div style={{ marginTop: 16, display: 'flex', gap: 8, flexWrap: 'wrap' }}><ActionButton primary onClick={() => go('projects')}>프로젝트 관리</ActionButton><ActionButton onClick={() => setSettingsModal('reconnect')}>재연동</ActionButton><ActionButton danger onClick={() => setSettingsModal('disconnect')}>연동 해제</ActionButton></div></div><div style={{ ...tile }}><SecTitle>연결된 데이터</SecTitle><div style={{ display: 'grid', gap: 10 }}>{[['분석 프로젝트', `${analyzedProjectCount}개`], ['저장 공고', `${JF.saved || 0}개`], ['지원 기록', `${JF.applications?.length || 0}개`]].map(([label, value]) => <div key={label} style={{ display: 'grid', gridTemplateColumns: '112px 1fr', gap: 10, borderTop: '1px solid ' + line, paddingTop: 10 }}><span style={{ color: faint, fontSize: 12, fontWeight: 900 }}>{label}</span><b style={{ fontSize: 13.5 }}>{value}</b></div>)}</div></div></section></div>{settingsModal && <Modal title={settingsModal === 'profile' ? '프로필 수정' : settingsModal === 'security' ? '계정 보안' : settingsModal === 'reconnect' ? 'GitHub 재연동' : settingsModal === 'disconnect' ? 'GitHub 연동 해제' : '로그아웃'} sub={settingsModal === 'disconnect' ? '연동을 해제하면 비공개 저장소 분석과 재분석이 중단됩니다.' : settingsModal === 'logout' ? '현재 기기에서만 로그아웃됩니다.' : '설정 변경 전 사용자에게 영향 범위를 짧게 설명합니다.'} onClose={() => setSettingsModal(null)}>{settingsModal === 'profile' && <><Field label="표시 이름" value={currentUserName} /><Field label="이메일" value={currentUserEmail} /><div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}><ActionButton onClick={() => setSettingsModal(null)}>취소</ActionButton><ActionButton primary onClick={() => setSettingsModal(null)}>저장</ActionButton></div></>}{settingsModal === 'security' && <div style={{ display: 'grid', gap: 10 }}><StatePanel type="unauthorized" title="비밀번호 변경" desc="로컬 로그인 사용자는 비밀번호 변경 링크를 이메일로 받습니다." action="메일 보내기" /><StatePanel type="forbidden" title="로그인 세션 관리" desc="다른 기기의 세션을 만료시키는 액션을 제공합니다." secondary="전체 로그아웃" /></div>}{settingsModal === 'reconnect' && <><StatePanel type="loading" title="GitHub 권한 다시 확인" desc="저장소 읽기 권한을 다시 승인하면 비공개 프로젝트 분석이 가능해집니다." /><div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 12 }}><ActionButton onClick={() => setSettingsModal(null)}>취소</ActionButton><ActionButton primary onClick={() => setSettingsModal(null)}>GitHub로 이동</ActionButton></div></>}{settingsModal === 'disconnect' && <><StatePanel type="forbidden" title="정말 해제할까요?" desc="이미 분석된 결과는 남지만, 새 분석과 재분석은 GitHub 재연동 전까지 불가능합니다." /><div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 12 }}><ActionButton onClick={() => setSettingsModal(null)}>취소</ActionButton><ActionButton danger onClick={() => setSettingsModal(null)}>연동 해제</ActionButton></div></>}{settingsModal === 'logout' && <><StatePanel type="empty" title="로그아웃하시겠어요?" desc="저장된 공고와 프로젝트 분석 결과는 계정에 그대로 남습니다." /><div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 12 }}><ActionButton onClick={() => setSettingsModal(null)}>취소</ActionButton><ActionButton danger onClick={() => { jobflowActions.logout?.(); setSettingsModal(null); go('login'); }}>로그아웃</ActionButton></div></>}</Modal>}</Shell>;
  const userJobsScreen = () => {
    const hasUserJobApi = ['saved', 'viewed', 'ignored'].some((key) => JF.__apiStatus?.[key] === 'ok');
    const userJobApiUnavailable = ['saved', 'viewed', 'ignored'].some((key) => JF.__apiStatus?.[key] === 'unavailable');
    const savedJobs = JF.userJobs?.saved || [];
    const viewedJobs = JF.userJobs?.viewed || [];
    const hiddenJobs = JF.userJobs?.ignored || [];
    const ignoredCount = JF.ignored ?? hiddenJobs.length;
    const TabBlock = ({ title, count, desc, jobs, hidden }) => <section style={{ ...tile }}><div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 12, marginBottom: 14 }}><div><H>{title}</H><div style={{ color: muted, fontSize: 13 }}>{desc}</div></div><b style={{ color: hidden ? coralDeep : greenInk, ...num }}>{count}</b></div>{jobs.length ? <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : 'repeat(2,1fr)', gap: 12 }}>{jobs.map((j) => <JobThumbnail key={title + j.companyKo} job={j} compact />)}</div> : <StatePanel type="empty" title="아직 공고가 없습니다" desc="관심 있는 공고를 저장하면 여기에 쌓입니다." action="공고 보러가기" />}{hidden && <div style={{ marginTop: 12 }}><StatePanel type="empty" title="숨김 해제 가능" desc="숨긴 공고는 추천 피드에서 제외되며, 필요하면 다시 되돌릴 수 있어요." secondary="숨김 해제" /></div>}</section>;
    return <Shell title="저장 / 숨김 / 조회 공고" sub="관심 공고, 최근 본 공고, 숨긴 공고를 한 화면에서 관리합니다." actions={<ActionButton primary onClick={() => go('jobs')}>공고 더 보기</ActionButton>}><div style={{ display: 'grid', gap: 18 }}>{userJobApiUnavailable && <StatePanel type="error" title="내 공고를 불러오지 못했습니다" desc="저장, 조회, 숨김 공고 중 일부를 불러오지 못했습니다. 잠시 후 다시 시도해주세요." action="새로고침 후 재시도" />}{!hasUserJobApi && !userJobApiUnavailable && <StatePanel type="empty" title="내 공고 데이터 대기" desc="로그인 후 저장하거나 조회한 공고가 생기면 이 화면에 표시됩니다." action="공고 보러가기" />}<div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : 'repeat(3, 1fr)', gap: 12 }}>{[['저장한 공고', JF.saved, '지원 전 다시 볼 후보'], ['최근 본 공고', JF.viewed, '읽었지만 저장하지 않은 공고'], ['숨긴 공고', ignoredCount, '추천에서 제외한 공고']].map(([l, v, d], i) => <div key={l} style={{ background: i === 2 ? coralTint : greenTint, border: '1px solid ' + (i === 2 ? coralTintBd : greenTintBd), borderRadius: 18, padding: 18 }}><b style={{ fontSize: 26, ...num }}>{v}</b><div style={{ fontWeight: 900, marginTop: 4 }}>{l}</div><div style={{ color: muted, fontSize: 12.5, marginTop: 3 }}>{d}</div></div>)}</div><TabBlock title="저장한 공고" count={JF.saved} desc="북마크한 공고입니다. 상세에서 지원 기록을 추가할 수 있어요." jobs={savedJobs} /><TabBlock title="최근 본 공고" count={JF.viewed} desc="조회 기록 기준으로 최근 열어본 공고입니다." jobs={viewedJobs} /><TabBlock title="숨긴 공고" count={ignoredCount} desc="관심 없다고 숨긴 공고입니다." jobs={hiddenJobs} hidden /></div></Shell>;
  };
  const demoStatusScreen = () => {
    const mono = "ui-monospace, 'SF Mono', 'JetBrains Mono', Menlo, monospace";
    const apiStatusEntries = Object.entries(JF.__apiStatus || {});
    const apiOkCount = apiStatusEntries.filter(([, status]) => status === 'ok').length;
    const apiUnavailableCount = apiStatusEntries.filter(([, status]) => status !== 'ok').length;
    const apiHealthLabel = apiStatusEntries.length && apiUnavailableCount === 0 ? '정상 연결' : apiStatusEntries.length ? '일부 점검 필요' : '확인 전';
    const apiHealthOk = apiStatusEntries.length > 0 && apiOkCount > 0;
    const services = [['요청 라우팅', apiHealthOk ? '정상' : '확인 필요', 8081], ['애플리케이션 서버', apiHealthOk ? '정상' : '확인 필요', 8080], ['데이터베이스', '설정됨', 3306], ['캐시', '설정됨', 6379], ['검색 인덱스', '설정됨', 9200], ['메트릭 수집', '설정됨', 9090], ['대시보드', '설정됨', 3001], ['트레이싱', '설정됨', 9411]];
    const smokes = apiStatusEntries.map(([name, status]) => [name, status === 'ok' ? '정상' : '확인 필요', status, '-']);
    const spark = (seed) => { const pts = Array.from({length:24},(_,k)=>40+Math.round(28*Math.abs(Math.sin(seed*1.3+k*0.55))+ (k%5===0?10:0))); const w=120,h=34,mx=Math.max(...pts); const d=pts.map((p,k)=>(k/(pts.length-1))*w+','+(h-(p/mx)*h)).join(' '); return <svg width={w} height={h} viewBox={'0 0 '+w+' '+h} preserveAspectRatio="none" style={{ display:'block' }}><polyline points={d} fill="none" stroke={green} strokeWidth="1.6" /><polyline points={'0,'+h+' '+d+' '+w+','+h} fill={green} opacity="0.08" stroke="none" /></svg>; };
    const Dot = ({ ok }) => <span style={{ position:'relative', width: 8, height: 8, flexShrink: 0 }}><span style={{ position:'absolute', inset: 0, borderRadius: 5, background: ok ? green : coral }} /><span className="jf-pulse" style={{ position:'absolute', inset: -3, borderRadius: 8, border: '1.5px solid ' + (ok ? green : coral) }} /></span>;
    const KPI = ({ label, value, unit, sub, seed }) => <div style={{ background: '#0e1116', border: '1px solid #1c2230', borderRadius: 16, padding: 18, position: 'relative', overflow: 'hidden' }}><div style={{ fontFamily: mono, color: '#5f6b7d', fontSize: 11, fontWeight: 700, letterSpacing: 1 }}>{label}</div><div style={{ display: 'flex', alignItems: 'baseline', gap: 4, marginTop: 8 }}><b style={{ fontFamily: mono, fontSize: 30, letterSpacing: -1, color: '#eef2f7' }}>{value}</b>{unit && <span style={{ fontFamily: mono, color: green, fontSize: 14, fontWeight: 700 }}>{unit}</span>}</div><div style={{ color: '#5f6b7d', fontSize: 11.5, marginTop: 6, fontFamily: mono }}>{sub}</div><div style={{ marginTop: 12, opacity: 0.9 }}>{spark(seed)}</div></div>;
    return <Shell title="시스템 상태" sub="서비스 상태, 데이터 흐름, 화면 연결 상태, 관측 지표를 한 화면에서 확인합니다." actions={<div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}><ActionButton onClick={() => go('home')}>사용자 홈</ActionButton><ActionButton primary onClick={() => go('project-analysis')}>핵심 플로우 보기</ActionButton></div>}>
      <div style={{ background: '#0a0d12', borderRadius: 24, padding: narrow ? 16 : 24, boxShadow: '0 24px 60px rgba(10,13,18,0.4)', backgroundImage: 'linear-gradient(#11151c 1px, transparent 1px), linear-gradient(90deg, #11151c 1px, transparent 1px)', backgroundSize: '34px 34px' }}>
        {/* console header bar */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap', padding: '4px 4px 18px', borderBottom: '1px solid #1c2230', marginBottom: 18 }}>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontFamily: mono, color: apiHealthOk ? green : coral, fontSize: 13, fontWeight: 800 }}><Dot ok={apiHealthOk} /> {apiHealthLabel}</span>
          <span style={{ fontFamily: mono, color: '#5f6b7d', fontSize: 12.5 }}>환경 <span style={{ color: '#aeb7c4' }}>로컬</span></span>
          <span style={{ fontFamily: mono, color: '#5f6b7d', fontSize: 12.5 }}>정상 <span style={{ color: green }}>{apiOkCount}</span></span>
          <span style={{ fontFamily: mono, color: '#5f6b7d', fontSize: 12.5 }}>점검 <span style={{ color: apiUnavailableCount ? coral : green }}>{apiUnavailableCount}</span></span>
          <span style={{ marginLeft: 'auto', fontFamily: mono, color: '#5f6b7d', fontSize: 12.5 }}>프로젝트 <span style={{ color: '#aeb7c4' }}>{JF.__userProjectId || '미연결'}</span></span>
        </div>
        {/* KPI row */}
        <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr 1fr' : 'repeat(4, 1fr)', gap: 12 }}>
          <KPI label="공고 인덱스" value={String(JF.market?.totalCount || JF.listings?.length || 0)} sub="검색 가능한 공고" seed={1} />
          <KPI label="연결된 기능" value={String(apiOkCount)} sub={`점검 필요 ${apiUnavailableCount}개`} seed={2} />
          <KPI label="분석 프로젝트" value={String(analyzedProjectCount)} sub="현재 계정 기준" seed={3} />
          <KPI label="지원 기록" value={String(JF.applications?.length || 0)} sub="추적 중인 지원" seed={4} />
        </div>
        {/* health + architecture */}
        <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1.05fr 0.95fr', gap: 14, marginTop: 14 }}>
          <section style={{ background: '#0e1116', border: '1px solid #1c2230', borderRadius: 16, padding: 18 }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}><span style={{ fontFamily: mono, color: '#5f6b7d', fontSize: 11, fontWeight: 800, letterSpacing: 1 }}>시스템 상태</span><span style={{ fontFamily: mono, color: apiHealthOk ? green : coral, fontSize: 11, fontWeight: 800 }}>{apiStatusEntries.length ? `${apiOkCount}/${apiStatusEntries.length} 연결` : '확인 전'}</span></div>
            <div style={{ display: 'grid', gap: 0, marginTop: 8 }}>{services.map(([name, st, port]) => <div key={name} style={{ display: 'grid', gridTemplateColumns: '16px 1fr 88px', gap: 10, alignItems: 'center', padding: '11px 0', borderTop: '1px solid #161b24' }}><Dot ok={st==='정상' || st === '설정됨'} /><span style={{ fontFamily: mono, fontSize: 13, color: '#dfe5ec', fontWeight: 600 }}>{name}<span style={{ color: '#4b5666' }}>:{port}</span></span><span style={{ fontFamily: mono, fontSize: 12, color: st === '정상' || st === '설정됨' ? green : '#8b95a4', textAlign: 'right', fontWeight: 700 }}>{st}</span></div>)}</div>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 16 }}>{['Grafana', 'Swagger', 'Prometheus', 'Zipkin'].map((x) => <span key={x} style={{ fontFamily: mono, fontSize: 12, fontWeight: 700, color: '#aeb7c4', background: '#151a22', border: '1px solid #232a38', borderRadius: 9, padding: '7px 11px' }}>{x} ↗</span>)}</div>
          </section>
          <section style={{ background: '#0e1116', border: '1px solid #1c2230', borderRadius: 16, padding: 18 }}>
            <span style={{ fontFamily: mono, color: '#5f6b7d', fontSize: 11, fontWeight: 800, letterSpacing: 1 }}>데이터 흐름</span>
            <div style={{ display: 'grid', gap: 9, marginTop: 12 }}>{[['분석', '프로젝트 분석 → 스킬·경험 태그 추출', green], ['색인', '공고 수집 → 정규화 → 검색 인덱스 반영', green], ['매칭', 'JD 매칭 → 갭 분석 → 추천 피드', green], ['알림', '저장 공고 → 배치 발송 → 재시도 관리', green]].map(([tag, txt], k) => <div key={tag} style={{ display: 'flex', alignItems: 'center', gap: 11, padding: '11px 13px', background: '#11151c', border: '1px solid #1c2230', borderRadius: 12 }}><span style={{ fontFamily: mono, fontSize: 10.5, fontWeight: 800, color: green, background: 'rgba(185,236,42,0.1)', border: '1px solid rgba(185,236,42,0.25)', borderRadius: 7, padding: '3px 7px', minWidth: 52, textAlign: 'center' }}>{tag}</span><span style={{ fontFamily: mono, fontSize: 12.5, color: '#cdd4dd', fontWeight: 600, lineHeight: 1.4 }}>{txt}</span><span style={{ marginLeft: 'auto', color: green, fontSize: 13 }}>✓</span></div>)}</div>
          </section>
        </div>
        {/* smoke + quality */}
        <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1.25fr 0.75fr', gap: 14, marginTop: 14 }}>
          <section style={{ background: '#0e1116', border: '1px solid #1c2230', borderRadius: 16, padding: 18 }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}><span style={{ fontFamily: mono, color: '#5f6b7d', fontSize: 11, fontWeight: 800, letterSpacing: 1 }}>기능 연결 상태</span><span style={{ fontFamily: mono, color: apiUnavailableCount ? coral : green, fontSize: 11, fontWeight: 800 }}>{apiStatusEntries.length ? `정상 ${apiOkCount} · 점검 ${apiUnavailableCount}` : '확인 전'}</span></div>
            <div>{smokes.map(([name, st, val, dur]) => <div key={name} style={{ display: 'grid', gridTemplateColumns: '16px 1fr auto 64px', gap: 10, alignItems: 'center', padding: '10px 0', borderTop: '1px solid #161b24' }}><span style={{ color: st === '정상' ? green : coral, fontFamily: mono, fontWeight: 900, fontSize: 13 }}>{st === '정상' ? '✓' : '!'}</span><span style={{ fontFamily: mono, fontSize: 12.5, color: '#dfe5ec' }}>{name}</span><span style={{ fontFamily: mono, fontSize: 11.5, color: '#8b95a4' }}>{st}</span><span style={{ fontFamily: mono, fontSize: 11.5, color: '#5f6b7d', textAlign: 'right' }}>{dur}</span></div>)}</div>
          </section>
          <section style={{ display: 'grid', gap: 12, gridAutoRows: 'min-content' }}>
            <div style={{ background: '#0e1116', border: '1px solid #1c2230', borderRadius: 16, padding: 18 }}><div style={{ fontFamily: mono, color: '#5f6b7d', fontSize: 11, fontWeight: 800, letterSpacing: 1 }}>화면 데이터 상태</div><div style={{ display: 'grid', gap: 7, marginTop: 10 }}>{(apiStatusEntries.length ? apiStatusEntries : [['api', 'unavailable']]).map(([name, status]) => <div key={name} style={{ display: 'grid', gridTemplateColumns: '1fr 58px', gap: 10, alignItems: 'center', fontFamily: mono, fontSize: 11.5 }}><span style={{ color: '#aeb7c4' }}>{name}</span><b style={{ color: status === 'ok' ? green : coral, textAlign: 'right' }}>{status === 'ok' ? '정상' : '점검'}</b></div>)}</div></div>
            <div style={{ background: '#0e1116', border: '1px solid #1c2230', borderRadius: 16, padding: 18 }}><div style={{ fontFamily: mono, color: '#5f6b7d', fontSize: 11, fontWeight: 800, letterSpacing: 1 }}>프로젝트 스킬</div><div style={{ display: 'flex', alignItems: 'baseline', gap: 6, marginTop: 8 }}><b style={{ fontFamily: mono, fontSize: 30, color: green, letterSpacing: -1 }}>{JF.skills?.length || 0}</b><span style={{ marginLeft: 'auto', fontFamily: mono, fontSize: 11, color: apiHealthOk ? green : coral, background: 'rgba(185,236,42,0.1)', border: '1px solid rgba(185,236,42,0.25)', borderRadius: 7, padding: '3px 8px', fontWeight: 800 }}>{apiHealthOk ? '연결됨' : '확인'}</span></div><div style={{ color: '#5f6b7d', fontSize: 11.5, marginTop: 6, fontFamily: mono }}>분석된 기술 스택</div></div>
            <div style={{ background: '#0e1116', border: '1px solid #1c2230', borderRadius: 16, padding: 18 }}><div style={{ fontFamily: mono, color: '#5f6b7d', fontSize: 11, fontWeight: 800, letterSpacing: 1 }}>트렌드 스킬</div><b style={{ display: 'block', fontFamily: mono, fontSize: 30, color: '#eef2f7', letterSpacing: -1, marginTop: 8 }}>{JF.trends?.length || 0}</b><div style={{ color: '#5f6b7d', fontSize: 11.5, marginTop: 6, fontFamily: mono }}>이번 달 집계 결과</div></div>
            <div style={{ background: 'linear-gradient(135deg, #16200a, #0e1116)', border: '1px solid rgba(185,236,42,0.25)', borderRadius: 16, padding: 18 }}><div style={{ display: 'flex', alignItems: 'center', gap: 8 }}><Dot ok /><span style={{ fontFamily: mono, color: green, fontSize: 11, fontWeight: 800, letterSpacing: 1 }}>관측 스택</span></div><b style={{ display: 'block', fontFamily: mono, fontSize: 22, color: '#eef2f7', marginTop: 10 }}>정상</b><div style={{ color: '#8b95a4', fontSize: 11.5, marginTop: 4, fontFamily: mono }}>Prometheus + Grafana 연동</div></div>
          </section>
        </div>
      </div>
    </Shell>;
  };

    const errorScreen = (code) => {
    const info = ({ '401': ['로그인이 필요합니다', '계정 기준 데이터를 보려면 먼저 로그인해주세요.', '로그인으로 이동'], '403': ['접근 권한이 없습니다', '이 프로젝트는 현재 계정에서 볼 수 없습니다.', '프로젝트 목록으로'], '404': ['페이지를 찾을 수 없습니다', '주소가 바뀌었거나 삭제된 화면입니다.', '홈으로 이동'], '500': ['서버 오류가 발생했습니다', '잠시 후 다시 시도하거나 시스템 상태를 확인하세요.', '상태 보기'], oauth: ['로그인이 완료되지 않았습니다', '권한 승인을 취소했거나 로그인 요청이 만료됐습니다.', '다시 로그인'] })[code] || ['문제가 발생했습니다', '요청을 처리하지 못했습니다.', '홈으로 이동'];
    const action = code === '401' || code === 'oauth' ? 'login' : code === '403' ? 'projects' : code === '500' ? 'demo-status' : 'home';
    return <Shell title={code === 'oauth' ? '로그인 실패' : code} sub="문제가 생긴 위치를 알려주고 다음 행동으로 바로 이어지는 에러 화면입니다." actions={<ActionButton onClick={() => go('demo-status')}>상태 보기</ActionButton>}><div style={{ minHeight: 520, display: 'grid', placeItems: 'center' }}><div style={{ width: 620, maxWidth: '100%', textAlign: 'center', background: '#fff', border: '1px solid ' + line, borderRadius: 28, padding: narrow ? 28 : 44, boxShadow: shadow }}><div style={{ margin: '0 auto 20px', width: 72, height: 72, borderRadius: 24, background: code === '500' || code === 'oauth' ? coralTint : greenTint, color: code === '500' || code === 'oauth' ? coralDeep : greenInk, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 28, fontWeight: 1000 }}>{code === '404' ? '?' : code === '401' ? '↗' : code === '403' ? '!' : '×'}</div><div style={{ fontSize: narrow ? 28 : 38, fontWeight: 900, letterSpacing: -1.2 }}>{info[0]}</div><p style={{ color: muted, fontSize: 15, lineHeight: 1.7, margin: '12px auto 22px', maxWidth: 440 }}>{info[1]}</p><div style={{ display: 'flex', gap: 8, justifyContent: 'center', flexWrap: 'wrap' }}><ActionButton primary onClick={() => go(action)}>{info[2]}</ActionButton><ActionButton onClick={() => go('home')}>홈</ActionButton></div></div></div></Shell>;
  };

  const applicationsScreen = () => {
    const stageColor = { APPLIED: { bg: '#f4f5f0', bd: '#e3e6db', fg: '#69705d' }, DOCUMENT_PASSED: { bg: '#f3f9d8', bd: '#e2efb0', fg: '#5c7012' }, CODING_TEST: { bg: '#e8f5b0', bd: '#d3e985', fg: '#46600a' }, INTERVIEW: { bg: '#d6ee70', bd: '#c4e34d', fg: '#2e4400' }, OFFER: { bg: green, bd: greenStrong, fg: ink }, REJECTED: { bg: coralTint, bd: coralTintBd, fg: coralDeep }, WITHDRAWN: { bg: soft, bd: line, fg: muted } };
    const statusLabel = { APPLIED: '지원 완료', DOCUMENT_PASSED: '서류 합격', CODING_TEST: '코딩테스트', INTERVIEW: '면접 진행', OFFER: '오퍼', REJECTED: '불합격', WITHDRAWN: '지원 취소' };
    return <Shell title="지원 현황" sub="외부에서 지원한 공고를 단계별로 추적하고, 상태를 바로 변경합니다." actions={<ActionButton primary onClick={() => setAddingApplication(true)}>지원 기록 추가</ActionButton>}>
      <div style={{ display: 'grid', gridTemplateColumns: narrow ? 'repeat(2,1fr)' : 'repeat(5,1fr)', gap: 10, marginBottom: 22 }}>{JF.pipeline.map((p, i) => { const c = stageColor[p.key]; return <div key={p.key} style={{ background: c.bg, border: '1px solid ' + c.bd, borderRadius: 16, padding: '16px 18px' }}><div style={{ fontSize: 10.5, fontWeight: 800, color: c.fg, letterSpacing: 0.5, marginBottom: 4 }}>단계 {i + 1}</div><b style={{ fontSize: 28, color: p.key === 'OFFER' ? ink : ink, ...num }}>{p.count}</b><div style={{ color: c.fg, fontWeight: 800, fontSize: 13, marginTop: 2 }}>{p.label}</div></div>; })}</div>
      {JF.__apiStatus?.applications === 'unavailable' && <div style={{ marginBottom: 18 }}><StatePanel type="error" title="지원 현황을 불러오지 못했습니다" desc="서비스 연결 상태를 확인한 뒤 다시 시도해주세요." action="다시 시도" /></div>}
      {JF.__apiStatus?.applications === 'ok' && !JF.applications.length && <div style={{ marginBottom: 18 }}><StatePanel type="empty" title="지원 내역 없음" desc="외부에서 지원한 공고를 기록하면 단계별 현황이 채워집니다." action="첫 지원 기록 추가" /></div>}
      <div style={{ ...tile, padding: 0, overflow: 'hidden' }}>
        <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr 116px' : '1fr 120px 150px 128px', gap: 14, padding: '13px 22px', borderBottom: '1px solid ' + line, fontSize: 11.5, fontWeight: 800, color: faint, letterSpacing: 0.5 }}><span>회사 · 공고</span>{!narrow && <><span>지원일</span><span>다음 액션</span></>}<span style={{ textAlign: 'right' }}>현재 단계</span></div>
        {JF.applications.map((a, i) => { const c = stageColor[a.status] || stageColor.APPLIED; return <div key={a.id || a.company} style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr 116px' : '1fr 120px 150px 128px', gap: 14, padding: '15px 22px', borderTop: i ? '1px solid ' + line : 'none', alignItems: 'center' }}><div onClick={() => go('detail', { jobId: a.jobId, company: a.company })} style={{ display: 'flex', alignItems: 'center', gap: 11, minWidth: 0, cursor: 'pointer' }}><div style={{ width: 36, height: 36, borderRadius: 11, background: ink, color: green, display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 900, fontSize: 13, flexShrink: 0 }}>{a.company.slice(0, 2)}</div><div style={{ minWidth: 0 }}><b style={{ fontSize: 14.5 }}>{a.company}</b><div style={{ color: muted, fontSize: 12.5, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{a.title}</div></div></div>{!narrow && <><span style={{ color: muted, fontSize: 13, ...num }}>{a.appliedAt ? String(a.appliedAt).slice(0, 10).replaceAll('-', '.') : '-'}</span><select defaultValue={a.status} onChange={async (e) => { try { await jobflowActions.updateApplicationStatus?.(a.id, e.target.value); notify('지원 상태를 변경했어요.'); } catch (error) { notify(error.message || '지원 상태 변경에 실패했어요.'); } }} style={{ font: 'inherit', border: '1px solid ' + line, background: '#fff', borderRadius: 12, padding: '8px 10px', fontSize: 12.5, fontWeight: 800 }}><option value="APPLIED">지원 완료</option><option value="DOCUMENT_PASSED">서류 합격</option><option value="CODING_TEST">코딩테스트</option><option value="INTERVIEW">면접 진행</option><option value="OFFER">오퍼</option><option value="REJECTED">불합격</option><option value="WITHDRAWN">지원 취소</option></select></>}<span style={{ justifySelf: 'end', fontSize: 12, fontWeight: 800, color: c.fg, background: c.bg, border: '1px solid ' + c.bd, borderRadius: 14, padding: '5px 11px', whiteSpace: 'nowrap' }}>{statusLabel[a.status] || a.status}</span></div>; })}
      </div>
      {addingApplication && <Modal title="지원 기록 추가" sub="공고 상세 화면에서 지원 기록을 만들면 회사명과 공고명이 자동으로 연결됩니다." onClose={() => setAddingApplication(false)}><Field label="회사명" placeholder="공고 상세에서 자동 입력됩니다" /><Field label="공고명" placeholder="공고 상세에서 자동 입력됩니다" /><div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1fr 1fr', gap: 10 }}><Field label="지원일" placeholder="저장 시점 기준" /><label><div style={{ fontSize: 12, color: muted, fontWeight: 850, marginBottom: 6 }}>현재 단계</div><select defaultValue="APPLIED" style={{ width: '100%', font: 'inherit', border: '1px solid ' + line, borderRadius: 13, padding: '12px 13px', background: '#fff' }}><option value="APPLIED">지원 완료</option><option value="DOCUMENT_PASSED">서류 합격</option><option value="CODING_TEST">코딩테스트</option><option value="INTERVIEW">면접 진행</option><option value="OFFER">오퍼</option></select></label></div><Field label="메모" placeholder="메모를 입력하세요" textarea /><div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 12 }}><ActionButton onClick={() => setAddingApplication(false)}>취소</ActionButton><ActionButton primary onClick={() => { notify('공고 상세 화면에서 지원 기록을 추가해주세요.'); setAddingApplication(false); go('jobs'); }}>공고 목록으로 이동</ActionButton></div></Modal>}
    </Shell>;
  };

  const map = { loading: loadingScreen, login: loginScreen, oauth: oauthScreen, jobs: jobsScreen, userJobs: userJobsScreen, applications: applicationsScreen, projects: projectsScreen, 'project-new': projectNewScreen, 'project-analysis': projectAnalysisScreen, gap: gapScreen, recommendations: recommendationsScreen, trends: trendsScreen, mypage: mypageScreen, 'demo-status': demoStatusScreen, 'error-401': () => errorScreen('401'), 'error-403': () => errorScreen('403'), 'error-404': () => errorScreen('404'), 'error-500': () => errorScreen('500'), 'oauth-failure': () => errorScreen('oauth') };
  return (map[screen] || jobsScreen)();
}
