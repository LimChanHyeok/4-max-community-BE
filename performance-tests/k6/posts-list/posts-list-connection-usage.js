import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'https://api.max-cm.cloud';
const ACCESS_TOKEN = __ENV.ACCESS_TOKEN;

if (!ACCESS_TOKEN) {
    throw new Error(
        'ACCESS_TOKEN 환경변수가 필요합니다. ' +
        '예: k6 run -e ACCESS_TOKEN="$ACCESS_TOKEN" posts-list-connection-usage.js'
    );
}

export const options = {
    discardResponseBodies: true,

    scenarios: {
        posts_list_connection_usage: {
            executor: 'ramping-arrival-rate',

            startRate: 5,
            timeUnit: '1s',

            preAllocatedVUs: 50,
            maxVUs: 100,

            stages: [
                // 워밍업: 5 → 33 RPS
                { target: 33, duration: '30s' },

                // 측정 구간: 33 RPS 유지
                { target: 33, duration: '3m' },

                // 종료 구간
                { target: 0, duration: '15s' },
            ],

            gracefulStop: '30s',
        },
    },

    thresholds: {
        checks: ['rate==1'],
        http_req_failed: ['rate==0'],
        dropped_iterations: ['count==0'],
        http_req_duration: ['p(95)<1000'],
    },
};

export default function () {
    const response = http.get(`${BASE_URL}/posts?size=5`, {
        headers: {
            Authorization: `Bearer ${ACCESS_TOKEN}`,
            Accept: 'application/json',
        },
        tags: {
            name: 'GET /posts?size=5',
            api: 'posts-list',
        },
        timeout: '10s',
    });

    check(response, {
        '게시글 목록 조회 상태는 200': (res) => res.status === 200,
    });
}