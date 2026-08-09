import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'https://api.max-cm.cloud';
const ACCESS_TOKEN = __ENV.ACCESS_TOKEN;
const TARGET_RPS = Number(__ENV.TARGET_RPS || 10);

const POST_IDS = (__ENV.POST_IDS || '14,15,16')
    .split(',')
    .map((id) => id.trim())
    .filter(Boolean);

if (!ACCESS_TOKEN) {
    throw new Error(
        'ACCESS_TOKEN 환경변수가 필요합니다. ' +
        '-e ACCESS_TOKEN="$ACCESS_TOKEN"으로 전달하세요.'
    );
}

if (POST_IDS.length === 0) {
    throw new Error('유효한 POST_IDS가 없습니다.');
}

if (!Number.isFinite(TARGET_RPS) || TARGET_RPS <= 0) {
    throw new Error('TARGET_RPS는 0보다 큰 숫자여야 합니다.');
}

export const options = {
    scenarios: {
        comments_create: {
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
                test_type: 'hikari-comments-create',
            },
        },
    },

    thresholds: {
        checks: ['rate==1'],
        http_req_failed: ['rate==0'],
        dropped_iterations: ['count==0'],

        'http_req_duration{endpoint:comments-create}': [
            'p(95)<1000',
        ],
    },

    /*
     * 응답 본문은 검증에 사용하지 않으므로 보관하지 않는다.
     */
    discardResponseBodies: true,
};

export default function () {
    const iteration = Number(exec.scenario.iterationInTest);
    const postId = POST_IDS[iteration % POST_IDS.length];

    const url = `${BASE_URL}/posts/${postId}/comments`;

    /*
     * iteration 번호와 VU 번호를 포함해 각 댓글 내용을 고유하게 만든다.
     */
    const payload = JSON.stringify({
        content:
            `k6 댓글 작성 테스트 ` +
            `post=${postId} ` +
            `iteration=${iteration} ` +
            `vu=${exec.vu.idInTest}`,
    });

    const response = http.post(url, payload, {
        headers: {
            Authorization: `Bearer ${ACCESS_TOKEN}`,
            Accept: 'application/json',
            'Content-Type': 'application/json',
        },

        timeout: '10s',

        tags: {
            endpoint: 'comments-create',
            post_id: String(postId),
        },

        /*
         * Backend 구현에 따라 댓글 작성 성공이
         * 200 또는 201일 수 있으므로 둘 다 정상으로 지정한다.
         */
        responseCallback: http.expectedStatuses(200, 201),
    });

    const succeeded = check(response, {
        'comments-create status is 200 or 201': (res) =>
            res.status === 200 || res.status === 201,
    });

    if (!succeeded) {
        console.error(
            `[comments-create failed] ` +
            `postId=${postId}, status=${response.status}`
        );
    }
}