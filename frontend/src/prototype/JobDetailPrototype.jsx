import React from 'react';

// JobFlow — 공고 상세. JD + matching report.
// Palette: lime = owned/match, charcoal = analysis, coral = urgent/missing.
export function JobDetail({ t, go, company, jobId, loading = false }) {
  const JF = window.JF || {};
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
  const sectionLabels = {
    work: ['주요업무', '주요 업무', '담당업무', '담당 업무'],
    required: ['자격요건', '자격 요건', '지원자격', '지원 자격', '필수요건', '필수 요건'],
    preferred: ['우대사항', '우대 사항'],
    process: ['채용절차', '채용 절차', '전형절차', '전형 절차'],
    meta: ['포지션 경력/학력/마감일/근무지역 정보', '기업/서비스 소개'],
  };
  const originalSectionAliases = [
    ['포지션 상세 정보', ['포지션 상세 정보', '포지션 정보', '포지션 경력/학력/마감일/근무지역 정보']],
    ['기술스택', ['기술스택', '기술 스택', '사용 기술', '스킬', 'Tech Stack']],
    ['주요 업무', ['주요 업무', '주요업무', '담당 업무', '담당업무', 'Main tasks']],
    ['자격 요건', ['자격 요건', '자격요건', '지원 자격', '지원자격', '필수 요건', '필수요건', 'Requirements']],
    ['우대 사항', ['우대 사항', '우대사항', 'Preferred points', 'Preferred qualifications']],
    ['복지 및 혜택', ['복지 및 혜택', '혜택 및 복지', '복리후생', 'Benefits']],
    ['채용절차 및 기타 지원 유의사항', ['채용절차 및 기타 지원 유의사항', '채용 절차 및 기타 지원 유의사항', '채용절차', '채용 절차', '전형절차', '전형 절차', 'Process']],
    ['기업/서비스 소개', ['기업/서비스 소개', '기업 소개', '회사 소개', '서비스 소개', 'Company introduction']],
    ['팀 소개', ['팀 소개', 'Team introduction']],
  ];
  const aliasToCanonical = new Map(originalSectionAliases.flatMap(([canonical, aliases]) => aliases.map((alias) => [alias.toLowerCase(), canonical])));
  const canonicalHeadings = originalSectionAliases.map(([canonical]) => canonical);
  const escapedSectionAliases = originalSectionAliases
    .flatMap(([, aliases]) => aliases)
    .sort((a, b) => b.length - a.length)
    .map((label) => label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
  const sectionHeadingPattern = new RegExp(`(?:\\[\\s*(${escapedSectionAliases.join('|')})\\s*\\]|(${escapedSectionAliases.join('|')}))`, 'giu');
  const canonicalizeHeading = (value) => aliasToCanonical.get(String(value || '').replace(/^\[|\]$/g, '').trim().toLowerCase()) || String(value || '').trim();
  const normalizeDescription = (value) => String(value || '')
    .replace(/\\n/g, '\n')
    .replace(/\r\n?/g, '\n')
    .replace(/[ \t]+/g, ' ')
    .replace(/[ \t]*\n[ \t]*/g, '\n')
    .replace(sectionHeadingPattern, (match, bracketHeading, plainHeading, offset, source) => {
      const previous = source.slice(Math.max(0, offset - 1), offset);
      const canonical = canonicalizeHeading(bracketHeading || plainHeading || match);
      const prefix = previous === '\n' || offset === 0 ? '' : '\n\n';
      return `${prefix}${canonical}\n`;
    })
    .replace(/\s+(?=(?:[-•]\s+|\d+\.\s+))/g, '\n')
    .replace(/(^|\n)\s*[ㆍ·]\s*/g, '\n• ')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
  const formatOriginalDescription = (value) => {
    const normalized = normalizeDescription(value);
    const firstUsefulSection = [
      '포지션 상세 정보',
      '주요업무',
      '주요 업무',
      '담당업무',
      '담당 업무',
      '자격요건',
      '자격 요건',
    ]
      .map((label) => normalized.indexOf(label))
      .filter((index) => index >= 0)
      .sort((a, b) => a - b)[0];
    const body = firstUsefulSection > 0 ? normalized.slice(firstUsefulSection) : normalized;
    return body
      .replace(/\n(기술스택)\n([^\n]+)/g, '\n$1\n$2')
      .replace(/\n(\d+\.\s+)/g, '\n$1')
      .replace(/\n{3,}/g, '\n\n')
      .trim();
  };
  const parseOriginalDescriptionSections = (value) => {
    const body = formatOriginalDescription(value);
    if (!body) return [];
    const sections = [];
    let current = null;

    body.split('\n').forEach((rawLine) => {
      const lineText = rawLine.trim();
      if (!lineText) {
        if (current && current.lines.length && current.lines[current.lines.length - 1] !== '') {
          current.lines.push('');
        }
        return;
      }
      const canonicalHeading = canonicalHeadings.includes(lineText) ? lineText : canonicalizeHeading(lineText);
      if (canonicalHeadings.includes(canonicalHeading) && lineText.length <= 40) {
        current = { title: canonicalHeading, lines: [] };
        sections.push(current);
        return;
      }
      if (!current) {
        current = { title: '공고 원문', lines: [] };
        sections.push(current);
      }
      current.lines.push(lineText);
    });

    return sections
      .map((section) => ({
        title: section.title,
        body: section.lines.join('\n').replace(/\n{3,}/g, '\n\n').trim(),
      }))
      .filter((section) => section.title || section.body)
      .filter((section) => section.title !== '공고 원문' || section.body.length > 20);
  };
  const splitItems = (value) => normalizeDescription(value)
    .split(/\n+|(?=\d+\.\s+)/)
    .map((line) => line.replace(/^[\-•]\s*/, '').trim())
    .filter((line) => line && !Object.values(sectionLabels).flat().includes(line));
  const findSectionBounds = (text, labels) => {
    const starts = labels
      .map((label) => ({ label, index: text.indexOf(`\n${label}\n`) }))
      .filter((item) => item.index >= 0)
      .sort((a, b) => a.index - b.index);
    if (!starts.length) return null;
    const start = starts[0].index + starts[0].label.length + 2;
    const allLabelIndexes = Object.values(sectionLabels).flat()
      .map((label) => text.indexOf(`\n${label}\n`, start))
      .filter((index) => index >= 0)
      .sort((a, b) => a - b);
    return { start, end: allLabelIndexes[0] || text.length };
  };
  const parseDescriptionSections = (description) => {
    const normalized = `\n${normalizeDescription(description)}\n`;
    const read = (key) => {
      const bounds = findSectionBounds(normalized, sectionLabels[key]);
      return bounds ? splitItems(normalized.slice(bounds.start, bounds.end)) : [];
    };
    const firstSection = Object.values(sectionLabels).flat()
      .map((label) => normalized.indexOf(`\n${label}\n`))
      .filter((index) => index >= 0)
      .sort((a, b) => a - b)[0];
    const overviewText = firstSection ? normalized.slice(0, firstSection) : normalized;
    return {
      overview: normalizeDescription(overviewText).split('\n').filter(Boolean).slice(0, 8),
      work: read('work'),
      required: read('required'),
      preferred: read('preferred'),
      process: read('process'),
    };
  };
  const cleanProcessItems = (items) => items
    .flatMap((item) => item
      .replace(/^및 기타 지원 유의사항\s*/u, '')
      .split(/\s*(?:→|-|•)\s*/u))
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(0, 8);

  const matches = JF.matches || [];
  const listings = JF.listings || [];
  const popular = JF.popular || [];
  const closing = JF.closing || [];
  const userJobs = JF.userJobs || {};
  const userSkillRows = JF.skills || [];
  const tagLabels = JF.tagLabel || {};
  const allJobs = [
    ...matches,
    ...listings,
    ...popular,
    ...closing,
    ...((userJobs.saved) || []),
    ...((userJobs.viewed) || []),
    ...((userJobs.ignored) || []),
  ];
  const targetById = jobId ? allJobs.find((x) => String(x.jobId || x.id) === String(jobId)) : null;
  const targetCompany = company || targetById?.companyKo;
  const primaryProject = JF.projectList?.[0] || {};
  const currentProjectName = primaryProject.name || '내 프로젝트';
  const currentProjectSkillSummary = (primaryProject.previewSkills || userSkillRows.map((skill) => skill.name) || []).slice(0, 4).join(', ') || '프로젝트 분석 스킬';
  const hasProjectContext = Boolean(JF.__userProjectId && (JF.projectList?.length || userSkillRows.length));
  const matchReportLabel = hasProjectContext ? `${currentProjectName} 기준 매칭률` : '프로젝트 연결 전 매칭률';
  const matchReason = hasProjectContext
    ? `${currentProjectName}에서 ${currentProjectSkillSummary} 근거를 확인했고, 부족 스킬은 갭 분석에서 우선순위로 볼 수 있어요.`
    : '프로젝트 분석 결과를 연결하면 보유 스킬과 부족 스킬 기준으로 매칭률을 다시 계산합니다.';
  const userSkills = new Set(userSkillRows.map((s) => s.name));
  const m = targetById || matches.find((x) => x.companyKo === targetCompany);
  const l = !m && (listings.find((x) => x.companyKo === targetCompany) || popular.find((x) => x.companyKo === targetCompany) || closing.find((x) => x.companyKo === targetCompany));
  const trackingJobId = (targetById && (targetById.jobId || targetById.id)) || (m && (m.jobId || m.id)) || (l && (l.jobId || l.id)) || jobId;
  const trackingCompany = targetCompany || (m && m.companyKo) || (l && l.companyKo);
  React.useEffect(() => {
    if (trackingJobId) window.__jobflowApi?.viewJobById?.(trackingJobId);
    else if (trackingCompany) window.__jobflowApi?.viewJobByCompany?.(trackingCompany);
  }, [trackingCompany, trackingJobId]);

  if (!m && !l && loading) {
    return (
      <div style={{ minHeight: '100vh', background: '#ffffff', fontFamily: font, color: ink }}>
        <div style={{ height: 64, borderBottom: '1px solid ' + line, background: 'rgba(255,255,255,0.82)', backdropFilter: 'blur(8px)', display: 'flex', alignItems: 'center', padding: narrow ? '0 18px' : '0 40px', gap: narrow ? 10 : 18, position: 'sticky', top: 0, zIndex: 20, overflow: 'hidden' }}>
          <div style={{ fontSize: narrow ? 20 : 22, fontWeight: 700, letterSpacing: -1, cursor: 'pointer', flexShrink: 0 }} onClick={() => go('home')}>jobflow<span style={{ color: green, WebkitTextStroke: '0.5px ' + greenInk }}>.</span></div>
          <span style={{ marginLeft: 'auto', fontSize: 13.5, fontWeight: 700, color: muted, cursor: 'pointer', whiteSpace: 'nowrap' }} onClick={() => go('jobs')}>← 공고 목록</span>
        </div>
        <div style={{ maxWidth: 760, margin: '0 auto', padding: narrow ? '70px 18px' : '96px 40px', textAlign: 'center' }}>
          <div style={{ margin: '0 auto 18px', width: 64, height: 64, borderRadius: 22, background: soft, color: muted, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 28, fontWeight: 900 }}>…</div>
          <h1 style={{ fontSize: narrow ? 26 : 34, letterSpacing: -1, margin: 0 }}>공고 데이터를 불러오는 중입니다</h1>
          <p style={{ color: muted, fontSize: 15, lineHeight: 1.7, margin: '12px auto 22px', maxWidth: 440 }}>공고 목록을 불러온 뒤 상세 내용을 표시합니다.</p>
        </div>
      </div>
    );
  }
  if (!m && !l) {
    return (
      <div style={{ minHeight: '100vh', background: '#ffffff', fontFamily: font, color: ink }}>
        <div style={{ height: 64, borderBottom: '1px solid ' + line, background: 'rgba(255,255,255,0.82)', backdropFilter: 'blur(8px)', display: 'flex', alignItems: 'center', padding: narrow ? '0 18px' : '0 40px', gap: narrow ? 10 : 18, position: 'sticky', top: 0, zIndex: 20, overflow: 'hidden' }}>
          <div style={{ fontSize: narrow ? 20 : 22, fontWeight: 700, letterSpacing: -1, cursor: 'pointer', flexShrink: 0 }} onClick={() => go('home')}>jobflow<span style={{ color: green, WebkitTextStroke: '0.5px ' + greenInk }}>.</span></div>
          <span style={{ marginLeft: 'auto', fontSize: 13.5, fontWeight: 700, color: muted, cursor: 'pointer', whiteSpace: 'nowrap' }} onClick={() => go('jobs')}>← 공고 목록</span>
        </div>
        <div style={{ maxWidth: 760, margin: '0 auto', padding: narrow ? '70px 18px' : '96px 40px', textAlign: 'center' }}>
          <div style={{ margin: '0 auto 18px', width: 64, height: 64, borderRadius: 22, background: soft, color: muted, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 28, fontWeight: 900 }}>?</div>
          <h1 style={{ fontSize: narrow ? 26 : 34, letterSpacing: -1, margin: 0 }}>공고 데이터를 찾을 수 없습니다</h1>
          <p style={{ color: muted, fontSize: 15, lineHeight: 1.7, margin: '12px auto 22px', maxWidth: 440 }}>목록에서 공고를 선택하면 상세 내용과 매칭 근거를 확인할 수 있어요.</p>
          <button onClick={() => go('jobs')} style={{ font: 'inherit', cursor: 'pointer', border: 'none', background: ink, color: '#fff', borderRadius: 22, padding: '12px 18px', fontWeight: 900 }}>공고 목록으로 이동</button>
        </div>
      </div>
    );
  }
  const reqList = (m && m.requiredSkills) || (l && (l.requiredSkills || l.skills)) || [];
  const prefList = (m && m.preferredSkills) || (l && l.preferredSkills) || [];
  const tags = (m && m.tags) || (l && l.tags) || [];
  const job = {
    companyKo: targetCompany || (m && m.companyKo) || (l && l.companyKo) || '회사명 없음',
    id: (m && (m.id || m.jobId)) || (l && (l.id || l.jobId)) || jobId,
    jobId: (m && (m.jobId || m.id)) || (l && (l.jobId || l.id)) || jobId,
    company: (m && m.company) || (l && l.company) || '',
    logo: (m && m.logo) || (l && l.logo) || ((targetCompany || '').slice(0, 2) || 'JD'),
    fullTitle: (m && m.fullTitle) || (l && l.fullTitle) || (m && m.title) || (l && l.title) || '제목 없음',
    title: (m && m.title) || (l && l.title) || '제목 없음',
    role: (m && m.role) || (l && l.role) || '직무 정보 없음',
    level: (m && m.level) || (l && l.level) || '경력 정보 없음',
    location: (m && m.location) || (l && l.location) || '지역 정보 없음',
    deadline: (m && m.deadline) || (l && l.deadline) || '마감 정보 없음',
    views: (m && m.views) || (l && l.views) || 0,
    applicants: (m && m.applicants) || (l && l.applicants) || 0,
    companyIntro: (m && m.companyIntro) || '',
    originalUrl: (m && m.originalUrl) || (l && l.originalUrl) || '',
    desc: (m && m.desc) || (l && l.desc) || '공고 상세 설명이 제공되지 않았습니다.',
    descriptionSections: (m && m.descriptionSections) || (l && l.descriptionSections) || [],
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
  const Pill = ({ children, miss, owned, dark }) => <span style={{ fontSize: 12, fontWeight: 760, padding: '5px 9px', borderRadius: 999, background: dark ? 'rgba(255,255,255,0.08)' : miss ? soft : owned ? greenTint : soft, color: dark ? 'rgba(255,255,255,0.82)' : miss ? muted : owned ? greenInk : muted, border: '1px solid ' + (dark ? 'rgba(255,255,255,0.12)' : miss ? line : owned ? greenTintBd : line), whiteSpace: 'nowrap', maxWidth: '100%', overflow: 'hidden', textOverflow: 'ellipsis' }}>{owned ? '✓ ' : miss ? '+ ' : ''}{children}</span>;
  const TagPill = ({ children, muted: dull }) => { const tone = tagTone; const P = ({ '보라': { bg: '#efeaff', fg: '#5b3fd6', bd: '#dcd2fb' }, '라임': { bg: '#eef8cf', fg: '#3f5c08', bd: '#dbeca8' }, '검정': { bg: '#14151a', fg: '#ffffff', bd: '#14151a' } })[tone] || { bg: '#efeaff', fg: '#5b3fd6', bd: '#dcd2fb' }; return <span style={{ fontSize: 12, fontWeight: 720, padding: '5px 9px', borderRadius: 999, background: P.bg, color: P.fg, border: '1px solid ' + P.bd, whiteSpace: 'nowrap', opacity: dull ? 0.82 : 1 }}>#{children}</span>; };
  const Stat = ({ label, value, tone }) => <div style={{ background: tone === 'green' ? greenTint : tone === 'coral' ? coralTint : soft, border: '1px solid ' + (tone === 'green' ? greenTintBd : tone === 'coral' ? coralTintBd : line), borderRadius: 14, padding: '13px 14px' }}><b style={{ fontSize: 23, color: tone === 'coral' ? coralDeep : ink, ...num }}>{value}</b><div style={{ color: tone === 'green' ? greenInk : muted, fontSize: 11.5, fontWeight: 800, marginTop: 2 }}>{label}</div></div>;
  const Bar = ({ label, value }) => <div style={{ marginBottom: 12 }}><div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12.5, fontWeight: 700, color: 'rgba(255,255,255,0.65)', marginBottom: 5 }}><span>{label}</span><span style={{ color: '#fff', ...num }}>{value}%</span></div><div style={{ height: 9, background: 'rgba(255,255,255,0.12)', borderRadius: 5, overflow: 'hidden' }}><div style={{ width: value + '%', height: '100%', background: green, borderRadius: 5 }} /></div></div>;
  const btn = (label, active, onClick, primary, icon) => <button className="jf-cta" onClick={onClick} disabled={active} style={{ font: 'inherit', flex: narrow ? '1 1 calc(50% - 6px)' : 1, minWidth: narrow ? 'calc(50% - 6px)' : 0, cursor: active ? 'default' : 'pointer', borderRadius: 12, padding: '13px 12px', fontSize: 14.5, fontWeight: 760, lineHeight: 1.15, border: primary ? 'none' : '1px solid ' + line, background: active ? soft : primary ? green : card, color: active ? muted : ink, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 7, whiteSpace: 'nowrap' }}>{icon}{label}</button>;
  const openOriginalJob = () => {
    if (!job.originalUrl) {
      fire('원본 공고 URL이 없습니다.');
      return;
    }
    window.open(job.originalUrl, '_blank', 'noopener,noreferrer');
  };
  const cleanItemText = (value) => String(value || '')
    .replace(/^\d+\.\s*/u, '')
    .replace(/\s+/g, ' ')
    .trim();
  const stripListMarker = (value) => String(value || '').replace(/^[-•]\s*/u, '').replace(/^\d+\.\s*/u, '').trim();
  const shortJdFragments = new Set([
    '추천',
    '추천 추론',
    '추론',
    '검색',
    '데이터베이스 설계',
    '인프라 및 성능 최적화',
    '서버 개발',
    '운영 실험 자동화',
  ]);
  const shouldJoinWithNextLine = (current, next) => {
    if (!next) return false;
    if (shortJdFragments.has(current)) return true;
    return false;
  };
  const shouldAppendToPreviousLine = (previous, current) => {
    if (!previous) return false;
    if (/^(운영|구현|구현·운영|서빙|검색|튜닝|서버|클라이언트|개발|문서화|최적화|관리|처리|배포|모니터링|알림|캐싱)(?:\s+|·|$)/u.test(current) && /(설계|구현|추천|추론|클라이언트|서버|운영|개발|최적화|데이터베이스 설계)$/u.test(previous)) return true;
    if (/^(검색 시스템|서빙 API|튜닝 MySQL|운영 Docker|운영 Prometheus|운영 Redis|API 개발|스키마 문서화|파이프라인 개발자 포지션)/u.test(current) && /(추천|추론|데이터베이스 설계|설계|설계·구현|OpenAPI|Swagger|GraphQL)$/u.test(previous)) return true;
    return false;
  };
  const joinJdFragments = (left, right) => {
    const previous = stripListMarker(left);
    const current = stripListMarker(right);
    const separator = /[A-Za-z0-9)]$/u.test(previous) || /^[A-Za-z0-9]/u.test(current) ? ' ' : '·';
    return `${previous}${separator}${current}`;
  };
  const compactOriginalLines = (rawLines) => {
    const cleaned = rawLines.map(stripListMarker).filter(Boolean);
    const result = [];
    for (let index = 0; index < cleaned.length; index += 1) {
      let current = cleaned[index];
      const next = cleaned[index + 1];
      while (cleaned[index + 1] && shouldJoinWithNextLine(current, cleaned[index + 1])) {
        current = joinJdFragments(current, cleaned[index + 1]);
        index += 1;
      }
      if (result.length && shouldAppendToPreviousLine(result[result.length - 1], current)) {
        result[result.length - 1] = joinJdFragments(result[result.length - 1], current);
        continue;
      }
      result.push(current);
    }
    return result;
  };
  const TextSection = ({ title, children }) => <section style={{ paddingTop: 24, marginTop: 24, borderTop: '1px solid ' + line }}>
    <h2 style={{ margin: 0, color: muted, fontSize: 16, fontWeight: 900, letterSpacing: -0.2 }}>{title}</h2>
    <div style={{ marginTop: 11 }}>{children}</div>
  </section>;
  const OriginalSection = ({ title, body, index }) => {
    const normalized = body
      ? body
        .replace(/([^\n])\s*(•)/g, '$1\n$2')
        .replace(/\n{3,}/g, '\n\n')
        .trim()
      : '';
    const rawLines = normalized.split('\n').map(l => l.trim()).filter(Boolean);
    const listLikeTitle = /주요|업무|자격|요건|우대|복지|혜택|채용|절차|팀 소개|기업\/서비스 소개/.test(title);
    const isSkillSection = /기술스택|기술 스택|스킬/.test(title);
    const lines = isSkillSection ? rawLines.map(stripListMarker) : compactOriginalLines(rawLines);
    const isBulletSection = listLikeTitle && lines.length >= 2;
    const skillItems = isSkillSection ? [...new Set(normalized.split(/\s+/).map((x) => x.trim()).filter(Boolean))].slice(0, 24) : [];
    const showTitle = !(index === 0 && title === '공고 원문');
    return (
      <section style={{ paddingTop: index ? 30 : 0, marginTop: index ? 30 : 0, borderTop: index ? '1px solid ' + line : 'none', minWidth: 0 }}>
        {showTitle && <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: body ? 12 : 0 }}>
          <h3 style={{ margin: 0, color: '#3d4451', fontSize: narrow ? 18 : 22, lineHeight: 1.22, fontWeight: 950, letterSpacing: -0.35 }}>{title}</h3>
        </div>}
        {body && (isSkillSection
          ? <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>{skillItems.map((skill) => <Pill key={skill} owned={has(skill)} miss={!has(skill)}>{skill}</Pill>)}</div>
          : isBulletSection
          ? <ul style={{ margin: 0, paddingLeft: 20, color: '#30343c', fontSize: narrow ? 15 : 16, lineHeight: 1.82, letterSpacing: -0.12, wordBreak: 'keep-all', overflowWrap: 'anywhere' }}>
              {lines.map((line, i) => <li key={line + i} style={{ margin: '5px 0', paddingLeft: 2, maxWidth: '100%' }}>{line}</li>)}
            </ul>
          : <p style={{ margin: 0, color: '#30343c', fontSize: narrow ? 15 : 16, lineHeight: 1.78, letterSpacing: -0.12, whiteSpace: 'pre-wrap', wordBreak: 'keep-all', overflowWrap: 'anywhere' }}>{lines.join('\n')}</p>
        )}
      </section>
    );
  };
  const SimilarJobsCard = ({ showScore }) => {
    const similarJobs = matches.filter((x) => x.companyKo !== job.companyKo).slice(0, 5);
    return <div style={{ background: card, border: '1px solid ' + line, borderRadius: 18, padding: 22, boxShadow: shadow }}>
      <div style={{ fontSize: 16, fontWeight: 800, marginBottom: 10 }}>비슷한 추천 공고</div>
      {similarJobs.length ? similarJobs.map((r) => <div key={(r.jobId || r.id || '') + r.companyKo} onClick={() => go('detail', { jobId: r.jobId || r.id, company: r.companyKo })} style={{ display: 'flex', alignItems: 'center', gap: 11, padding: '11px 0', borderTop: '1px solid ' + line, cursor: 'pointer' }}><div style={{ width: 36, height: 36, borderRadius: 11, background: ink, color: green, display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 900, fontSize: 13, flexShrink: 0 }}>{r.logo}</div><div style={{ flex: 1, minWidth: 0 }}><span style={{ fontSize: 14, fontWeight: 650 }}>{r.companyKo}</span><div style={{ color: muted, fontSize: 12, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{r.title} · {r.level}</div></div>{showScore && <b style={{ color: greenInk, ...num }}>{r.score}%</b>}</div>) : <div style={{ color: muted, fontSize: 13, lineHeight: 1.55, borderTop: '1px solid ' + line, paddingTop: 12 }}>현재 프로젝트 기준으로 표시할 추가 추천 공고가 없습니다.</div>}
    </div>;
  };
  const parsedDescription = parseDescriptionSections(job.desc);
  const bullets = {
    required: reqList.length ? reqList : parsedDescription.required,
    preferred: prefList.length ? prefList : parsedDescription.preferred,
    process: cleanProcessItems(parsedDescription.process),
  };
  const originalSections = job.descriptionSections.length
    ? job.descriptionSections
      .map((section) => ({
        title: section.title || '공고 원문',
        body: section.body || '',
      }))
      .filter((section) => section.body)
    : parseOriginalDescriptionSections(job.desc);
  const mainOriginalSections = originalSections.filter((section) => !/기술스택|기술 스택|스킬/.test(section.title));
  const processItems = bullets.process;

  return (
    <div style={{ minHeight: '100vh', background: '#f7f8fa', fontFamily: font, color: ink }}>
      <div style={{ height: 64, borderBottom: '1px solid ' + line, background: 'rgba(255,255,255,0.88)', backdropFilter: 'blur(14px)', display: 'flex', alignItems: 'center', padding: narrow ? '0 18px' : '0 40px', gap: narrow ? 10 : 18, position: 'sticky', top: 0, zIndex: 20, overflow: 'hidden' }}>
        <div style={{ fontSize: narrow ? 20 : 22, fontWeight: 700, letterSpacing: -1, cursor: 'pointer', flexShrink: 0 }} onClick={() => go('home')}>jobflow<span style={{ color: green, WebkitTextStroke: '0.5px ' + greenInk }}>.</span></div>
        <div style={{ display: 'flex', gap: 4, whiteSpace: 'nowrap', flex: 1, minWidth: 0, overflowX: 'auto', scrollbarWidth: 'none' }}>{[['홈', 'home'], ['공고', 'jobs'], ['프로젝트', 'projects'], ['갭 분석', 'gap'], ['트렌드', 'trends']].map(([n, route]) => <button key={n} onClick={() => go(route)} style={{ font: 'inherit', cursor: 'pointer', border: 'none', borderRadius: 20, padding: narrow ? '7px 10px' : '7px 13px', fontSize: narrow ? 12.5 : 13.5, fontWeight: route === 'jobs' ? 800 : 500, background: route === 'jobs' ? green : 'transparent', color: route === 'jobs' ? ink : muted, whiteSpace: 'nowrap', flexShrink: 0 }}>{n}</button>)}</div>
        <span className="jf-link" style={{ marginLeft: 'auto', display: narrow ? 'none' : 'inline', fontSize: 13.5, fontWeight: 700, color: muted, cursor: 'pointer', whiteSpace: 'nowrap' }} onClick={() => go('jobs')}>← 공고 목록</span>
      </div>

      <div style={{ maxWidth: 1520, margin: '0 auto', padding: narrow ? '24px 14px 64px' : '36px 48px 64px' }}>
        <section style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : 'minmax(0, 1fr) 380px', gap: 24, alignItems: 'start' }}>
          <div>
            <div style={{ background: card, border: '1px solid ' + line, borderRadius: 26, padding: narrow ? 20 : 28, boxShadow: '0 18px 44px rgba(20,21,26,0.08)', display: 'grid', gap: 20 }}>
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: 16 }}>
              <Logo />
              <div style={{ minWidth: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 9, flexWrap: 'wrap' }}><span style={{ fontSize: 18, fontWeight: 650 }}>{job.companyKo}</span><span style={{ color: muted, fontSize: 13.5 }}>{job.companyIntro}</span></div>
                <h1 style={{ fontSize: narrow ? 27 : (v3 ? 34 : 40), lineHeight: 1.1, margin: '10px 0 8px', letterSpacing: -1.2, fontWeight: 820, maxWidth: '100%', wordBreak: 'keep-all', overflowWrap: 'anywhere' }}>{job.fullTitle}</h1>
                <div style={{ color: muted, fontSize: 14.5 }}>{job.role} · {job.level} · {job.location}</div>
              </div>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
              <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, background: coralTint, border: '1px solid ' + coralTintBd, borderRadius: 12, padding: '9px 14px', whiteSpace: 'nowrap' }}><span style={{ fontSize: 11.5, fontWeight: 800, color: coralDeep }}>마감</span><b style={{ fontSize: 16, color: coralDeep, ...num }}>{job.deadline}</b></div>
              <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, background: soft, border: '1px solid ' + line, borderRadius: 12, padding: '9px 14px', whiteSpace: 'nowrap' }}><span style={{ fontSize: 11.5, fontWeight: 800, color: muted }}>경력</span><b style={{ fontSize: 16 }}>{job.level}</b></div>
              <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, background: soft, border: '1px solid ' + line, borderRadius: 12, padding: '9px 14px', whiteSpace: 'nowrap' }}><span style={{ fontSize: 11.5, fontWeight: 800, color: muted }}>근무지</span><b style={{ fontSize: 16 }}>{job.location}</b></div>
              <span style={{ color: faint, fontSize: 12.5, fontWeight: 650, ...num, display: 'inline-flex', alignItems: 'center', gap: 5 }}><EyeIcon />{job.views}</span>
            </div>
            <div style={{ display: 'flex', gap: 10, flexWrap: narrow ? 'wrap' : 'nowrap' }}>
              {btn('지원하기', false, openOriginalJob, true)}
              {login && btn(saved ? '저장됨' : '저장', saved, async () => { try { if (job.jobId) await window.__jobflowApi?.saveJobById?.(job.jobId); else await window.__jobflowApi?.saveJobByCompany?.(job.companyKo); setSaved(true); fire('저장한 공고에 추가했어요'); } catch (e) { fire(e.message || '저장에 실패했어요'); } }, false, <BookmarkIcon filled={saved} />)}
              {login && btn(hidden ? '숨김 처리됨' : '숨김', hidden, async () => { try { if (job.jobId) await window.__jobflowApi?.ignoreJobById?.(job.jobId); setHidden(true); fire('추천에서 숨겼어요'); } catch (e) { fire(e.message || '숨김 처리에 실패했어요'); } }, false)}
              {login && btn(applied ? '지원 기록 있음 ✓' : '지원 기록 추가', applied, async () => { try { if (job.jobId) await window.__jobflowApi?.createApplicationByJobId?.(job.jobId); else await window.__jobflowApi?.createApplicationByCompany?.(job.companyKo); setApplied(true); fire('지원 기록을 만들었어요'); } catch (e) { fire(e.message || '지원 기록 생성에 실패했어요'); } }, false)}
            </div>
            </div>

            <article style={{ background: card, border: '1px solid ' + line, borderRadius: 24, padding: narrow ? '24px 20px' : '36px 42px', boxShadow: shadow, marginTop: 18, overflow: 'hidden' }}>
              <div>
                <h2 style={{ margin: 0, color: '#20242c', fontSize: narrow ? 22 : 26, fontWeight: 950, letterSpacing: -0.55 }}>공고 원문</h2>
                <div style={{ marginTop: 18 }}>
                  {mainOriginalSections.length
                    ? mainOriginalSections.map((section, index) => <OriginalSection key={section.title + index} title={section.title} body={section.body} index={index} />)
                    : <p style={{ margin: 0, color: '#30343c', fontSize: narrow ? 15.5 : 16.5, lineHeight: 1.85, letterSpacing: -0.25, whiteSpace: 'pre-wrap', wordBreak: 'keep-all', overflowWrap: 'anywhere' }}>{formatOriginalDescription(job.desc)}</p>}
                </div>
              </div>

              <TextSection title="필수 스킬">
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>{reqList.length ? reqList.map((s) => <Pill key={s} owned={has(s)} miss={!has(s)}>{s}</Pill>) : <span style={{ color: muted, fontSize: 14 }}>필수 스킬 정보가 없습니다.</span>}</div>
              </TextSection>

              <TextSection title="우대 스킬">
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>{prefList.length ? prefList.map((s) => <Pill key={s} owned={has(s)} miss={!has(s)}>{s}</Pill>) : <span style={{ color: muted, fontSize: 14 }}>우대 스킬 정보가 없습니다.</span>}</div>
              </TextSection>

              <TextSection title="경험 태그">
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>{tags.length ? tags.map((c, i) => <TagPill key={c} muted={i > 0}>{tagLabels[c] || c}</TagPill>) : <span style={{ color: muted, fontSize: 14 }}>경험 태그 정보가 없습니다.</span>}</div>
              </TextSection>

              {!!processItems.length && <TextSection title="채용 절차">
                <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : 'repeat(2, minmax(0, 1fr))', gap: 8 }}>{processItems.slice(0, 8).map((x, i) => <div key={x + i} style={{ background: soft, border: '1px solid ' + line, borderRadius: 14, padding: '10px 12px', fontSize: 13, fontWeight: 850, lineHeight: 1.45, wordBreak: 'keep-all', overflowWrap: 'anywhere', minWidth: 0 }}><span style={{ color: faint, marginRight: 6, ...num }}>{i + 1}.</span>{cleanItemText(x)}</div>)}</div>
              </TextSection>}
            </article>
          </div>

          {login ? (
            <aside style={{ display: 'grid', gap: 14, position: narrow ? 'static' : 'sticky', top: 86 }}>
              <div style={{ background: ink, color: '#fff', borderRadius: 22, padding: 24, boxShadow: '0 14px 30px rgba(20,21,26,0.16)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}><span style={{ fontSize: 11, fontWeight: 900, letterSpacing: 1, color: green }}>매칭 리포트</span><select style={{ font: 'inherit', border: '1px solid rgba(255,255,255,0.18)', background: 'rgba(255,255,255,0.08)', color: '#fff', borderRadius: 10, padding: '7px 9px', fontSize: 12, fontWeight: 800 }}><option>{currentProjectName} 기준</option></select></div>
                <div style={{ marginTop: 16, color: 'rgba(255,255,255,0.72)', fontSize: 13 }}>{matchReportLabel}</div>
                <div style={{ display: 'flex', alignItems: 'baseline', gap: 5, margin: '2px 0 16px' }}><span style={{ fontSize: 62, fontWeight: 900, letterSpacing: -3, color: green, lineHeight: 1, ...num }}>{score}</span><span style={{ fontSize: 22, fontWeight: 800, color: 'rgba(255,255,255,0.72)' }}>%</span></div>
                <Bar label={`필수 스킬 충족 · ${reqMatched.length}/${reqList.length}`} value={requiredRate} />
                <Bar label={`우대 스킬 충족 · ${prefMatched.length}/${prefList.length}`} value={preferredRate} />
                <div style={{ color: 'rgba(255,255,255,0.5)', fontSize: 11.5, fontWeight: 700, marginTop: 2 }}>필수 70% + 우대 30% 가중치 기준</div>
                {v3 && <div style={{ borderTop: '1px solid rgba(255,255,255,0.12)', marginTop: 14, paddingTop: 14, color: 'rgba(255,255,255,0.76)', fontSize: 12.5, lineHeight: 1.6 }}><b style={{ color: green }}>왜 {score}%인가요?</b><br />{matchReason}</div>}
                <div style={{ borderTop: '1px solid rgba(255,255,255,0.12)', marginTop: 16, paddingTop: 15, display: 'grid', gap: 12 }}>
                  <div><div style={{ fontSize: 11.5, fontWeight: 800, color: green, marginBottom: 7 }}>이미 충족한 스킬</div><div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>{ownedSkills.length ? ownedSkills.map((s) => <span key={s} style={{ fontSize: 12, fontWeight: 800, padding: '4px 9px', borderRadius: 8, background: 'rgba(185,236,42,0.14)', color: green, border: '1px solid rgba(185,236,42,0.28)', whiteSpace: 'nowrap' }}>✓ {s}</span>) : <span style={{ color: 'rgba(255,255,255,0.5)', fontSize: 12.5 }}>없음</span>}</div></div>
                  <div><div style={{ fontSize: 11.5, fontWeight: 800, color: 'rgba(255,255,255,0.62)', marginBottom: 7 }}>보강하면 좋은 스킬</div><div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>{missingSkills.length ? missingSkills.map((s) => <span key={s} style={{ fontSize: 12, fontWeight: 800, padding: '4px 9px', borderRadius: 8, background: 'rgba(255,255,255,0.08)', color: 'rgba(255,255,255,0.62)', border: '1px solid rgba(255,255,255,0.14)', whiteSpace: 'nowrap' }}>+ {s}</span>) : <span style={{ color: green, fontSize: 12.5, fontWeight: 700 }}>모두 충족했어요</span>}</div></div>
                </div>
                <button onClick={() => go('gap')} style={{ font: 'inherit', cursor: 'pointer', width: '100%', marginTop: 16, border: 'none', background: green, color: ink, borderRadius: 12, padding: 12, fontWeight: 900 }}>부족 스킬 갭 분석 보기 →</button>
              </div>

              <SimilarJobsCard showScore />
            </aside>
          ) : (
            <aside style={{ display: 'grid', gap: 14, position: narrow ? 'static' : 'sticky', top: 86 }}>
              <div style={{ background: ink, color: '#fff', borderRadius: 22, padding: 24, boxShadow: '0 14px 30px rgba(20,21,26,0.16)' }}>
                <span style={{ fontSize: 11, fontWeight: 900, letterSpacing: 1, color: green }}>매칭 리포트</span>
                <div style={{ fontSize: 19, fontWeight: 900, margin: '12px 0 8px', lineHeight: 1.3 }}>내 스킬과 이 공고의<br />매칭률이 궁금하다면</div>
                <p style={{ color: 'rgba(255,255,255,0.62)', fontSize: 13.5, lineHeight: 1.6, margin: '0 0 16px' }}>GitHub 프로젝트를 연결하거나 스킬을 입력하면 필수·우대 스킬 충족률이 여기에 채워져요.</p>
                <button onClick={() => go('login')} style={{ font: 'inherit', cursor: 'pointer', width: '100%', border: 'none', background: green, color: ink, borderRadius: 12, padding: 12, fontWeight: 900 }}>GitHub 연결하고 매칭 보기</button>
              </div>

              <SimilarJobsCard />
            </aside>
          )}
        </section>
      </div>

      {toast && <div style={{ position: 'fixed', bottom: 28, left: '50%', transform: 'translateX(-50%)', background: ink, color: '#fff', padding: '12px 20px', borderRadius: 24, fontSize: 14, fontWeight: 700, boxShadow: '0 8px 28px rgba(0,0,0,0.25)', zIndex: 200, display: 'flex', alignItems: 'center', gap: 9 }}><span style={{ width: 7, height: 7, borderRadius: 4, background: green }} />{toast}</div>}
    </div>
  );
}
