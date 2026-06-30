import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ACCESS_TOKEN = __ENV.ACCESS_TOKEN || '';
const CAPACITY_SCENARIO = __ENV.CAPACITY_SCENARIO || 'hot';
const TARGET_RPS = Number.parseInt(__ENV.TARGET_RPS || '1000', 10);
const BASE_RPS = Number.parseInt(__ENV.BASE_RPS || '500', 10);
const DURATION = __ENV.DURATION || '2m';
const SPIKE_WARMUP_DURATION = __ENV.SPIKE_WARMUP_DURATION || '30s';
const SPIKE_RAMP_DURATION = __ENV.SPIKE_RAMP_DURATION || '10s';
const SPIKE_HOLD_DURATION = __ENV.SPIKE_HOLD_DURATION || '2m';
const SPIKE_RECOVERY_DURATION = __ENV.SPIKE_RECOVERY_DURATION || '30s';
const RAMP_DOWN_DURATION = __ENV.RAMP_DOWN_DURATION || '30s';
const PRE_ALLOCATED_VUS = Number.parseInt(__ENV.PRE_ALLOCATED_VUS || '800', 10);
const MAX_VUS = Number.parseInt(__ENV.MAX_VUS || '4000', 10);
const SEARCH_LIMIT = __ENV.SEARCH_LIMIT || '10';
const HOT_TRAFFIC_PERCENT = Number.parseInt(__ENV.HOT_TRAFFIC_PERCENT || '70', 10);
const SPIKE_TRAFFIC_PROFILE = __ENV.SPIKE_TRAFFIC_PROFILE || 'hot';
const LONG_TAIL_PREFIX = __ENV.LONG_TAIL_PREFIX || 'capacity-tail';
const LONG_TAIL_RUN_ID = __ENV.LONG_TAIL_RUN_ID || `${Date.now()}`;
const LONG_TAIL_VARIANTS = Number.parseInt(__ENV.LONG_TAIL_VARIANTS || '1000000', 10);
const P95_THRESHOLD_MS = Number.parseInt(__ENV.P95_THRESHOLD_MS || '1000', 10);
const FAIL_RATE_THRESHOLD = Number.parseFloat(__ENV.FAIL_RATE_THRESHOLD || '0.01');
const HOT_KEYWORDS = (__ENV.HOT_KEYWORDS || '백엔드,Spring Boot,프론트엔드,React,데이터 엔지니어,DevOps,Kubernetes,Python,Java,TypeScript')
    .split(',')
    .map((keyword) => keyword.trim())
    .filter((keyword) => keyword.length > 0);

const VALID_SCENARIOS = new Set(['hot', 'cold', 'mixed', 'spike']);
const VALID_SPIKE_PROFILES = new Set(['hot', 'mixed']);

if (!VALID_SCENARIOS.has(CAPACITY_SCENARIO)) {
    throw new Error('CAPACITY_SCENARIO must be one of hot, cold, mixed, or spike');
}

if (!VALID_SPIKE_PROFILES.has(SPIKE_TRAFFIC_PROFILE)) {
    throw new Error('SPIKE_TRAFFIC_PROFILE must be hot or mixed');
}

if (HOT_KEYWORDS.length === 0) {
    throw new Error('HOT_KEYWORDS must contain at least one non-empty keyword');
}

if (!Number.isFinite(TARGET_RPS) || TARGET_RPS < 1) {
    throw new Error('TARGET_RPS must be a positive integer');
}

if (!Number.isFinite(BASE_RPS) || BASE_RPS < 1) {
    throw new Error('BASE_RPS must be a positive integer');
}

if (!Number.isFinite(PRE_ALLOCATED_VUS) || PRE_ALLOCATED_VUS < 1) {
    throw new Error('PRE_ALLOCATED_VUS must be a positive integer');
}

if (!Number.isFinite(MAX_VUS) || MAX_VUS < PRE_ALLOCATED_VUS) {
    throw new Error('MAX_VUS must be greater than or equal to PRE_ALLOCATED_VUS');
}

if (!Number.isFinite(HOT_TRAFFIC_PERCENT) || HOT_TRAFFIC_PERCENT < 0 || HOT_TRAFFIC_PERCENT > 100) {
    throw new Error('HOT_TRAFFIC_PERCENT must be an integer between 0 and 100');
}

if (!Number.isFinite(LONG_TAIL_VARIANTS) || LONG_TAIL_VARIANTS < 1) {
    throw new Error('LONG_TAIL_VARIANTS must be greater than 0');
}

function scenarioOptions() {
    if (CAPACITY_SCENARIO === 'spike') {
        return {
            executor: 'ramping-arrival-rate',
            startRate: BASE_RPS,
            timeUnit: '1s',
            preAllocatedVUs: PRE_ALLOCATED_VUS,
            maxVUs: MAX_VUS,
            stages: [
                { target: BASE_RPS, duration: SPIKE_WARMUP_DURATION },
                { target: TARGET_RPS, duration: SPIKE_RAMP_DURATION },
                { target: TARGET_RPS, duration: SPIKE_HOLD_DURATION },
                { target: BASE_RPS, duration: SPIKE_RECOVERY_DURATION },
                { target: 0, duration: RAMP_DOWN_DURATION },
            ],
        };
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
        search_capacity_followup: scenarioOptions(),
    },
    summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
    thresholds: {
        http_req_failed: [{ threshold: `rate<${FAIL_RATE_THRESHOLD}`, abortOnFail: true, delayAbortEval: '10s' }],
        [`http_req_duration{endpoint:jobs_search,capacity_scenario:${CAPACITY_SCENARIO}}`]: [
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

function longTailKeyword(iteration) {
    const variant = iteration % LONG_TAIL_VARIANTS;
    return `${LONG_TAIL_PREFIX}-${LONG_TAIL_RUN_ID}-${variant}`;
}

function mixedSearch(iteration) {
    if ((iteration % 100) < HOT_TRAFFIC_PERCENT) {
        return {
            traffic: 'hot',
            keyword: HOT_KEYWORDS[iteration % HOT_KEYWORDS.length],
        };
    }

    return {
        traffic: 'long_tail',
        keyword: longTailKeyword(iteration),
    };
}

function chooseSearch() {
    const iteration = exec.scenario.iterationInTest;

    if (CAPACITY_SCENARIO === 'cold') {
        return {
            traffic: 'cold_long_tail',
            keyword: longTailKeyword(iteration),
        };
    }

    if (CAPACITY_SCENARIO === 'mixed') {
        return mixedSearch(iteration);
    }

    if (CAPACITY_SCENARIO === 'spike' && SPIKE_TRAFFIC_PROFILE === 'mixed') {
        return mixedSearch(iteration);
    }

    return {
        traffic: 'hot',
        keyword: HOT_KEYWORDS[iteration % HOT_KEYWORDS.length],
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
                capacity_scenario: CAPACITY_SCENARIO,
                traffic: search.traffic,
                target_rps: String(TARGET_RPS),
                hot_traffic_percent: String(HOT_TRAFFIC_PERCENT),
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
