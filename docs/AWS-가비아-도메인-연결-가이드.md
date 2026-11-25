# AWS 가비아 도메인 연결 가이드

이 문서는 도메인 이름을 AWS EC2의 공개 IP로 연결하는 절차입니다. 설명에 적는 주소는 샘플 도메인 `example.com`입니다. 집 PC의 클러스터는 인터넷에서 접속되지 않습니다. EC2 인스턴스에 붙는 퍼블릭 IPv4가 인터넷에서 80번과 443번 포트로 접속하는 주소입니다.

저장소에 설정된 도메인은 `darkhanmarie.site`입니다. 구매한 도메인이 다르면 그 이름으로 바꿉니다. 이 가이드의 `example.com`은 구매한 도메인 예시입니다. `darkhanmarie.site`를 `example.com`으로 바꾸듯이, 아래 파일의 같은 이름도 구매한 도메인으로 맞춥니다.

- `k8s/base/platform/ingress.yaml`의 호스트
- `k8s/base/platform/config.yaml`의 `FRONTEND_URL`, `USER_WEB_URL`, `APP_CORS_ALLOWED_ORIGINS`, `NAVER_REDIRECT_URI`, `GOOGLE_REDIRECT_URI`, `COMMUNITY_CHAT_ALLOWED_ORIGINS`
- `.github/workflows/images.yaml`의 `build-args`
- `frontends/user-web/Dockerfile`과 `frontends/admin-web/Dockerfile`의 `VITE_` 기본값

이름을 바꾼 뒤 이미지를 다시 빌드하고 Sync합니다. 가비아 DNS가 그 이름을 인스턴스 IP로 보내면, Ingress가 해당 서비스로 넘깁니다.

6장에서 이 서버에 Argo CD와 프로젝트를 올립니다. Sync는 그 Argo CD가 이 클러스터에 수행합니다.

## 비용

이 절차에서는 EC2의 인스턴스 사용 시간, 디스크, 퍼블릭 IPv4와 가비아 도메인 등록이 청구될 수 있습니다.

인스턴스 사용 시간입니다. Linux `t3.xlarge`를 켜 둔 동안 나옵니다.

디스크입니다. 1장의 100GiB gp3 볼륨입니다. 인스턴스를 중지해도 용량만큼 나옵니다.

퍼블릭 IPv4입니다. 인스턴스에 연결한 탄력적 IP입니다. 연결되어 있어도 나오고, 연결하지 않고 남겨 두어도 나옵니다.

도메인 등록입니다. 가비아에서 도메인을 구매할 때 나옵니다. 등록을 유지하면 갱신 기간마다 갱신 비용이 나옵니다.

DNS 관리툴의 A 레코드와 안전잠금은 도메인 등록에 포함된 기능이라 따로 나오지 않습니다.

보안 그룹, k3s, Ingress 컨트롤러는 AWS 요금이 없습니다.

## 요금을 멈출 때

이 절의 화면은 리전이 서울일 때 보입니다.

인스턴스 사용 시간을 멈추려면 `coliving`을 중지합니다. 인스턴스 목록에서 `coliving`을 선택하고 인스턴스 상태에서 인스턴스 중지를 누릅니다.

중지는 서버를 끄는 작업입니다. 디스크와 탄력적 IP는 그대로 남습니다.

디스크 요금을 멈추려면 100GiB gp3 볼륨을 지웁니다. 인스턴스를 중지한 뒤 왼쪽 메뉴 볼륨에서 `coliving`에 붙은 그 볼륨을 선택합니다. 작업에서 볼륨 분리를 누르고, 상태가 사용 가능이 되면 작업에서 볼륨 삭제를 누릅니다.

볼륨 삭제는 디스크를 계정에서 지우는 작업입니다. 그 안의 데이터도 사라집니다. 이 볼륨은 인스턴스의 루트 디스크라서, 지우면 `coliving`은 다시 켜지지 않습니다.

퍼블릭 IPv4 요금을 멈추려면 탄력적 IP를 릴리스합니다. 왼쪽 메뉴 탄력적 IP에서 그 주소를 선택하고 작업에서 탄력적 IP 주소 연결 해제를 누릅니다. 그다음 작업에서 탄력적 IP 주소 릴리스를 누릅니다.

릴리스는 그 주소를 계정에서 놓는 작업입니다. 가비아에 넣어 둔 값과는 더 이상 같지 않습니다.

