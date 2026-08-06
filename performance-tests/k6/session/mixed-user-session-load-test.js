import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Rate, Trend } from 'k6/metrics';

const sessionSuccess = new Rate('session_success');
const sessionDuration = new Trend('session_duration', true);

function required(name) {
    const value = __ENV[name];

    if (!value) {
        throw new Error(`필수 환경변수가 없습니다: ${name}`);
    }

    return value;
}

function positiveInt(name, fallback) {
    const value = Number(__ENV[name] || fallback);

    if (!Number.isInteger(value) || value <= 0) {
        throw new Error(
            `${name}은 양의 정수여야 합니다: ${value}`
        );
    }

    return value;
}

function positiveNumber(name, fallback) {
    const value = Number(__ENV[name] || fallback);

    if (!Number.isFinite(value) || value <= 0) {
        throw new Error(
            `${name}은 0보다 큰 숫자여야 합니다: ${value}`
        );
    }

    return value;
}

const BASE_URL =
    required('BASE_URL').replace(/\/$/, '');

const TEST_PASSWORD =
    required('TEST_PASSWORD');

const TEST_MODE =
    (__ENV.TEST_MODE || 'smoke').toLowerCase();

const USER_COUNT =
    positiveInt('USER_COUNT', 1500);

const USER_PREFIX =
    __ENV.USER_PREFIX || 'perf-session';

const EMAIL_DOMAIN =
    __ENV.EMAIL_DOMAIN || 'example.com';

/*
 * Smoke와 Full이 서로 다른 계정을 사용한다.
 *
 * smoke:
 *   perf-session-1401 ~ 1480
 *
 * full:
 *   perf-session-0001 ~ 최대 1400
 */
const DEFAULT_ACCOUNT_OFFSET =
    TEST_MODE === 'smoke' ? 1400 : 0;

const ACCOUNT_OFFSET =
    Number(
        __ENV.ACCOUNT_OFFSET ??
        DEFAULT_ACCOUNT_OFFSET
    );

if (
    !Number.isInteger(ACCOUNT_OFFSET) ||
    ACCOUNT_OFFSET < 0
) {
    throw new Error(
        `ACCOUNT_OFFSET은 0 이상의 정수여야 합니다: ` +
        `${ACCOUNT_OFFSET}`
    );
}

const POST_IDS =
    required('POST_IDS')
        .split(',')
        .map((id) => id.trim())
        .filter(Boolean);

const THINK_TIME_SCALE =
    positiveNumber('THINK_TIME_SCALE', 1);

const RUN_ID =
    __ENV.RUN_ID || `pool5-${TEST_MODE}`;

const IMAGE_PATH =
    __ENV.IMAGE_PATH ||
    './test-image.jpg';

const IMAGE_FILENAME =
    __ENV.IMAGE_FILENAME ||
    'test-image.jpg';

const IMAGE_MIME_TYPE =
    __ENV.IMAGE_MIME_TYPE ||
    'image/jpeg';

/*
 * 게시글 작성 DTO의 이미지 필드명.
 *
 * imageIds:
 *   { imageIds: [1] }
 *
 * imageId:
 *   { imageId: 1 }
 *
 * none:
 *   게시글 생성 요청에 이미지 ID를 포함하지 않음
 */
const POST_IMAGE_FIELD =
    __ENV.POST_IMAGE_FIELD ||
    'imageIds';

if (
    !['smoke', 'full'].includes(TEST_MODE)
) {
    throw new Error(
        `TEST_MODE는 smoke 또는 full이어야 합니다: ` +
        `${TEST_MODE}`
    );
}

if (POST_IDS.length === 0) {
    throw new Error(
        'POST_IDS가 비어 있습니다.'
    );
}

if (
    ![
        'imageIds',
        'imageId',
        'none',
    ].includes(POST_IMAGE_FIELD)
) {
    throw new Error(
        `POST_IMAGE_FIELD 값이 올바르지 않습니다: ` +
        `${POST_IMAGE_FIELD}`
    );
}

/*
 * open()은 k6 init context에서 실행된다.
 */
const IMAGE_BYTES =
    open(IMAGE_PATH, 'b');

