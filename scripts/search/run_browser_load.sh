#!/bin/bash
# 기존 API 부하와 브라우저 화면 측정을 한 번에 실행한다.
set -euo pipefail
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"
repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
cd -- "$repo_dir"
command -v k6 >/dev/null || { printf 'k6를 먼저 설치하세요.\n' >&2; exit 1; }
export SEARCH_BASE_URL="${SEARCH_BASE_URL:-https://54.116.191.172}"
export SEARCH_TEST_EMAIL="${SEARCH_TEST_EMAIL:-bench81-001@example.test}"
export SEARCH_DATASET_SIZE="${SEARCH_DATASET_SIZE:-10000}"
password_file="${SEARCH_PASSWORD_FILE:-$HOME/.cache/community-search/bench81-password}"
if [ -z "${SEARCH_TEST_PASSWORD:-}" ] && [ -r "$password_file" ]; then
    SEARCH_TEST_PASSWORD=$(cat -- "$password_file")
fi
if [ -z "${SEARCH_TEST_PASSWORD:-}" ]; then
    read -r -s -p '테스트 계정 비밀번호: ' SEARCH_TEST_PASSWORD
    printf '\n'
fi
[ -n "$SEARCH_TEST_PASSWORD" ] || { printf '비밀번호가 필요합니다.\n' >&2; exit 1; }
export SEARCH_TEST_PASSWORD
trap 'unset SEARCH_TEST_PASSWORD' EXIT
start_seconds="${SEARCH_BROWSER_START_SECONDS:-360}"
if [ "${SEARCH_BROWSER_ONLY:-false}" = true ]; then start_seconds=0; fi
run_dir=$(mktemp -d "${TMPDIR:-/tmp}/community-search-browser.XXXXXX")
printf '대상 %s\n결과 폴더 %s\n' "$SEARCH_BASE_URL" "$run_dir"
{
    date -u '+UTC 실행 시각 %Y-%m-%dT%H:%M:%SZ'
    k6 version
    printf '측정 스크립트 기준 커밋 '; git rev-parse HEAD
    printf '대상 %s · 데이터 %s건\n' "$SEARCH_BASE_URL" "$SEARCH_DATASET_SIZE"
    printf '측정 스크립트 미커밋 변경\n'
    git status --short -- scripts/search/search_browser.js scripts/search/search_load.js scripts/search/run_browser_load.sh
    printf '기기 아키텍처 '; uname -m
    printf '브라우저 사용자 %s · 측정 %s초 · 브라우저 단독 %s\n' \
        "${SEARCH_BROWSER_USERS:-3}" "${SEARCH_BROWSER_SECONDS:-180}" "${SEARCH_BROWSER_ONLY:-false}"
    printf '브라우저 시작 지연 %s초 · viewport 1280x900 · headless %s\n' \
        "$start_seconds" "${K6_BROWSER_HEADLESS:-true}"
} > "$run_dir/environment.txt"
set +e
k6 run --out "json=$run_dir/requests.json" \
    --summary-export "$run_dir/summary.json" \
    scripts/search/search_browser.js 2>&1 | tee "$run_dir/console.log"

result=${PIPESTATUS[0]}
set -e
printf '\nk6 종료 코드 %s\n결과 폴더 %s\n' "$result" "$run_dir"
exit "$result"
