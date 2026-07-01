import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const ACCESS_TOKEN = __ENV.ACCESS_TOKEN || '';
const LOGIN_EMAIL = __ENV.LOGIN_EMAIL || '';
const LOGIN_PASSWORD = __ENV.LOGIN_PASSWORD || '';
const USER_PROJECT_ID = __ENV.USER_PROJECT_ID || '';
const USER_PROJECT_IDS = csv(__ENV.USER_PROJECT_IDS || USER_PROJECT_ID);
const ENDPOINTS = csv(__ENV.ENDPOINTS || 'gap_analysis,jd_match,recommendations_jobs');
const TARGET_ROLES = csv(__ENV.TARGET_ROLES || 'BACKEND,DATA_ENGINEER,DEVOPS');
const TARGET_CAREER_LEVELS = csv(__ENV.TARGET_CAREER_LEVELS || 'JUNIOR,MID,SENIOR');
const LIMIT_VALUES = csv(__ENV.LIMIT_VALUES || __ENV.LIMIT || '20').map((value) => Number(value));

const VUS = numberEnv('VUS', 200);
const DURATION = __ENV.DURATION || '10m';
const TARGET_RPS = numberEnv('TARGET_RPS', 0);
const PRE_ALLOCATED_VUS = numberEnv('PRE_ALLOCATED_VUS', Math.max(VUS, TARGET_RPS || VUS));
const MAX_VUS = numberEnv('MAX_VUS', Math.max(PRE_ALLOCATED_VUS, VUS));
const SLEEP_SECONDS = numberEnv('SLEEP_SECONDS', TARGET_RPS > 0 ? 0 : 1);
const P95_THRESHOLD_MS = numberEnv('P95_THRESHOLD_MS', 5000);
const FAIL_RATE_THRESHOLD = numberEnv('FAIL_RATE_THRESHOLD', 0.02);
const FAILURE_SAMPLE_BODY_LIMIT = numberEnv('FAILURE_SAMPLE_BODY_LIMIT', 500);
const CACHE_MODE = __ENV.CACHE_MODE || 'enabled';
const WORKLOAD_MODE = __ENV.WORKLOAD_MODE || 'hot';
const HOT_RATIO = numberEnv('HOT_RATIO', 0.7);
const HOT_VARIANTS = numberEnv('HOT_VARIANTS', 9);
const LONG_TAIL_VARIANTS = numberEnv('LONG_TAIL_VARIANTS', 100000);
const ROLE_COMBINATION_SIZE = numberEnv('ROLE_COMBINATION_SIZE', 1);

const httpStatusCodes = new Counter('jobflow_analysis_cache_http_status_codes');
const failedResponses = new Counter('jobflow_analysis_cache_failed_responses');
const loggedFailureSamples = {};

validateConfig();

export const options = buildOptions();

function csv(value) {
    return String(value || '')
        .split(',')
        .map((item) => item.trim())
        .filter((item) => item.length > 0);
}

function numberEnv(name, defaultValue) {
    const value = Number(__ENV[name] || defaultValue);
    if (!Number.isFinite(value)) {
        throw new Error(`${name} must be a finite number`);
    }
    return value;
}