/*
 * =========================================================
 * 부하 프로파일
 *
 * rate와 target 단위는 sessions/min이다.
 * =========================================================
 */

const PROFILES = {
    /*
     * 약 2분짜리 사전 검증
     */
    smoke: {
        browse: {
            startRate: 8,
            preAllocatedVUs: 25,
            maxVUs: 50,

            stages: [
                {
                    duration: '30s',
                    target: 40,
                },
                {
                    duration: '1m',
                    target: 40,
                },
                {
                    duration: '30s',
                    target: 0,
                },
            ],
        },

        reaction: {
            startRate: 2,
            preAllocatedVUs: 10,
            maxVUs: 20,

            stages: [
                {
                    duration: '30s',
                    target: 9,
                },
                {
                    duration: '1m',
                    target: 9,
                },
                {
                    duration: '30s',
                    target: 0,
                },
            ],
        },

        content: {
            startRate: 1,
            preAllocatedVUs: 5,
            maxVUs: 10,

            stages: [
                {
                    duration: '30s',
                    target: 1,
                },
                {
                    duration: '1m',
                    target: 1,
                },
                {
                    duration: '30s',
                    target: 0,
                },
            ],
        },
    },

    /*
     * 15분 본 테스트
     *
     * 0~2분:
     *   저부하 워밍업
     *
     * 2~7분:
     *   지속 피크까지 증가
     *
     * 7~9분:
     *   1,000 sessions/min 유지
     *
     * 9~10분:
     *   순간 피크까지 증가
     *
     * 10~12분:
     *   2,000 sessions/min 유지
     *
     * 12~13분:
     *   지속 피크로 감소
     *
     * 13~14분:
     *   지속 피크 유지
     *
     * 14~15분:
     *   종료
     */
    full: {
        browse: {
            startRate: 80,
            preAllocatedVUs: 800,
            maxVUs: 1000,

            stages: [
                {
                    duration: '2m',
                    target: 240,
                },
                {
                    duration: '5m',
                    target: 800,
                },
                {
                    duration: '2m',
                    target: 800,
                },
                {
                    duration: '1m',
                    target: 1600,
                },
                {
                    duration: '2m',
                    target: 1600,
                },
                {
                    duration: '1m',
                    target: 800,
                },
                {
                    duration: '1m',
                    target: 800,
                },
                {
                    duration: '1m',
                    target: 0,
                },
            ],
        },

        reaction: {
            startRate: 18,
            preAllocatedVUs: 180,
            maxVUs: 300,

            stages: [
                {
                    duration: '2m',
                    target: 54,
                },
                {
                    duration: '5m',
                    target: 180,
                },
                {
                    duration: '2m',
                    target: 180,
                },
                {
                    duration: '1m',
                    target: 360,
                },
                {
                    duration: '2m',
                    target: 360,
                },
                {
                    duration: '1m',
                    target: 180,
                },
                {
                    duration: '1m',
                    target: 180,
                },
                {
                    duration: '1m',
                    target: 0,
                },
            ],
        },

        content: {
            startRate: 2,
            preAllocatedVUs: 20,
            maxVUs: 100,

            stages: [
                {
                    duration: '2m',
                    target: 6,
                },
                {
                    duration: '5m',
                    target: 20,
                },
                {
                    duration: '2m',
                    target: 20,
                },
                {
                    duration: '1m',
                    target: 40,
                },
                {
                    duration: '2m',
                    target: 40,
                },
                {
                    duration: '1m',
                    target: 20,
                },
                {
                    duration: '1m',
                    target: 20,
                },
                {
                    duration: '1m',
                    target: 0,
                },
            ],
        },
    },
};

const PROFILE =
    PROFILES[TEST_MODE];

const REQUIRED_USERS =
    PROFILE.browse.maxVUs +
    PROFILE.reaction.maxVUs +
    PROFILE.content.maxVUs;

if (
    ACCOUNT_OFFSET + REQUIRED_USERS >
    USER_COUNT
) {
    throw new Error(
        `계정 범위 부족: ` +
        `offset=${ACCOUNT_OFFSET}, ` +
        `필요 VU=${REQUIRED_USERS}, ` +
        `USER_COUNT=${USER_COUNT}`
    );
}

