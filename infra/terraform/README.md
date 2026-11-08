# 검색 실험 환경

백엔드와 MySQL을 같은 가용 영역의 퍼블릭 서브넷에 한 대씩 구성한다. 서버에는 Docker·Compose·SSM Agent·AWS CLI를 준비하고, MySQL의 별도 데이터 볼륨을 `/srv/mysql`에 마운트한다.

| 항목 | 초기 설정 |
|---|---|
| 리전 · 가용 영역 | 서울 · ap-northeast-2a |
| 네트워크 | 전용 VPC 10.20.0.0/16 · 퍼블릭 서브넷 10.20.1.0/24 |
| 백엔드 | t3a.small · 루트 gp3 20GiB |
| MySQL | t3a.medium · 루트 gp3 20GiB · 데이터 gp3 20GiB |
| OS | Ubuntu Server 24.04 LTS amd64 · AMI ID 고정 |
| 접근 | HTTP 80은 전체 IPv4 허용 · MySQL은 백엔드 보안 그룹 · 관리 접속은 SSM |
| 상태 | 전용 S3 버킷 · 버전 관리 · 암호화 · S3 상태 잠금 |

## 초기 준비

AWS 인증은 환경변수나 AWS 프로필에서 가져온다. 현재 로컬에서는 `AWS_PROFILE=terraform`을 사용한다. 대상 계정은 `929368845727`로 제한하며, 다른 계정은 변수와 백엔드 설정을 함께 변경한다.

먼저 상태 저장용 버킷의 생성 계획을 확인한다. 최초 bootstrap 상태는 로컬에서 시작한다.

```bash
AWS_PROFILE=terraform terraform -chdir=infra/terraform/bootstrap init
AWS_PROFILE=terraform terraform -chdir=infra/terraform/bootstrap plan -out=bootstrap.tfplan
```

계획을 검토하고 AWS 생성 승인을 받은 뒤 bootstrap을 적용한다. 버킷 생성 후에는 bootstrap 자체의 상태도 S3로 이전한다.

```bash
cp infra/terraform/bootstrap/remote-backend.tf.example infra/terraform/bootstrap/backend.tf
cp infra/terraform/bootstrap/backend.hcl.example infra/terraform/bootstrap/backend.hcl
AWS_PROFILE=terraform terraform -chdir=infra/terraform/bootstrap init -migrate-state -backend-config=backend.hcl
```

새 노트북에서도 위 템플릿을 복사하고 bootstrap을 초기화해 같은 S3 상태를 연결한다. 새 로컬 상태가 없다면 `-migrate-state` 없이 `init -backend-config=backend.hcl`을 실행한다.

## 서버 생성 계획

```bash
cp infra/terraform/backend.hcl.example infra/terraform/backend.hcl
cp infra/terraform/terraform.tfvars.example infra/terraform/terraform.tfvars
```

`terraform.tfvars`의 HTTP 허용 CIDR은 `0.0.0.0/0`으로 설정해 웹 접속을 공개한다. AMI ID는 서울 리전의 Canonical Ubuntu 24.04 amd64 이미지로 확인한 값이며, 비교 실험 중에는 고정한다.

```bash
AWS_PROFILE=terraform terraform -chdir=infra/terraform init -backend-config=backend.hcl
AWS_PROFILE=terraform terraform -chdir=infra/terraform plan -out=search.tfplan
```

생성·변경·삭제 대상과 비용을 검토하고 별도 승인 후 적용한다. `.tfstate`, `.tfplan`, 실제 변수 파일과 인증 정보는 Git에서 제외한다.

## 비밀값 전달

DB 비밀번호와 JWT 키는 서버 생성 전에 서울 리전 Parameter Store의 Standard SecureString으로 등록한다. 서버의 최초 부팅 설정이 EC2 IAM 역할로 필요한 값을 조회해 보호된 파일에 저장하고, Compose가 해당 파일을 컨테이너에 전달한다. 초기 암호화 키는 AWS 관리형 `aws/ssm`을 사용한다. Terraform에는 파라미터 이름과 읽기 권한을 정의한다. 실제 값 등록은 별도로 진행하며, 복호화한 값을 Terraform의 data source나 변수로 읽어 상태에 저장하지 않는다.

| 파라미터 | 읽는 서버 |
|---|---|
| `/community/search/db/password` | 백엔드 · MySQL |
| `/community/search/backend/jwt-secret` | 백엔드 |
| `/community/search/mysql/root-password` | MySQL |

SSM 관리형 정책에 전체 파라미터 읽기 권한이 포함돼 있어, 서버별로 위 이름 이외의 파라미터 조회를 거부하는 정책을 함께 적용한다. 두 EC2는 이 정책이 작성된 뒤 생성한다.

