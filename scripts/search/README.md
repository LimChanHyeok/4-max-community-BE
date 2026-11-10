# 검색 벤치마크 게시글

`benchmark_posts.py`는 #81의 테스트 작성자 20명으로 글을 생성하고 검색 실험 MySQL에 누적 적재한다. 벤치마크 계획과 동일한 글 길이·일치 건수를 적용한다. 작성 시각은 UTC 2024-11-09부터 2026-11-09 직전까지 분포한다. 글은 작성 시각 순으로 추가하고 DB가 게시글 ID를 발급한다.

## 실행

저장소 루트에서 실행한다. `preview`는 표준 라이브러리만 사용한다. `load`와 `verify`는 Python 환경에 `PyMySQL[rsa]==1.1.2`가 필요하다.

```bash
python3 scripts/search/benchmark_posts.py preview --target 10000
```

DB 접속은 백엔드 EC2를 경유하는 SSM 포트 포워딩을 사용한다. MySQL의 사설 주소로 연결하고 로컬 포트는 13381을 사용한다. 검색 DB 앱 계정의 비밀번호를 `BENCHMARK_DB_PASSWORD` 환경 변수로 제공한다.

```bash
python3 scripts/search/benchmark_posts.py load --target 10000
python3 scripts/search/benchmark_posts.py verify --target 10000
```

`load`는 1,000건마다 커밋하고 마지막에 `verify`와 같은 검증을 실행한다. 중간에 멈추면 같은 목표값으로 다시 실행한다. 기존 테스트 글의 개수와 작성 시각 범위가 맞는 경우에만 이어서 추가한다. 작성자 판별에는 `bench81-001@example.test`부터 `bench81-020@example.test`까지의 이메일을 사용하므로, 이 계정들은 벤치마크 글 적재에만 사용한다.

다음 규모는 `--target 100000`, 이후 `--target 1000000`으로 실행한다. 각 규모의 기준 측정을 끝낸 뒤 다음 규모를 적재한다. MySQL 비밀번호와 생성된 데이터는 Git에 저장하지 않는다.

## LIKE 단일 요청 기준 측정

각 데이터 규모에서 적재 검증을 마친 뒤, 검색 실험 환경에 다른 부하를 주지 않은 상태에서 실행한다.

```bash
export SEARCH_BASE_URL='https://<검색 서버 주소>'
export SEARCH_TEST_EMAIL='bench81-001@example.test'
# SEARCH_TEST_PASSWORD에는 테스트 계정 비밀번호를 환경 변수로 제공한다.
k6 run scripts/search/search_baseline.js
```

가상 사용자 1명이 김민재·오버래핑·아약스의 첫 페이지를 번갈아 조회한다. 검색어당 워밍업 10회는 집계에서 빼고 본 측정 200회를 기록한다. 5건·5건·0건을 각각 정상 응답으로 판정한다.

`search_*_duration`은 검증에 성공한 HTTP 요청의 송신·응답 대기·수신 시간이며 브라우저 렌더링 시간은 포함하지 않는다. `search_*_failures`는 HTTP 또는 응답 검증 실패를, `search_*_timeouts`는 k6의 HTTP 요청 타임아웃을 센다. 실패에 타임아웃이 포함된다. 클라이언트 요청 상한은 120초다. 검색어별 본 측정 건수가 200건에 못 미치면 실행을 실패로 표시한다.

## 검색·스크롤 동시 부하 측정

`search_load.js`는 검색 시작과 페이지 요청을 구분한다. 검색 하나가 끝나기 전에도 새 검색을 시작하며, 검색어는 김민재·오버래핑·아약스를 차례로 선택해 각각 약 ⅓을 적용한다.

| 구간 | 검색 시작 | 시간 |
|---|---:|---:|
| warmup | 5회/초 | 2분 |
| steady | 5회/초 | 5분 |
| peak | 10회/초 | 1분 |
| recovery | 5회/초 | 2분 |

기존 기준 측정과 같은 계정 환경 변수를 준비한다. `SEARCH_DATASET_SIZE`는 현재 검증한 벤치마크 게시글 수다. 세 검색어의 일치 건수가 각각 10%·0.1%·0%라는 조건에서 실행한다. 측정 중 게시글을 추가·삭제하지 않는다.