function scenario(
    execName,
    config,
    userType
) {
    return {
        executor:
            'ramping-arrival-rate',

        exec:
            execName,

        startRate:
            config.startRate,

        timeUnit:
            '1m',

        preAllocatedVUs:
            config.preAllocatedVUs,

        maxVUs:
            config.maxVUs,

        stages:
            config.stages,

        gracefulStop:
            '30s',

        tags: {
            test_type:
                'mixed-user-session',

            test_mode:
                TEST_MODE,

            user_type:
                userType,
        },
    };
}

export const options = {
    /*
     * 같은 VU가 iteration 사이에도
     * Refresh Token Cookie를 유지한다.
     */
    noCookiesReset: true,

    /*
     * 응답이 필요 없는 API의 본문은 폐기한다.
     */
    discardResponseBodies: true,

    scenarios: {
        browse_sessions: scenario(
            'browseSession',
            PROFILE.browse,
            'browse'
        ),

        reaction_sessions: scenario(
            'reactionSession',
            PROFILE.reaction,
            'reaction'
        ),

        content_sessions: scenario(
            'contentSession',
            PROFILE.content,
            'content'
        ),
    },

    thresholds: {
        checks: [
            'rate==1',
        ],

        session_success: [
            'rate==1',
        ],

        http_req_failed: [
            'rate==0',
        ],

        dropped_iterations: [
            'count==0',
        ],
    },

    summaryTrendStats: [
        'avg',
        'med',
        'p(90)',
        'p(95)',
        'p(99)',
        'max',
    ],
};

/*
 * =========================================================
 * VU별 상태
 * =========================================================
 */

let accessToken = null;
let loggedIn = false;
let failureLogs = 0;

/*
 * 같은 테스트 실행 안에서
 * VU별 게시글 좋아요 상태를 추적한다.
 */
const likedPosts = {};

/*
 * =========================================================
 * 공통 함수
 * =========================================================
 */

function pad4(value) {
    return String(value)
        .padStart(4, '0');
}

function currentUser() {
    const vuId =
        Number(exec.vu.idInTest);

    const accountNumber =
        ACCOUNT_OFFSET + vuId;

    if (
        accountNumber < 1 ||
        accountNumber > USER_COUNT
    ) {
        throw new Error(
            `VU 계정 범위 초과: ` +
            `vu=${vuId}, ` +
            `account=${accountNumber}, ` +
            `USER_COUNT=${USER_COUNT}`
        );
    }

    return {
        email:
            `${USER_PREFIX}-` +
            `${pad4(accountNumber)}` +
            `@${EMAIL_DOMAIN}`,

        password:
            TEST_PASSWORD,
    };
}

function random(min, max) {
    return (
        min +
        Math.random() * (max - min)
    );
}

function think(min, max) {
    sleep(
        random(min, max) *
        THINK_TIME_SCALE
    );
}

function randomPostId(offset = 0) {
    const iteration =
        Number(
            exec.scenario.iterationInTest
        );

    const vu =
        Number(exec.vu.idInTest);

    const index =
        (
            iteration +
            vu +
            offset
        ) % POST_IDS.length;

    return POST_IDS[index];
}

function parseJson(response) {
    if (!response?.body) {
        return null;
    }

    try {
        return JSON.parse(
            response.body
        );
    } catch (_) {
        return null;
    }
}

function tokenFrom(response) {
    const body =
        parseJson(response);

    const header =
        response?.headers?.Authorization ||
        response?.headers?.authorization;

    const token =
        header ||
        body?.data?.access_token ||
        body?.data?.accessToken ||
        body?.access_token ||
        body?.accessToken;

    if (
        typeof token !== 'string'
    ) {
        return null;
    }

    return token
        .replace(
            /^Bearer\s+/i,
            ''
        )
        .trim();
}

function entityIdFrom(
    response,
    keys
) {
    const body =
        parseJson(response);

    const targets = [
        body,
        body?.data,
        body?.result,
        body?.data?.result,
    ].filter(Boolean);

    for (const target of targets) {
        for (const key of keys) {
            const value =
                target[key];

            if (
                value !== undefined &&
                value !== null
            ) {
                return String(value);
            }
        }
    }

    return null;
}

