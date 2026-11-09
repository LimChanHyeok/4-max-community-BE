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
