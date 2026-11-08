client_max_body_size 10m;
access_log /dev/stdout search;
error_log /dev/stderr warn;

root /srv/community-frontend;

proxy_http_version 1.1;
proxy_set_header Connection "";
proxy_set_header Host $http_host;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $remote_addr;
proxy_set_header X-Forwarded-Proto $scheme;

# 검색 성능의 기준 측정에 Nginx 캐시가 개입하지 않도록 한다.
proxy_cache off;
proxy_connect_timeout 5s;
proxy_read_timeout 60s;
proxy_send_timeout 60s;

location = / {
    return 302 /html/index.html;
}

location = /config.js {
    default_type application/javascript;
    add_header Cache-Control no-store;
    return 200 'window.__APP_CONFIG__ = { API_BASE_URL: "/api", CDN_BASE_URL: "/api/images/public" };';
}

location = /favicon.ico {
    try_files $uri =404;
}

location ~ /\. {
    return 404;
}

location ~ ^/(html|css|js|component|public|requests|utils)/ {
    try_files $uri =404;
}

location /api/ {
    # 브라우저의 /api/auth 요청을 백엔드의 /auth로 전달한다.
    proxy_pass http://community_backend/;
}

location / {
    # 기존 API 경로와 /health/ready를 사용하는 도구도 그대로 연결한다.
    proxy_pass http://community_backend;
}