도메인 유지 요금을 멈추려면 등록한 도메인의 기간 연장을 하지 않습니다.

기간 연장을 하지 않으면 만기 뒤부터 갱신 요금이 나오지 않습니다. 만기 뒤에는 이 도메인을 쓸 수 없습니다.

## 1. EC2 인스턴스

EC2 인스턴스는 이 클러스터를 올릴 가상 서버입니다. AWS 콘솔에 로그인한 뒤 오른쪽 위의 리전을 서울로 바꿉니다. 리전은 서버가 놓이는 지역입니다. 서울이 아닌 화면에서는 방금 만든 인스턴스가 보이지 않습니다.

EC2 메뉴에서 인스턴스로 들어가 인스턴스 시작을 누릅니다. 한 화면에서 아래를 차례로 고릅니다.

이름은 `coliving`으로 적습니다. 이름은 인스턴스 목록에서 이 서버를 찾는 태그입니다.

AMI는 Ubuntu Server 24.04 LTS, 64비트(x86)를 고릅니다. AMI는 인스턴스에 설치되는 운영체제입니다. 뒤에서 Kubernetes를 이 Ubuntu에 설치합니다.

인스턴스 유형은 `t3.xlarge`를 고릅니다. 이 유형은 vCPU 4개, 메모리 16GiB입니다. 발표용으로 사용자가 적고 데이터를 자주 초기화하면 이 메모리로 충분합니다.

키 페어가 없으면 새로 만듭니다. 이름은 `coliving-key`, 유형은 RSA, 형식은 `.pem`입니다. 키 페어는 나중에 SSH로 이 서버에 들어갈 때 쓰는 열쇠입니다. 만든 직후 브라우저가 `.pem` 파일을 한 번 받습니다. AWS는 이 파일을 다시 주지 않습니다. Git 저장소에는 넣지 않습니다.

네트워크 설정에서 편집을 누릅니다.

VPC와 서브넷은 기본값을 그대로 둡니다. VPC는 인스턴스가 들어가는 네트워크이고, 기본 서브넷은 인터넷으로 나가는 경로가 있습니다.

퍼블릭 IP 자동 할당은 활성화합니다. 이 값이 꺼져 있으면 인스턴스에 인터넷에서 접속할 주소가 생기지 않습니다.

방화벽은 보안 그룹 생성을 고르고 이름은 `coliving-sg`로 적습니다. 보안 그룹은 이 인스턴스로 들어오는 연결을 허용하는 규칙입니다. 규칙은 세 개입니다.

| 유형 | 포트 | 소스 | 이유 |
|---|---|---|---|
| SSH | 22 | 내 IP | 이 PC에서 서버에 접속합니다. 집 IP가 바뀌면 이 규칙만 고칩니다. |
| HTTP | 80 | `0.0.0.0/0` | 사이트 접속과 Let’s Encrypt 소유 확인이 이 포트로 들어옵니다. |
| HTTPS | 443 | `0.0.0.0/0` | HTTPS 접속입니다. |

스토리지는 100GiB, 유형 gp3으로 바꿉니다. 기본 8GiB나 30GiB는 서비스 이미지와 Postgres, Elasticsearch 데이터에 부족합니다.

요약에서 인스턴스 시작을 누릅니다. 인스턴스 상태가 실행 중이고 상태 검사가 통과할 때까지 기다립니다. 목록의 퍼블릭 IPv4 주소가 지금 붙은 공개 주소입니다.

인스턴스를 중지했다가 다시 시작하면 이 자동 주소는 바뀝니다. 도메인에 넣는 주소는 유지되어야 하므로 탄력적 IP로 바꿉니다.

탄력적 IP는 계정에 고정되는 공개 IPv4입니다. 왼쪽 메뉴 탄력적 IP에서 탄력적 IP 주소 할당을 누릅니다. 리전은 인스턴스와 같은 서울이고, 풀은 Amazon의 IPv4 주소 풀입니다. 할당된 주소는 아직 인스턴스에 붙어 있지 않습니다. 연결하지 않은 탄력적 IP는 요금이 나옵니다.

할당된 주소를 선택하고 작업에서 탄력적 IP 주소 연결을 누릅니다. 리소스 유형은 인스턴스, 대상은 `coliving`입니다. 연결한 뒤 인스턴스 목록으로 돌아와 퍼블릭 IPv4 주소가 방금 연결한 탄력적 IP와 같은지 확인합니다. 가비아에 넣을 값은 이 주소입니다.

