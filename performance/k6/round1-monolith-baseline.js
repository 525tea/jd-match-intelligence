import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8081/api').replace(/\/$/, '');
const KEYWORDS = (__ENV.KEYWORDS || 'performance,backend,data,devops,security')
    .split(',')
    .map((keyword) => keyword.trim())
    .filter((keyword) => keyword.length > 0);
const ENDPOINTS = (__ENV.ENDPOINTS || 'jobs_list,jobs_search,recommendations_jobs,gap_analysis')
    .split(',')
    .map((endpoint) => endpoint.trim())
    .filter((endpoint) => endpoint.length > 0);

const PAGE_SIZE = Number(__ENV.PAGE_SIZE || 20);
const SEARCH_LIMIT = Number(__ENV.SEARCH_LIMIT || 10);
const RECOMMENDATION_LIMIT = Number(__ENV.RECOMMENDATION_LIMIT || 10);
const GAP_LIMIT = Number(__ENV.GAP_LIMIT || 10);
const SLEEP_SECONDS = Number(__ENV.SLEEP_SECONDS || 1);
const EXPECT_PERF_FIXTURE = (__ENV.EXPECT_PERF_FIXTURE || 'true') !== 'false';
const REQUIRE_AUTH_ENDPOINTS = (__ENV.REQUIRE_AUTH_ENDPOINTS || 'true') !== 'false';
const FAILURE_SAMPLE_BODY_LIMIT = Number(__ENV.FAILURE_SAMPLE_BODY_LIMIT || 500);

const httpStatusCodes = new Counter('jobflow_http_status_codes');
const failedResponses = new Counter('jobflow_failed_responses');
const loggedFailureSamples = {};

function endpointEnabled(endpoint) {
    return ENDPOINTS.includes(endpoint);
}

export const options = {
    vus: Number(__ENV.VUS || 20),
    duration: __ENV.DURATION || '10m',
    summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
    thresholds: {
        checks: ['rate>0.95'],
        http_req_failed: ['rate<0.02'],
        'http_req_duration{endpoint:jobs_list}': ['p(95)<1500'],
        'http_req_duration{endpoint:jobs_search}': ['p(95)<2000'],
        'http_req_duration{endpoint:recommendations_jobs}': ['p(95)<3000'],
        'http_req_duration{endpoint:gap_analysis}': ['p(95)<3000'],
    },
};

function authorizationHeaders(token) {
    if (!token) {
        return {};
    }

    return {
        Authorization: `Bearer ${token}`,
    };
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
    const vu = typeof __VU === 'undefined' ? 'setup' : __VU;
    const iteration = typeof __ITER === 'undefined' ? 'setup' : __ITER;
    console.error(`[failure-sample] vu=${vu} iter=${iteration} endpoint=${endpoint} status=${response.status} body=${truncateBody(response.body)}`);
}

function observeResponse(endpoint, response) {
    recordResponse(endpoint, response);
    logFailureSample(endpoint, response);
}

function login() {
    if (!__ENV.LOGIN_EMAIL || !__ENV.LOGIN_PASSWORD) {
        return '';
    }

    const response = http.post(
        `${BASE_URL}/auth/login`,
        JSON.stringify({
            email: __ENV.LOGIN_EMAIL,
            password: __ENV.LOGIN_PASSWORD,
        }),
        {
            headers: {
                'Content-Type': 'application/json',
            },
            tags: {
                endpoint: 'auth_login',
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
    if (__ENV.USER_PROJECT_ID) {
        return String(__ENV.USER_PROJECT_ID);
    }

    if (!token) {
        return '';
    }

    const response = http.get(`${BASE_URL}/auth/me`, {
        headers: authorizationHeaders(token),
        tags: {
            endpoint: 'auth_me',
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

function hasPerformanceFixtureRows(response) {
    if (!EXPECT_PERF_FIXTURE) {
        return true;
    }

    try {
        const data = response.json('data');
        if (!Array.isArray(data)) {
            return false;
        }

        return data.some((row) => String(row.externalId || '').startsWith('perf-job-'));
    } catch {
        return false;
    }
}

function hasSuccessData(response) {
    try {
        return response.json('success') === true && response.json('data') !== undefined;
    } catch {
        return false;
    }
}

export function setup() {
    const token = __ENV.ACCESS_TOKEN || login();
    const userProjectId = resolveUserProjectId(token);

    if (REQUIRE_AUTH_ENDPOINTS && (!token || !userProjectId)) {
        fail('ACCESS_TOKEN and USER_PROJECT_ID, or LOGIN_EMAIL and LOGIN_PASSWORD, are required for Round 1 auth endpoints.');
    }

    return {
        token,
        userProjectId,
    };
}

export default function (context) {
    const keyword = KEYWORDS[__ITER % KEYWORDS.length];
    const page = __ITER % 5;
    const headers = authorizationHeaders(context.token);

    if (endpointEnabled('jobs_list')) {
        const listResponse = http.get(`${BASE_URL}/jobs?page=${page}&size=${PAGE_SIZE}`, {
            tags: {
                endpoint: 'jobs_list',
            },
        });
        observeResponse('jobs_list', listResponse);

        check(listResponse, {
            'jobs list status is 200': (res) => res.status === 200,
            'jobs list returns data array': (res) => {
                try {
                    return Array.isArray(res.json('data'));
                } catch {
                    return false;
                }
            },
            'jobs list uses performance fixture': hasPerformanceFixtureRows,
        });
    }

    if (endpointEnabled('jobs_search')) {
        const searchResponse = http.get(
            `${BASE_URL}/jobs/search?keyword=${encodeURIComponent(keyword)}&limit=${SEARCH_LIMIT}`,
            {
                tags: {
                    endpoint: 'jobs_search',
                    keyword,
                },
            },
        );
        observeResponse('jobs_search', searchResponse);

        check(searchResponse, {
            'jobs search status is 200': (res) => res.status === 200,
            'jobs search success is true': (res) => res.status === 200 && res.json('success') === true,
            'jobs search returns data array': (res) => {
                try {
                    return Array.isArray(res.json('data'));
                } catch {
                    return false;
                }
            },
            'jobs search uses performance fixture': hasPerformanceFixtureRows,
        });
    }

    if (context.token && context.userProjectId) {
        if (endpointEnabled('recommendations_jobs')) {
            const recommendationResponse = http.get(
                `${BASE_URL}/recommendations/jobs?userProjectId=${encodeURIComponent(context.userProjectId)}&limit=${RECOMMENDATION_LIMIT}`,
                {
                    headers,
                    tags: {
                        endpoint: 'recommendations_jobs',
                    },
                },
            );
            observeResponse('recommendations_jobs', recommendationResponse);

            check(recommendationResponse, {
                'recommendations jobs status is 200': (res) => res.status === 200,
                'recommendations jobs returns success data': hasSuccessData,
            });
        }

        if (endpointEnabled('gap_analysis')) {
            const gapAnalysisResponse = http.get(
                `${BASE_URL}/gap-analysis/projects/${encodeURIComponent(context.userProjectId)}?limit=${GAP_LIMIT}`,
                {
                    headers,
                    tags: {
                        endpoint: 'gap_analysis',
                    },
                },
            );
            observeResponse('gap_analysis', gapAnalysisResponse);

            check(gapAnalysisResponse, {
                'gap analysis status is 200': (res) => res.status === 200,
                'gap analysis returns success data': hasSuccessData,
            });
        }
    }

    sleep(SLEEP_SECONDS);
}
