import http from 'k6/http';
import { sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';

const cases = [
  { id: 'frequent', keyword: '김민재', fraction: 0.1 },
  { id: 'rare', keyword: '오버래핑', fraction: 0.001 },
  { id: 'no_match', keyword: '아약스', fraction: 0 },
];
const datasetSize = Number(__ENV.SEARCH_DATASET_SIZE || 10000);
if (![10000, 100000, 1000000].includes(datasetSize)) {
  throw new Error('SEARCH_DATASET_SIZE는 10000, 100000, 1000000 중 하나여야 합니다.');
}
const phases = [
  { name: 'warmup', seconds: 120, rate: 5 },
  { name: 'steady', seconds: 300, rate: 5 },
  { name: 'peak', seconds: 60, rate: 10 },
  { name: 'recovery', seconds: 120, rate: 5 },
];
const starts = new Counter('search_flow_starts');
const completions = new Counter('search_flow_completions');
const flowFailures = new Counter('search_flow_failures');
const requests = new Counter('search_requests');
const failures = new Counter('search_failures');
const timeouts = new Counter('search_timeouts');
const duration = new Trend('search_duration', true);
const thresholds = { dropped_iterations: ['count==0'] };

// 구간·검색어별 지표를 별도로 집계한다.
for (const phase of phases) {
  thresholds[`search_flow_starts{start_phase:${phase.name}}`] = ['count>0'];
  for (const testCase of cases) {
    const selector = `phase:${phase.name},search_case:${testCase.id}`;
    thresholds[`search_duration{${selector}}`] = [];
    thresholds[`search_requests{${selector}}`] = [];
    thresholds[`search_failures{${selector}}`] = ['count==0'];
    thresholds[`search_timeouts{${selector}}`] = ['count==0'];
  }
}

export const options = {
  scenarios: {
    search_load: {
      executor: 'ramping-arrival-rate',
      startRate: 5,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: 600,
      gracefulStop: '3m',
      stages: [
        { duration: '2m', target: 5 },
        { duration: '5m', target: 5 },
        { duration: '0s', target: 10 },
        { duration: '1m', target: 10 },
        { duration: '0s', target: 5 },
        { duration: '2m', target: 5 },
      ],
    },
  },
  summaryTrendStats: ['count', 'p(50)', 'p(95)', 'p(99)', 'max'],
  thresholds,
};

function currentPhase() {
  const elapsed = (Date.now() - exec.scenario.startTime) / 1000;
  let boundary = 0;
  for (const phase of phases) {
    boundary += phase.seconds;
    if (elapsed < boundary) {
      return phase.name;
    }
  }
  return 'drain';
}

export function setup() {
  const baseUrl = (__ENV.SEARCH_BASE_URL || '').replace(/\/$/, '');
  if (!/^https:\/\/[^/?#@]+$/.test(baseUrl) || !__ENV.SEARCH_TEST_EMAIL || !__ENV.SEARCH_TEST_PASSWORD) {
    throw new Error('HTTPS 주소와 SEARCH_TEST_EMAIL, SEARCH_TEST_PASSWORD를 지정하세요.');
  }
  const response = http.post(`${baseUrl}/api/auth`, JSON.stringify({
    email: __ENV.SEARCH_TEST_EMAIL,
    password: __ENV.SEARCH_TEST_PASSWORD,
  }), {
    headers: { 'Content-Type': 'application/json' },
    redirects: 0,
    timeout: '15s',
    tags: { name: 'search_login', phase: 'setup' },
  });
  let token;
  if (response.status === 200) {
    try {
      token = response.json('data.access_token');
    } catch (_) {
      // 응답이 JSON이 아니면 로그인 실패로 처리한다.
    }
  }
  if (!token) {
    throw new Error(`테스트 계정 로그인 실패 (HTTP ${response.status})`);
  }
  console.log(`시험 준비 완료 · 데이터 ${datasetSize}건 · 요청 상한 120초`);
  return { baseUrl, token };
}

export default function ({ baseUrl, token }) {
  const index = exec.scenario.iterationInTest;
  if (index === 0) {
    console.log(`검색 부하 시작 UTC ${new Date(exec.scenario.startTime).toISOString()}`);
  }
  const testCase = cases[index % cases.length];
  // 30회마다 검색어 3개와 탐색 깊이 비율이 고르게 조합된다.
  const depthIndex = index % 10;
  const maxPosts = depthIndex < 3 ? 10 : depthIndex < 8 ? 25 : 50;
  const expectedTotal = datasetSize * testCase.fraction;
  const startPhase = currentPhase();
  const flowTags = { search_case: testCase.id, start_phase: startPhase };
  starts.add(1, flowTags);
  let seen = 0;
  let lastId = Infinity;
  let cursor;
  const cursors = new Set();

  for (let page = 0; page < maxPosts / 5; page++) {
    // 이전 구간에서 시작한 사용자의 후속 요청도 실제 요청 시작 구간에 집계한다.
    const tags = {
      ...flowTags,
      phase: currentPhase(),
      page: page === 0 ? 'first' : 'next',
    };
    let url = `${baseUrl}/api/posts?size=5&keyword=${encodeURIComponent(testCase.keyword)}`;
    if (cursor) {
      url += `&cursor=${encodeURIComponent(cursor)}`;
    }
    requests.add(1, tags);
    const response = http.get(url, {
      headers: { Authorization: `Bearer ${token}` },
      redirects: 0,
      timeout: '120s',
      tags: { ...tags, name: 'search_page' },
    });
    let data;
    if (response.status === 200) {
      try {
        data = response.json('data');
      } catch (_) {
        // 응답 구조 검증에서 실패로 집계한다.
      }
    }
    const expectedRows = Math.min(5, expectedTotal - seen);
    const hasNext = seen + expectedRows < expectedTotal;
    let valid = data && Array.isArray(data.posts)
      && data.posts.length === expectedRows
      && data.has_next === hasNext;

    if (valid) {
      for (const post of data.posts) {
        if (!Number.isSafeInteger(post.post_id) || post.post_id <= 0 || post.post_id >= lastId) {
          valid = false;
          break;
        }
        lastId = post.post_id;
      }
      if (hasNext && (typeof data.next_cursor !== 'string'
        || !data.next_cursor || cursors.has(data.next_cursor))) {
        valid = false;
      }
    }

    failures.add(valid ? 0 : 1, tags);
    timeouts.add(response.error_code === 1050 ? 1 : 0, tags);
    if (!valid) {
      flowFailures.add(1, flowTags);
      completions.add(1, flowTags);
      return;
    }
    duration.add(response.timings.duration, tags);
    seen += data.posts.length;
    if (!hasNext || seen >= maxPosts) {
      break;
    }
    cursor = data.next_cursor;
    cursors.add(cursor);
    // 실제 화면에서 제목을 훑어보는 시간을 페이지 사이에만 반영한다.
    sleep(2 + Math.random() * 3);
  }
  flowFailures.add(0, flowTags);
  completions.add(1, flowTags);
}