## 2. 보안 그룹을 나중에 수정할 때

80, 443, 22번 규칙은 1장의 인스턴스 시작 화면에서 `coliving-sg`에 넣습니다.

빠뜨린 규칙은 인스턴스 목록에서 `coliving`을 선택하고 보안 탭의 보안 그룹을 연 뒤, 인바운드 규칙 편집에서 추가합니다. 22번은 서버 접속용이라 도메인 레코드 값과는 관계가 없습니다.

## 3. SSH로 서버에 들어가기

SSH는 집 PC에서 EC2의 Ubuntu 터미널을 여는 프로그램입니다. 이 절의 명령은 집 PC PowerShell에서, 이 저장소 폴더를 연 상태로 칩니다.

1장에서 받은 `coliving-key.pem`을 `keystore/secret/coliving-key.pem`으로 저장합니다.

`keystore/secret/`은 `.gitignore`에 있습니다. `coliving-key.pem`은 Git 저장소에 올리지 마세요.

SSH는 다른 사용자도 읽을 수 있는 열쇠 파일로는 접속을 거절합니다. 그때 나오는 문장은 `WARNING: UNPROTECTED PRIVATE KEY FILE!`입니다.

아래 명령은 폴더에서 물려받은 권한을 지우고, 현재 Windows 사용자에게 읽기 권한을 주는 작업입니다. `1 파일을 처리했으며 0 파일은 처리하지 못했습니다`는 명령이 실행됐다는 뜻입니다. 다른 사용자의 읽기 권한이 없어졌다는 뜻은 아닙니다.

```powershell
icacls keystore\secret\coliving-key.pem /inheritance:r /grant:r "${env:USERNAME}:(R)"
```

파일에 직접 적힌 권한은 위 명령 뒤에 남습니다. `BUILTIN\Users`와 `NT AUTHORITY\Authenticated Users`의 읽기가 남아 있으면 SSH는 같은 경고를 냅니다. 아래 명령은 그 읽기 권한을 지우는 작업입니다. `Administrators`와 `SYSTEM`의 권한도 함께 지웁니다.

```powershell
icacls keystore\secret\coliving-key.pem /remove "BUILTIN\Users" "NT AUTHORITY\Authenticated Users" "BUILTIN\Administrators" "NT AUTHORITY\SYSTEM"
icacls keystore\secret\coliving-key.pem /grant:r "${env:USERNAME}:(R)"
```

아래 명령은 지금 누가 이 파일을 읽을 수 있는지 보는 작업입니다. 현재 사용자 한 줄만 `(R)`로 남아 있으면 SSH를 다시 실행합니다.

```powershell
icacls keystore\secret\coliving-key.pem
```

아래 명령은 `ubuntu` 계정으로 서버 터미널을 여는 작업입니다. `ubuntu`는 Ubuntu AMI의 기본 계정입니다. `탄력적IP`는 1장 인스턴스 목록의 퍼블릭 IPv4로 바꿉니다.

