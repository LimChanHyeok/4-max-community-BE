#!/usr/bin/env bash

set -Eeuo pipefail

# EC2 접속 정보
EC2_USER="ubuntu"
EC2_HOST="52.79.250.142"
PEM_KEY="$HOME/Desktop/pemkey/community-key.pem"

# EC2에 새 버전으로 업로드할 경로
REMOTE_NEW_JAR="/home/ubuntu/community/backend/community-new.jar"

# EC2에서 실행할 재배포 스크립트
REMOTE_RESTART_SCRIPT="/home/ubuntu/community/scripts/restart.sh"

echo "1. Spring Boot JAR을 빌드합니다."
./gradlew clean bootJar -x test

# -plain.jar를 제외한 실행 가능한 Spring Boot JAR 검색
JAR_FILE=$(find build/libs \
    -maxdepth 1 \
    -type f \
    -name "*.jar" \
    ! -name "*-plain.jar" \
    | head -n 1)

if [ -z "$JAR_FILE" ]; then
    echo "빌드된 실행 JAR을 찾지 못했습니다."
    exit 1
fi

if [ ! -f "$PEM_KEY" ]; then
    echo "PEM 키를 찾지 못했습니다."
    echo "확인 경로: $PEM_KEY"
    exit 1
fi

echo "빌드된 JAR: $JAR_FILE"

echo "2. 새 JAR을 EC2에 업로드합니다."
scp -i "$PEM_KEY" \
    "$JAR_FILE" \
    "$EC2_USER@$EC2_HOST:$REMOTE_NEW_JAR"

echo "3. EC2에서 새 JAR로 교체하고 서비스를 재시작합니다."
ssh -i "$PEM_KEY" \
    "$EC2_USER@$EC2_HOST" \
    "sudo $REMOTE_RESTART_SCRIPT"

echo "배포가 완료되었습니다."
