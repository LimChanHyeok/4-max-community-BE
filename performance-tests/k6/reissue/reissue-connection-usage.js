import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';

/**
 * 필수 환경변수를 읽는다.
 */
function requiredEnv(name) {
    const value = __ENV[name];

    if (!value) {
        throw new Error(`필수 환경변수가 없습니다: ${name}`);
    }

    return value;
}

/**
 * 양의 정수 환경변수를 읽는다.
 */
function positiveIntegerEnv(name, defaultValue) {
    const rawValue = __ENV[name] || String(defaultValue);
    const value = Number(rawValue);

    if (!Number.isInteger(value) || value <= 0) {
        throw new Error(
            `${name}은 양의 정수여야 합니다: ${rawValue}`,
        );
    }

    return value;
}

/**
 * 양수 실수 환경변수를 읽는다.
 */
function positiveNumberEnv(name, defaultValue) {
    const rawValue = __ENV[name] || String(defaultValue);
    const value = Number(rawValue);

    if (!Number.isFinite(value) || value <= 0) {
        throw new Error(
            `${name}은 0보다 큰 숫자여야 합니다: ${rawValue}`,
        );
    }

    return value;
}

/**
 * boolean 환경변수를 읽는다.
 */
function booleanEnv(name, defaultValue) {
    const rawValue = __ENV[name];

    if (rawValue === undefined || rawValue === '') {
        return defaultValue;
    }

    const normalized = rawValue.toLowerCase();

    if (normalized === 'true') {
        return true;
    }

    if (normalized === 'false') {
        return false;
    }

    throw new Error(
        `${name}은 true 또는 false여야 합니다: ${rawValue}`,
    );
}

const BASE_URL = requiredEnv('BASE_URL').replace(/\/$/, '');
const TEST_PASSWORD = requiredEnv('TEST_PASSWORD');

const LOGIN_PATH = __ENV.LOGIN_PATH || '/auth';
const REISSUE_PATH = __ENV.REISSUE_PATH || '/auth/reissue';

const LOGIN_URL = `${BASE_URL}${LOGIN_PATH}`;
const REISSUE_URL = `${BASE_URL}${REISSUE_PATH}`;

const USER_PREFIX = __ENV.USER_PREFIX || 'perf-reissue';
const EMAIL_DOMAIN = __ENV.EMAIL_DOMAIN || 'example.com';

const USER_COUNT = positiveIntegerEnv('USER_COUNT', 140);
const TEST_VUS = positiveIntegerEnv('TEST_VUS', 140);

/*
 * VU 하나가 reissue를 호출하는 주기다.
 *
 * 140 VU / 2초 = 약 70 RPS
 */
const PER_VU_INTERVAL_SECONDS = positiveNumberEnv(
    'PER_VU_INTERVAL_SECONDS',
    2,
);

const TEST_DURATION = __ENV.TEST_DURATION || '3m30s';
const SETUP_TIMEOUT = __ENV.SETUP_TIMEOUT || '3m';

const CONFIGURED_REFRESH_COOKIE_NAME =
    __ENV.REFRESH_COOKIE_NAME || '';

const DISCARD_RESPONSE_BODIES = booleanEnv(
    'DISCARD_RESPONSE_BODIES',
    true,
);

if (TEST_VUS > USER_COUNT) {
    throw new Error(
        `TEST_VUS(${TEST_VUS})는 ` +
        `USER_COUNT(${USER_COUNT})보다 클 수 없습니다.`,
    );
}

/*
 * 참고용 예상 RPS.
 * 실제 측정값은 Grafana Backend API RPS에서 확인한다.
 */
const EXPECTED_RPS =
    TEST_VUS / PER_VU_INTERVAL_SECONDS;

export const options = {
    setupTimeout: SETUP_TIMEOUT,

    scenarios: {
        reissueConnectionUsage: {
            executor: 'constant-vus',
            vus: TEST_VUS,
            duration: TEST_DURATION,
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
    },
};

/**
 * 계정 번호를 001 형태로 변환한다.
 */
function pad(number) {
    return String(number).padStart(3, '0');
}

/**
 * index에 해당하는 테스트 계정 정보를 생성한다.
 */
function createUser(index) {
    const sequence = index + 1;

    return {
        email:
            `${USER_PREFIX}-${pad(sequence)}` +
            `@${EMAIL_DOMAIN}`,

        password: TEST_PASSWORD,
    };
}

/**
 * 실패 응답 본문을 최대 500자까지만 반환한다.
 * 토큰 원문은 출력하지 않는다.
 */
function safeResponseBody(response) {
    if (!response.body) {
        return '';
    }

    return String(response.body).slice(0, 500);
}

/**
 * 로그인 응답에서 Refresh Token 쿠키를 찾는다.
 */
function findRefreshCookie(response, email) {
    const responseCookies = response.cookies || {};
    const cookieNames = Object.keys(responseCookies);

    let cookieName = CONFIGURED_REFRESH_COOKIE_NAME;

    /*
     * 쿠키 이름을 명시하지 않았다면:
     * 1. 이름에 refresh가 포함된 쿠키
     * 2. 응답 쿠키가 하나뿐이면 해당 쿠키
     */
    if (!cookieName) {
        cookieName =
            cookieNames.find((name) =>
                name.toLowerCase().includes('refresh'),
            ) ||
            (
                cookieNames.length === 1
                    ? cookieNames[0]
                    : ''
            );
    }

    if (!cookieName) {
        throw new Error(
            `${email} 로그인 응답에서 Refresh Token 쿠키를 ` +
            `찾지 못했습니다. ` +
            `응답 쿠키 이름=[${cookieNames.join(', ')}]. ` +
            `REFRESH_COOKIE_NAME을 지정하세요.`,
        );
    }

    const cookies = responseCookies[cookieName] || [];

    const cookie = cookies.find(
        (candidate) =>
            Boolean(candidate && candidate.value),
    );

    if (!cookie || !cookie.value) {
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

/**
 * 부하 테스트 시작 전에 140개 계정을 각각 로그인한다.
 *
 * 로그인 요청은 reissue 시나리오 시작 전에 실행된다.
 * 각 계정의 최초 Refresh Token을 setup data로 반환한다.
 */
export function setup() {
    const refreshCookies = [];

    console.log(
        `setup 시작: ${USER_COUNT}개 계정 로그인, ` +
        `예상 reissue RPS=${EXPECTED_RPS.toFixed(2)}`,
    );

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
                    name: 'POST /auth - setup login',
                    endpoint: 'setup-login',
                },

                timeout: '10s',
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

        const refreshCookie = findRefreshCookie(
            response,
            user.email,
        );

        refreshCookies.push(refreshCookie);
    }

    /*
     * 모든 계정이 서로 다른 최초 Refresh Token을 받았는지 확인한다.
     */
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
        `setup 완료: ${refreshCookies.length}개 계정 로그인 성공`,
    );

    return {
        refreshCookies,
    };
}