```bash
export SEARCH_DATASET_SIZE=10000
# SEARCH_BASE_URL, SEARCH_TEST_EMAIL, SEARCH_TEST_PASSWORD를 환경 변수로 제공한다.
run_dir=$(mktemp -d /private/tmp/community-search-load.XXXXXX)
k6 run --out "json=$run_dir/requests.json" \
  --summary-export "$run_dir/summary.json" \
  scripts/search/search_load.js
```

한 번에 검색 시작 3,300회를 목표로 하며, VU 부족으로 놓친 실행은 `dropped_iterations`로 확인한다. 탐색 깊이는 10·25·50건을 30%·50%·20%로 선택한다. 각 페이지는 5건이며 다음 페이지 전에 2~5초 기다린다. 빈 결과와 마지막 페이지는 즉시 종료한다. 드문 검색어는 1만 건에서 10건만 일치하므로 최대 두 페이지로 끝난다. 이 조건에서는 계산상의 25.5·51 RPS보다 실제 요청률이 낮아질 수 있다.

각 요청의 예상 결과 건수·마지막 페이지 여부·커서 반복·ID 내림차순과 페이지 중복을 확인한다. 목록 응답에는 본문이 없으므로 본문 검색 일치와 전체 결과 누락 여부는 적재 검증에서 확인한다.

`search_duration`은 검증에 성공한 요청의 HTTP 시간이며 P50·P95·P99·최댓값을 집계한다. `search_requests`·`search_failures`·`search_timeouts`는 요청 시도·HTTP/검증 실패·타임아웃 건수다. `phase`는 요청 시작 구간, `start_phase`는 검색 흐름 시작 구간, `page`는 첫 페이지와 후속 페이지를 구분한다. 구간·검색어별 하위 지표가 기본 요약에 나타나고, 원본 JSON에서는 첫 페이지·후속 페이지도 나눠 분석할 수 있다. 로그인은 `setup`으로 표시한다. 워밍업과 종료 후 `drain`은 본 측정과 분리한다.

200 VU를 미리 준비하고 최대 600 VU까지 허용한다. 검색 시작 속도는 서버 응답 지연에 맞춰 줄어들지 않는다. VU 부족으로 누락된 실행, 검증 실패, 타임아웃이 발생하면 실행을 실패로 표시한다. 종료 후 최대 3분 동안 진행 중인 탐색을 기다리므로 실제 실행은 10분을 초과할 수 있다. 시작·완료 흐름 건수와 interrupted iterations를 확인하며 미완료 흐름이 있으면 비교 결과에 함께 기록한다.

같은 조건으로 3회 개별 실행하고 매번 새 결과 폴더를 사용한다. 이전 탐색이 끝나고 CPU·연결 대기가 시험 전 수준으로 돌아온 뒤 다음 실행을 시작한다. 원본 JSON에는 요청별 측정값과 태그가 기록되므로 결과 파일은 임시 폴더에 보관한다. 콘솔에 표시되는 UTC 시험 시작 시각과 구간을 Grafana에 주석으로 남긴다. 이 스크립트는 API 지연을 측정하며 화면 렌더링을 포함한 1초 SLO는 별도 브라우저 측정으로 확인한다.

## API 부하 중 브라우저 검색 화면 측정 (#95)

`search_browser.js`는 기존 `search_load.js`의 API 부하 함수를 그대로 사용하고, 별도 시나리오에서 브라우저 사용자 3명의 검색을 측정한다. 프론트·백엔드 배포 코드는 수정하지 않는다. 브라우저는 기본적으로 headless Chromium을 사용하며 각 사용자에게 별도 쿠키·페이지를 할당한다.

| 전체 시험 경과 | API 부하 | 브라우저 측정 |
|---|---|---|
| 0~2분 | 워밍업 5회/초 | 대기 |
| 2~6분 | 지속 5회/초 | 대기 |
| 6~7분 | 지속 5회/초 | 3명 검색 |
| 7~8분 | 피크 10회/초 | 3명 검색 |
| 8~9분 | 회복 5회/초 | 3명 검색 |
| 9~10분 | 회복 5회/초 | 종료 |

브라우저 3명은 김민재·오버래핑·아약스를 번갈아 검색하고 결과 확인 후 2~5초 기다린다. API 부하의 검색·스크롤 요청에 브라우저 요청이 소량 추가된다. 전체 API 부하를 3분으로 축약하는 시험이 아니며 진행 중인 API 부하를 따로 다시 실행하면 중복 부하가 되므로, 이 스크립트만 실행한다. 로그인 준비는 3분 구간의 시작에 수행하고 측정값에서는 제외한다. 각 사용자에게 초기 전체 목록의 첫 페이지가 표시된 뒤 검색을 시작한다.

