import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'https://api.max-cm.cloud';
const ACCESS_TOKEN = __ENV.ACCESS_TOKEN || '';

const POSTS_PATH = __ENV.POSTS_PATH || '/posts?size=5';
const ME_PATH = __ENV.ME_PATH || '/users/me';

const START_RPS = Number(__ENV.START_RPS || 100);
const TARGET_RPS = Number(__ENV.TARGET_RPS || 800);

const RAMP_DURATION = __ENV.RAMP_DURATION || '1m';
const HOLD_DURATION = __ENV.HOLD_DURATION || '2m';

const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || 400);
const MAX_VUS = Number(__ENV.MAX_VUS || 800);

const POST_IDS = (__ENV.POST_IDS || '34,33,32,31')
    .split(',')
    .map((id) => id.trim())
    .filter(Boolean);

if (!ACCESS_TOKEN) {
    throw new Error(
        'ACCESS_TOKEN이 없습니다. read -s ACCESS_TOKEN으로 토큰을 입력하세요.',
    );
}

if (!Number.isInteger(TARGET_RPS) || TARGET_RPS <= 0) {
    throw new Error('TARGET_RPS는 1 이상의 정수여야 합니다.');
}

if (MAX_VUS < PRE_ALLOCATED_VUS) {
    throw new Error('MAX_VUS는 PRE_ALLOCATED_VUS 이상이어야 합니다.');
}

export const options = {
    discardResponseBodies: true,

    scenarios: {
        hikari_mixed_read_test: {
            executor: 'ramping-arrival-rate',

            startRate: START_RPS,
            timeUnit: '1s',

            stages: [
                // 100 RPS에서 800 RPS까지 서서히 증가
                {
                    target: TARGET_RPS,
                    duration: RAMP_DURATION,
                },

                // 800 RPS 유지
                {
                    target: TARGET_RPS,
                    duration: HOLD_DURATION,
                },

                // 요청을 서서히 종료
                {
                    target: 0,
                    duration: '15s',
                },
            ],

            preAllocatedVUs: PRE_ALLOCATED_VUS,
            maxVUs: MAX_VUS,

            gracefulStop: '30s',

            tags: {
                test_type: 'hikari-mixed-read-test',
                target_rps: String(TARGET_RPS),
            },
        },
    },

    thresholds: {
        // 네트워크 오류, Timeout, HTTP 오류 응답 비율 1% 미만
        http_req_failed: ['rate<0.01'],

        // 클라이언트 관점 전체 응답 P95 1초 미만
        http_req_duration: ['p(95)<1000'],

        // 200 응답 성공률 99% 이상
        // 기존 rate==1은 단 한 건만 실패해도 테스트 전체가 실패했음
        checks: ['rate>=0.99'],

        // 목표 RPS 검증이므로 시작하지 못한 요청은 허용하지 않음
        dropped_iterations: ['count==0'],
    },

    summaryTrendStats: [
        'avg',
        'min',
        'med',
        'max',
        'p(90)',
        'p(95)',
        'p(99)',
    ],
};

function createRequestOptions(name) {
    return {
        headers: {
            Accept: 'application/json',
            Authorization: `Bearer ${ACCESS_TOKEN}`,
        },

        tags: {
            name,
        },

        timeout: '10s',
    };
}

function getRandomPostId() {
    const index = Math.floor(Math.random() * POST_IDS.length);
    return POST_IDS[index];
}

export default function () {
    const random = Math.random();

    let response;
    let requestName;

    if (random < 0.6) {
        // 60%: 게시글 목록 조회
        requestName = 'GET /posts';

        response = http.get(
            `${BASE_URL}${POSTS_PATH}`,
            createRequestOptions(requestName),
        );
    } else if (random < 0.8) {
        // 20%: 사용자 정보 조회
        requestName = 'GET /users/me';

        response = http.get(
            `${BASE_URL}${ME_PATH}`,
            createRequestOptions(requestName),
        );
    } else {
        // 20%: 게시글 상세 조회
        const postId = getRandomPostId();

        requestName = 'GET /posts/{id}';

        response = http.get(
            `${BASE_URL}/posts/${encodeURIComponent(postId)}`,
            createRequestOptions(requestName),
        );
    }

    check(response, {
        [`${requestName} 응답 상태가 200이다`]:
            (res) => res.status === 200,
    });
}