/*
 * 아래 변수는 VU별로 독립적으로 유지된다.
 *
 * 각 VU는 자신에게 배정된 하나의 Refresh Token 체인만 사용한다.
 */
let initialized = false;
let pacingInitialized = false;

let refreshCookieName = '';
let currentRefreshToken = '';

/**
 * 현재 VU에 계정 하나와 Refresh Token 하나를 배정한다.
 */
function initializeVu(data) {
    /*
     * idInTest는 1부터 시작하므로 배열 index로 사용할 때 -1 한다.
     */
    const tokenIndex = exec.vu.idInTest - 1;

    const assignedCookie =
        data.refreshCookies[tokenIndex];

    if (!assignedCookie) {
        exec.test.abort(
            `VU ${exec.vu.idInTest}에 배정할 Refresh Token이 ` +
            `없습니다. ` +
            `USER_COUNT=${USER_COUNT}, TEST_VUS=${TEST_VUS}`,
        );

        return;
    }

    refreshCookieName = assignedCookie.name;
    currentRefreshToken = assignedCookie.value;

    initialized = true;
}

/**
 * reissue 응답에서 이전 토큰과 다른 새로운 토큰을 찾는다.
 */
function findRotatedToken(response, previousToken) {
    const responseCookies =
        response.cookies?.[refreshCookieName] || [];

    const cookieValues = responseCookies
        .map((cookie) => cookie?.value)
        .filter((value) => Boolean(value));

    return (
        cookieValues.find(
            (value) => value !== previousToken,
        ) || ''
    );
}

/**
 * 각 VU가 2초 구간 안에서 서로 다른 시점에
 * 첫 요청을 보내도록 초기 지연시간을 계산한다.
 *
 * 140 VU가 동시에 요청을 보내는 초기 스파이크를 방지한다.
 */
function applyInitialPacing() {
    if (pacingInitialized) {
        return;
    }

    const vuIndex = exec.vu.idInTest - 1;

    const initialDelaySeconds =
        (vuIndex / TEST_VUS) *
        PER_VU_INTERVAL_SECONDS;

    if (initialDelaySeconds > 0) {
        sleep(initialDelaySeconds);
    }

    pacingInitialized = true;
}

/**
 * /auth/reissue 부하 테스트
 */
export default function (data) {
    if (!initialized) {
        initializeVu(data);
    }

    applyInitialPacing();

    const iterationStartedAt = Date.now();
    const previousToken = currentRefreshToken;

    /*
     * Cookie Jar의 기존 쿠키에 의존하지 않는다.
     *
     * 현재 VU가 가지고 있는 Refresh Token을
     * 요청 단위 쿠키로 직접 전달한다.
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

            timeout: '10s',
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

    const successful = check(
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
     * 성공한 경우에만 다음 요청에 사용할 토큰으로 교체한다.
     */
    if (
        successful &&
        statusSucceeded &&
        tokenReceived &&
        tokenRotated
    ) {
        currentRefreshToken = nextToken;
    } else {
        console.error(
            `reissue 실패: ` +
            `vu=${exec.vu.idInTest}, ` +
            `status=${response.status}, ` +
            `responseCookieNames=[${Object.keys(
                response.cookies || {},
            ).join(', ')}], ` +
            `body=${safeResponseBody(response)}`,
        );

        /*
         * RTR 체인이 끊긴 상태로 계속 요청하면
         * 실패 요청이 Hikari 측정값에 섞이므로 즉시 중단한다.
         */
        exec.test.abort(
            `Refresh Token RTR 체인이 끊어져 ` +
            `테스트를 중단합니다. ` +
            `vu=${exec.vu.idInTest}, ` +
            `status=${response.status}`,
        );

        return;
    }

    /*
     * HTTP 처리시간을 포함하여 각 iteration 시작 간격을
     * 약 2초로 맞춘다.
     *
     * 예:
     * 요청 처리 0.1초 + sleep 1.9초 = 약 2초
     */
    const elapsedSeconds =
        (Date.now() - iterationStartedAt) / 1000;

    const remainingSeconds =
        PER_VU_INTERVAL_SECONDS - elapsedSeconds;

    if (remainingSeconds > 0) {
        sleep(remainingSeconds);
    }
}