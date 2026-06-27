import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ACCESS_TOKEN = __ENV.ACCESS_TOKEN || '';
const KEYWORDS = (__ENV.KEYWORDS || '백엔드,Spring Boot,프론트엔드,React,데이터 엔지니어,DevOps,Kubernetes,Python,Java,TypeScript')
    .split(',')
    .map((k) => k.trim())
    .filter((k) => k.length > 0);

if (KEYWORDS.length === 0) {
    throw new Error('KEYWORDS must contain at least one non-empty keyword');
}

export const options = {
    stages: [
        { duration: '3m', target: 50 },
        { duration: '3m', target: 100 },
        { duration: '3m', target: 200 },
        { duration: '3m', target: 500 },
        { duration: '10m', target: 500 },
        { duration: '1m', target: 0 },
    ],
    summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
    thresholds: {
        http_req_failed: [{ threshold: 'rate<0.50', abortOnFail: true, delayAbortEval: '10s' }],
        'http_req_duration{endpoint:jobs_search}': [{ threshold: 'p(95)<60000', abortOnFail: true, delayAbortEval: '30s' }],
    },
};

function authorizationHeaders() {
    if (!ACCESS_TOKEN) {
        return {};
    }

    return { Authorization: `Bearer ${ACCESS_TOKEN}` };
}

export default function () {
    const keyword = KEYWORDS[__ITER % KEYWORDS.length];

    const res = http.get(
        `${BASE_URL}/jobs/search?keyword=${encodeURIComponent(keyword)}&limit=10`,
        {
            headers: authorizationHeaders(),
            tags: { endpoint: 'jobs_search' },
        }
    );

    check(res, {
        'status 200': (r) => r.status === 200,
        'success true': (r) => {
            try { return r.json('success') === true; } catch { return false; }
        },
    });

    sleep(1);
}