OpenSSH는 경로의 `\`를 지웁니다. `keystore\secret\coliving-key.pem`은 `keystoresecretcoliving-key.pem`이 되어 파일을 찾지 못합니다. `ssh`와 `scp`의 경로는 `/`로 적습니다.

```powershell
ssh -i keystore/secret/coliving-key.pem ubuntu@탄력적IP
```

처음 접속하면 서버 지문을 신뢰하는지 묻습니다. `yes`를 입력합니다. 프롬프트가 `ubuntu@ip-...`로 바뀌면 서버 안에 들어온 것입니다. 이 창에서 이후 치는 명령은 집 PC가 아니라 서버에서 실행됩니다. `exit`를 치면 집 PC PowerShell로 돌아옵니다.

접속이 시간 초과되면 보안 그룹의 SSH 소스가 지금 PC의 공개 IP인지 확인합니다. 집 IP가 바뀌었으면 2장대로 소스를 내 IP로 다시 저장합니다.

## 4. Kubernetes 설치

이 절의 명령은 3장에서 연 SSH 창에서 칩니다.

k3s는 서버 한 대에 Kubernetes를 설치하는 배포판입니다. 아래 명령은 k3s를 설치하고, 80번과 443번 포트를 쓰는 Traefik은 켜지 않습니다. 5장의 Ingress 컨트롤러가 그 포트를 사용합니다.

```bash
curl -sfL https://get.k3s.io | sudo sh -s - --disable traefik
```

kubeconfig는 `kubectl`이 접속할 클러스터를 적은 파일입니다. 아래 명령은 root만 읽던 그 파일을 `ubuntu` 계정으로 복사하는 작업입니다.

```bash
mkdir -p $HOME/.kube
sudo cp /etc/rancher/k3s/k3s.yaml $HOME/.kube/config
sudo chown $USER:$USER $HOME/.kube/config
echo 'export KUBECONFIG=$HOME/.kube/config' >> $HOME/.bashrc
source $HOME/.bashrc
```

아래 명령은 노드가 준비됐는지 보는 작업입니다. `STATUS`가 `Ready`이면 Kubernetes 설치가 된 것입니다.

```bash
kubectl get nodes
```

Helm은 이 서버에서 Kubernetes 차트를 설치하는 프로그램입니다. 5장 명령이 Helm을 사용합니다. 아래 명령은 Helm 3을 설치하는 작업입니다.

```bash
curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | sudo bash
```

## 5. Ingress 컨트롤러

NGINX Ingress Controller는 4장의 서버에 둡니다. `ingress.yaml`은 nginx 설정을 사용합니다. 이 절의 명령은 3장에서 연 SSH 창에서 칩니다.

컨트롤러는 인스턴스의 80번과 443번 포트에서 요청을 받아야 합니다. 클라우드 로드밸런서용 설치는 인스턴스 IP와 다른 호스트 이름을 만들므로, 이 가이드에서는 그 방식을 쓰지 않습니다.

아래 명령은 Helm으로 NGINX Ingress Controller를 설치하는 작업입니다. 컨트롤러가 인스턴스 네트워크에서 80번과 443번을 직접 엽니다.

```bash
helm upgrade --install ingress-nginx ingress-nginx \
  --repo https://kubernetes.github.io/ingress-nginx \
  --namespace ingress-nginx --create-namespace \
  --set controller.hostNetwork=true \
  --set controller.dnsPolicy=ClusterFirstWithHostNet \
  --set controller.service.type=ClusterIP \
  --set controller.kind=DaemonSet \
  --set controller.ingressClassResource.default=true
