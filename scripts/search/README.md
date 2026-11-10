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