최초 부팅 시 `prepare-secrets.sh.tftpl`을 user-data에 포함해 실행한다. 이 파일은 별도로 수동 실행하는 배포 스크립트가 아니다. AWS CLI의 복호화 응답은 출력하지 않고, 필요한 값이 모두 있는지 확인한 다음 파일에 기록한다. 파라미터가 없거나 조회에 실패하면 초기 설정을 중단한다. 값은 Terraform 변수·상태·user-data 본문에 넣지 않는다.

비밀값 파일은 재부팅 후에도 유지되며 컨테이너는 `unless-stopped`로 재시작한다. Parameter Store 값을 나중에 바꿔도 실행 중인 DB 비밀번호나 JWT 키를 자동 교체하지 않는다. 변경 적용과 서버 배포는 후속 CD에서 별도로 연결한다. 생성 후에는 SSM에서 `cloud-init status --wait`로 최초 준비 결과를 확인한 뒤 Compose를 배포한다.

콘솔 등록 순서와 파일별 권한은 [서버 실행 설정](../../deploy/search/README.md#parameter-store-값-등록)에 정리했다.

## 데이터와 재구성

검색 환경은 `SPRING_PROFILES_ACTIVE=search`로 실행한다. Flyway가 `src/main/resources/db/migration/search/`의 버전별 SQL을 실행한 뒤, JPA가 `ddl-auto=validate`로 엔티티와 DB 구조를 확인한다. Flyway는 공통 설정에서 비활성화하고 `search`에서만 활성화한다.

새 검색 DB는 비어 있는 상태로 준비한다. 최초 시작 시 `V1__create_community_tables.sql`로 현재 엔티티의 6개 테이블과 제약조건을 생성한다. `flyway_schema_history`에는 적용 버전과 체크섬을 남기므로, 재시작할 때 이미 성공한 버전을 다시 실행하지 않는다. 기존 테이블이 있는데 이력이 없으면 자동으로 건너뛰지 않고 시작을 중단한다. 기존 DB를 연결해야 한다면 실제 구조를 대조한 뒤 별도 도입 절차를 정한다.

이후 테이블·컬럼·인덱스 변경은 `V2__...sql`, `V3__...sql`처럼 다음 버전 파일에 작성하고 배포한다. 적용된 파일을 수정하거나 삭제하지 않는다. SQL 실행이 실패하면 앱 시작을 중단하며, MySQL DDL은 부분 적용될 수 있으므로 실패 상태를 확인한 뒤 복구한다. JPA 검증은 인덱스 구성을 모두 확인하지 않으므로, 성능 비교 전에는 실제 인덱스도 확인한다.

MySQL 데이터는 별도 EBS 리소스로 관리하고 `prevent_destroy`를 적용한다. 이 보호는 리소스 선언이 유지될 때 유효하므로, 데이터 볼륨의 리소스 블록을 제거하기 전에는 보존 방법을 확인한다.

부팅 시 지정된 EBS 볼륨이 연결될 때까지 기다리고, 파일시스템이 없는 새 볼륨에만 ext4를 생성한다. 기존 ext4 데이터는 그대로 마운트한다. 재시작 시에도 같은 UUID로 마운트한다.

백엔드에는 Elastic IP를 연결한다. 공개 접속 주소는 Terraform 출력 `backend.api_url`에서 확인한다. T3a의 CPU 크레딧 모드는 AWS 기본값을 사용하므로, 지속 부하 측정 시 크레딧과 추가 요금을 확인한다.

## 이어서 구성할 것

MySQL·백엔드 Compose와 Nginx 설정, 최초 부팅의 Parameter Store 조회·파일 저장을 작성했다. 사용자가 Parameter Store에 3개 값을 등록했고 이름·타입·암호화 키만 확인했다. 값 내용은 조회하지 않았다. 상태 저장용 S3를 생성하고 bootstrap 상태를 `bootstrap/terraform.tfstate`로 이전했다. 서버용 backend도 같은 버킷의 `search/terraform.tfstate`에 연결했다. 서버 환경은 검토한 plan으로 26개 리소스를 생성했다. EC2 2대의 cloud-init 정상 종료·SSM Online·Docker 설치·MySQL EBS 마운트·비밀값 파일 권한을 확인했다. 비밀값 내용은 출력하지 않았다. 검색 브랜치 CI에서 백엔드 이미지를 ECR에 발행하고, SSM을 통해 컨테이너를 배포한다. 후속 CD에서는 EBS·비밀번호 파일 확인과 Compose 실행을 자동화한다.

Terraform은 백엔드 Elastic IP와 Nginx에 연결하는 80·443번 보안 그룹 규칙을 관리한다. 인증서 발급과 갱신은 백엔드 EC2의 Certbot이 담당한다. Nginx는 80번에서 HTTP 인증 경로를 유지하고, 일반 요청을 443번의 HTTPS 주소로 안내한다. 상세 절차는 [서버 실행 설정](../../deploy/search/README.md#고정-ip에서-https-제공하기)을 참고한다.

프론트의 HTML·JS는 백엔드 EC2의 Nginx에서 제공한다. 브라우저는 같은 HTTPS 출처의 `/api`로 API를 호출한다.
