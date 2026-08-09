import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'https://api.max-cm.cloud';
const API_PATH = __ENV.API_PATH || '/posts';

// 코드에는 토큰을 직접 작성하지 않는다.
// 실행할 때 -e ACCESS_TOKEN='...' 형태로 전달한다.
const ACCESS_TOKEN = __ENV.ACCESS_TOKEN || '';

export const options = {
    // 가상 사용자 1명이 10초 동안 요청을 반복한다.
    vus: 1,
    duration: '10s',

    thresholds: {
        // HTTP 요청 실패율이 1% 미만이어야 한다.
        http_req_failed: ['rate<0.01'],

        // 요청의 95%가 1초 이내에 응답해야 한다.
        http_req_duration: ['p(95)<1000'],

        // 아래 check가 모두 성공해야 한다.
        checks: ['rate==1'],
    },
};

export default function () {
    if (!ACCESS_TOKEN) {
        throw new Error(
            'ACCESS_TOKEN이 없습니다. 실행할 때 -e ACCESS_TOKEN 값을 전달하세요.',
        );
    }

    const url = `${BASE_URL}${API_PATH}`;

    const response = http.get(url, {
        headers: {
            Accept: 'application/json',
            Authorization: `Bearer ${ACCESS_TOKEN}`,
        },
        tags: {
            name: API_PATH.split('?')[0],
        },
    });

    const passed = check(response, {
        '응답 상태가 200이다': (res) => res.status === 200,
        '응답 시간이 1초 미만이다': (res) => res.timings.duration < 1000,
    });

    if (!passed) {
        console.error(
            `요청 실패: status=${response.status}, url=${url}, body=${response.body}`,
        );
    }

    sleep(1);
}