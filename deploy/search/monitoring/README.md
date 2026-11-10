# 검색 실험 모니터링

기존 검색 VPC에 `t3a.small` EC2(메모리 2GiB)를 한 대 구성한다. Grafana·Prometheus·Loki는 이 EC2에서 실행하고, 백엔드와 MySQL EC2에는 Alloy를 한 개씩 실행한다. Alloy는 호스트 지표와 Docker 로그를 수집한다. 백엔드 Alloy만 기존 백엔드 Compose 네트워크에서 Spring 지표를 읽는다.

Prometheus와 Loki는 모니터링 EC2의 사설 IP에 바인딩되며, 백엔드·MySQL 보안 그룹에서만 들어올 수 있다. Grafana는 루프백에 바인딩하고 SSM으로 접속한다. EC2의 공인 IP는 SSM 및 패키지·이미지 다운로드에 사용한다. 공개 인바운드 규칙은 없다.

## 배포 순서

1. Terraform plan에서 모니터링 리소스 생성만 확인하고 적용한다. 모니터링 EC2에서 `sudo cloud-init status --wait`가 성공해야 한다.
2. #84가 머지된 백엔드 CD가 성공했는지 확인한다. 새 백엔드 이미지는 Docker 내부의 `8081/actuator/prometheus`에서 지표를 제공한다.
3. 아래와 같이 레포 루트에서 같은 커밋의 모니터링 파일을 세 EC2에 전송한다. AWS CLI 자격 증명과 `jq`가 필요하다.

```bash
monitor=$(terraform -chdir=infra/terraform output -json monitoring)
monitor_id=$(printf '%s' "$monitor" | jq -r .instance_id)
monitor_ip=$(printf '%s' "$monitor" | jq -r .private_ip)
backend_id=$(terraform -chdir=infra/terraform output -json backend | jq -r .instance_id)
mysql_id=$(terraform -chdir=infra/terraform output -json mysql | jq -r .instance_id)

bundle=$(git archive --format=tar.gz HEAD deploy/search/monitoring | base64 | tr -d '\n')
parameters=$(MONITOR_BUNDLE="$bundle" python3 -c '
import json, os
bundle = os.environ["MONITOR_BUNDLE"]
print(json.dumps({"commands": [
    "set -eu",
    "install -d /opt/community-search",
    "printf %s " + bundle + " | base64 -d | tar -xzf - -C /opt/community-search",
]}))
')
command_id=$(aws ssm send-command --region ap-northeast-2 \
  --document-name AWS-RunShellScript --instance-ids "$monitor_id" "$backend_id" "$mysql_id" \
  --parameters "$parameters" --query Command.CommandId --output text)
(
set -e
for instance_id in "$monitor_id" "$backend_id" "$mysql_id"; do
  aws ssm wait command-executed --region ap-northeast-2 \
    --command-id "$command_id" --instance-id "$instance_id"
done
printf '모니터링 사설 IP %s\n' "$monitor_ip"
)
```

4. 모니터링 EC2의 SSM 셸에서 Grafana 비밀번호를 최초 1회 생성하고 스택을 실행한다. `<모니터링 사설 IP>`에는 위 값을 넣는다.

```bash
cd /opt/community-search/deploy/search/monitoring
sudo bash -c '
  set -eu
  install -d -m 0750 /var/lib/community-search/monitoring
  file=/var/lib/community-search/monitoring/grafana-password
  if [ ! -s "$file" ]; then
    umask 077
    openssl rand -hex 24 > "$file"
    chown 472 "$file"
    chmod 0400 "$file"
  fi
'
sudo env MONITOR_PRIVATE_IP='<모니터링 사설 IP>' \
  GRAFANA_PASSWORD_FILE=/var/lib/community-search/monitoring/grafana-password \
  docker compose -f server.compose.yaml up -d --force-recreate
```

5. 백엔드와 MySQL EC2의 SSM 셸에서 각각 Alloy를 실행한다.

```bash
# 백엔드 EC2
cd /opt/community-search/deploy/search/monitoring
sudo env MONITOR_PRIVATE_IP='<모니터링 사설 IP>' MONITOR_ROLE=backend \
  docker compose -f agent.compose.yaml -f agent-backend.compose.yaml up -d --force-recreate
```

```bash
# MySQL EC2
cd /opt/community-search/deploy/search/monitoring
sudo env MONITOR_PRIVATE_IP='<모니터링 사설 IP>' MONITOR_ROLE=mysql \
  docker compose -f agent.compose.yaml up -d --force-recreate
```

## 접속과 본시험 전 확인

배포 후 로컬에서 Session Manager 플러그인을 준비하고, 레포 루트에서 다음 명령으로 접속 명령을 확인한다.

```bash
terraform -chdir=infra/terraform output -json monitoring | jq -r .grafana_forward
```

출력된 AWS CLI 명령을 실행한 터미널을 열어 둔 채 브라우저에서 `http://127.0.0.1:13000`을 연다. `admin`과 모니터링 EC2에서 보관하는 비밀번호로 로그인한다.

- **Community Search → 커뮤니티 검색 실험** 대시보드에서 두 EC2의 CPU·메모리, MySQL EC2의 NVMe 디스크 읽기·쓰기, HikariCP 연결 대기, 게시글 목록 API P95를 확인한다. 목록 API 패널은 같은 경로의 일반 목록과 검색 요청을 함께 집계한다. 검색 요청만의 응답시간은 k6 결과로 비교한다.
- Grafana에서 백엔드·Nginx·MySQL 컨테이너 로그가 들어오는지 확인한다. MySQL 슬로우 쿼리 로그는 별도 설정으로 수집할 때 추가한다.
- 모니터링 EC2에서 `free -h`, `sudo docker stats --no-stream`, `sudo docker inspect --format '{{.Name}} {{.RestartCount}} {{.State.OOMKilled}}' $(sudo docker ps -aq --filter label=com.docker.compose.project=community-search-monitoring)`로 메모리 여유와 재시작을 확인한다. 현재 EC2는 메모리 2GiB이며 컨테이너 상한은 Grafana 768MiB, Prometheus·Loki 각각 256MiB다. 상한의 합은 1,280MiB로 운영체제와 기타 프로세스의 여유를 남긴다. 상한은 예약량이 아니므로 실제 사용량과 제한 도달 여부를 함께 확인한 뒤 본시험을 진행한다.

설정 파일을 갱신한 뒤에는 같은 Compose 명령으로 컨테이너를 다시 만든다. 기존 데이터 볼륨을 재사용한다. Prometheus와 Loki는 72시간을 보관하고, Prometheus에는 TSDB 크기 제한도 둔다. 수집 데이터는 EC2의 Docker 볼륨에 저장된다. 새 노트북에서는 SSM 접속을 다시 열면 된다. 새 EC2에서도 레포의 Compose·프로비저닝 파일로 같은 대시보드를 만들 수 있으며, 이전 시계열과 로그를 옮기려면 저장 볼륨을 별도로 이전해야 한다.
