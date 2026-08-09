import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'https://api.max-cm.cloud';
const ACCESS_TOKEN = __ENV.ACCESS_TOKEN;
const TARGET_RPS = Number(__ENV.TARGET_RPS || 2);

/*
 * 이미지 파일은 이 스크립트와 같은 폴더에 둔다.
 *
 * 기본 구조:
 * images-upload/
 * ├── images-upload-connection-usage.js
 * └── test-image.jpg
 */
const IMAGE_PATH = __ENV.IMAGE_PATH || './test-image.jpg';
const IMAGE_FILENAME = __ENV.IMAGE_FILENAME || 'test-image.jpg';
const IMAGE_MIME_TYPE = __ENV.IMAGE_MIME_TYPE || 'image/jpeg';

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
 * open()은 k6 init 단계에서 실행해야 한다.
 * b 옵션은 이미지를 바이너리로 읽는다는 의미다.
 *
 * 각 요청에서 같은 이미지 바이트를 재사용한다.
 */
const IMAGE_BYTES = open(IMAGE_PATH, 'b');

export const options = {
    scenarios: {
        images_upload: {
            executor: 'ramping-arrival-rate',

            startRate: 1,
            timeUnit: '1s',

            preAllocatedVUs: 20,
            maxVUs: 50,

            stages: [
                // 30초 워밍업: 1 → 2 RPS
                {
                    target: TARGET_RPS,
                    duration: '30s',
                },

                // 2 RPS로 3분 유지
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

            gracefulStop: '15s',

            tags: {
                test_type: 'hikari-images-upload',
            },
        },
    },

    thresholds: {
        checks: ['rate==1'],
        http_req_failed: ['rate==0'],
        dropped_iterations: ['count==0'],

        'http_req_duration{endpoint:images-upload}': [
            'p(95)<2000',
        ],
    },

    /*
     * 상태 코드만 확인하므로 응답 본문은 보관하지 않는다.
     */
    discardResponseBodies: true,
};

export default function () {
    const iteration = Number(exec.scenario.iterationInTest);

    const url = `${BASE_URL}/images/posts`;

    /*
     * Controller의 @RequestPart("image")와 이름을 맞춘다.
     *
     * 실제 이미지 바이트는 같지만 요청마다 전달되는 원본 파일명은
     * 다르게 만들어 로그와 DB 행을 구분하기 쉽게 한다.
     */
    const multipartBody = {
        image: http.file(
            IMAGE_BYTES,
            `k6-${iteration}-${IMAGE_FILENAME}`,
            IMAGE_MIME_TYPE
        ),
    };

    const response = http.post(url, multipartBody, {
        headers: {
            Authorization: `Bearer ${ACCESS_TOKEN}`,
            Accept: 'application/json',

            /*
             * Content-Type은 직접 설정하지 않는다.
             * k6가 multipart/form-data boundary까지 자동 설정한다.
             */
        },

        timeout: '15s',

        tags: {
            endpoint: 'images-upload',
        },

        responseCallback: http.expectedStatuses(200),
    });

    const succeeded = check(response, {
        'images-upload status is 200': (res) =>
            res.status === 200,
    });

    if (!succeeded) {
        console.error(
            `[images-upload failed] ` +
            `iteration=${iteration}, ` +
            `status=${response.status}`
        );
    }
}