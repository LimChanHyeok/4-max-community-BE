import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'https://api.max-cm.cloud';
const ACCESS_TOKEN = __ENV.ACCESS_TOKEN;
const TARGET_RPS = Number(__ENV.TARGET_RPS || 10);

/*
 * 이 스크립트와 같은 디렉터리에 배치한다.
 *
 * 파일 형식:
 * 101
 * 102
 * 103
 *
 * 쉼표로 구분해도 된다:
 * 101,102,103
 */
const POST_IDS_FILE =
    __ENV.POST_IDS_FILE || './delete-post-ids.txt';

if (!ACCESS_TOKEN) {
    throw new Error(
        'ACCESS_TOKEN 환경변수가 필요합니다. ' +
        '-e ACCESS_TOKEN="$ACCESS_TOKEN"으로 전달하세요.'
    );
}

if (!Number.isFinite(TARGET_RPS) || TARGET_RPS <= 0) {
    throw new Error('TARGET_RPS는 0보다 큰 숫자여야 합니다.');
}

/*
 * open()은 k6 초기화 단계에서만 사용할 수 있다.
 */
const postIdsText = open(POST_IDS_FILE);

const POST_IDS = postIdsText
    .split(/[\r\n,]+/)
    .map((id) => id.trim())
    .filter(Boolean);

const UNIQUE_POST_IDS = [...new Set(POST_IDS)];

/*
 * 이번 테스트의 예상 요청 수
 *
 * 30초: 1 → TARGET_RPS
 * 180초: TARGET_RPS 유지
 * 15초: TARGET_RPS → 0
 *
 * TARGET_RPS=10이면 약 2,040건이다.
 */
const EXPECTED_REQUESTS = Math.ceil(
    ((1 + TARGET_RPS) / 2) * 30 +
    TARGET_RPS * 180 +
    (TARGET_RPS / 2) * 15
);

if (UNIQUE_POST_IDS.length < EXPECTED_REQUESTS) {
    throw new Error(
        `삭제할 게시글 ID가 부족합니다. ` +
        `필요=${EXPECTED_REQUESTS}개, ` +
        `현재=${UNIQUE_POST_IDS.length}개`
    );
}

console.log(
    `[init] targetRps=${TARGET_RPS}, ` +
    `expectedRequests=${EXPECTED_REQUESTS}, ` +
    `loadedPostIds=${UNIQUE_POST_IDS.length}`
);

export const options = {
    scenarios: {
        posts_delete: {
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
                test_type: 'hikari-posts-delete',
            },
        },
    },

    thresholds: {
        checks: ['rate==1'],
        http_req_failed: ['rate==0'],
        dropped_iterations: ['count==0'],

        'http_req_duration{endpoint:posts-delete}': [
            'p(95)<1000',
        ],
    },

    discardResponseBodies: true,
};

export default function () {
    /*
     * iterationInTest는 시나리오 전체에서 고유하게 증가하므로
     * 각 게시글 ID가 정확히 한 번만 사용된다.
     */
    const iteration = Number(exec.scenario.iterationInTest);
    const postId = UNIQUE_POST_IDS[iteration];

    if (!postId) {
        throw new Error(
            `삭제할 게시글 ID가 없습니다. iteration=${iteration}`
        );
    }

    const url = `${BASE_URL}/posts/${postId}`;

    const response = http.del(url, null, {
        headers: {
            Authorization: `Bearer ${ACCESS_TOKEN}`,
            Accept: 'application/json',
        },

        timeout: '10s',

        tags: {
            endpoint: 'posts-delete',
        },

        responseCallback: http.expectedStatuses(200, 204),
    });

    const succeeded = check(response, {
        'posts-delete status is 200 or 204': (res) =>
            res.status === 200 || res.status === 204,
    });

    if (!succeeded) {
        console.error(
            `[posts-delete failed] ` +
            `postId=${postId}, ` +
            `iteration=${iteration}, ` +
            `status=${response.status}`
        );
    }
}