function authHeaders(
    json = false
) {
    const headers = {
        Accept:
            'application/json',

        Authorization:
            `Bearer ${accessToken}`,
    };

    if (json) {
        headers['Content-Type'] =
            'application/json';
    }

    return headers;
}

function redact(body) {
    return String(body || '')
        .replace(
            /eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g,
            '[JWT]'
        )
        .slice(0, 400);
}

function logFailure(
    name,
    response,
    showBody = false
) {
    /*
     * 각 VU에서 최대 5개 오류만 출력한다.
     */
    if (failureLogs >= 5) {
        return;
    }

    failureLogs += 1;

    const body =
        showBody
            ? `, body=${redact(response?.body)}`
            : '';

    console.error(
        `[${name}] ` +
        `vu=${exec.vu.idInTest}, ` +
        `scenario=${exec.scenario.name}, ` +
        `status=${response?.status}` +
        `${body}`
    );
}

function expect2xx(
    name,
    response,
    showBody = false
) {
    const ok =
        check(
            response,
            {
                [`${name} 2xx`]:
                    (result) =>
                        result.status >= 200 &&
                        result.status < 300,
            }
        );

    if (!ok) {
        logFailure(
            name,
            response,
            showBody
        );

        if (
            response?.status === 401 ||
            response?.status === 403
        ) {
            resetAuth();
        }
    }

    return ok;
}

function resetAuth() {
    accessToken = null;
    loggedIn = false;
}

function finish(
    startedAt,
    succeeded
) {
    sessionSuccess.add(
        succeeded
    );

    sessionDuration.add(
        Date.now() - startedAt
    );
}

/*
 * API 하나라도 실패하면
 * 현재 세션을 즉시 실패 처리하고 종료한다.
 */
function requireAction(
    startedAt,
    action
) {
    if (action()) {
        return true;
    }

    finish(
        startedAt,
        false
    );

    return false;
}

/*
 * =========================================================
 * 인증
 * =========================================================
 */

function login() {
    if (
        loggedIn &&
        accessToken
    ) {
        return true;
    }

    const user =
        currentUser();

    const response =
        http.post(
            `${BASE_URL}/auth`,

            JSON.stringify({
                email:
                    user.email,

                password:
                    user.password,
            }),

            {
                headers: {
                    Accept:
                        'application/json',

                    'Content-Type':
                        'application/json',
                },

                timeout:
                    '10s',

                responseType:
                    'text',

                responseCallback:
                    http.expectedStatuses(
                        200,
                        201
                    ),

                tags: {
                    endpoint:
                        'auth-login',

                    phase:
                        'initialization',
                },
            }
        );

    const statusOk =
        expect2xx(
            'auth-login',
            response
        );

    const token =
        tokenFrom(response);

    const tokenOk =
        check(
            token,
            {
                'auth-login token exists':
                    (value) =>
                        Boolean(value),
            }
        );

    if (
        !statusOk ||
        !tokenOk
    ) {
        /*
         * JWT가 포함될 수 있으므로
         * 로그인 응답 본문은 출력하지 않는다.
         */
        logFailure(
            'auth-login-token',
            response,
            false
        );

        resetAuth();

        return false;
    }

    accessToken =
        token;

    loggedIn =
        true;

    /*
     * Refresh Token은 로그인 응답의
     * Set-Cookie를 통해 VU Cookie Jar에 저장된다.
     */
    return true;
}

function reissue() {
    const response =
        http.post(
            `${BASE_URL}/auth/reissue`,

            null,

            {
                headers:
                    authHeaders(),

                timeout:
                    '10s',

                responseType:
                    'text',

                responseCallback:
                    http.expectedStatuses(
                        200
                    ),

                tags: {
                    endpoint:
                        'auth-reissue',

                    phase:
                        'business',
                },
            }
        );

    const statusOk =
        expect2xx(
            'auth-reissue',
            response
        );

    const token =
        tokenFrom(response);

    const tokenOk =
        check(
            token,
            {
                'auth-reissue token exists':
                    (value) =>
                        Boolean(value),
            }
        );

    if (
        !statusOk ||
        !tokenOk
    ) {
        logFailure(
            'auth-reissue-token',
            response,
            false
        );

        /*
         * RTR 체인이 깨진 경우
         * 다음 iteration에서 다시 로그인한다.
         */
        resetAuth();

        return false;
    }

    accessToken =
        token;

    return true;
}

