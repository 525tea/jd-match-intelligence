import React from 'react';

const ink = '#14151a';
const muted = '#5b616e';
const line = '#e7eaf0';
const green = '#b9ec2a';
const font = "'Space Grotesk', 'Pretendard', 'Apple SD Gothic Neo', system-ui, sans-serif";

export class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, info) {
    if (import.meta.env.DEV) {
      console.error('Frontend render error', error, info);
    }
  }

  render() {
    if (!this.state.error) return this.props.children;

    return (
      <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', background: '#f7f8fa', color: ink, fontFamily: font, padding: 24 }}>
        <section style={{ width: 'min(560px, 100%)', background: '#fff', border: `1px solid ${line}`, borderRadius: 24, padding: 30, boxShadow: '0 16px 40px rgba(20,21,26,0.08)' }}>
          <div style={{ width: 52, height: 52, borderRadius: 18, background: green, display: 'grid', placeItems: 'center', fontWeight: 950, fontSize: 24 }}>!</div>
          <h1 style={{ margin: '18px 0 8px', fontSize: 28, letterSpacing: -0.8 }}>화면을 다시 불러와야 해요</h1>
          <p style={{ margin: 0, color: muted, lineHeight: 1.65 }}>일시적인 화면 오류가 발생했습니다. 새로고침하면 최신 API 응답 기준으로 다시 렌더링합니다.</p>
          <button
            onClick={() => window.location.reload()}
            style={{ marginTop: 18, border: 'none', borderRadius: 14, background: ink, color: '#fff', padding: '12px 16px', font: 'inherit', fontWeight: 900, cursor: 'pointer' }}
          >
            새로고침
          </button>
        </section>
      </div>
    );
  }
}
