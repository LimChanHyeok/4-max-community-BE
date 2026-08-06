import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

function requiredEnv(name) {
    const value = __ENV[name];

    if (!value) {
        throw new Error(`필수 환경변수가 없습니다: ${name}`);
    }

    return value;
}

function positiveIntegerEnv(name, defaultValue) {
    const value = Number(__ENV[name] || defaultValue);

    if (!Number.isInteger(value) || value <= 0) {
        throw new Error(
            `${name}은 양의 정수여야 합니다: ${value}`,
        );
    }

    return value;
}

function booleanEnv(name, defaultValue) {
    const rawValue = __ENV[name];

    if (rawValue === undefined || rawValue === '') {
        return defaultValue;
    }

    return rawValue.toLowerCase() === 'true';
}

const BASE_URL = requiredEnv('BASE_URL').replace(/\/$/, '');
const TEST_PASSWORD = requiredEnv('TEST_PASSWORD');

const LOGIN_PATH = __ENV.LOGIN_PATH || '/auth';
const REISSUE_PATH = __ENV.REISSUE_PATH || '/auth/reissue';

const LOGIN_URL = `${BASE_URL}${LOGIN_PATH}`;
const REISSUE_URL = `${BASE_URL}${REISSUE_PATH}`;

const USER_PREFIX = __ENV.USER_PREFIX || 'perf-reissue';
const EMAIL_DOMAIN = __ENV.EMAIL_DOMAIN || 'example.com';

const USER_COUNT = positiveIntegerEnv('USER_COUNT', 30);
const RATE = positiveIntegerEnv('RATE', 5);

const PRE_ALLOCATED_VUS = positiveIntegerEnv(
    'PRE_ALLOCATED_VUS',
    30,
);

const MAX_VUS = positiveIntegerEnv(
    'MAX_VUS',
    30,
);

const RAMP_DURATION = __ENV.RAMP_DURATION || '10s';
const HOLD_DURATION = __ENV.HOLD_DURATION || '30s';
const RAMP_DOWN_DURATION = __ENV.RAMP_DOWN_DURATION || '10s';

/*
 * 로그인 응답에 Refresh Token 쿠키가 여러 개 있거나
 * 쿠키 이름에 refresh 문자열이 포함되지 않는다면
 * 실행할 때 REFRESH_COOKIE_NAME을 직접 지정한다.
 */
const CONFIGURED_REFRESH_COOKIE_NAME =
    __ENV.REFRESH_COOKIE_NAME || '';

const DISCARD_RESPONSE_BODIES = booleanEnv(
    'DISCARD_RESPONSE_BODIES',
    false,
);

if (PRE_ALLOCATED_VUS > USER_COUNT) {
    throw new Error(
        `PRE_ALLOCATED_VUS(${PRE_ALLOCATED_VUS})는 ` +
        `USER_COUNT(${USER_COUNT})보다 클 수 없습니다.`,
    );
}

if (MAX_VUS > USER_COUNT) {
    throw new Error(
        `MAX_VUS(${MAX_VUS})는 ` +
        `USER_COUNT(${USER_COUNT})보다 클 수 없습니다.`,
    );
}

if (PRE_ALLOCATED_VUS > MAX_VUS) {
    throw new Error(
        `PRE_ALLOCATED_VUS(${PRE_ALLOCATED_VUS})는 ` +
        `MAX_VUS(${MAX_VUS})보다 클 수 없습니다.`,
    );
}

export const options = {
    scenarios: {
        reissueConnectionUsage: {
            executor: 'ramping-arrival-rate',

            startRate: 1,
            timeUnit: '1s',

            preAllocatedVUs: PRE_ALLOCATED_VUS,
            maxVUs: MAX_VUS,

            stages: [
                {
                    duration: RAMP_DURATION,
                    target: RATE,
                },
                {
                    duration: HOLD_DURATION,
                    target: RATE,
                },
                {
                    duration: RAMP_DOWN_DURATION,
                    target: 0,
                },
            ],

            gracefulStop: '10s',
        },
    },

    discardResponseBodies: DISCARD_RESPONSE_BODIES,

    thresholds: {
        'checks{endpoint:reissue}': [
            'rate==1',
        ],

        'http_req_failed{endpoint:reissue}': [
            'rate==0',
        ],

        'http_req_duration{endpoint:reissue}': [
            'p(95)<1000',
        ],

        dropped_iterations: [
            'count==0',
        ],
    },
};

function pad(number) {
    return String(number).padStart(3, '0');
}

function createUser(index) {
    const sequence = index + 1;

    return {
        email:
            `${USER_PREFIX}-${pad(sequence)}` +
            `@${EMAIL_DOMAIN}`,

        password: TEST_PASSWORD,
    };
}

function safeResponseBody(response) {
    if (!response.body) {
        return '';
    }

    return String(response.body).slice(0, 500);
}

function findRefreshCookie(response, email) {
    const responseCookies = response.cookies || {};
    const cookieNames = Object.keys(responseCookies);

    let cookieName = CONFIGURED_REFRESH_COOKIE_NAME;

    if (!cookieName) {
        cookieName =
            cookieNames.find((name) =>
                name.toLowerCase().includes('refresh'),
            ) ||
            (cookieNames.length === 1
                ? cookieNames[0]
                : '');
    }

    if (!cookieName) {
        throw new Error(
            `${email} 로그인 응답에서 Refresh Token 쿠키를 ` +
            `찾지 못했습니다. 응답 쿠키 이름: ` +
            `[${cookieNames.join(', ')}]. ` +
            `REFRESH_COOKIE_NAME 환경변수를 지정하세요.`,
        );
    }

    const cookies = responseCookies[cookieName] || [];

    const cookie = cookies.find(
        (candidate) => Boolean(candidate?.value),
    );

    if (!cookie?.value) {
        throw new Error(
            `${email} 로그인 응답의 ${cookieName} 쿠키에 ` +
            `값이 없습니다.`,
        );
    }

    return {
        name: cookieName,
        value: cookie.value,
    };
}