function getMyInfo() {
    const response =
        http.get(
            `${BASE_URL}/users/me`,

            {
                headers:
                    authHeaders(),

                timeout:
                    '10s',

                responseType:
                    'none',

                responseCallback:
                    http.expectedStatuses(
                        200
                    ),

                tags: {
                    endpoint:
                        'users-me',

                    phase:
                        'business',
                },
            }
        );

    return expect2xx(
        'users-me',
        response
    );
}

/*
 * 화면 진입 시 수행되는 자동 호출.
 *
 * 로그인은 VU 최초 1회
 * → Access Token 재발급
 * → 사용자 정보 조회
 */
function enterScreen() {
    if (!login()) {
        return false;
    }

    if (!reissue()) {
        return false;
    }

    think(
        0.05,
        0.15
    );

    return getMyInfo();
}

function requireScreen(
    startedAt
) {
    return requireAction(
        startedAt,
        enterScreen
    );
}

/*
 * =========================================================
 * 조회 API
 * =========================================================
 */

function getPostList() {
    const response =
        http.get(
            `${BASE_URL}/posts?size=5`,

            {
                headers:
                    authHeaders(),

                timeout:
                    '10s',

                responseType:
                    'none',

                responseCallback:
                    http.expectedStatuses(
                        200
                    ),

                tags: {
                    endpoint:
                        'posts-list',

                    phase:
                        'business',
                },
            }
        );

    return expect2xx(
        'posts-list',
        response
    );
}

function getPostDetail(
    postId
) {
    const response =
        http.get(
            `${BASE_URL}/posts/${postId}`,

            {
                headers:
                    authHeaders(),

                timeout:
                    '10s',

                responseType:
                    'none',

                responseCallback:
                    http.expectedStatuses(
                        200
                    ),

                tags: {
                    endpoint:
                        'posts-detail',

                    phase:
                        'business',
                },
            }
        );

    return expect2xx(
        'posts-detail',
        response
    );
}

function getComments(
    postId
) {
    const response =
        http.get(
            `${BASE_URL}/posts/${postId}/comments?size=10`,

            {
                headers:
                    authHeaders(),

                timeout:
                    '10s',

                responseType:
                    'none',

                responseCallback:
                    http.expectedStatuses(
                        200
                    ),

                tags: {
                    endpoint:
                        'comments-list',

                    phase:
                        'business',
                },
            }
        );

    return expect2xx(
        'comments-list',
        response
    );
}

/*
 * =========================================================
 * 반응 API
 * =========================================================
 */

