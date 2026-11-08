# 검색 실험 서버 실행 설정

## MySQL

MySQL EC2의 실행 설정은 `mysql.compose.yaml`로 관리한다. 설정 전달과 Compose 실행은 후속 GitHub Actions CD에서 AWS SSM을 통해 자동화한다. 현재 CD는 아직 연결하지 않았다.

| 항목 | 설정 |
|---|---|
| 이미지 | 공식 MySQL 8.4.13 · linux/amd64 · 다이제스트 고정 |
| 데이터 | EBS `/srv/mysql` 아래의 `data/` → 컨테이너 `/var/lib/mysql` |
| DB · 앱 계정 | 기본값은 모두 `community_search` · `DB_NAME`, `DB_USERNAME`으로 변경 가능 |
| 접속 | MySQL EC2의 사설 IP:3306 · 보안 그룹에서 백엔드만 허용 |
| 비밀번호 | Parameter Store에서 준비할 파일을 Compose secrets로 전달 |
| 초기 테이블 | 백엔드 시작 시 Flyway가 생성 |

### 실행 전에 필요한 것

Terraform의 초기 설정이 끝나 Docker·Compose가 설치되고 `/srv/mysql`이 EBS 마운트 지점이어야 한다. 서버 최초 부팅 시 Terraform의 초기 설정이 Parameter Store에서 값을 읽어 아래 파일을 준비한다. 서버 생성 전에 파라미터 등록을 마쳐야 한다.

| Parameter Store 이름 | 서버의 파일 경로 |
|---|---|
| `/community/search/db/password` | `/var/lib/community-search/mysql/db-password` |
| `/community/search/mysql/root-password` | `/var/lib/community-search/mysql/root-password` |

비밀번호 파일은 root 소유·권한 `600`으로 준비하고, 보관 디렉터리는 root만 접근하도록 권한 `700`을 사용한다. 파일은 재부팅 후에도 유지되는 서버 루트 볼륨의 `/var/lib/community-search/mysql/`에 보관하고 Git에 저장하지 않는다. 최초 조회·저장은 서버 초기 설정에 연결했다. 재부팅마다 조회하지 않는다.

### CD에서 처리할 작업

별도 수동 실행 스크립트를 두지 않고, 후속 CD에서 아래 순서를 실행한다.

1. 버전 관리된 Compose 파일을 MySQL 서버에 전달한다.
2. EBS 마운트와 비밀번호 파일을 확인하고 `/srv/mysql/data` 디렉터리를 준비한다.
3. `MYSQL_BIND_IP`에 MySQL 사설 IP를, `MYSQL_PASSWORD_FILE`·`MYSQL_ROOT_PASSWORD_FILE`에 위 파일 경로를 전달한다.
4. Compose 설정 검증 후 `up --detach --wait`로 변경을 적용하고 DB 준비 완료를 확인한다.

CD의 사전 확인·실행 명령은 하나라도 실패하면 다음 단계로 진행하지 않도록 구성한다. MySQL 설정이 바뀐 경우에만 MySQL 배포를 실행하며, 백엔드 배포 때마다 DB 컨테이너를 다시 만들지 않는다. 실제 앱 계정의 `SELECT 1` 성공 여부는 Compose 헬스체크에서 확인한다.

### 초기화와 재시작

최초 배포와 실행 설정 변경은 CD에서 Compose를 실행해 반영한다. 이후 컨테이너 종료·서버 재부팅에 따른 자동 재시작은 Compose의 `restart: unless-stopped`가 담당한다. 별도 MySQL 재시작 스크립트나 서비스는 만들지 않는다. 직접 중지한 컨테이너는 자동으로 다시 시작하지 않는다.

MySQL EC2의 초기 설정에서 Docker 서비스에 `/srv/mysql` 마운트 의존성과 마운트 확인을 추가한다. 재부팅 시 EBS가 마운트된 뒤 Docker가 시작돼야 기존 데이터로 컨테이너를 올릴 수 있다.

데이터 디렉터리가 비어 있는 최초 실행에서만 공식 이미지가 DB와 앱 계정을 생성한다. 앱 계정에는 해당 DB의 권한이 주어져 Flyway도 같은 계정으로 실행할 수 있다. root 계정은 컨테이너 내부 관리용으로 사용한다.

기존 데이터가 있으면 그대로 사용한다. 비밀번호 파일이나 DB 이름을 바꿔도 기존 계정·DB를 자동으로 변경하지 않으므로, 계정 변경은 별도 SQL로 적용한다. 데이터 재초기화를 위해 `/srv/mysql/data`를 삭제하지 않는다.

상태와 로그는 비밀번호를 출력하지 않고 확인한다.

```bash
sudo docker ps --all --filter label=com.docker.compose.project=community-search-mysql
sudo docker logs --tail 100 community-search-mysql
```

