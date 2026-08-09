import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'https://api.max-cm.cloud';
const ACCESS_TOKEN = __ENV.ACCESS_TOKEN;

// 필요하면 실행 시 -e POST_IDS="34,33,32,31" 형태로 변경 가능
const POST_IDS = (__ENV.POST_IDS || '16,15,14')
    .split(',')
    .map((id) => id.trim())
    .filter((id) => id.length > 0);

if (!ACCESS_TOKEN) {
    throw new Error(
        'ACCESS_TOKEN 환경변수가 필요합니다. ' +
        '예: k6 run -e ACCESS_TOKEN="$ACCESS_TOKEN" posts-detail-connection-usage.js'
    );
}

if (POST_IDS.length === 0) {
    throw new Error('POST_IDS에 유효한 게시글 ID가 하나 이상 필요합니다.');
}

export const options = {
    discardResponseBodies: true,

    scenarios: {
        posts_detail_connection_usage: {
            executor: 'ramping-arrival-rate',

            startRate: 5,
            timeUnit: '1s',

            preAllocatedVUs: 50,
            maxVUs: 100,

            stages: [
                // 워밍업: 5 → 36 RPS
                { target: 36, duration: '30s' },

                // 측정 구간: 36 RPS 3분 유지
                { target: 36, duration: '3m' },

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
    // 전체 iteration 번호를 기준으로 게시글 ID를 순환
    const index = Number(exec.scenario.iterationInTest) % POST_IDS.length;
    const postId = POST_IDS[index];

    const response = http.get(`${BASE_URL}/posts/${postId}`, {
        headers: {
            Authorization: `Bearer ${ACCESS_TOKEN}`,
            Accept: 'application/json',
        },
        tags: {
            name: 'GET /posts/{postId}',
            api: 'posts-detail',
        },
        timeout: '10s',
    });

    check(response, {
        '게시글 상세 조회 상태는 200': (res) => res.status === 200,
    });
}