function toggleLike(postId) {
    const url =
        `${BASE_URL}/posts/${postId}/likes`;

    const currentlyLiked =
        likedPosts[postId] === true;

    /*
     * 현재 k6 실행 안에서 이미 좋아요 상태로 알고 있다면
     * DELETE를 호출한다.
     */
    if (currentlyLiked) {
        const unlikeResponse =
            http.del(
                url,
                null,
                {
                    headers:
                        authHeaders(),

                    timeout:
                        '10s',

                    responseType:
                        'none',

                    responseCallback:
                        http.expectedStatuses(
                            200,
                            204
                        ),

                    tags: {
                        endpoint:
                            'post-unlike',

                        phase:
                            'business',
                    },
                }
            );

        const unlikeSucceeded =
            expect2xx(
                'post-unlike',
                unlikeResponse
            );

        if (unlikeSucceeded) {
            likedPosts[postId] =
                false;
        }

        return unlikeSucceeded;
    }

    /*
     * 로컬 상태에서는 좋아요가 없다고 판단한 경우.
     *
     * 이전 테스트에서 DB에 좋아요가 남아 있을 수 있으므로
     * 409도 상태 동기화 대상으로 받는다.
     */
    const likeResponse =
        http.post(
            url,
            null,
            {
                headers:
                    authHeaders(),

                timeout:
                    '10s',

                responseType:
                    'none',

                responseCallback:
                    http.expectedStatuses(
                        200,
                        201,
                        204,
                        409
                    ),

                tags: {
                    endpoint:
                        'post-like',

                    phase:
                        'business',
                },
            }
        );

    /*
     * 정상적으로 새 좋아요가 생성됨.
     */
    if (
        likeResponse.status === 200 ||
        likeResponse.status === 201 ||
        likeResponse.status === 204
    ) {
        const likeSucceeded =
            expect2xx(
                'post-like',
                likeResponse
            );

        if (likeSucceeded) {
            likedPosts[postId] =
                true;
        }

        return likeSucceeded;
    }

    /*
     * 409:
     * 이전 테스트에서 해당 사용자의 좋아요가 이미 DB에 존재함.
     *
     * 실제 상태는 좋아요=true이므로 DELETE로 전환하여
     * 토글 동작을 완성하고 로컬 상태를 동기화한다.
     */
    if (likeResponse.status === 409) {
        const syncResponse =
            http.del(
                url,
                null,
                {
                    headers:
                        authHeaders(),

                    timeout:
                        '10s',

                    responseType:
                        'none',

                    responseCallback:
                        http.expectedStatuses(
                            200,
                            204
                        ),

                    tags: {
                        endpoint:
                            'post-unlike-state-sync',

                        phase:
                            'state-sync',
                    },
                }
            );

        const syncSucceeded =
            expect2xx(
                'post-unlike-state-sync',
                syncResponse
            );

        if (syncSucceeded) {
            likedPosts[postId] =
                false;
        }

        return syncSucceeded;
    }

    /*
     * 그 외 예상하지 못한 상태 코드.
     */
    return expect2xx(
        'post-like',
        likeResponse
    );
}

function createComment(
    postId
) {
    const response =
        http.post(
            `${BASE_URL}/posts/${postId}/comments`,

            JSON.stringify({
                content:
                    `${RUN_ID} 댓글 ` +
                    `vu=${exec.vu.idInTest} ` +
                    `iteration=` +
                    `${exec.scenario.iterationInTest}`,
            }),

            {
                headers:
                    authHeaders(true),

                timeout:
                    '10s',

                responseType:
                    'text',

                responseCallback:
                    http.expectedStatuses(
                        200,
                        201
                    ),

                tags: {
                    endpoint:
                        'comments-create',

                    phase:
                        'business',
                },
            }
        );

    return expect2xx(
        'comments-create',
        response,
        true
    );
}

/*
 * =========================================================
 * 작성 API
 * =========================================================
 */

function uploadImage() {
    const filename =
        `${RUN_ID}-` +
        `${exec.vu.idInTest}-` +
        `${exec.scenario.iterationInTest}-` +
        `${IMAGE_FILENAME}`;

    const response =
        http.post(
            `${BASE_URL}/images/posts`,

            {
                image:
                    http.file(
                        IMAGE_BYTES,
                        filename,
                        IMAGE_MIME_TYPE
                    ),
            },

            {
                /*
                 * multipart boundary는 k6가 설정하므로
                 * Content-Type은 직접 지정하지 않는다.
                 */
                headers:
                    authHeaders(),

                timeout:
                    '15s',

                responseType:
                    'text',

                responseCallback:
                    http.expectedStatuses(
                        200,
                        201
                    ),

                tags: {
                    endpoint:
                        'images-upload',

                    phase:
                        'business',
                },
            }
        );

    if (
        !expect2xx(
            'images-upload',
            response,
            true
        )
    ) {
        return null;
    }

    return entityIdFrom(
        response,
        [
            'imageId',
            'image_id',
            'id',
        ]
    );
}

function postPayload(
    imageId
) {
    const payload = {
        title:
            `${RUN_ID} 게시글 ` +
            `${exec.vu.idInTest}-` +
            `${exec.scenario.iterationInTest}`,

        content:
            `${RUN_ID} 혼합 부하 게시글 ` +
            `vu=${exec.vu.idInTest}, ` +
            `iteration=` +
            `${exec.scenario.iterationInTest}`,
    };

    if (
        !imageId ||
        POST_IMAGE_FIELD === 'none'
    ) {
        return payload;
    }

    if (
        POST_IMAGE_FIELD === 'imageId'
    ) {
        payload.imageId =
            Number(imageId);
    } else {
        payload.imageIds = [
            Number(imageId),
        ];
    }

    return payload;
}

