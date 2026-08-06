import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'https://api.max-cm.cloud';
const ACCESS_TOKEN = __ENV.ACCESS_TOKEN;

/*
 * 좋아요 등록 + 좋아요 취소를 합친 전체 HTTP 요청률.
 *
 * 한 iteration에서 HTTP 요청을 2번 보내므로:
 * 10 HTTP RPS ÷ 2 = 5 iterations/s
 */
const TARGET_RPS = Number(__ENV.TARGET_RPS || 10);
const ITERATION_RATE = TARGET_RPS / 2;

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

if (!Number.isInteger(ITERATION_RATE)) {
    throw new Error(
        `TARGET_RPS는 짝수로 설정하세요. 현재 TARGET_RPS=${TARGET_RPS}`
    );
}

const commonHeaders = {
    Authorization: `Bearer ${ACCESS_TOKEN}`,
    Accept: 'application/json',
};

/*
 * 테스트 시작 전 게시글의 좋아요 상태를 해제한다.
 *
 * 2xx:
 *   실제 좋아요 취소 성공
 *
 * 4xx:
 *   이미 좋아요가 해제된 상태 등 비즈니스 예외일 수 있으므로
 *   인증 오류가 아니라면 초기 상태로 인정하고 테스트를 진행한다.
 */
export function setup() {
    for (const postId of POST_IDS) {
        const url = `${BASE_URL}/posts/${postId}/likes`;

        const response = http.del(url, null, {
            headers: commonHeaders,
            timeout: '10s',
            responseType: 'text',

            tags: {
                endpoint: 'post-unlike-setup',
                phase: 'setup',
                post_id: String(postId),
            },

            /*
             * setup에서 발생 가능한 2xx와 4xx를
             * http_req_failed로 집계하지 않는다.
             */
            responseCallback: http.expectedStatuses(
                { min: 200, max: 299 },
                { min: 400, max: 499 }
            ),
        });

        const body = response.body || '';

        console.log(
            `[setup] postId=${postId}, ` +
            `status=${response.status}, ` +
            `body=${body}`
        );

        if (response.status === 401 || response.status === 403) {
            throw new Error(
                `인증 실패: ACCESS_TOKEN을 다시 발급하세요. ` +
                `postId=${postId}, status=${response.status}, body=${body}`
            );
        }

        if (response.status === 0 || response.status >= 500) {
            throw new Error(
                `좋아요 초기화 중 서버 또는 네트워크 오류가 발생했습니다. ` +
                `postId=${postId}, status=${response.status}, body=${body}`
            );
        }
    }
}

export const options = {
    scenarios: {
        post_likes: {
            executor: 'ramping-arrival-rate',

            /*
             * 한 iteration에서:
             * 1. POST 좋아요 등록
             * 2. DELETE 좋아요 취소
             *
             * TARGET_RPS=10이면 5 iterations/s가 된다.
             */
            startRate: 1,
            timeUnit: '1s',

            preAllocatedVUs: 20,
            maxVUs: 50,

            stages: [
                // 30초 워밍업
                {
                    target: ITERATION_RATE,
                    duration: '30s',
                },

                // 총 10 HTTP RPS로 3분 유지
                {
                    target: ITERATION_RATE,
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
                test_type: 'hikari-post-likes',
            },
        },
    },

    thresholds: {
        checks: ['rate==1'],
        http_req_failed: ['rate==0'],
        dropped_iterations: ['count==0'],

        'http_req_duration{endpoint:post-like}': [
            'p(95)<1000',
        ],

        'http_req_duration{endpoint:post-unlike}': [
            'p(95)<1000',
        ],
    },

    discardResponseBodies: true,
};

export default function () {
    const iteration = Number(exec.scenario.iterationInTest);
    const postId = POST_IDS[iteration % POST_IDS.length];

    const url = `${BASE_URL}/posts/${postId}/likes`;

    /*
     * 1. 좋아요 등록
     *
     * http.post()가 완료된 후 다음 코드가 실행되므로
     * DELETE보다 POST가 먼저 완료된다.
     */
    const likeResponse = http.post(url, null, {
        headers: commonHeaders,
        timeout: '10s',

        tags: {
            endpoint: 'post-like',
            action: 'like',
            post_id: String(postId),
        },
    });

    const likeSucceeded = check(likeResponse, {
        'post-like status is 2xx': (response) =>
            response.status >= 200 &&
            response.status < 300,
    });

    /*
     * 좋아요 등록에 실패한 상태에서 DELETE를 실행하면
     * 상태 오류가 연쇄적으로 발생하므로 해당 iteration을 종료한다.
     */
    if (!likeSucceeded) {
        console.error(
            `[like failed] postId=${postId}, ` +
            `status=${likeResponse.status}`
        );

        return;
    }

    /*
     * 2. 좋아요 취소
     */
    const unlikeResponse = http.del(url, null, {
        headers: commonHeaders,
        timeout: '10s',

        tags: {
            endpoint: 'post-unlike',
            action: 'unlike',
            post_id: String(postId),
        },
    });

    check(unlikeResponse, {
        'post-unlike status is 2xx': (response) =>
            response.status >= 200 &&
            response.status < 300,
    });
}