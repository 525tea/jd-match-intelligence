import React from 'react';
import { GitBranch } from 'lucide-react';
import { api, authStore } from '../api/client.js';
import { Button } from '../components/ui.jsx';

export function LoginPage({ go, setUser }) {
  const [mode, setMode] = React.useState('login');
  const [form, setForm] = React.useState({ name: '사용자', email: 'user@example.com', password: '' });
  const [error, setError] = React.useState('');
  const [loading, setLoading] = React.useState(false);
  const submit = async (demo = false) => {
    setLoading(true); setError('');
    try {
      if (demo) { localStorage.setItem('jobflow.demo', '1'); setUser((u) => ({ ...u, name: '사용자' })); go('home'); return; }
      const res = mode === 'login' ? await api.login({ email: form.email, password: form.password }) : await api.signup(form);
      if (res.accessToken) authStore.setToken(res.accessToken);
      const me = res.accessToken ? await api.me().catch(() => null) : null;
      if (me) setUser(me);
      go('home');
    } catch (e) { setError(e.message || '로그인에 실패했습니다.'); }
    finally { setLoading(false); }
  };
  return <main className="auth-page"><section className="tile auth-card"><div className="auth-form"><div style={{ display: 'flex', gap: 8, marginBottom: 18 }}><Button variant={mode === 'login' ? 'lime' : 'light'} onClick={() => setMode('login')}>로그인</Button><Button variant={mode === 'signup' ? 'lime' : 'light'} onClick={() => setMode('signup')}>회원가입</Button></div>{mode === 'signup' && <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="이름" />}<input value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} placeholder="이메일" /><input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} placeholder="비밀번호" />{error && <p style={{ color: 'var(--coral)', fontWeight: 800 }}>{error}</p>}<Button variant="dark" style={{ width: '100%', marginTop: 10 }} disabled={loading} onClick={() => submit(false)}>{loading ? '처리 중' : '이메일로 계속하기'}</Button><Button variant="lime" style={{ width: '100%', marginTop: 10 }} onClick={() => submit(true)}>데모 계정으로 둘러보기</Button><div className="grid grid-2" style={{ marginTop: 12 }}><Button variant="light" onClick={() => go('oauth')}><GitBranch size={17} />GitHub</Button><Button variant="light" onClick={() => go('oauth')}>Google</Button></div></div><aside className="auth-art"><p className="eyebrow">REPOSITORY MATCHING</p><h1>레포지토리에서<br />스킬 근거를 읽고<br /><span style={{ color: 'var(--lime)' }}>공고를 정렬합니다.</span></h1><div className="grid">{['코드 기반 스킬 추출', '경험 태그 자동 요약', '공고별 매칭률 계산'].map((x, i) => <div key={x}><BadgeNum>{i + 1}</BadgeNum> {x}</div>)}</div></aside></section></main>;
}
function BadgeNum({ children }) { return <span style={{ display: 'inline-grid', placeItems: 'center', width: 26, height: 26, borderRadius: 13, background: 'rgba(185,236,42,.18)', color: 'var(--lime)', fontWeight: 900, marginRight: 8 }}>{children}</span>; }

export function OAuthPage({ go }) {
  React.useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');
    if (!code) return;
    api.oauthToken({ code, provider: 'GITHUB' }).then((res) => { authStore.setToken(res.accessToken); go('home'); }).catch(() => go('oauth-failure'));
  }, []);
  return <main className="auth-page"><section className="tile" style={{ padding: 36, width: 'min(560px, 100%)' }}><p className="eyebrow">OAUTH CALLBACK</p><h1>로그인 처리 중</h1><p style={{ color: 'var(--muted)' }}>authorization code를 토큰으로 교환하고 사용자 정보를 불러옵니다.</p><Button variant="dark" onClick={() => go('home')}>홈으로 이동</Button></section></main>;
}