function validateConfig() {
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

    if (!Number.isFinite(TARGET_RPS) || TARGET_RPS < 0) {
        throw new Error('TARGET_RPS must be zero or a positive number');
    }

    if (!Number.isFinite(PRE_ALLOCATED_VUS) || PRE_ALLOCATED_VUS < 1) {
        throw new Error('PRE_ALLOCATED_VUS must be a positive number');
    }

    if (!Number.isFinite(MAX_VUS) || MAX_VUS < PRE_ALLOCATED_VUS) {
        throw new Error('MAX_VUS must be greater than or equal to PRE_ALLOCATED_VUS');
    }

    if (LIMIT_VALUES.length === 0 || LIMIT_VALUES.some((limit) => !Number.isInteger(limit) || limit < 1 || limit > 50)) {
        throw new Error('LIMIT_VALUES must contain integers between 1 and 50');
    }

    if (!['enabled', 'disabled'].includes(CACHE_MODE)) {
        throw new Error('CACHE_MODE must be enabled or disabled');
    }

    if (!['hot', 'mixed', 'cold'].includes(WORKLOAD_MODE)) {
        throw new Error('WORKLOAD_MODE must be hot, mixed, or cold');
    }

    if (HOT_RATIO < 0 || HOT_RATIO > 1) {
        throw new Error('HOT_RATIO must be between 0 and 1');
    }

    if (!Number.isInteger(HOT_VARIANTS) || HOT_VARIANTS < 1) {
        throw new Error('HOT_VARIANTS must be a positive integer');
    }

    if (!Number.isInteger(LONG_TAIL_VARIANTS) || LONG_TAIL_VARIANTS < 1) {
        throw new Error('LONG_TAIL_VARIANTS must be a positive integer');
    }

    if (!Number.isInteger(ROLE_COMBINATION_SIZE) || ROLE_COMBINATION_SIZE < 1) {
        throw new Error('ROLE_COMBINATION_SIZE must be a positive integer');
    }
}

function buildOptions() {
    const commonOptions = {
        summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
        thresholds: {
            checks: [{ threshold: 'rate>0.99', abortOnFail: true, delayAbortEval: '10s' }],
            http_req_failed: [{ threshold: `rate<${FAIL_RATE_THRESHOLD}`, abortOnFail: true, delayAbortEval: '10s' }],
            'http_req_duration{endpoint:gap_analysis}': [{ threshold: `p(95)<${P95_THRESHOLD_MS}`, abortOnFail: false }],
            'http_req_duration{endpoint:jd_match}': [{ threshold: `p(95)<${P95_THRESHOLD_MS}`, abortOnFail: false }],
            'http_req_duration{endpoint:recommendations_jobs}': [{ threshold: `p(95)<${P95_THRESHOLD_MS}`, abortOnFail: false }],
        },
    };

    if (TARGET_RPS > 0) {
        return {
            ...commonOptions,
            scenarios: {
                analysis_cache: {
                    executor: 'constant-arrival-rate',
                    rate: TARGET_RPS,
                    timeUnit: '1s',
                    duration: DURATION,
                    preAllocatedVUs: PRE_ALLOCATED_VUS,
                    maxVUs: MAX_VUS,
                },
            },
        };
    }

    return {
        ...commonOptions,
        vus: VUS,
        duration: DURATION,
    };
}

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

function recordResponse(endpoint, cachePath, response) {
    const tags = {
        endpoint,
        status: String(response.status),
        cache_mode: CACHE_MODE,
        workload_mode: WORKLOAD_MODE,
        cache_path: cachePath,
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
    console.error(`[failure-sample] vu=${__VU} iter=${iterationIndex()} endpoint=${endpoint} status=${response.status} body=${truncateBody(response.body)}`);
}

function iterationIndex() {
    return exec.scenario && Number.isInteger(exec.scenario.iterationInTest)
        ? exec.scenario.iterationInTest
        : 0;
}

function observeResponse(endpoint, cachePath, response) {
    recordResponse(endpoint, cachePath, response);
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
                workload_mode: WORKLOAD_MODE,
                cache_path: 'auth',
            },
        },
    );
    observeResponse('auth_login', 'auth', response);

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
    if (USER_PROJECT_IDS.length > 0) {
        return String(USER_PROJECT_IDS[0]);
    }

    if (!token) {
        return '';
    }

    const response = http.get(`${BASE_URL}/auth/me`, {
        headers: authorizationHeaders(token),
        tags: {
            endpoint: 'auth_me',
            cache_mode: CACHE_MODE,
            workload_mode: WORKLOAD_MODE,
            cache_path: 'auth',
        },
    });
    observeResponse('auth_me', 'auth', response);

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

function cachePathForIteration() {
    if (WORKLOAD_MODE === 'hot') {
        return 'hot';
    }

    if (WORKLOAD_MODE === 'cold') {
        return 'long_tail';
    }

    const bucket = (iterationIndex() * 37) % 10000;
    return bucket < HOT_RATIO * 10000 ? 'hot' : 'long_tail';
}

