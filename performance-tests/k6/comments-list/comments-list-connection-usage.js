import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'https://api.max-cm.cloud';
const ACCESS_TOKEN = __ENV.ACCESS_TOKEN;

const TARGET_RPS = Number(__ENV.TARGET_RPS || 36);
const COMMENT_SIZE = Number(__ENV.COMMENT_SIZE || 10);

const POST_IDS = (__ENV.POST_IDS || '14,15,16')
    .split(',')
    .map((id) => id.trim())
    .filter(Boolean);

if (!ACCESS_TOKEN) {
    throw new Error(
        'ACCESS_TOKEN 환경변수가 필요합니다. -e ACCESS_TOKEN="$ACCESS_TOKEN" 형태로 전달하세요.'
    );
}

if (POST_IDS.length === 0) {
    throw new Error('유효한 POST_IDS가 없습니다.');
}

export const options = {
    scenarios: {
        comments_list: {
            executor: 'ramping-arrival-rate',

            // 워밍업 시작 시 초당 요청 수
            startRate: 1,

            // 초 단위로 요청률을 제어
            timeUnit: '1s',

            preAllocatedVUs: 100,
            maxVUs: 300,

            stages: [
                // 30초 동안 1 RPS → 36 RPS
                {
                    target: TARGET_RPS,
                    duration: '30s',
                },

                // 36 RPS로 3분 유지
                {
                    target: TARGET_RPS,
                    duration: '3m',
                },

                // 15초 동안 0 RPS로 감소
                {
                    target: 0,
                    duration: '15s',
                },
            ],

            gracefulStop: '5s',

            tags: {
                test_type: 'hikari-comments-list',
            },
        },
    },

    thresholds: {
        checks: ['rate==1'],
        http_req_failed: ['rate==0'],
        dropped_iterations: ['count==0'],

        // 기존 테스트 기준과 동일하게 안전한 상한으로 설정
        'http_req_duration{endpoint:comments-list}': ['p(95)<1000'],
    },

    // 응답 본문을 메모리에 유지하지 않음
    discardResponseBodies: true,
};

export default function () {
    /*
     * scenario.iterationInTest는 테스트 전체에서 증가하므로
     * arrival-rate executor에서도 14, 15, 16을 고르게 순환할 수 있다.
     */
    const iteration = Number(exec.scenario.iterationInTest);
    const postId = POST_IDS[iteration % POST_IDS.length];

    /*
     * 첫 번째 댓글 페이지 조회.
     * 커서 파라미터는 전달하지 않고 size만 전달한다.
     *
     * 실제 API가 size 기본값 10을 사용하고 쿼리 파라미터가 필요 없다면
     * ?size=${COMMENT_SIZE} 부분을 제거해도 된다.
     */
    const url =
        `${BASE_URL}/posts/${postId}/comments` +
        `?size=${COMMENT_SIZE}`;

    const response = http.get(url, {
        headers: {
            Authorization: `Bearer ${ACCESS_TOKEN}`,
            Accept: 'application/json',
        },

        tags: {
            endpoint: 'comments-list',
            post_id: String(postId),
        },

        timeout: '10s',
    });

    check(response, {
        'comments status is 200': (res) => res.status === 200,
    });
}