### 어떤 시간을 측정하는가?

시작은 검색 버튼의 브라우저 클릭 이벤트 시각이다. 종료는 해당 검색어의 정상 API 응답을 받은 뒤, 표시된 게시글 ID·제목·작성자가 새 응답과 일치하고 그리기 기회를 기다린 시각이다. 목록이 한번 비워졌는지도 확인해 이전 결과를 새 결과로 세지 않는다. 프로필 이미지의 다운로드·표시는 기다리지 않는다.

`browser_search_dom_ms`는 새 목록의 DOM 반영을 확인하기까지의 시간이다. `browser_search_display_ms`는 그 뒤 `requestAnimationFrame` 두 번을 추가로 기다린 **화면 표시 시간의 근사값**이다. 실제 픽셀이 디스플레이에 표시된 시각을 보장하는 값은 아니다. 프레임 주기·로컬 기기 부하·자동화 확인 비용도 측정값에 포함된다. 검색 API 시간이나 INP와 구분한다.

현재 서비스는 빈 검색 결과 문구를 제공하지 않는다. 아약스는 정상 응답의 `posts: []`와 비워진 목록을 확인한 뒤 같은 그리기 기준을 적용한다. 이를 ‘결과 없음 안내 문구 표시 완료’라고 표현하지 않는다. 페이지가 숨김 상태이면 정상 표본으로 처리하지 않는다.

### 실행

최신 k6와 Chromium 계열 브라우저(예: Google Chrome)가 필요하다. API 부하용 `bench81-001@example.test` 외에 브라우저용 `bench81-002@example.test`~`bench81-004@example.test`가 동일한 테스트 비밀번호를 사용하는 조건이다. 비밀번호와 로그인 응답은 결과 로그에 출력하지 않는다.

```bash
# 기본: 기존 10분 API 부하 + 6분 지연 후 브라우저 3명·3분
bash scripts/search/run_browser_load.sh
```

실행기는 기존 `SEARCH_BASE_URL`·`SEARCH_TEST_EMAIL`·`SEARCH_DATASET_SIZE` 환경 변수를 사용한다. 비밀번호는 `SEARCH_TEST_PASSWORD`, `SEARCH_PASSWORD_FILE`, 기존 로컬 캐시 `~/.cache/community-search/bench81-password`, 숨김 입력 순서로 준비한다. 다른 서버에서 실행할 때는 주소와 계정 조건을 먼저 확인한다.

첫 실행 전에는 짧은 브라우저 단독 확인으로 로그인·빈 목록 판별을 검증할 수 있다. 이 모드는 API 부하를 만들지 않는다.

```bash
SEARCH_BROWSER_ONLY=true SEARCH_BROWSER_SECONDS=15 \
  bash scripts/search/run_browser_load.sh
```

브라우저 초기화 포함 3분 동안 검색하고, 시작한 검색은 최대 15초 기다린다. 종료 시 30초의 여유를 두므로 프로세스 실행 시간은 측정 구간보다 길 수 있다. 실패한 검색은 실패와 1초 이내 완료 비율에 포함하고, 성공 시간 Trend에는 넣지 않는다. `browser_search_within_1s >= 0.99`를 통과 조건으로 확인하며, 각 브라우저 사용자와 검색어의 실제 검색 시도도 검사한다. 단, 초기 표본의 통과가 모든 사용자 환경의 99%를 보장하지는 않는다. 브라우저 사용자·검색어·구간별 표본 수와 세션 실패도 함께 확인한다.

### 결과

매 실행마다 임시 결과 폴더를 만든다. `requests.json`에는 API·브라우저 표본, `summary.json`에는 집계, `console.log`에는 브라우저별 측정값, `environment.txt`에는 실행 환경을 남긴다. 브라우저 버전은 준비 로그의 User-Agent에 기록한다.

| 지표 | 의미 |
|---|---|
| `browser_search_attempts` | 검색 시도 수 |
| `browser_search_display_ms` | 성공한 검색의 화면 표시 근사 시간, P50·P95·P99·최댓값 |
| `browser_search_dom_ms` | 새 결과의 DOM 반영 확인 시간 |
| `browser_search_within_1s` | 전체 시도 중 정상 완료하고 표시 근사 시간이 1초 이내인 비율 |
| `browser_search_failures` / `browser_search_timeouts` | 실패 / 그중 타임아웃 건수 |
| `browser_session_failures` | 로그인·초기 목록·브라우저 세션 준비 실패 |

