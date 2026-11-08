#!/bin/bash
set -euo pipefail
exec > >(tee -a /var/log/community-search-bootstrap.log) 2>&1

export DEBIAN_FRONTEND=noninteractive
apt-get -o Acquire::Retries=3 update
apt-get install -y ca-certificates curl python3

install -m 0755 -d /etc/apt/keyrings
curl --fail --show-error --silent --location \
  https://download.docker.com/linux/ubuntu/gpg \
  -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

cat > /etc/apt/sources.list.d/docker.sources <<'DOCKER'
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: noble
Components: stable
Architectures: amd64
Signed-By: /etc/apt/keyrings/docker.asc
DOCKER

apt-get -o Acquire::Retries=3 update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker

if systemctl cat amazon-ssm-agent.service >/dev/null 2>&1; then
  systemctl enable --now amazon-ssm-agent.service
else
  if ! snap list amazon-ssm-agent >/dev/null 2>&1; then
    snap install amazon-ssm-agent --classic
  fi
  snap start --enable amazon-ssm-agent
fi

# 서버 IAM 역할로 Parameter Store와 ECR을 사용할 AWS CLI를 설치한다.
if ! snap list aws-cli >/dev/null 2>&1; then
  snap install aws-cli --classic
fi
export PATH="/snap/bin:$PATH"

aws --version
docker --version
docker compose version
