import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'https://api.max-cm.cloud';
const ACCESS_TOKEN = __ENV.ACCESS_TOKEN;
const TARGET_RPS = Number(__ENV.TARGET_RPS || 10);

/*
 * 반드시 ACCESS_TOKEN 사용자가 작성한 게시글 ID를 입력한다.
 *
 * 예:
 * -e POST_IDS="101,102,103,104,105"
 */
const POST_IDS = (__ENV.POST_IDS || '')
    .split(',')
    .map((id) => id.trim())
    .filter(Boolean);

if (!ACCESS_TOKEN) {
    throw new Error(
        'ACCESS_TOKEN 환경변수가 필요합니다. ' +
        '-e ACCESS_TOKEN="$ACCESS_TOKEN"으로 전달하세요.'
    );
}

if (!Number.isFinite(TARGET_RPS) || TARGET_RPS <= 0) {
    throw new Error('TARGET_RPS는 0보다 큰 숫자여야 합니다.');
}

if (POST_IDS.length === 0) {
    throw new Error(
        '수정할 게시글 ID가 필요합니다. ' +
        '-e POST_IDS="101,102,103" 형식으로 전달하세요.'
    );
}

if (POST_IDS.length < 10) {
    console.warn(
        `[주의] POST_IDS가 ${POST_IDS.length}개뿐입니다. ` +
        '동일 행 경합을 줄이려면 20~30개 사용을 권장합니다.'
    );
}

export const options = {
    scenarios: {
        posts_update: {
            executor: 'ramping-arrival-rate',

            startRate: 1,
            timeUnit: '1s',

            preAllocatedVUs: 50,
            maxVUs: 100,

            stages: [
                // 30초 워밍업
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
                test_type: 'hikari-posts-update',
            },
        },
    },

    thresholds: {
        checks: ['rate==1'],
        http_req_failed: ['rate==0'],
        dropped_iterations: ['count==0'],

        'http_req_duration{endpoint:posts-update}': [
            'p(95)<1000',
        ],
    },

    discardResponseBodies: true,
};

export default function () {
    const iteration = Number(exec.scenario.iterationInTest);
    const vuId = Number(exec.vu.idInTest);

    const postId = POST_IDS[iteration % POST_IDS.length];
    const url = `${BASE_URL}/posts/${postId}`;

    /*
     * 매 요청마다 값이 달라야 실제 UPDATE SQL이 수행된다.
     */
    const payload = JSON.stringify({
        title: `k6 수정 게시글 ${postId}-${iteration}`,
        content:
            `HikariCP 게시글 수정 Connection Usage 테스트입니다. ` +
            `postId=${postId}, iteration=${iteration}, vu=${vuId}`,
    });

    const response = http.patch(url, payload, {
        headers: {
            Authorization: `Bearer ${ACCESS_TOKEN}`,
            Accept: 'application/json',
            'Content-Type': 'application/json',
        },

        timeout: '10s',

        tags: {
            endpoint: 'posts-update',
            post_id: String(postId),
        },

        responseCallback: http.expectedStatuses(200, 204),
    });

    const succeeded = check(response, {
        'posts-update status is 200 or 204': (res) =>
            res.status === 200 || res.status === 204,
    });

    if (!succeeded) {
        console.error(
            `[posts-update failed] ` +
            `postId=${postId}, ` +
            `iteration=${iteration}, ` +
            `status=${response.status}`
        );
    }
}