검색별 `search_case`, 클릭 시점의 `phase`, `browser_user`로 나눠 본다. 세션 준비 실패와 중단된 실행이 있으면 완료 비율만 보고 성공으로 판단하지 않는다. 3명·3분은 초기 확인용이며, 표본 수를 함께 제시하고 99% 목표의 최종 검증과 구분한다. 브라우저와 API 부하를 같은 노트북에서 실행하므로 로컬 CPU·메모리 상태와 시험 환경을 기록한다. 이 결과를 브라우저가 없었던 이전 API 시험과 완전히 같은 조건의 반복으로 취급하지 않는다.


### API와 브라우저를 같은 조건으로 비교하기

검색 코드 개선 효과는 같은 API 부하를 변경 전후 각각 실행해 비교한다. 브라우저 측정은 같은 검색의 API 구간과 목록 표시 구간을 함께 기록해 사용자 대기 시간을 분리한다. 두 목적의 결과를 한 개선율로 합치지 않는다.

**부하 생성용 API와 브라우저 표본의 조건**은 기본 6~9분·같은 검색어·첫 페이지 5건으로 맞춘다. `search_load.js`는 기존 전체 검색·스크롤 지표를 유지하면서 이 조건에 해당하는 요청을 `search_compare_*`로 별도 집계한다. API 전용과 통합 실행 모두 같은 집계 규칙을 쓴다. 비교 구간을 바꿀 경우 두 실행에 동일한 `SEARCH_BROWSER_START_SECONDS`와 `SEARCH_BROWSER_SECONDS`를 제공한다. 통합 시험의 브라우저 요청은 추가 부하이므로 API 전용 시험과 부하가 완전히 같다고 표현하지 않는다.

| 구분 | 지표 | 표본과 범위 |
|---|---|---|
| API 전체 부하 결과 | `search_duration` | 기존 10분 전체 검색·스크롤, 워밍업·종료 후 대기는 분리 |
| 조건을 맞춘 API 표본 | `search_compare_api_ms` | 6~9분에 시작한 첫 페이지 검색, 송신·대기·수신 시간 |
| API 호출 전체 시간 | `search_compare_http_call_ms` | 같은 API 표본의 HTTP 함수 호출 시작~반환, 연결 준비 등 포함 |
| 브라우저 자체의 API 시간 | `browser_search_api_ms` | 해당 브라우저 검색의 Resource Timing `requestStart`~`responseEnd` |
| 클릭~응답 수신 | `browser_search_click_to_response_ms` | 같은 검색의 클릭 이벤트~응답 본문 수신 완료 |
| 응답 수신~목록 표시 | `browser_search_after_response_ms` | 같은 응답의 수신 완료~목록 표시 근사 시점 |
| 클릭~목록 표시 | `browser_search_display_ms` | 같은 검색 전체 구간 |

브라우저는 URL과 요청 시작 시각으로 해당 요청의 Resource Timing을 식별한다. 그 요청을 식별하지 못하면 유효한 표시 시간으로 집계하지 않는다. 각 `browser_sample`에 `sample_id`·클릭 시각·API 구간·응답 후 구간·표시 시간을 함께 저장한다. 표본별로 `표시 시간 = 요청 전 시간 + API 시간 + 응답 후 시간`이며, 정상 표본에서 응답 수신 이전에 표시 완료로 판정하지 않도록 검사한다.

요청 전 시간은 `browser_search_before_request_ms`에 기록하며 클릭 이벤트~네트워크 요청 시작까지다. `browser_search_after_response_ms`에는 JSON 처리, 목록 반영, 테스트의 확인 비용 및 그리기 주기 대기가 포함되므로 순수 렌더링 시간으로 부르지 않는다. 프로필 이미지 제외·headless 브라우저라는 측정 조건은 이전과 같다.

검색어·지속/피크/회복·첫 페이지 조건이 같은 API 표본과 브라우저 표본을 비교하되 서로 다른 요청 집합임을 표시한다. API와 화면의 차이를 분해할 때는 **같은 `sample_id`의 값끼리** 사용한다. API P95와 화면 P95를 빼서 화면 처리 P95를 계산하지 않는다. 10분 전체·스크롤 포함 API 통계와 3분 첫 페이지 브라우저 통계를 직접 비교하지 않는다.
