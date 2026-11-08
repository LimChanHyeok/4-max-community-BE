#!/bin/sh
set -eu

# 다른 이름으로 발급한 인증서를 갱신할 때는 검색용 Nginx를 건드리지 않는다.
[ "${RENEWED_LINEAGE:-}" = /etc/letsencrypt/live/community-search ] || exit 0
docker exec community-search-nginx nginx -t
docker exec community-search-nginx nginx -s reload
