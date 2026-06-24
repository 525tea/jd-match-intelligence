import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081/api';
const KEYWORDS = (__ENV.KEYWORDS || 'performance,backend,data,devops,security')
    .split(',')
    .map((keyword) => keyword.trim())
    .filter((keyword) => keyword.length > 0);

const PAGE_SIZE = __ENV.PAGE_SIZE || '20';
const SEARCH_LIMIT = __ENV.SEARCH_LIMIT || '10';
const EXPECT_PERF_FIXTURE = (__ENV.EXPECT_PERF_FIXTURE || 'true') !== 'false';

export const options = {
    vus: Number(__ENV.VUS || 10),
    duration: __ENV.DURATION || '1m',
    summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
    thresholds: {
        http_req_failed: ['rate<0.01'],
        'http_req_duration{endpoint:jobs_list}': ['p(95)<1000'],
        'http_req_duration{endpoint:jobs_search}': ['p(95)<1200'],
    },
};

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

export default function () {
    const keyword = KEYWORDS[__ITER % KEYWORDS.length];

    const listResponse = http.get(`${BASE_URL}/jobs?page=0&size=${PAGE_SIZE}`, {
        tags: {
            endpoint: 'jobs_list',
        },
    });

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

    const searchResponse = http.get(
        `${BASE_URL}/jobs/search?keyword=${encodeURIComponent(keyword)}&limit=${SEARCH_LIMIT}`,
        {
            tags: {
                endpoint: 'jobs_search',
                keyword,
            },
        },
    );

    check(searchResponse, {
        'jobs search status is 200': (res) => res.status === 200,
        'jobs search success is true': (res) => {
            try {
                return res.json('success') === true;
            } catch {
                return false;
            }
        },
        'jobs search returns data array': (res) => {
            try {
                return Array.isArray(res.json('data'));
            } catch {
                return false;
            }
        },
        'jobs search uses performance fixture': hasPerformanceFixtureRows,
    });

    sleep(Number(__ENV.SLEEP_SECONDS || 1));
}
