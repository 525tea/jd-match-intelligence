import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ACCESS_TOKEN = __ENV.ACCESS_TOKEN || '';
const HOT_TRAFFIC_PERCENT = Number.parseInt(__ENV.HOT_TRAFFIC_PERCENT || '70', 10);
const SEARCH_LIMIT = __ENV.SEARCH_LIMIT || '10';
const LONG_TAIL_PREFIX = __ENV.LONG_TAIL_PREFIX || 'mixed-tail';
const LONG_TAIL_RUN_ID = __ENV.LONG_TAIL_RUN_ID || `${Date.now()}`;
const LONG_TAIL_VARIANTS = Number.parseInt(__ENV.LONG_TAIL_VARIANTS || '1000000', 10);
const SLEEP_SECONDS = Number.parseFloat(__ENV.SLEEP_SECONDS || '1');

const HOT_KEYWORDS = (__ENV.HOT_KEYWORDS || '백엔드,Spring Boot,프론트엔드,React,데이터 엔지니어,DevOps,Kubernetes,Python,Java,TypeScript')
    .split(',')
    .map((k) => k.trim())
    .filter((k) => k.length > 0);

if (HOT_KEYWORDS.length === 0) {
    throw new Error('HOT_KEYWORDS must contain at least one non-empty keyword');
}

if (!Number.isFinite(HOT_TRAFFIC_PERCENT) || HOT_TRAFFIC_PERCENT < 0 || HOT_TRAFFIC_PERCENT > 100) {
    throw new Error('HOT_TRAFFIC_PERCENT must be an integer between 0 and 100');
}

if (!Number.isFinite(LONG_TAIL_VARIANTS) || LONG_TAIL_VARIANTS < 1) {
    throw new Error('LONG_TAIL_VARIANTS must be greater than 0');
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
        'http_req_duration{endpoint:jobs_search,traffic:hot}': [{ threshold: 'p(95)<10000', abortOnFail: true, delayAbortEval: '30s' }],
        'http_req_duration{endpoint:jobs_search,traffic:long_tail}': [{ threshold: 'p(95)<60000', abortOnFail: true, delayAbortEval: '30s' }],
    },
};

function authorizationHeaders() {
    if (!ACCESS_TOKEN) {
        return {};
    }

    return { Authorization: `Bearer ${ACCESS_TOKEN}` };
}

function chooseSearch() {
    const bucket = __ITER % 100;
    if (bucket < HOT_TRAFFIC_PERCENT) {
        return {
            traffic: 'hot',
            keyword: HOT_KEYWORDS[__ITER % HOT_KEYWORDS.length],
        };
    }

    const variant = ((__VU - 1) * LONG_TAIL_VARIANTS) + (__ITER % LONG_TAIL_VARIANTS);
    return {
        traffic: 'long_tail',
        keyword: `${LONG_TAIL_PREFIX}-${LONG_TAIL_RUN_ID}-${variant}`,
    };
}

export default function () {
    const search = chooseSearch();
    const res = http.get(
        `${BASE_URL}/jobs/search?keyword=${encodeURIComponent(search.keyword)}&limit=${SEARCH_LIMIT}`,
        {
            headers: authorizationHeaders(),
            tags: {
                endpoint: 'jobs_search',
                traffic: search.traffic,
                hit_profile: `${HOT_TRAFFIC_PERCENT}_hot`,
            },
        }
    );

    check(res, {
        'status 200': (r) => r.status === 200,
        'success true': (r) => {
            try { return r.json('success') === true; } catch { return false; }
        },
    });

    sleep(SLEEP_SECONDS);
}
