import { browser } from 'k6/browser';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';
import apiSearch, { options as apiOptions, setup as apiSetup } from './search_load.js';

const browserOnly = __ENV.SEARCH_BROWSER_ONLY === 'true';
const durationSeconds = Number(__ENV.SEARCH_BROWSER_SECONDS || 180);
const startSeconds = browserOnly ? 0 : Number(__ENV.SEARCH_BROWSER_START_SECONDS || 360);
const browserUsers = Number(__ENV.SEARCH_BROWSER_USERS || 3);
const timeoutMs = 15000;
if (!Number.isInteger(browserUsers) || browserUsers < 1 || browserUsers > 3
    || !Number.isFinite(durationSeconds) || durationSeconds < 1 || durationSeconds > 180
    || !Number.isFinite(startSeconds) || startSeconds < 0
    || (!browserOnly && startSeconds + durationSeconds > 600)) {
  throw new Error('브라우저 사용자는 1~3명, 측정 시간은 1~180초, 측정 구간은 API 부하의 10분 안으로 지정하세요.');
}
const cases = [
  { id: 'frequent', keyword: '김민재', rows: 5 },
  { id: 'rare', keyword: '오버래핑', rows: 5 },
  { id: 'no_match', keyword: '아약스', rows: 0 },
];
const attempts = new Counter('browser_search_attempts');
const failures = new Counter('browser_search_failures');
const timeouts = new Counter('browser_search_timeouts');
const sessionFailures = new Counter('browser_session_failures');
const withinSecond = new Rate('browser_search_within_1s');
const displayDuration = new Trend('browser_search_display_ms', true);
const domDuration = new Trend('browser_search_dom_ms', true);
const apiDuration = new Trend('browser_search_api_ms', true);
const clickToResponse = new Trend('browser_search_click_to_response_ms', true);
const beforeRequest = new Trend('browser_search_before_request_ms', true);
const afterResponse = new Trend('browser_search_after_response_ms', true);
const thresholds = {
  browser_search_attempts: ['count>0'],
  browser_search_failures: ['count==0'],
  browser_session_failures: ['count==0'],
  browser_search_within_1s: ['rate>=0.99'],
};
for (const testCase of cases) {
  thresholds[`browser_search_display_ms{search_case:${testCase.id}}`] = [];
  thresholds[`browser_search_api_ms{search_case:${testCase.id}}`] = [];
  thresholds[`browser_search_attempts{search_case:${testCase.id}}`] = ['count>0'];
  thresholds[`browser_search_within_1s{search_case:${testCase.id}}`] = [];
}
for (const phase of browserOnly ? ['standalone'] : ['steady', 'peak', 'recovery']) {
  thresholds[`browser_search_display_ms{phase:${phase}}`] = [];
  thresholds[`browser_search_within_1s{phase:${phase}}`] = [];
  thresholds[`browser_search_api_ms{phase:${phase}}`] = [];
  for (const testCase of cases) {
    const selector = `phase:${phase},search_case:${testCase.id}`;
    thresholds[`browser_search_display_ms{${selector}}`] = [];
    thresholds[`browser_search_api_ms{${selector}}`] = [];
  }
}
for (let user = 1; user <= browserUsers; user++) {
  thresholds[`browser_search_attempts{browser_user:${user}}`] = ['count>0'];
}

export const options = {
  scenarios: {
    ...(browserOnly ? {} : {
      search_load: { ...apiOptions.scenarios.search_load, exec: 'apiLoad' },
    }),
    browser_search: {
      executor: 'per-vu-iterations',
      exec: 'browserSession',
      vus: browserUsers,
      iterations: 1,
      startTime: `${startSeconds}s`,
      maxDuration: `${durationSeconds + 30}s`,
      options: { browser: { type: 'chromium' } },
    },
  },
  summaryTrendStats: ['count', 'p(50)', 'p(95)', 'p(99)', 'max'],
  thresholds: { ...(browserOnly ? {} : apiOptions.thresholds), ...thresholds },
};

export function setup() {
  const data = apiSetup();
  console.log(`브라우저 ${browserUsers}명 · ${durationSeconds}초 · API 부하 ${browserOnly ? '없음' : '유지'} · 시작 지연 ${startSeconds}초`);
  return data;
}

export function apiLoad(data) {
  apiSearch(data);
}

const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
function phaseAt(epochMs) {
  if (browserOnly) return 'standalone';
  const elapsed = (epochMs - exec.scenario.startTime) / 1000 + startSeconds;
  return elapsed < 120 ? 'warmup' : elapsed < 420 ? 'steady' : elapsed < 480 ? 'peak' : 'recovery';
}

