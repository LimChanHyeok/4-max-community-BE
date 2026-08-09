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
            `${name}은 양의 정수여야 합니다. 입력값=${value}`
        );
    }

    return value;
}

const BASE_URL = requiredEnv('BASE_URL').replace(/\/$/, '');
const TEST_PASSWORD = requiredEnv('TEST_PASSWORD');

/*
 * Community 회원가입 API 경로
 */
const SIGNUP_PATH = __ENV.SIGNUP_PATH || '/users';

/*
 * 생성 계정 형식:
 *
 * perf-session-0001@example.com
 * perf-session-0002@example.com
 * ...
 * perf-session-1500@example.com
 */
const USER_PREFIX = __ENV.USER_PREFIX || 'perf-session';
const EMAIL_DOMAIN = __ENV.EMAIL_DOMAIN || 'example.com';

/*
 * 기본값으로 1,500개 계정을 생성한다.
 */
const USER_COUNT = positiveIntegerEnv('USER_COUNT', 1500);

/*
 * 동시에 실행할 회원가입 요청 수.
 * 10개 정도면 운영 서버에 과도한 부하를 주지 않으면서
 * 충분히 빠르게 계정을 생성할 수 있다.
 */
const SEED_VUS = Math.min(
    positiveIntegerEnv('SEED_VUS', 10),
    USER_COUNT,
);

/*
 * 200 또는 201: 신규 회원가입 성공
 * 409: 동일 이메일 계정이 이미 존재
 *
 * 동일 스크립트를 재실행해도 409를 정상으로 처리한다.
 */
const signupExpectedStatuses = http.expectedStatuses(
    200,
    201,
    409,
);

export const options = {
    scenarios: {
        seed_users: {
            executor: 'shared-iterations',

            /*
             * SEED_VUS개의 VU가 USER_COUNT개의 회원가입 작업을
             * 나누어 처리한다.
             */
            vus: SEED_VUS,
            iterations: USER_COUNT,

            /*
             * 1,500개 생성에 시간이 걸릴 수 있으므로
             * 최대 실행시간을 10분으로 둔다.
             */
            maxDuration: '10m',

            tags: {
                test_type: 'seed-session-users',
            },
        },
    },

    thresholds: {
        /*
         * 200, 201, 409 중 하나가 아닌 요청이 있으면 실패한다.
         */
        checks: ['rate==1'],
    },
};

/*
 * 계정 번호를 네 자리로 표현한다.
 *
 * 1    → 0001
 * 15   → 0015
 * 1500 → 1500
 */
function pad(number) {
    return String(number).padStart(4, '0');
}

function createUser(index) {
    /*
     * iterationInTest는 0부터 시작하므로 1을 더한다.
     *
     * index=0    → sequence=1
     * index=1499 → sequence=1500
     */
    const sequence = index + 1;
    const paddedSequence = pad(sequence);

    return {
        email:
            `${USER_PREFIX}-${paddedSequence}` +
            `@${EMAIL_DOMAIN}`,

        password: TEST_PASSWORD,

        /*
         * 닉네임은 이메일보다 짧게 유지한다.
         */
        nickname: `perfS${paddedSequence}`,
    };
}

function buildSignupPayload(user) {
    return {
        email: user.email,
        password: user.password,
        nickname: user.nickname,
    };
}

export default function () {
    const index = Number(exec.scenario.iterationInTest);
    const user = createUser(index);

    const response = http.post(
        `${BASE_URL}${SIGNUP_PATH}`,
        JSON.stringify(buildSignupPayload(user)),
        {
            headers: {
                Accept: 'application/json',
                'Content-Type': 'application/json',
            },

            timeout: '10s',

            tags: {
                endpoint: 'seed-session-user',
            },

            responseCallback: signupExpectedStatuses,
        },
    );

    const acceptable =
        response.status === 200 ||
        response.status === 201 ||
        response.status === 409;

    check(response, {
        '사용자 생성 또는 기존 사용자 확인': () =>
            acceptable,
    });

    if (!acceptable) {
        console.error(
            `[signup failed] ` +
            `sequence=${index + 1}, ` +
            `email=${user.email}, ` +
            `status=${response.status}, ` +
            `body=${response.body || ''}`
        );
    }
}