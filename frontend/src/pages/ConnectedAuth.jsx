import React from 'react';
import { API_BASE_URL, api, authStore, projectStore } from '../api/client.js';

const ink = '#14151a';
const muted = '#5b616e';
const faint = '#9aa1ad';
const line = '#e7eaf0';
const green = '#b9ec2a';
const greenInk = '#3f5c08';
const greenTint = '#eef8cf';
const greenTintBd = '#dbeca8';
const coralDeep = '#c2391f';
const coralTint = '#ffe8e1';
const coralTintBd = '#f6c9bc';
const soft = '#f1f3f7';
const font = "'Space Grotesk', 'Pretendard', 'Apple SD Gothic Neo', system-ui, sans-serif";

function GithubMark({ size = 16 }) {
  return <svg width={size} height={size} viewBox="0 0 16 16" fill="currentColor"><path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38v-1.34c-2.23.49-2.7-1.07-2.7-1.07-.36-.93-.89-1.18-.89-1.18-.73-.5.05-.49.05-.49.8.06 1.23.83 1.23.83.72 1.23 1.88.87 2.34.67.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.96 0-.87.31-1.59.83-2.15-.08-.2-.36-1.02.08-2.13 0 0 .67-.21 2.2.82a7.6 7.6 0 0 1 4 0c1.53-1.03 2.2-.82 2.2-.82.44 1.11.16 1.93.08 2.13.52.56.82 1.28.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48v2.2c0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8z"/></svg>;
}