// 배포 파일은 수정하지 않는다. 이 테스트 브라우저에만 임시 측정기를 설치한다.
function installProbe() {
  const probe = window.__searchProbe = { current: null, next: 0 };
  const nativeFetch = window.fetch.bind(window);
  performance.setResourceTimingBufferSize(5000);
  const sameContents = state => {
    const list = document.querySelector('.boardList');
    const input = document.querySelector('#searchInput');
    if (!list || input?.value.trim() !== state.keyword || !state.posts || !state.cleared) return false;
    if (new URL(location.href).searchParams.get('keyword') !== state.keyword) return false;
    const links = [...list.querySelectorAll(':scope > a')];
    if (links.length !== state.posts.length) return false;
    return links.every((link, i) => {
      const post = state.posts[i];
      return new URL(link.href).searchParams.get('id') === String(post.post_id)
        && link.querySelector('.title')?.textContent === post.title
        && link.querySelector('.writer')?.textContent === post.writer?.nickname
        && link.getBoundingClientRect().width > 0
        && link.getBoundingClientRect().height > 0;
    });
  };
  const fail = (state, reason) => {
    if (probe.current === state && !state.result) state.result = { ok: false, reason };
  };
  // 자식 삭제 기록까지 확인해 같은 내용이 재표시돼도 이전 목록과 구분한다.
  const observer = new MutationObserver(records => {
    const state = probe.current;
    if (!state?.started || state.result) return;
    if (records.some(r => r.target.matches?.('.boardList') && r.removedNodes.length > 0)) state.cleared = true;
  });
  observer.observe(document.querySelector('.boardList'), { childList: true });
  document.addEventListener('click', event => {
    const state = probe.current;
    if (!state || state.started || !event.target.closest('.searchButton')) return;
    state.started = true;
    state.start = event.timeStamp;
    state.clickedAt = Date.now();
    state.cleared = document.querySelector('.boardList').children.length === 0;
    const check = () => {
      if (probe.current !== state || state.result) return;
      if (performance.now() - state.start >= 15000) return fail(state, 'timeout');
      if (document.visibilityState !== 'visible') return fail(state, 'hidden_page');
      if (!sameContents(state)) return requestAnimationFrame(check);
      if (state.domMs === undefined) state.domMs = performance.now() - state.start;
      // DOM 반영 뒤 그리기 기회를 기다린다. 실제 픽셀 표시 완료를 보장하는 API는 아니다.
      requestAnimationFrame(() => requestAnimationFrame(() => {
        if (probe.current !== state || state.result) return;
        if (!sameContents(state)) return requestAnimationFrame(check);
        // 이 검색이 보낸 정확한 URL·시각의 Resource Timing만 사용한다.
        const timing = performance.getEntriesByName(state.requestUrl || '')
          .filter(entry => entry.initiatorType === 'fetch'
            && entry.startTime >= state.fetchStarted - 0.5
            && entry.responseEnd > 0).pop();
        if (!timing) return requestAnimationFrame(check);
        const displayedAt = performance.now();
        if (timing.requestStart < state.start || timing.responseEnd > displayedAt
            || timing.responseEnd < timing.requestStart) return fail(state, 'invalid_timing');
        state.result = {
          ok: true,
          // 같은 표본에서 표시 시간 = 요청 전 + API + 응답 후 시간이 된다.
          apiMs: timing.responseEnd - timing.requestStart,
          beforeRequestMs: timing.requestStart - state.start,
          clickToResponseMs: timing.responseEnd - state.start,
          afterResponseMs: displayedAt - timing.responseEnd,
          displayMs: displayedAt - state.start,
          domMs: state.domMs,
          clickedAt: state.clickedAt,
          rows: state.posts.length,
        };
      }));
    };
    requestAnimationFrame(check);
  }, true);
  window.fetch = async (...args) => {
    const state = probe.current;
    const input = args[0];
    const url = new URL(typeof input === 'string' ? input : input.url, location.href);
    const measured = state?.started && url.pathname === '/api/posts'
      && url.searchParams.get('keyword') === state.keyword && !url.searchParams.has('cursor');
    if (measured) {
      if (state.requestUrl) {
        fail(state, 'duplicate_request');
      } else {
        state.requestUrl = url.href;
        state.fetchStarted = performance.now();
      }
    }
    let response;
    try {
      response = await nativeFetch(...args);
    } catch (error) {
      if (measured) fail(state, 'network');
      throw error;
    }
    if (measured && probe.current === state) {
      if (response.status !== 200) {
        fail(state, `http_${response.status}`);
      } else {
        response.clone().json().then(body => {
          if (probe.current !== state) return;
          const posts = body?.data?.posts;
          if (!Array.isArray(posts) || posts.length !== state.expectedRows
              || posts.some(p => !Number.isSafeInteger(p.post_id) || !p.title || !p.writer?.nickname)) {
            fail(state, 'invalid_response');
          } else {
            state.posts = posts;
          }
        }).catch(() => fail(state, 'invalid_json'));
      }
    }
    return response;
  };
}

