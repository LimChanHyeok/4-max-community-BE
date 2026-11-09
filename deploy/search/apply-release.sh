#!/bin/bash
set -euo pipefail

export AWS_PAGER=""
deploy_root=/opt/community-search/backend
digest=${SSM_ImageDigest:-}
frontend_sha=${SSM_FrontendSha:-}
if [[ ( -z "$digest" && -z "$frontend_sha" ) || ( -n "$digest" && ! "$digest" =~ ^sha256:[0-9a-f]{64}$ ) || ( -n "$frontend_sha" && ! "$frontend_sha" =~ ^[0-9a-f]{40}$ ) ]]; then
    echo "이미지 다이제스트 또는 프론트 커밋 형식이 올바르지 않습니다." >&2
    exit 1
fi
public_ip=${SEARCH_PUBLIC_IP:?고정 IP가 필요합니다.}
export BACKEND_DB_PASSWORD_FILE=/var/lib/community-search/backend/db-password
export BACKEND_JWT_SECRET_FILE=/var/lib/community-search/backend/jwt-secret

exec 9>"$deploy_root/.deploy.lock"
flock -w 300 9 || { echo "다른 검색 배포가 끝나기를 기다리다 시간이 초과됐습니다." >&2; exit 1; }

previous=$(docker inspect --format '{{index .Config.Labels "com.docker.compose.project.working_dir"}}' community-search-nginx)
previous_image=$(docker inspect --format '{{.Config.Image}}' community-search-backend)
image=$previous_image
services=(nginx)
if [[ -n "$digest" ]]; then
    image="${SEARCH_IMAGE_REPOSITORY:?ECR 저장소가 필요합니다.}@$digest"
    services=(backend nginx)
fi
if [[ "$previous" != "$deploy_root/"* || ! -d "$previous" ]]; then
    echo "기존 검색 서버의 Compose 배포 경로를 확인할 수 없습니다." >&2
    exit 1
fi
test -s "$BACKEND_DB_PASSWORD_FILE"
test -s "$BACKEND_JWT_SECRET_FILE"
test -s /etc/letsencrypt/live/community-search/fullchain.pem
docker image inspect "$previous_image" >/dev/null

compose() {
    local dir=$1 image_ref=$2
    shift 2
    (
        cd -- "$dir"
        BACKEND_IMAGE="$image_ref" BACKEND_ENV_FILE="$dir/backend.runtime.env" \
            docker compose -f backend.compose.yaml -f frontend.compose.yaml -f https.compose.yaml "$@"
    )
}

release=$(mktemp -d "$deploy_root/cd.XXXXXXXX")
switched=0
cleanup() {
    local result=$?
    trap - EXIT
    if (( result != 0 )); then
        if (( switched )) && ! compose "$previous" "$previous_image" up -d --no-deps --pull never --wait --wait-timeout 150 "${services[@]}"; then
            echo "이전 컨테이너 복구 실패. 현재 배포 디렉터리 $release 를 보존합니다." >&2
            exit "$result"
        fi
        if (( switched )); then echo "이전 컨테이너로 복구했습니다." >&2; fi
        rm -rf -- "$release"
    fi
    exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

cp -- "$previous/backend.compose.yaml" "$previous/frontend.compose.yaml" "$previous/https.compose.yaml" "$release/"
cp -a -- "$previous/nginx" "$release/nginx"
install -m 600 "$previous/backend.runtime.env" "$release/backend.runtime.env"

if [[ -n "$frontend_sha" ]]; then
    repo=4-max-community-FE
    prefix="$repo-$frontend_sha"
    mkdir -m 755 "$release/frontend"
    curl --fail --silent --show-error --location --retry 2 --max-time 60 \
        --output "$release/frontend.tar.gz" "https://codeload.github.com/100-hours-a-week/$repo/tar.gz/$frontend_sha"
    tar -xzf "$release/frontend.tar.gz" --strip-components=1 -C "$release/frontend" \
        "$prefix/html" "$prefix/css" "$prefix/js" "$prefix/component" \
        "$prefix/public" "$prefix/requests" "$prefix/utils" "$prefix/favicon.ico"
    rm -f -- "$release/frontend.tar.gz"
    printf '%s\n' "$frontend_sha" > "$release/frontend.sha"
else
    ln -s -- "$(realpath "$previous/frontend")" "$release/frontend"
    if [[ -f "$previous/frontend.sha" ]]; then
        cp -- "$previous/frontend.sha" "$release/frontend.sha"
    fi
fi
test -s "$release/frontend/html/index.html"
compose "$release" "$image" config --quiet

if [[ -n "$digest" ]]; then
    # 로그인 파일은 이미지 다운로드에만 쓰고 저장하지 않는다.
    mkdir -m 700 "$release/.docker"
    aws ecr get-login-password --region "$AWS_REGION" |
        DOCKER_CONFIG="$release/.docker" docker login --username AWS --password-stdin "${SEARCH_IMAGE_REPOSITORY%%/*}"
    DOCKER_CONFIG="$release/.docker" docker pull "$image"
    rm -rf -- "$release/.docker"
fi

switched=1
compose "$release" "$image" up -d --no-deps --pull never --wait --wait-timeout 180 "${services[@]}"
docker exec community-search-nginx nginx -t
probe=(curl --fail --silent --show-error --retry 3 --retry-connrefused --max-time 8
       --connect-to "$public_ip:443:127.0.0.1:443")
if ! ready=$("${probe[@]}" "https://$public_ip/api/health/ready") || [[ "$ready" != READY ]]; then
    echo "HTTPS API 준비 상태 확인에 실패했습니다." >&2
    exit 1
fi
"${probe[@]}" --output /dev/null "https://$public_ip/html/index.html"
printf 'HTTPS 확인 완료 · https://%s/html/index.html\n백엔드 %s\n' "$public_ip" "$image"
printf 'PUBLIC_HTTPS_ORIGIN=https://%s\n' "$public_ip"
if [[ -f "$release/frontend.sha" ]]; then
    printf '프론트 %s\n' "$(cat "$release/frontend.sha")"
fi