export function ConnectedLogin({ go, onAuthenticated }) {
  const [mode, setMode] = React.useState('login');
  const [form, setForm] = React.useState({ name: '', email: '', password: '' });
  const [error, setError] = React.useState('');
  const [loading, setLoading] = React.useState(false);
  const narrow = window.innerWidth < 980;
  const demoEmail = import.meta.env.VITE_DEMO_EMAIL;
  const demoPassword = import.meta.env.VITE_DEMO_PASSWORD;
  const demoProjectId = import.meta.env.VITE_DEMO_PROJECT_ID;
  const defaultProjectId = import.meta.env.VITE_DEFAULT_USER_PROJECT_ID || demoProjectId || '';
  const demoConfigured = Boolean(demoEmail && demoPassword);
  const [projectId, setProjectId] = React.useState(projectStore.getProjectId() || defaultProjectId);

  const persistProjectId = () => {
    const normalizedProjectId = String(projectId || '').trim();
    if (normalizedProjectId) projectStore.setProjectId(normalizedProjectId);
    else projectStore.clear();
  };

  const submit = async () => {
    setLoading(true);
    setError('');
    try {
      if (mode === 'signup') {
        await api.signup({ name: form.name, email: form.email, password: form.password });
      }
      const token = await api.login({ email: form.email, password: form.password });
      authStore.setToken(token.accessToken);
      persistProjectId();
      await onAuthenticated?.();
      go('home');
    } catch (e) {
      setError(e.message || '로그인 요청에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const startOAuth = () => {
    persistProjectId();
    window.location.href = `${API_BASE_URL}/oauth2/authorization/github`;
  };

  const startDemo = async () => {
    if (!demoConfigured) {
      setError('데모 계정이 설정되지 않았습니다. GitHub 로그인 또는 직접 로그인으로 계속해주세요.');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const token = await api.login({ email: demoEmail, password: demoPassword });
      authStore.setToken(token.accessToken);
      if (String(projectId || '').trim()) persistProjectId();
      else if (demoProjectId) projectStore.setProjectId(demoProjectId);
      try {
        await onAuthenticated?.();
      } catch (refreshError) {
        console.warn('Demo login succeeded, but data refresh failed.', refreshError);
      }
      go('home');
    } catch (e) {
      authStore.clear();
      setError(e.message || '데모 계정 로그인이 실패했습니다. GitHub 로그인 또는 직접 로그인으로 계속해주세요.');
    } finally {
      setLoading(false);
    }
  };

  return <div style={{ minHeight: '100vh', background: '#fff', fontFamily: font, color: ink }}>
    <div style={{ height: 64, borderBottom: '1px solid ' + line, display: 'flex', alignItems: 'center', padding: narrow ? '0 18px' : '0 40px' }}>
      <button onClick={() => go('home')} style={{ border: 'none', background: 'transparent', font: 'inherit', fontSize: 22, fontWeight: 900, letterSpacing: -1, cursor: 'pointer' }}>jobflow<span style={{ color: green }}>.</span></button>
    </div>
    <main style={{ maxWidth: 1120, margin: '0 auto', padding: narrow ? '28px 18px 64px' : '42px 40px 72px' }}>
      <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : '1.05fr 0.95fr', gap: 22, alignItems: 'stretch' }}>
        <section style={{ background: '#fff', border: '1px solid ' + line, borderRadius: 22, padding: 28, boxShadow: '0 1px 2px rgba(20,21,26,0.04), 0 8px 22px rgba(20,21,26,0.06)' }}>
          <div style={{ display: 'flex', gap: 8, marginBottom: 22 }}>
            <button onClick={() => setMode('login')} style={{ font: 'inherit', border: mode === 'login' ? 'none' : '1px solid ' + line, background: mode === 'login' ? green : '#fff', borderRadius: 20, padding: '8px 16px', fontWeight: 900, cursor: 'pointer' }}>로그인</button>
            <button onClick={() => setMode('signup')} style={{ font: 'inherit', border: mode === 'signup' ? 'none' : '1px solid ' + line, background: mode === 'signup' ? green : '#fff', color: mode === 'signup' ? ink : muted, borderRadius: 20, padding: '8px 16px', fontWeight: 800, cursor: 'pointer' }}>회원가입</button>
          </div>
          {mode === 'signup' && <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="이름" style={inputStyle} />}
          <input value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} placeholder="이메일" style={inputStyle} />
          <input value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} placeholder="비밀번호" type="password" style={inputStyle} />
          <input value={projectId} onChange={(e) => setProjectId(e.target.value)} placeholder="분석 프로젝트 ID (예: 2)" style={inputStyle} />
          {error && <div style={{ background: coralTint, border: '1px solid ' + coralTintBd, color: coralDeep, borderRadius: 12, padding: '10px 12px', fontSize: 13, fontWeight: 800, marginBottom: 10 }}>{error}</div>}
          <button disabled={loading} onClick={submit} style={{ font: 'inherit', cursor: loading ? 'default' : 'pointer', width: '100%', border: 'none', background: ink, color: '#fff', borderRadius: 12, padding: 14, fontWeight: 900 }}>{loading ? '처리 중...' : mode === 'signup' ? '가입하고 계속하기' : '이메일로 계속하기'}</button>
          <button onClick={startDemo} style={{ font: 'inherit', cursor: 'pointer', width: '100%', border: '1px solid ' + greenTintBd, background: greenTint, color: greenInk, borderRadius: 12, padding: 13, fontWeight: 900, marginTop: 10 }}>{demoConfigured ? '데모 계정으로 둘러보기' : '데모 계정 설정 필요'}</button>
          <button onClick={startOAuth} style={{ font: 'inherit', cursor: 'pointer', width: '100%', border: '1px solid ' + line, background: '#fff', borderRadius: 12, padding: 13, fontWeight: 850, marginTop: 12, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}><GithubMark />GitHub로 로그인</button>
          <div style={{ color: faint, fontSize: 12, lineHeight: 1.5, marginTop: 12 }}>로그인 성공 시 accessToken을 저장하고 보호 API 요청에 Authorization 헤더를 자동으로 붙입니다. 프로젝트 ID는 project inventory, JD 매칭, 갭 분석, 추천 API 조회 기준으로 사용합니다.</div>
        </section>
        <section style={{ background: ink, color: '#fff', borderRadius: 24, padding: 30, minHeight: 440, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
          <span style={{ fontSize: 11, fontWeight: 900, letterSpacing: 1, color: green }}>REPOSITORY MATCHING</span>
          <div style={{ marginTop: 42, fontSize: narrow ? 30 : 40, fontWeight: 900, letterSpacing: -1.4, lineHeight: 1.08 }}>레포지토리에서<br />스킬 근거를 읽고<br /><span style={{ color: green }}>공고를 정렬합니다.</span></div>
          <div style={{ marginTop: 'auto', display: 'grid', gap: 10 }}>{['코드 기반 스킬 추출', '경험 태그 자동 요약', '공고별 매칭률 계산'].map((x, i) => <div key={x} style={{ display: 'flex', alignItems: 'center', gap: 10, color: 'rgba(255,255,255,0.82)', fontSize: 14, fontWeight: 800 }}><span style={{ width: 26, height: 26, borderRadius: 13, background: 'rgba(185,236,42,0.16)', color: green, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 1000 }}>{i + 1}</span>{x}</div>)}</div>
        </section>
      </div>
    </main>
  </div>;
}

const inputStyle = { width: '100%', font: 'inherit', fontSize: 14, padding: '13px 15px', borderRadius: 12, border: '1px solid ' + line, marginBottom: 10, outline: 'none', boxSizing: 'border-box', background: '#fff' };

export function ConnectedOAuth({ go, onAuthenticated }) {
  const [status, setStatus] = React.useState('processing');
  const [message, setMessage] = React.useState('authorization code를 토큰으로 교환하고 사용자 정보를 불러옵니다.');

  React.useEffect(() => {
    const code = new URLSearchParams(window.location.search).get('code');
    if (!code) {
      setStatus('error');
      setMessage('OAuth code가 없습니다. 다시 로그인해주세요.');
      return;
    }
    api.oauthToken({ code })
      .then(async (token) => {
        authStore.setToken(token.accessToken);
        await onAuthenticated?.();
        setStatus('success');
        setMessage('로그인 완료! 홈으로 이동합니다.');
        setTimeout(() => go('home'), 500);
      })
      .catch((e) => {
        setStatus('error');
        setMessage(e.message || 'OAuth 로그인이 완료되지 않았습니다.');
      });
  }, []);

  return <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', background: '#fff', fontFamily: font, color: ink, padding: 24 }}>
    <section style={{ width: 'min(560px, 100%)', background: '#fff', border: '1px solid ' + line, borderRadius: 22, padding: 30, boxShadow: '0 8px 22px rgba(20,21,26,0.06)' }}>
      <div style={{ width: 54, height: 54, borderRadius: 18, background: status === 'error' ? coralTint : green, color: status === 'error' ? coralDeep : ink, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 24, fontWeight: 1000 }}>{status === 'error' ? '!' : status === 'success' ? '✓' : '…'}</div>
      <h1 style={{ margin: '18px 0 8px', letterSpacing: -1 }}>{status === 'error' ? '로그인이 완료되지 않았습니다' : '로그인 처리 중'}</h1>
      <p style={{ color: muted, lineHeight: 1.6 }}>{message}</p>
      {status === 'error' && <button onClick={() => go('login')} style={{ font: 'inherit', border: 'none', background: ink, color: '#fff', borderRadius: 13, padding: '12px 16px', fontWeight: 900, cursor: 'pointer' }}>로그인으로 돌아가기</button>}
    </section>
  </div>;
}
