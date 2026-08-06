import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'https://api.max-cm.cloud';
const ACCESS_TOKEN = __ENV.ACCESS_TOKEN;
const TARGET_RPS = Number(__ENV.TARGET_RPS || 10);

if (!ACCESS_TOKEN) {
    throw new Error(
        'ACCESS_TOKEN 환경변수가 필요합니다. ' +
        '-e ACCESS_TOKEN="$ACCESS_TOKEN"으로 전달하세요.'
    );
}

if (!Number.isFinite(TARGET_RPS) || TARGET_RPS <= 0) {
    throw new Error('TARGET_RPS는 0보다 큰 숫자여야 합니다.');
}

export const options = {
    scenarios: {
        posts_create: {
            executor: 'ramping-arrival-rate',

            startRate: 1,
            timeUnit: '1s',

            preAllocatedVUs: 50,
            maxVUs: 100,

            stages: [
                // 30초 워밍업: 1 → 10 RPS
                {
                    target: TARGET_RPS,
                    duration: '30s',
                },

                // 10 RPS로 3분 유지
                {
                    target: TARGET_RPS,
                    duration: '3m',
                },

                // 15초 동안 종료
                {
                    target: 0,
                    duration: '15s',
                },
            ],

            gracefulStop: '10s',

            tags: {
                test_type: 'hikari-posts-create',
            },
        },
    },

    thresholds: {
        checks: ['rate==1'],
        http_req_failed: ['rate==0'],
        dropped_iterations: ['count==0'],

        'http_req_duration{endpoint:posts-create}': [
            'p(95)<1000',
        ],
    },
};

export default function () {
    const iteration = Number(exec.scenario.iterationInTest);
    const vuId = Number(exec.vu.idInTest);

    const url = `${BASE_URL}/posts`;

    /*
     * 이미지 없는 게시글 작성.
     *
     * iterationInTest를 사용해 제목과 본문을 고유하게 만든다.
     */
    const payload = JSON.stringify({
        title: `k6 게시글 ${iteration}`,
        content:
            `HikariCP 게시글 작성 Connection Usage 테스트입니다. ` +
            `iteration=${iteration}, vu=${vuId}`,
    });

    const response = http.post(url, payload, {
        headers: {
            Authorization: `Bearer ${ACCESS_TOKEN}`,
            Accept: 'application/json',
            'Content-Type': 'application/json',
        },

        timeout: '10s',
        responseType: 'text',

        tags: {
            endpoint: 'posts-create',
        },

        responseCallback: http.expectedStatuses(200, 201),
    });

    const succeeded = check(response, {
        'posts-create status is 200 or 201': (res) =>
            res.status === 200 || res.status === 201,
    });

    if (!succeeded) {
        console.error(
            `[posts-create failed] ` +
            `iteration=${iteration}, ` +
            `status=${response.status}, ` +
            `body=${response.body || ''}`
        );
    }
}