function variantIndex(cachePath) {
    if (cachePath === 'hot') {
        return iterationIndex() % HOT_VARIANTS;
    }

    return HOT_VARIANTS + (iterationIndex() % LONG_TAIL_VARIANTS);
}

function longTailSequenceIndex(variant) {
    return Math.floor(Math.max(0, variant - HOT_VARIANTS) / ENDPOINTS.length);
}

function roleCombinationSpace() {
    return TARGET_ROLES.length;
}

function cacheParameterSpace(endpoint) {
    const careerLevelFactor = endpoint === 'jd_match' ? TARGET_CAREER_LEVELS.length : 1;
    return Math.max(1, roleCombinationSpace() * LIMIT_VALUES.length * careerLevelFactor);
}

function userProjectIdForVariant(endpoint, defaultUserProjectId, parameterVariant) {
    if (USER_PROJECT_IDS.length === 0) {
        return defaultUserProjectId;
    }

    const projectIndex = Math.floor(parameterVariant / cacheParameterSpace(endpoint)) % USER_PROJECT_IDS.length;
    return String(USER_PROJECT_IDS[projectIndex]);
}

function rolesForVariant(index) {
    const roleCount = TARGET_ROLES.length;
    const size = Math.min(ROLE_COMBINATION_SIZE, roleCount);
    const roles = [];

    for (let offset = 0; offset < size; offset += 1) {
        const role = TARGET_ROLES[(index + offset) % roleCount];
        if (!roles.includes(role)) {
            roles.push(role);
        }
    }

    return roles;
}

function paramsForVariant(endpoint, index) {
    const roleSpace = roleCombinationSpace();
    const careerLevelIndex = endpoint === 'jd_match' ? Math.floor(index / roleSpace) : index;
    const limitDivisor = endpoint === 'jd_match' ? roleSpace * TARGET_CAREER_LEVELS.length : roleSpace;

    return {
        roles: rolesForVariant(index),
        careerLevel: TARGET_CAREER_LEVELS[careerLevelIndex % TARGET_CAREER_LEVELS.length],
        limit: LIMIT_VALUES[Math.floor(index / limitDivisor) % LIMIT_VALUES.length],
    };
}

function endpointUrl(endpoint, userProjectId, variant) {
    const params = paramsForVariant(endpoint, variant);
    const commonParams = {
        targetRoles: params.roles,
        limit: params.limit,
    };

    if (endpoint === 'gap_analysis') {
        return `${BASE_URL}/gap-analysis/projects/${encodeURIComponent(userProjectId)}?${queryString(commonParams)}`;
    }

    if (endpoint === 'jd_match') {
        return `${BASE_URL}/projects/${encodeURIComponent(userProjectId)}/job-matches?${queryString({
            ...commonParams,
            targetCareerLevel: params.careerLevel,
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

    if (!token || (!userProjectId && USER_PROJECT_IDS.length === 0)) {
        fail('ACCESS_TOKEN and USER_PROJECT_ID/USER_PROJECT_IDS, or LOGIN_EMAIL and LOGIN_PASSWORD, are required for analysis cache stress test.');
    }

    return {
        token,
        userProjectId,
    };
}

export default function (context) {
    const endpoint = ENDPOINTS[iterationIndex() % ENDPOINTS.length];
    const cachePath = cachePathForIteration();
    const variant = variantIndex(cachePath);
    const parameterVariant = cachePath === 'long_tail' ? longTailSequenceIndex(variant) : variant;
    const userProjectId = userProjectIdForVariant(endpoint, context.userProjectId, parameterVariant);
    const response = http.get(endpointUrl(endpoint, userProjectId, parameterVariant), {
        headers: authorizationHeaders(context.token),
        tags: {
            name: endpoint,
            endpoint,
            cache_mode: CACHE_MODE,
            workload_mode: WORKLOAD_MODE,
            cache_path: cachePath,
        },
    });
    observeResponse(endpoint, cachePath, response);

    check(response, {
        [`${endpoint} status is 200`]: (res) => res.status === 200,
        [`${endpoint} returns success data`]: hasSuccessData,
    });

    if (SLEEP_SECONDS > 0) {
        sleep(SLEEP_SECONDS);
    }
}
