import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const ACCESS_TOKEN = __ENV.ACCESS_TOKEN || '';
const LOGIN_EMAIL = __ENV.LOGIN_EMAIL || '';
const LOGIN_PASSWORD = __ENV.LOGIN_PASSWORD || '';
const USER_PROJECT_ID = __ENV.USER_PROJECT_ID || '';
const ENDPOINTS = (__ENV.ENDPOINTS || 'gap_analysis,jd_match,recommendations_jobs')
    .split(',')
    .map((endpoint) => endpoint.trim())
    .filter((endpoint) => endpoint.length > 0);
const TARGET_ROLES = (__ENV.TARGET_ROLES || 'BACKEND,DATA_ENGINEER,DEVOPS')
    .split(',')
    .map((role) => role.trim())
    .filter((role) => role.length > 0);
const TARGET_CAREER_LEVELS = (__ENV.TARGET_CAREER_LEVELS || 'JUNIOR,MID,SENIOR')
    .split(',')
    .map((careerLevel) => careerLevel.trim())
    .filter((careerLevel) => careerLevel.length > 0);

const VUS = Number(__ENV.VUS || 200);
const DURATION = __ENV.DURATION || '10m';
const LIMIT = Number(__ENV.LIMIT || 20);
const SLEEP_SECONDS = Number(__ENV.SLEEP_SECONDS || 1);
const P95_THRESHOLD_MS = Number(__ENV.P95_THRESHOLD_MS || 5000);
const FAIL_RATE_THRESHOLD = Number(__ENV.FAIL_RATE_THRESHOLD || 0.02);
const FAILURE_SAMPLE_BODY_LIMIT = Number(__ENV.FAILURE_SAMPLE_BODY_LIMIT || 500);
const CACHE_MODE = __ENV.CACHE_MODE || 'enabled';

const httpStatusCodes = new Counter('jobflow_analysis_cache_http_status_codes');
const failedResponses = new Counter('jobflow_analysis_cache_failed_responses');
const loggedFailureSamples = {};

if (ENDPOINTS.length === 0) {
    throw new Error('ENDPOINTS must contain at least one endpoint');
}

if (TARGET_ROLES.length === 0) {
    throw new Error('TARGET_ROLES must contain at least one role');
}

if (ENDPOINTS.includes('jd_match') && TARGET_CAREER_LEVELS.length === 0) {
    throw new Error('TARGET_CAREER_LEVELS must contain at least one career level when jd_match is enabled');
}

if (!Number.isFinite(VUS) || VUS < 1) {
    throw new Error('VUS must be a positive number');
}

if (!Number.isFinite(LIMIT) || LIMIT < 1 || LIMIT > 50) {
    throw new Error('LIMIT must be between 1 and 50');
}

export const options = {
    vus: VUS,
    duration: DURATION,
    summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
    thresholds: {
        checks: [{ threshold: 'rate>0.99', abortOnFail: true, delayAbortEval: '10s' }],
        http_req_failed: [{ threshold: `rate<${FAIL_RATE_THRESHOLD}`, abortOnFail: true, delayAbortEval: '10s' }],
        'http_req_duration{endpoint:gap_analysis}': [{ threshold: `p(95)<${P95_THRESHOLD_MS}`, abortOnFail: false }],
        'http_req_duration{endpoint:jd_match}': [{ threshold: `p(95)<${P95_THRESHOLD_MS}`, abortOnFail: false }],
        'http_req_duration{endpoint:recommendations_jobs}': [{ threshold: `p(95)<${P95_THRESHOLD_MS}`, abortOnFail: false }],
    },
};

function authorizationHeaders(token) {
    if (!token) {
        return {};
    }

    return { Authorization: `Bearer ${token}` };
}

function truncateBody(body) {
    const value = String(body || '');
    if (value.length <= FAILURE_SAMPLE_BODY_LIMIT) {
        return value;
    }

    return `${value.slice(0, FAILURE_SAMPLE_BODY_LIMIT)}...<truncated>`;
}

function recordResponse(endpoint, response) {
    const tags = {
        endpoint,
        status: String(response.status),
        cache_mode: CACHE_MODE,
    };

    httpStatusCodes.add(1, tags);

    if (response.status >= 400 || response.status === 0) {
        failedResponses.add(1, tags);
    }
}

function logFailureSample(endpoint, response) {
    if (response.status < 400) {
        return;
    }

    const key = `${endpoint}:${response.status}`;
    if (loggedFailureSamples[key]) {
        return;
    }

    loggedFailureSamples[key] = true;
    console.error(`[failure-sample] vu=${__VU} iter=${__ITER} endpoint=${endpoint} status=${response.status} body=${truncateBody(response.body)}`);
}

