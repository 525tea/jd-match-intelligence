import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const KEYWORDS = (__ENV.KEYWORDS || '백엔드,Spring,k8s,Kubernetes')
    .split(',')
    .map((keyword) => keyword.trim())
    .filter((keyword) => keyword.length > 0);
const LIMIT = __ENV.LIMIT || '10';

export const options = {
    vus: Number(__ENV.VUS || 5),
    duration: __ENV.DURATION || '30s',
    summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
    thresholds: {
        http_req_failed: ['rate<0.01'],
    },
};

export default function () {
    const keyword = KEYWORDS[__ITER % KEYWORDS.length];

    const response = http.get(`${BASE_URL}/jobs/search?keyword=${encodeURIComponent(keyword)}&limit=${LIMIT}`, {
        tags: {
            endpoint: 'jobs_search',
            keyword,
        },
    });

    check(response, {
        'status is 200': (res) => res.status === 200,
        'response is successful': (res) => {
            try {
                return res.json('success') === true;
            } catch {
                return false;
            }
        },
        'data is array': (res) => {
            try {
                return Array.isArray(res.json('data'));
            } catch {
                return false;
            }
        },
    });

    sleep(1);
}