async function openBoard(context, baseUrl, email) {
  const page = await context.newPage();
  page.setDefaultTimeout(timeoutMs);
  await page.goto(`${baseUrl}/html/login.html`, { waitUntil: 'domcontentloaded' });
  // 로딩·로그인은 검색 시간에 넣지 않으며, 사용자별 쿠키와 메모리를 분리한다.
  const status = await page.evaluate(async ({ email, password }) => {
    const response = await fetch('/api/auth', {
      method: 'POST', credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    });
    return response.status;
  }, { email, password: __ENV.SEARCH_TEST_PASSWORD });
  if (status !== 200) throw new Error('browser_login_failed');
  await page.goto(`${baseUrl}/html/index.html`, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => document.querySelector('.boardList .boardItem')
    && document.querySelector('header'), { timeout: timeoutMs });
  await page.evaluate(installProbe);
  return page;
}

export async function browserSession({ baseUrl }) {
  // API 부하 계정 001과 충돌하지 않도록 브라우저에는 002~004를 배정한다.
  // VU ID 예약 방식에 의존하지 않고 시나리오의 반복 번호로 0~2를 얻는다.
  const slot = exec.scenario.iterationInTest;
  const email = `bench81-${String(slot + 2).padStart(3, '0')}@example.test`;
  const end = exec.scenario.startTime + durationSeconds * 1000;
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  let page;
  let index = slot;
  sessionFailures.add(0);
  try {
    page = await openBoard(context, baseUrl, email);
    const agent = await page.evaluate(() => navigator.userAgent);
    console.log(`브라우저 사용자 ${slot + 1} 준비 · ${agent}`);
    while (Date.now() < end) {
      const testCase = cases[index++ % cases.length];
      const tags = { search_case: testCase.id, phase: phaseAt(Date.now()), browser_user: String(slot + 1) };
      const sampleId = `${slot + 1}-${index}`;
      await page.locator('#searchInput').fill(testCase.keyword);
      await page.evaluate(({ keyword, rows }) => {
        const probe = window.__searchProbe;
        probe.current = { id: ++probe.next, keyword, expectedRows: rows, started: false };
      }, testCase);
      attempts.add(1, tags);
      let result;
      try {
        await page.locator('.searchButton').click();
        await page.waitForFunction(() => !!window.__searchProbe?.current?.result,
          { timeout: timeoutMs + 2000 });
        result = await page.evaluate(() => window.__searchProbe.current.result);
      } catch (_) {
        result = { ok: false, reason: 'automation_or_timeout' };
      }
      if (result.ok) tags.phase = phaseAt(result.clickedAt);
      failures.add(result.ok ? 0 : 1, tags);
      timeouts.add(!result.ok && result.reason.includes('timeout') ? 1 : 0, tags);
      withinSecond.add(result.ok && result.displayMs <= 1000, tags);
      if (result.ok) {
        displayDuration.add(result.displayMs, tags);
        domDuration.add(result.domMs, tags);
        apiDuration.add(result.apiMs, tags);
        clickToResponse.add(result.clickToResponseMs, tags);
        beforeRequest.add(result.beforeRequestMs, tags);
        afterResponse.add(result.afterResponseMs, tags);
        console.log(`browser_sample ${JSON.stringify({ sample_id: sampleId, ...tags, ...result })}`);
      } else {
        console.warn(`browser_sample ${JSON.stringify({ sample_id: sampleId, ...tags, ok: false, reason: result.reason })}`);
        // 늦게 도착한 이전 응답이 다음 측정을 오염시키지 않게 페이지를 새로 준비한다.
        await page.close();
        page = await openBoard(context, baseUrl, email);
      }
      await pause(Math.min(2000 + Math.random() * 3000, Math.max(0, end - Date.now())));
    }
  } catch (_) {
    sessionFailures.add(1);
    console.error(`브라우저 사용자 ${slot + 1} 세션 실패: 로그인·페이지·브라우저 상태를 확인하세요.`);
  } finally {
    await context.close();
  }
}