function createPost(
    imageId
) {
    const response =
        http.post(
            `${BASE_URL}/posts`,

            JSON.stringify(
                postPayload(imageId)
            ),

            {
                headers:
                    authHeaders(true),

                timeout:
                    '10s',

                responseType:
                    'text',

                responseCallback:
                    http.expectedStatuses(
                        200,
                        201
                    ),

                tags: {
                    endpoint:
                        'posts-create',

                    phase:
                        'business',
                },
            }
        );

    if (
        !expect2xx(
            'posts-create',
            response,
            true
        )
    ) {
        return null;
    }

    return entityIdFrom(
        response,
        [
            'postId',
            'post_id',
            'id',
        ]
    );
}

function updatePost(
    postId
) {
    const response =
        http.patch(
            `${BASE_URL}/posts/${postId}`,

            JSON.stringify({
                title:
                    `${RUN_ID} 수정 게시글 ` +
                    `${postId}`,

                content:
                    `${RUN_ID} 수정 ` +
                    `vu=${exec.vu.idInTest}, ` +
                    `iteration=` +
                    `${exec.scenario.iterationInTest}`,
            }),

            {
                headers:
                    authHeaders(true),

                timeout:
                    '10s',

                responseType:
                    'text',

                responseCallback:
                    http.expectedStatuses(
                        200,
                        204
                    ),

                tags: {
                    endpoint:
                        'posts-update',

                    phase:
                        'business',
                },
            }
        );

    return expect2xx(
        'posts-update',
        response,
        true
    );
}

function deletePost(
    postId
) {
    const response =
        http.del(
            `${BASE_URL}/posts/${postId}`,

            null,

            {
                headers:
                    authHeaders(),

                timeout:
                    '10s',

                responseType:
                    'text',

                responseCallback:
                    http.expectedStatuses(
                        200,
                        204
                    ),

                tags: {
                    endpoint:
                        'posts-delete',

                    phase:
                        'business',
                },
            }
        );

    return expect2xx(
        'posts-delete',
        response,
        true
    );
}

/*
 * =========================================================
 * 조회형 세션
 *
 * reissue       4
 * users/me      4
 * post list     2
 * post detail   2
 * comments      2
 *
 * 총 14 requests/session
 * =========================================================
 */

export function browseSession() {
    const startedAt =
        Date.now();

    for (
        let cycle = 0;
        cycle < 2;
        cycle += 1
    ) {
        const postId =
            randomPostId(cycle);

        if (
            !requireScreen(startedAt)
        ) {
            return;
        }

        if (
            !requireAction(
                startedAt,
                getPostList
            )
        ) {
            return;
        }

        think(
            1.0,
            2.0
        );

        if (
            !requireAction(
                startedAt,
                () =>
                    getPostDetail(postId)
            )
        ) {
            return;
        }

        think(
            1.5,
            3.0
        );

        if (
            !requireAction(
                startedAt,
                () =>
                    getComments(postId)
            )
        ) {
            return;
        }

        think(
            1.0,
            2.0
        );
    }

    if (
        !requireScreen(startedAt)
    ) {
        return;
    }

    think(
        0.8,
        1.5
    );

    if (
        !requireScreen(startedAt)
    ) {
        return;
    }

    think(
        0.8,
        1.5
    );

    finish(
        startedAt,
        true
    );
}

/*
 * =========================================================
 * 반응형 세션
 *
 * reissue         5
 * users/me        5
 * post list       2
 * post detail     3
 * comments list   3
 * like/unlike     1
 * comment create  1
 *
 * 총 20 requests/session
 * =========================================================
 */

