import http from 'k6/http';
import { scenario } from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';

const warmupRequests = 10;
const measuredRequests = 200;
const pageSize = 5;
const requestTimeout = '120s';
const cases = [
  { id: 'frequent', keyword: '김민재', expectedRows: 5 },
  { id: 'rare', keyword: '오버래핑', expectedRows: 5 },
  { id: 'no_match', keyword: '아약스', expectedRows: 0 },
].map((testCase) => ({
  ...testCase,
  attempts: new Counter(`search_${testCase.id}_attempts`),
  failures: new Counter(`search_${testCase.id}_failures`),
  timeouts: new Counter(`search_${testCase.id}_timeouts`),
  duration: new Trend(`search_${testCase.id}_duration`, true),
}));

export const options = {
  scenarios: {
    search_baseline: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: cases.length * (warmupRequests + measuredRequests),
      maxDuration: '10m',
    },
  },
  summaryTrendStats: ['count', 'p(50)', 'p(95)', 'p(99)', 'max'],
  thresholds: Object.fromEntries(cases.map(({ attempts }) => [
    attempts.name,
    [`count==${measuredRequests}`],
  ])),
};

export function setup() {
  const baseUrl = (__ENV.SEARCH_BASE_URL || '').replace(/\/$/, '');
  const email = __ENV.SEARCH_TEST_EMAIL;
  const password = __ENV.SEARCH_TEST_PASSWORD;
  if (!/^https:\/\/[^/?#@]+$/.test(baseUrl) || !email || !password) {
    throw new Error('검색 실험 환경의 HTTPS 주소와 테스트 계정 환경 변수를 지정하세요.');
  }

  const response = http.post(`${baseUrl}/api/auth`, JSON.stringify({ email, password }), {
    headers: { 'Content-Type': 'application/json' },
    redirects: 0,
    timeout: '15s',
    tags: { name: 'search_login' },
  });
  if (response.status !== 200) {
    throw new Error(`테스트 계정 로그인 실패 (HTTP ${response.status})`);
  }
  const token = response.json('data.access_token');
  if (!token) {
    throw new Error('로그인 응답에 액세스 토큰이 없습니다.');
  }
  return { baseUrl, token };
}

export default function ({ baseUrl, token }) {
  // 검색어를 한 번씩 번갈아 보내며, 응답이 끝나야 다음 반복을 시작한다.
  const testCase = cases[scenario.iterationInTest % cases.length];
  const warmup = scenario.iterationInTest < warmupRequests * cases.length;
  const response = http.get(
    `${baseUrl}/api/posts?size=${pageSize}&keyword=${encodeURIComponent(testCase.keyword)}`,
    {
      headers: { Authorization: `Bearer ${token}` },
      redirects: 0,
      timeout: requestTimeout,
      tags: { name: 'search_first_page', search_case: testCase.id, phase: warmup ? 'warmup' : 'measure' },
    },
  );

  if (warmup) {
    return;
  }
  let posts;
  if (response.status === 200) {
    try {
      posts = response.json('data.posts');
    } catch (_) {
      // JSON을 읽을 수 없는 응답은 결과 검증 실패로 집계한다.
    }
  }
  const valid = Array.isArray(posts) && posts.length === testCase.expectedRows;
  testCase.attempts.add(1);
  testCase.failures.add(valid ? 0 : 1);
  testCase.timeouts.add(response.error_code === 1050 ? 1 : 0);
  if (valid) {
    testCase.duration.add(response.timings.duration);
  }
}