```

설치 확인은 컨트롤러 파드가 Running인지를 보는 작업입니다.

```bash
kubectl get pods -n ingress-nginx
```

`kubectl get ingress -n coliving coliving`은 가비아에 넣을 IP를 만들지 않습니다. 로드밸런서를 쓰지 않으면 `ADDRESS`가 비어 있어도 됩니다. 레코드 값은 1장의 퍼블릭 IPv4입니다.

## 6. 서버에 프로젝트 올리기

이 절은 이 EC2의 Kubernetes에 프로젝트를 올리고, Argo CD로 Sync할 수 있게 합니다. 공개 주소로 사이트가 열리려면 Ingress와 파드가 이 서버에 있어야 합니다.

집 PC PowerShell의 `kubectl`은 docker-desktop 클러스터를 봅니다. 이 절의 `kubectl`과 Sync는 3장 SSH 창에서 칩니다. 그 창의 `kubectl`은 4장에서 복사한 이 서버의 kubeconfig를 봅니다.

Secret 파일은 집 PC에서 만듭니다. `coliving-infra.yaml`과 `coliving-app.yaml`의 값은 `docs/배포-가이드.md` 1장대로 채웁니다. `repo-coliving-platform.yaml`은 같은 문서 3장대로 만듭니다. 앱 이미지는 같은 문서 4장대로 GHCR에 올라가 있어야 합니다.

`coliving-infra.yaml`과 `coliving-app.yaml`은 `.gitignore`에 있습니다. `git status`에 나오면 안 됩니다.

`keystore/secret/`은 `.gitignore`에 있습니다. `repo-coliving-platform.yaml`은 Git 저장소에 올리지 마세요.

아래 명령은 서버에 받을 폴더를 만드는 작업입니다. 집 PC PowerShell에서, 저장소 폴더를 연 상태로 칩니다. `탄력적IP`는 1장의 퍼블릭 IPv4입니다.

```powershell
ssh -i keystore\secret\coliving-key.pem ubuntu@탄력적IP "mkdir -p setup"
```

아래 명령은 집 PC의 파일 다섯 개를 서버의 `setup` 폴더로 복사하는 작업입니다.

```powershell
scp -i keystore/secret/coliving-key.pem k8s/base/platform/coliving-infra.yaml k8s/base/platform/coliving-app.yaml k8s/argocd/project.yaml k8s/argocd/application.yaml keystore/secret/repo-coliving-platform.yaml ubuntu@52.90.142.61:setup/
```

이후 명령은 3장의 SSH 창에서 칩니다. 프롬프트가 `ubuntu@ip-...`인지 확인합니다.

Argo CD가 없으면 Application을 등록할 수 없습니다. 아래 명령은 이 서버의 클러스터에 Argo CD를 설치하는 작업입니다.

```bash
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl wait --namespace argocd --for=condition=Ready pod --all --timeout=300s
```

`schema.sql`은 `k8s/base` 밖에 있습니다. Argo CD 3는 Application 매니페스트의 `kustomize.buildOptions`를 받지 않습니다. 아래 명령은 ConfigMap `argocd-cm`에 그 옵션을 넣는 작업입니다.

```bash
kubectl -n argocd patch cm argocd-cm --type merge --patch '{"data":{"kustomize.buildOptions":"--load-restrictor LoadRestrictionsNone"}}'
```

아래 명령은 repo-server를 재시작해서 방금 넣은 옵션을 읽게 하는 작업입니다.

```bash
kubectl -n argocd rollout restart deploy/argocd-repo-server
kubectl -n argocd rollout status deploy/argocd-repo-server
```

저장소는 비공개입니다. 아래 명령은 복사해 둔 저장소 Secret을 적용하는 작업입니다. 이 Secret이 없으면 Sync는 `Repository not found`로 끝납니다.

```bash
kubectl apply -f setup/repo-coliving-platform.yaml
```

아래 명령은 AppProject `coliving`과 Application `coliving-platform`을 등록하는 작업입니다. Application은 `main`의 `k8s/base`를 이 클러스터에 올립니다. 자동 Sync는 없습니다.

```bash
kubectl apply -f setup/project.yaml
kubectl apply -f setup/application.yaml
```

Secret `coliving-infra`와 `coliving-app`은 Git에 없습니다. Sync는 이 Secret을 만들지 않습니다. 아래 명령은 네임스페이스 `coliving`을 만들고 두 Secret을 적용하는 작업입니다.

```bash
kubectl create namespace coliving --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f setup/coliving-infra.yaml
kubectl apply -f setup/coliving-app.yaml
```

GHCR 패키지가 비공개이면 파드는 `ImagePullBackOff`가 됩니다. `docs/배포-가이드.md` 4장대로 패키지를 Public으로 바꾸거나, pull Secret을 이 클러스터에 만듭니다.

아래 명령은 Application `coliving-platform`에 Sync를 요청하는 작업입니다. 패치는 `operation.sync`만 넣고, Argo CD는 그 필드를 Sync 한 번으로 처리합니다.

```bash
kubectl patch application coliving-platform -n argocd --type merge --patch '{"operation":{"sync":{}}}'
```

Sync가 끝나면 이 서버에 Ingress `coliving`과 앱 파드가 생깁니다. 이미지가 GHCR에 없거나 이름이 다르면 파드는 `ImagePullBackOff`입니다.

## 7. 가비아 DNS

도메인 DNS는 가비아에 있습니다. 아래 주소는 샘플 도메인 `example.com`입니다. 실제 도메인도 같은 형태로 넣습니다. 레코드 타입은 A이고, 값은 1장의 탄력적 IP입니다.

추가하는 곳은 DNS 관리툴의 레코드 설정입니다. 호스트에 `user`를 넣으면 접속 이름은 `user.example.com`이 됩니다. 이 이름이 서브도메인입니다. 웹호스팅 메뉴의 서브도메인 추가는 가비아 호스팅 폴더를 만드는 기능이라 이번 연결에는 쓰지 않습니다.

안전잠금은 해제하지 않습니다. 안전잠금이 막는 DNS 호스트는 도메인 정보 변경 메뉴의 자체 네임서버 등록입니다. A 레코드의 호스트 칸과는 다른 메뉴입니다. 그 화면이 잠겨 있으면 잠금을 풀지 말고 DNS 관리툴에서 레코드를 추가합니다.

DNS 레코드 수정의 호스트 칸에는 이름만 적습니다. 화면이 `.example.com`을 붙입니다. 값/위치는 1장의 탄력적 IP입니다. TTL은 `600`입니다. A 레코드의 우선순위는 비웁니다. 루트 `example.com`은 Ingress가 쓰지 않습니다.

| 타입 | 호스트 | 값/위치 | TTL | 우선순위 |
|---|---|---|---|---|
| A | `user` | 탄력적 IP | 600 | |
| A | `admin` | 탄력적 IP | 600 | |
| A | `api` | 탄력적 IP | 600 | |
| A | `eureka` | 탄력적 IP | 600 | |
| A | `grafana` | 탄력적 IP | 600 | |
| A | `kibana` | 탄력적 IP | 600 | |
| A | `prometheus` | 탄력적 IP | 600 | |
| A | `rabbitmq` | 탄력적 IP | 600 | |

레코드를 저장한 뒤 반영을 기다립니다. 반영에는 시간이 걸릴 수 있습니다.

## 8. Let’s Encrypt 인증서

cert-manager가 이 서버에서 인증서를 발급합니다. `k8s/base/platform/ingress.yaml`에는 `cert-manager.io/cluster-issuer: letsencrypt-prod`와 Secret `coliving-tls`가 이미 있습니다. 그 파일에 같은 값을 다시 넣지 않습니다.

발급 전에 6장 Sync와 7장 DNS가 끝나 있어야 합니다. Let’s Encrypt는 `http://호스트/.well-known/acme-challenge/`로 이 서버의 80번에 접속합니다.