export function reactionSession() {
    const startedAt =
        Date.now();

    for (
        let cycle = 0;
        cycle < 2;
        cycle += 1
    ) {
        const postId =
            randomPostId(cycle);

        if (
            !requireScreen(startedAt)
        ) {
            return;
        }

        if (
            !requireAction(
                startedAt,
                getPostList
            )
        ) {
            return;
        }

        think(
            0.8,
            1.8
        );

        if (
            !requireAction(
                startedAt,
                () =>
                    getPostDetail(postId)
            )
        ) {
            return;
        }

        think(
            1.0,
            2.2
        );

        if (
            !requireAction(
                startedAt,
                () =>
                    getComments(postId)
            )
        ) {
            return;
        }

        think(
            0.8,
            1.8
        );
    }

    const actionPostId =
        randomPostId(2);

    if (
        !requireScreen(startedAt)
    ) {
        return;
    }

    if (
        !requireAction(
            startedAt,
            () =>
                getPostDetail(
                    actionPostId
                )
        )
    ) {
        return;
    }

    think(
        0.8,
        1.5
    );

    if (
        !requireAction(
            startedAt,
            () =>
                getComments(
                    actionPostId
                )
        )
    ) {
        return;
    }

    think(
        0.3,
        0.8
    );

    if (
        !requireAction(
            startedAt,
            () =>
                toggleLike(
                    actionPostId
                )
        )
    ) {
        return;
    }

    think(
        1.5,
        3.0
    );

    if (
        !requireAction(
            startedAt,
            () =>
                createComment(
                    actionPostId
                )
        )
    ) {
        return;
    }

    think(
        0.8,
        1.5
    );

    if (
        !requireScreen(startedAt)
    ) {
        return;
    }

    think(
        0.5,
        1.2
    );

    if (
        !requireScreen(startedAt)
    ) {
        return;
    }

    think(
        0.5,
        1.2
    );

    finish(
        startedAt,
        true
    );
}

/*
 * =========================================================
 * 작성형 세션
 *
 * 기본:
 * reissue        4
 * users/me       4
 * post list      1
 * post detail    1
 * comments       1
 * post create    1
 *
 * 기본 12 requests/session
 *
 * 확률:
 * image upload   51%
 * post update    21%
 * post delete    21%
 *
 * 평균 약 12.93 requests/session
 * =========================================================
 */

export function contentSession() {
    const startedAt =
        Date.now();

    const postId =
        randomPostId();

    if (
        !requireScreen(startedAt)
    ) {
        return;
    }

    if (
        !requireAction(
            startedAt,
            getPostList
        )
    ) {
        return;
    }

    think(
        1.0,
        2.0
    );

    if (
        !requireAction(
            startedAt,
            () =>
                getPostDetail(postId)
        )
    ) {
        return;
    }

    think(
        1.2,
        2.5
    );

    if (
        !requireAction(
            startedAt,
            () =>
                getComments(postId)
        )
    ) {
        return;
    }

    think(
        1.0,
        2.0
    );

    /*
     * 추가 화면 이동 3회
     */
    for (
        let index = 0;
        index < 3;
        index += 1
    ) {
        if (
            !requireScreen(startedAt)
        ) {
            return;
        }

        think(
            0.5,
            1.2
        );
    }

    /*
     * 게시글 작성 시간
     */
    think(
        2.0,
        4.0
    );

    let imageId = null;

    /*
     * 작성형 세션 중 51% 이미지 업로드
     */
    if (
        TEST_MODE === 'smoke' ||
        Math.random() < 0.51
    ) {
        think(
            0.8,
            1.8
        );

        imageId =
            uploadImage();

        if (!imageId) {
            finish(
                startedAt,
                false
            );

            return;
        }
    }

    const createdPostId =
        createPost(imageId);

    if (!createdPostId) {
        finish(
            startedAt,
            false
        );

        return;
    }

    think(
        0.8,
        1.5
    );

    /*
     * 작성형 세션 중 21% 수정
     */
    if (
        TEST_MODE === 'smoke' ||
        Math.random() < 0.21
    ) {
        if (
            !requireAction(
                startedAt,
                () =>
                    updatePost(
                        createdPostId
                    )
            )
        ) {
            return;
        }

        think(
            0.5,
            1.0
        );
    }

    /*
     * 작성형 세션 중 21% 삭제
     */
    if (
        TEST_MODE === 'smoke' ||
        Math.random() < 0.21
    ) {
        if (
            !requireAction(
                startedAt,
                () =>
                    deletePost(
                        createdPostId
                    )
            )
        ) {
            return;
        }
    }

    finish(
        startedAt,
        true
    );
}