function observeResponse(endpoint, response) {
    recordResponse(endpoint, response);
    logFailureSample(endpoint, response);
}

function login() {
    if (!LOGIN_EMAIL || !LOGIN_PASSWORD) {
        return '';
    }

    const response = http.post(
        `${BASE_URL}/auth/login`,
        JSON.stringify({
            email: LOGIN_EMAIL,
            password: LOGIN_PASSWORD,
        }),
        {
            headers: {
                'Content-Type': 'application/json',
            },
            tags: {
                endpoint: 'auth_login',
                cache_mode: CACHE_MODE,
            },
        },
    );
    observeResponse('auth_login', response);

    const ok = check(response, {
        'auth login status is 200': (res) => res.status === 200,
        'auth login returns access token': (res) => Boolean(res.json('data.accessToken')),
    });

    if (!ok) {
        fail(`auth login failed. status=${response.status} body=${response.body}`);
    }

    return response.json('data.accessToken');
}

function resolveUserProjectId(token) {
    if (USER_PROJECT_ID) {
        return String(USER_PROJECT_ID);
    }

    if (!token) {
        return '';
    }

    const response = http.get(`${BASE_URL}/auth/me`, {
        headers: authorizationHeaders(token),
        tags: {
            endpoint: 'auth_me',
            cache_mode: CACHE_MODE,
        },
    });
    observeResponse('auth_me', response);

    const ok = check(response, {
        'auth me status is 200': (res) => res.status === 200,
        'auth me returns user project id': (res) => Boolean(res.json('data.userProjectId')),
    });

    if (!ok) {
        fail(`auth me failed. status=${response.status} body=${response.body}`);
    }

    return String(response.json('data.userProjectId'));
}

function hasSuccessData(response) {
    try {
        return response.status === 200 && response.json('success') === true && response.json('data') !== undefined;
    } catch {
        return false;
    }
}

function queryString(params) {
    const pairs = [];
    for (const [key, value] of Object.entries(params)) {
        if (Array.isArray(value)) {
            for (const item of value) {
                pairs.push(`${encodeURIComponent(key)}=${encodeURIComponent(item)}`);
            }
        } else if (value !== undefined && value !== null && value !== '') {
            pairs.push(`${encodeURIComponent(key)}=${encodeURIComponent(value)}`);
        }
    }

    return pairs.join('&');
}

function endpointUrl(endpoint, userProjectId) {
    const role = TARGET_ROLES[__ITER % TARGET_ROLES.length];
    const careerLevel = TARGET_CAREER_LEVELS[__ITER % TARGET_CAREER_LEVELS.length];
    const commonParams = {
        targetRoles: [role],
        limit: LIMIT,
    };

    if (endpoint === 'gap_analysis') {
        return `${BASE_URL}/gap-analysis/projects/${encodeURIComponent(userProjectId)}?${queryString(commonParams)}`;
    }

    if (endpoint === 'jd_match') {
        return `${BASE_URL}/projects/${encodeURIComponent(userProjectId)}/job-matches?${queryString({
            ...commonParams,
            targetCareerLevel: careerLevel,
        })}`;
    }

    if (endpoint === 'recommendations_jobs') {
        return `${BASE_URL}/recommendations/jobs?${queryString({
            ...commonParams,
            userProjectId,
        })}`;
    }

    throw new Error(`Unsupported endpoint: ${endpoint}`);
}

export function setup() {
    const token = ACCESS_TOKEN || login();
    const userProjectId = resolveUserProjectId(token);

    if (!token || !userProjectId) {
        fail('ACCESS_TOKEN and USER_PROJECT_ID, or LOGIN_EMAIL and LOGIN_PASSWORD, are required for analysis cache stress test.');
    }

    return {
        token,
        userProjectId,
    };
}

export default function (context) {
    const endpoint = ENDPOINTS[__ITER % ENDPOINTS.length];
    const response = http.get(endpointUrl(endpoint, context.userProjectId), {
        headers: authorizationHeaders(context.token),
        tags: {
            endpoint,
            cache_mode: CACHE_MODE,
        },
    });
    observeResponse(endpoint, response);

    check(response, {
        [`${endpoint} status is 200`]: (res) => res.status === 200,
        [`${endpoint} returns success data`]: hasSuccessData,
    });

    if (SLEEP_SECONDS > 0) {
        sleep(SLEEP_SECONDS);
    }
}