cert-manager는 저장소 매니페스트에 없습니다. 아래 명령은 이 클러스터에 한 번 설치하는 작업입니다. SSH 창에서 칩니다.

```bash
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.21.3/cert-manager.yaml
kubectl wait --namespace cert-manager --for=condition=Ready pod --all --timeout=180s
```

발급자는 ClusterIssuer입니다. `letsencrypt-prod.yaml`은 `k8s/base`에 넣지 않습니다. Kustomize가 네임스페이스 `coliving`을 붙입니다. 집 PC에서 아래 내용을 `keystore/secret/letsencrypt-prod.yaml`로 저장합니다. `email`은 만료 알림을 받을 주소로 바꿉니다.

`keystore/secret/`은 `.gitignore`에 있습니다. `letsencrypt-prod.yaml`은 Git 저장소에 올리지 마세요.

```yaml
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: you@example.com
    privateKeySecretRef:
      name: letsencrypt-prod-account
    solvers:
      - http01:
          ingress:
            class: nginx
```

아래 명령은 그 파일을 서버 `setup` 폴더로 복사하는 작업입니다. 집 PC PowerShell에서 칩니다.

```powershell
scp -i keystore/secret/coliving-key.pem keystore/secret/letsencrypt-prod.yaml ubuntu@탄력적IP:setup/
```

아래 명령은 ClusterIssuer를 이 클러스터에 적용하는 작업입니다. SSH 창에서 칩니다.

```bash
kubectl apply -f setup/letsencrypt-prod.yaml
```

ClusterIssuer가 생기면 cert-manager가 6장에서 올라간 Ingress를 보고 `Certificate` `coliving-tls`를 만듭니다. 아래 명령은 발급 상태를 보는 작업입니다.

```bash
kubectl get certificate -n coliving
kubectl describe certificate coliving-tls -n coliving
```

`READY`가 `True`이면 `https://user.example.com`, `https://admin.example.com`, `https://api.example.com`이 열립니다. `wrong status code '404'`이면 챌린지 Ingress가 이 서버가 아닌 다른 클러스터에 있습니다. 집 PC 클러스터에서 만든 `Certificate`는 공개 주소 발급에 쓰이지 않습니다.

## 9. 확인

아래 주소는 샘플 도메인 `example.com`입니다. 실제 이름은 `k8s/base/platform/ingress.yaml`의 `host`와 같습니다.

아래 명령어는 `nslookup`으로 도메인 이름이 어떤 주소로 응답하는지 조회하는 작업입니다.

```powershell
nslookup user.example.com
nslookup admin.example.com
nslookup api.example.com
```

응답의 Address가 1장의 탄력적 IP와 같으면 브라우저에서 `http://user.example.com`, `http://admin.example.com`, `http://api.example.com`을 엽니다. 6장 Sync로 이 서버에 Ingress와 파드가 올라가 있어야 페이지가 나옵니다.

HTTPS는 8장 발급이 끝난 뒤에 열립니다.

구매한 도메인으로 맞출 파일은 문서 앞의 목록과 같습니다. 이름을 바꾼 뒤 이미지를 다시 빌드하고 Sync합니다.
