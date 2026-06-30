import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ACCESS_TOKEN = __ENV.ACCESS_TOKEN || '';
const TARGET_RPS = Number.parseInt(__ENV.TARGET_RPS || '1000', 10);
const DURATION = __ENV.DURATION || '2m';
const LOAD_PROFILE = __ENV.LOAD_PROFILE || 'constant';
const RAMP_UP_DURATION = __ENV.RAMP_UP_DURATION || '1m';
const STEADY_DURATION = __ENV.STEADY_DURATION || DURATION;
const RAMP_DOWN_DURATION = __ENV.RAMP_DOWN_DURATION || '30s';
const PRE_ALLOCATED_VUS = Number.parseInt(__ENV.PRE_ALLOCATED_VUS || '800', 10);
const MAX_VUS = Number.parseInt(__ENV.MAX_VUS || '4000', 10);
const SEARCH_LIMIT = __ENV.SEARCH_LIMIT || '10';
const P95_THRESHOLD_MS = Number.parseInt(__ENV.P95_THRESHOLD_MS || '1000', 10);
const FAIL_RATE_THRESHOLD = Number.parseFloat(__ENV.FAIL_RATE_THRESHOLD || '0.01');
const HOT_KEYWORDS = (__ENV.HOT_KEYWORDS || '백엔드,Spring Boot,프론트엔드,React,데이터 엔지니어,DevOps,Kubernetes,Python,Java,TypeScript')
    .split(',')
    .map((k) => k.trim())
    .filter((k) => k.length > 0);

if (HOT_KEYWORDS.length === 0) {
    throw new Error('HOT_KEYWORDS must contain at least one non-empty keyword');
}

if (!Number.isFinite(TARGET_RPS) || TARGET_RPS < 1) {
    throw new Error('TARGET_RPS must be a positive integer');
}

if (!Number.isFinite(PRE_ALLOCATED_VUS) || PRE_ALLOCATED_VUS < 1) {
    throw new Error('PRE_ALLOCATED_VUS must be a positive integer');
}

if (!Number.isFinite(MAX_VUS) || MAX_VUS < PRE_ALLOCATED_VUS) {
    throw new Error('MAX_VUS must be greater than or equal to PRE_ALLOCATED_VUS');
}

function scenarioOptions() {
    if (LOAD_PROFILE === 'ramping') {
        return {
            executor: 'ramping-arrival-rate',
            startRate: 0,
            timeUnit: '1s',
            preAllocatedVUs: PRE_ALLOCATED_VUS,
            maxVUs: MAX_VUS,
            stages: [
                { target: TARGET_RPS, duration: RAMP_UP_DURATION },
                { target: TARGET_RPS, duration: STEADY_DURATION },
                { target: 0, duration: RAMP_DOWN_DURATION },
            ],
        };
    }

    if (LOAD_PROFILE !== 'constant') {
        throw new Error('LOAD_PROFILE must be constant or ramping');
    }

    return {
        executor: 'constant-arrival-rate',
        rate: TARGET_RPS,
        timeUnit: '1s',
        duration: DURATION,
        preAllocatedVUs: PRE_ALLOCATED_VUS,
        maxVUs: MAX_VUS,
    };
}

export const options = {
    scenarios: {
        search_saturation: scenarioOptions(),
    },
    summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
    thresholds: {
        http_req_failed: [{ threshold: `rate<${FAIL_RATE_THRESHOLD}`, abortOnFail: true, delayAbortEval: '10s' }],
        'http_req_duration{endpoint:jobs_search,workload:saturation}': [
            { threshold: `p(95)<${P95_THRESHOLD_MS}`, abortOnFail: false },
        ],
        checks: [{ threshold: 'rate>0.99', abortOnFail: true, delayAbortEval: '10s' }],
    },
};

function authorizationHeaders() {
    if (!ACCESS_TOKEN) {
        return {};
    }

    return { Authorization: `Bearer ${ACCESS_TOKEN}` };
}

export default function () {
    const keyword = HOT_KEYWORDS[__ITER % HOT_KEYWORDS.length];
    const res = http.get(
        `${BASE_URL}/jobs/search?keyword=${encodeURIComponent(keyword)}&limit=${SEARCH_LIMIT}`,
        {
            headers: authorizationHeaders(),
            tags: {
                endpoint: 'jobs_search',
                workload: 'saturation',
                load_profile: LOAD_PROFILE,
                target_rps: String(TARGET_RPS),
            },
        }
    );

    check(res, {
        'status 200': (r) => r.status === 200,
        'success true': (r) => {
            try { return r.json('success') === true; } catch { return false; }
        },
    });
}
