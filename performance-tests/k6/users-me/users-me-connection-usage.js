import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'https://api.max-cm.cloud';
const ACCESS_TOKEN = __ENV.ACCESS_TOKEN;

if (!ACCESS_TOKEN) {
    throw new Error(
        'ACCESS_TOKEN 환경변수가 필요합니다. ' +
        '예: k6 run -e ACCESS_TOKEN="$ACCESS_TOKEN" users-me-connection-usage.js'
    );
}

export const options = {
    discardResponseBodies: true,

    scenarios: {
        users_me_connection_usage: {
            executor: 'ramping-arrival-rate',

            // 워밍업 시작 처리량
            startRate: 10,
            timeUnit: '1s',

            // 응답시간이 길어져도 VU 부족으로 iteration이 버려지지 않도록 여유 확보
            preAllocatedVUs: 100,
            maxVUs: 200,

            stages: [
                // 30초 동안 10 → 70 RPS
                { target: 70, duration: '30s' },

                // 측정 구간: 70 RPS 3분 유지
                { target: 70, duration: '3m' },

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
    const response = http.get(`${BASE_URL}/users/me`, {
        headers: {
            Authorization: `Bearer ${ACCESS_TOKEN}`,
            Accept: 'application/json',
        },
        tags: {
            name: 'GET /users/me',
            api: 'users-me',
        },
        timeout: '10s',
    });

    check(response, {
        '/users/me status is 200': (res) => res.status === 200,
    });
}