/*
 * 부하 측정 전에 테스트 계정 30개를 각각 로그인한다.
 *
 * 여기서 받은 최초 Refresh Token을 setup data로 반환하고,
 * 각 VU가 하나씩 배정받는다.
 */
export function setup() {
    const refreshCookies = [];

    for (let index = 0; index < USER_COUNT; index += 1) {
        const user = createUser(index);

        const response = http.post(
            LOGIN_URL,
            JSON.stringify({
                email: user.email,
                password: user.password,
            }),
            {
                headers: {
                    'Content-Type': 'application/json',
                    Accept: 'application/json',
                },

                tags: {
                    name: 'setup-login',
                    endpoint: 'setup-login',
                },
            },
        );

        if (response.status !== 200) {
            throw new Error(
                `로그인 실패: ` +
                `email=${user.email}, ` +
                `status=${response.status}, ` +
                `body=${safeResponseBody(response)}`,
            );
        }

        refreshCookies.push(
            findRefreshCookie(response, user.email),
        );
    }

    const uniqueTokens = new Set(
        refreshCookies.map((cookie) => cookie.value),
    );

    if (uniqueTokens.size !== refreshCookies.length) {
        throw new Error(
            `중복 Refresh Token이 감지됐습니다. ` +
            `전체=${refreshCookies.length}, ` +
            `고유=${uniqueTokens.size}`,
        );
    }

    console.log(
        `setup 완료: ` +
        `${refreshCookies.length}개 계정 로그인 성공`,
    );

    return {
        refreshCookies,
    };
}

/*
 * init 영역의 변수는 VU마다 독립적으로 존재한다.
 *
 * 각 VU는 자신에게 배정된 Refresh Token 체인을
 * currentRefreshToken 변수에 계속 이어서 보관한다.
 */
let initialized = false;
let refreshCookieName = '';
let currentRefreshToken = '';
let failureLogged = false;

function initializeVu(data) {
    const tokenIndex = exec.vu.idInTest - 1;
    const assignedCookie =
        data.refreshCookies[tokenIndex];

    if (!assignedCookie) {
        exec.test.abort(
            `VU ${exec.vu.idInTest}에 배정할 Refresh Token이 ` +
            `없습니다. USER_COUNT와 MAX_VUS를 확인하세요.`,
        );

        return;
    }

    refreshCookieName = assignedCookie.name;
    currentRefreshToken = assignedCookie.value;
    initialized = true;
}

function findRotatedToken(response, previousToken) {
    const cookies =
        response.cookies?.[refreshCookieName] || [];

    const values = cookies
        .map((cookie) => cookie?.value)
        .filter((value) => Boolean(value));

    return (
        values.find((value) => value !== previousToken) ||
        ''
    );
}

export default function (data) {
    if (!initialized) {
        initializeVu(data);
    }

    const previousToken = currentRefreshToken;

    /*
     * Cookie Jar의 이전 값에 의존하지 않고,
     * 현재 VU가 보관한 Refresh Token을 요청에 직접 넣는다.
     */
    const requestCookies = {
        [refreshCookieName]: {
            value: previousToken,
            replace: true,
        },
    };

    const response = http.post(
        REISSUE_URL,
        null,
        {
            headers: {
                Accept: 'application/json',
            },

            cookies: requestCookies,

            tags: {
                name: 'POST /auth/reissue',
                endpoint: 'reissue',
            },
        },
    );

    const nextToken = findRotatedToken(
        response,
        previousToken,
    );

    const statusSucceeded =
        response.status === 200;

    const tokenReceived =
        Boolean(nextToken);

    const tokenRotated =
        tokenReceived &&
        nextToken !== previousToken;

    check(
        response,
        {
            'reissue status is 200': () =>
                statusSucceeded,

            'new refresh token cookie exists': () =>
                tokenReceived,

            'refresh token was rotated': () =>
                tokenRotated,
        },
        {
            endpoint: 'reissue',
        },
    );

    /*
     * 성공한 경우에만 다음 요청에서 사용할 토큰을 갱신한다.
     */
    if (
        statusSucceeded &&
        tokenReceived &&
        tokenRotated
    ) {
        currentRefreshToken = nextToken;
        return;
    }

    /*
     * 한 VU에서 실패하면 그 토큰 체인은 이후에도 연속 실패할
     * 가능성이 있으므로 전체 측정을 중단한다.
     *
     * Refresh Token 원문은 로그에 출력하지 않는다.
     */
    if (!failureLogged) {
        console.error(
            `reissue 실패: ` +
            `vu=${exec.vu.idInTest}, ` +
            `status=${response.status}, ` +
            `responseCookieNames=[${Object.keys(
                response.cookies || {},
            ).join(', ')}], ` +
            `body=${safeResponseBody(response)}`,
        );

        failureLogged = true;
    }

    exec.test.abort(
        `Refresh Token RTR 체인이 끊어져 테스트를 중단합니다. ` +
        `vu=${exec.vu.idInTest}, status=${response.status}`,
    );
}