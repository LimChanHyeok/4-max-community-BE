#!/usr/bin/env bash

set -Eeuo pipefail

AWS_REGION="ap-northeast-2"
S3_JAR_URI="s3://community-board-deploy-prod/backend/community.jar"

# Backend EC2 Instance ID로 바꿔야 함
BACKEND_INSTANCE_ID="i-05d18c0c281746825"

echo "1. Spring Boot JAR을 빌드합니다."
./gradlew clean bootJar -x test

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

echo "빌드된 JAR: $JAR_FILE"

echo "2. JAR을 S3 배포 버킷에 업로드합니다."
aws s3 cp "$JAR_FILE" "$S3_JAR_URI" --region "$AWS_REGION"

echo "S3 업로드 완료: $S3_JAR_URI"

echo "3. Backend EC2에 SSM Run Command로 배포 명령을 보냅니다."

COMMAND_ID=$(aws ssm send-command \
  --region "$AWS_REGION" \
  --instance-ids "$BACKEND_INSTANCE_ID" \
  --document-name "AWS-RunShellScript" \
  --comment "Deploy community backend jar from S3" \
  --parameters 'commands=[
    "aws s3 cp s3://community-board-deploy-prod/backend/community.jar /home/ubuntu/community/backend/community-new.jar --region ap-northeast-2",
    "sudo /home/ubuntu/community/scripts/restart.sh"
  ]' \
  --query "Command.CommandId" \
  --output text)

echo "SSM Command ID: $COMMAND_ID"

echo "4. 배포 명령 실행 결과를 기다립니다."
sleep 10

aws ssm get-command-invocation \
  --region "$AWS_REGION" \
  --command-id "$COMMAND_ID" \
  --instance-id "$BACKEND_INSTANCE_ID" \
  --query '{Status:Status, StandardOutputContent:StandardOutputContent, StandardErrorContent:StandardErrorContent}' \
  --output text

echo "배포 요청이 완료되었습니다."