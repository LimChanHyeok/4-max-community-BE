import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'https://api.max-cm.cloud';
const API_PATH = __ENV.API_PATH || '/posts?size=5';
const ACCESS_TOKEN = __ENV.ACCESS_TOKEN || '';

if (!ACCESS_TOKEN) {
    throw new Error('ACCESS_TOKEN이 없습니다.');
}

export const options = {
    discardResponseBodies: true,

    scenarios: {
        hikari_load_test: {
            executor: 'constant-arrival-rate',

            // 초당 50번 실행
            rate: 50,
            timeUnit: '1s',

            // 3분 동안 유지
            duration: '3m',

            // 목표 RPS를 생성할 VU 확보
            preAllocatedVUs: 60,
            maxVUs: 100,
        },
    },

    thresholds: {
        // HTTP 실패는 없어야 함
        http_req_failed: ['rate<0.01'],

        // 전체 요청의 P95가 1초 미만
        http_req_duration: ['p(95)<1000'],

        // 상태 코드 검증은 전부 성공
        checks: ['rate==1'],

        // VU 부족으로 누락된 요청이 없어야 함
        dropped_iterations: ['count==0'],
    },
};

export default function () {
    const response = http.get(`${BASE_URL}${API_PATH}`, {
        headers: {
            Accept: 'application/json',
            Authorization: `Bearer ${ACCESS_TOKEN}`,
        },
        tags: {
            name: 'GET /posts?size=5',
        },
    });

    check(response, {
        '응답 상태가 200이다': (res) => res.status === 200,
    });
}