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