위 명령은 Compose 파일을 읽지 않고 컨테이너 상태와 로그를 조회한다.

### 참고

- [공식 MySQL 이미지의 초기화·비밀번호 파일 설정](https://hub.docker.com/_/mysql)
- [Docker Compose의 바인드 마운트·헬스체크 설정](https://docs.docker.com/reference/compose-file/services/)
- [Docker 컨테이너 자동 재시작 정책](https://docs.docker.com/engine/containers/start-containers-automatically/)

## 백엔드와 Nginx

백엔드 EC2에서는 `backend.compose.yaml`과 `nginx/default.conf`를 함께 사용한다. 외부에는 Nginx의 80·443번 포트를 열고, 백엔드의 8080번 포트는 Docker 내부 네트워크에서 사용한다.

`HTTPS 요청 → Nginx:443 → backend:8080 → MySQL EC2 사설 IP:3306`

기존 `/posts`, `/auth`, `/users`, `/health` 경로를 그대로 전달한다. Nginx에는 응답 캐시를 사용하지 않고 전체 처리 시간과 백엔드 응답 시간을 기록한다. 백엔드 컨테이너 IP가 바뀌면 Docker DNS를 통해 다시 조회한다.

### 같은 주소에서 프론트 제공하기

Nginx는 `/`에서 프론트 화면을 제공하고 `/api/` 요청은 앞부분을 제거해 백엔드로 전달한다. 예를 들어 브라우저가 `/api/posts?size=10`을 호출하면 백엔드는 `/posts?size=10`을 받는다. 프론트가 불러오는 `/config.js`는 Nginx에서 제공한다.

프론트 저장소의 검토한 커밋에서 아래 공개 파일만 추려, 백엔드 EC2의 `backend.compose.yaml` 옆 `frontend/`에 배치한다. 저장소 전체나 환경 파일을 웹 디렉터리에 복사하지 않는다.

```bash
git archive --format=tar -o /tmp/community-search-frontend.tar HEAD html css js component public requests utils favicon.ico
```

아카이브를 `frontend/`에 풀고 기존 실행 명령에서 `-f backend.compose.yaml -f frontend.compose.yaml`을 함께 지정한다. 기본 Compose 파일만 지정해도 백엔드 API와 헬스체크를 검증할 수 있다.

이미지 키는 `/api/images/public/` 아래에서 조회한다. 검색 DB에 등록된 이미지라면 백엔드가 5분 동안 유효한 S3 URL로 안내한다. 버킷은 비공개로 유지하며 기존 이미지 업로드 방식은 그대로 사용한다.



`backend.env.example`은 비밀값을 제외한 설정 예시다. 실제 파일은 서버에 `backend.runtime.env`로 준비하고, MySQL 사설 IP와 필요한 프론트 출처를 반영한다. 실제 실행 파일은 Git에서 제외한다.

| Compose에 전달할 변수 | 용도 |
|---|---|
| `BACKEND_IMAGE` | ECR 이미지의 고유 태그 또는 다이제스트를 포함한 주소 |
| `BACKEND_ENV_FILE` | 서버의 `backend.runtime.env` 절대 경로 |
| `BACKEND_DB_PASSWORD_FILE` | `/var/lib/community-search/backend/db-password` |
| `BACKEND_JWT_SECRET_FILE` | `/var/lib/community-search/backend/jwt-secret` |

서버 최초 부팅 시 DB 비밀번호는 `/community/search/db/password`, JWT 키는 `/community/search/backend/jwt-secret`에서 읽어 위 파일로 저장한다. 서버 생성 전에 두 값을 등록해야 한다.

백엔드는 UID·GID `10001`의 앱 계정으로 실행한다. 두 비밀값 파일은 서버에서 소유자 `root`, 그룹 `10001`, 권한 `640`으로 준비하고, 보관 디렉터리는 root 소유·권한 `700`을 사용한다. Compose가 파일을 `/run/secrets/DB_PASSWORD`, `/run/secrets/JWT_SECRET`에 마운트하고 Spring Boot가 `configtree:`로 읽는다. 비밀번호와 JWT 키를 일반 환경설정 파일에 적지 않는다.

### 고정 IP에서 HTTPS 제공하기

1. Terraform으로 Elastic IP와 443번 인바운드 규칙을 적용한다. Terraform의 `backend.public_ip`를 서버 실행 환경의 `SEARCH_PUBLIC_IP`로 설정한다.
2. 백엔드 EC2에서 `/var/www/letsencrypt`를 준비하고 기본 Compose와 프론트 오버레이로 HTTP Nginx를 시작한다. `/.well-known/acme-challenge/`가 외부에서 접근 가능해야 한다.
3. EC2에서 Certbot 5.4 이상을 설치하고 아래와 같이 IP 인증서를 발급한다. 실제 발급 전에 `--dry-run`을 붙여 HTTP 검증 경로를 확인한다.

```bash
sudo /snap/bin/certbot certonly --non-interactive --agree-tos --register-unsafely-without-email \
  --preferred-profile shortlived --webroot -w /var/www/letsencrypt \
  --cert-name community-search --ip-address "$SEARCH_PUBLIC_IP"
```

4. `nginx/https.conf.template`의 `__SEARCH_PUBLIC_IP__`를 실제 고정 IP로 치환해 `/var/lib/community-search/nginx/https.conf`에 저장한다. `backend.runtime.env`의 `APP_CORS_ALLOWED_ORIGIN`을 고정 IP의 HTTPS 주소로 바꾸고 `APP_COOKIE_SECURE=true`를 추가한다.
5. Compose 실행 시 기존 파일 뒤에 `-f https.compose.yaml`을 붙인다. Nginx는 발급된 인증서를 읽어 443에서 서비스하며 80에서는 HTTP 인증 경로를 제공하고 나머지 요청은 고정 IP의 HTTPS로 안내한다.
6. `nginx/reload-after-renewal.sh`를 `/etc/letsencrypt/renewal-hooks/deploy/community-search-nginx.sh`에 권한 `755`로 설치한다. `snap.certbot.renew.timer`가 활성화되었는지 확인하고 `sudo /snap/bin/certbot renew --cert-name community-search --dry-run --run-deploy-hooks`로 갱신과 Nginx 재로드를 검증한다.

인증서와 키는 EC2의 `/etc/letsencrypt`에 저장된다. Nginx 컨테이너는 갱신된 파일을 읽기 전용으로 마운트하고, 갱신 후 검증을 거쳐 설정을 다시 읽는다.

### 상태 확인과 배포 연결

백엔드 헬스체크는 `/health/ready`에서 DB 연결을 확인한다. Nginx는 최초 Compose 실행 시 백엔드가 준비된 뒤 시작하며, Nginx 헬스체크도 같은 요청을 프록시해 전체 경로를 확인한다. 두 컨테이너 모두 `unless-stopped`로 재시작한다.

후속 CD에서 이미지 로그인·다운로드, 실행 설정과 비밀값 준비, Nginx 설정 검증 후 Compose 변경 적용을 연결한다. 별도 수동 실행 스크립트는 만들지 않는다.

- [Spring Boot의 파일 기반 비밀값 설정](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.files.configtree)
- [Nginx의 백엔드 주소 재조회](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#server)

## Parameter Store 값 등록

실제 비밀값은 사용자가 AWS 콘솔에서 등록한다. 서울 리전 `ap-northeast-2`의 Systems Manager → Parameter Store → 파라미터 생성으로 들어가 아래 3개를 만든다.

| 이름 | 값 |
|---|---|
| `/community/search/db/password` | 백엔드와 MySQL이 함께 사용할 DB 앱 계정 비밀번호 |
| `/community/search/mysql/root-password` | MySQL 내부 관리용 root 비밀번호 |
| `/community/search/backend/jwt-secret` | 토큰 서명용 JWT 키 |

모두 계층 `Standard`, 타입 `SecureString`, KMS 키는 AWS 관리형 `alias/aws/ssm`을 선택한다. 세 값은 서로 다른 난수를 사용한다. 로컬 터미널에서 아래 명령을 세 번 실행해 각 출력값을 서로 다른 파라미터 값으로 넣을 수 있다. 이 명령은 64자의 16진수 문자열을 만든다.

```bash
openssl rand -hex 32
```

값은 채팅·이슈·코드에 붙이지 않는다. JWT 키는 애플리케이션 요구사항상 UTF-8 기준 32바이트 이상이어야 하며, 위 방식의 64자 문자열이면 충족한다. 비밀번호와 JWT 키는 줄바꿈 없이 등록한다.

등록 후 에이전트는 `describe-parameters`로 이름·타입 등 메타데이터만 확인할 수 있다. 실제 복호화 조회는 서버가 자신의 IAM 역할로 수행한다. AWS에 값을 등록하는 작업과 Terraform으로 서버를 생성하는 작업은 별개다.

최초 서버 구성은 모든 필수 값이 있는지 확인한 뒤 파일을 저장한다. 조회에 실패하거나 파라미터가 누락되면 초기 설정을 실패로 처리하고 컨테이너 배포를 진행하지 않는다. 서버 생성 후에는 `cloud-init status --wait`가 성공했는지 확인한다. 이후 비밀값을 변경하는 경우에는 MySQL 계정 변경·앱 재생성 등 실제 적용 절차도 함께 정한다.

- [AWS 콘솔에서 Parameter Store 생성](https://docs.aws.amazon.com/systems-manager/latest/userguide/sysman-paramstore-su-create.html)
- [AWS CLI 복호화 조회](https://docs.aws.amazon.com/cli/latest/reference/ssm/get-parameters.html)
