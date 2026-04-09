# 홈서버 Docker + GitHub Actions CI/CD 배포 구축 실습기

> 작성 계기: 미니PC를 홈서버로 구축하고, CALI 프로젝트를 Docker Compose 기반으로 배포하면서 겪은 전 과정 기록.
> 대상 독자: CI/CD, 네트워크, Docker, 인프라 개념을 처음 공부하는 초보 개발자.

---

## 목차

1. [전체 아키텍처 한눈에 보기](#1-전체-아키텍처-한눈에-보기)
2. [핵심 개념 정리](#2-핵심-개념-정리)
   - Docker란?
   - Docker Compose란?
   - CI/CD란?
   - GitHub Actions란?
   - SSH / Deploy Key란?
3. [구축 과정 단계별 기록](#3-구축-과정-단계별-기록)
   - 3-0. Docker 설치 이슈 (ubuntu 기본 저장소 문제)
   - 3-1. 전략 설계
   - 3-2. Docker 파일 작성
   - 3-3. 홈서버 초기 설정
   - 3-4. GitHub Deploy Key 설정
   - 3-5. 포트 22 차단 문제 해결
   - 3-6. git clone 및 파일 배치
   - 3-7. 배포 테스트 및 오류 해결
4. [발생한 문제 & 해결 과정 총정리](#4-발생한-문제--해결-과정-총정리)
5. [완성된 파일 구조](#5-완성된-파일-구조)
6. [배포 흐름 최종 정리](#6-배포-흐름-최종-정리)
7. [알아두면 좋은 추가 개념들](#7-알아두면-좋은-추가-개념들)

---

## 1. 전체 아키텍처 한눈에 보기

```
[ 개발 PC (Windows) ]
       │
       │  git push (develop 브랜치)
       ▼
[ GitHub 원격 저장소 ]
       │
       │  workflow_dispatch (수동 트리거)
       ▼
[ GitHub Actions Runner (ubuntu-latest) ]
       │
       │  SSH 접속 (appleboy/ssh-action)
       ▼
[ 홈서버 (Ubuntu Server, 미니PC) ]
       │
       ├── git fetch + reset (최신 코드 반영)
       ├── docker compose down
       └── docker compose up -d --build
              │
              ├── [cali-backend 컨테이너]  포트 8050
              │     Spring Boot JAR 실행
              │     /opt/cali/application.properties     (볼륨 마운트)
              │     /opt/cali/application-dev.properties (볼륨 마운트)
              │
              └── [cali-frontend 컨테이너] 포트 8080
                    Nginx
                    /admin/  → React SPA 정적 파일
                    /api/    → backend:8050 프록시
                    /        → backend:8050 프록시 (SSR)
```

---

## 2. 핵심 개념 정리

### 🐳 Docker란?

Docker는 애플리케이션을 **컨테이너(Container)** 라는 격리된 환경에서 실행하게 해주는 도구입니다.

#### 가상머신(VM)과 Docker 컨테이너의 차이

| 항목 | 가상머신(VM) | Docker 컨테이너 |
|---|---|---|
| 운영체제 | 각각 OS 포함 (무거움) | 호스트 OS 커널 공유 (가벼움) |
| 부팅 시간 | 분 단위 | 초 단위 |
| 이미지 크기 | GB 단위 | MB~수백MB |
| 격리 수준 | 완전 격리 | 프로세스 격리 |

#### 주요 개념

- **이미지(Image)**: 컨테이너의 설계도. `Dockerfile`로 만들어지며, 실행 가능한 패키지.
- **컨테이너(Container)**: 이미지를 실제로 실행한 인스턴스. 프로세스처럼 동작.
- **Dockerfile**: 이미지를 어떻게 만들지 정의하는 파일. 레시피라고 생각하면 됨.
- **Docker Hub / Registry**: 이미지를 저장하고 공유하는 저장소. npm registry와 유사한 개념.
- **레이어(Layer)**: Dockerfile의 각 명령어가 하나의 레이어를 만듦. 변경이 없으면 캐시 사용 → 빌드 속도 향상.

#### 멀티스테이지 빌드 (Multi-stage Build)

이번 프로젝트에서 사용한 핵심 기술.

```dockerfile
# Stage 1: 빌드 환경 (JDK 포함, 무거움)
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /build
COPY . .
RUN ./gradlew bootJar

# Stage 2: 실행 환경 (JRE만, 가벼움)
FROM eclipse-temurin:17-jre-jammy AS runtime
COPY --from=builder /build/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**왜 멀티스테이지인가?**
- 빌드 시에는 JDK(Java Development Kit) + Gradle이 필요하지만
- 실행 시에는 JRE(Java Runtime Environment)만 있으면 됨
- Stage 1의 빌드 도구들을 최종 이미지에 포함시키지 않아 **이미지 크기를 크게 줄임**

#### 볼륨(Volume) 마운트

```yaml
volumes:
  - /opt/cali/application.properties:/app/application.properties:ro
```

- 호스트의 파일/디렉토리를 컨테이너 내부에 연결하는 기능
- `호스트경로:컨테이너경로:옵션` 형식
- `:ro` = read-only (컨테이너에서 수정 불가)
- 민감한 설정 파일을 git에 포함시키지 않고 서버에만 두는 패턴에 활용

#### restart 정책

```yaml
restart: unless-stopped
```

| 정책 | 동작 |
|---|---|
| `no` | 재시작 안 함 (기본값) |
| `always` | 항상 재시작 (수동 중지해도 재시작) |
| `unless-stopped` | 수동으로 중지하지 않는 한 항상 재시작 |
| `on-failure` | 오류 종료 시에만 재시작 |

`unless-stopped`는 서버 재부팅 후 자동으로 컨테이너를 다시 띄우면서도, `docker stop`으로 수동 중지하면 그 상태를 유지함. 개발서버에 가장 적합한 선택.

---

### 🐳 Docker Compose란?

여러 컨테이너를 하나의 설정 파일(`docker-compose.yml`)로 관리하는 도구.

이번 프로젝트처럼 backend + frontend(nginx) 두 컨테이너가 함께 동작해야 할 때 유용.

```yaml
services:
  backend:
    build: ./backend
    ports:
      - "8050:8050"
  frontend:
    build: ./frontend
    ports:
      - "8080:80"
    depends_on:
      - backend
```

#### 주요 명령어

```bash
docker compose up -d --build   # 이미지 빌드 후 백그라운드 실행
docker compose down            # 컨테이너 중지 및 제거
docker compose logs -f         # 실시간 로그 확인 (Ctrl+C로 중단)
docker compose logs backend    # 특정 서비스 로그만 확인
docker compose ps              # 실행 중인 컨테이너 목록
docker compose restart backend # 특정 서비스만 재시작
```

#### 네트워크

```yaml
networks:
  cali-network:
    name: cali-network
```

같은 네트워크에 속한 컨테이너끼리는 **서비스명으로 통신** 가능.
이번 프로젝트에서 nginx 컨테이너가 `http://backend:8050` 으로 Spring Boot에 접근하는 것이 이 덕분.

---

### 🔄 CI/CD란?

**CI (Continuous Integration, 지속적 통합)**
- 코드를 자주 병합하고, 병합할 때마다 자동으로 빌드/테스트
- "내 로컬에서는 됐는데 서버에서 안 됨" 문제를 줄이는 것이 목적

**CD (Continuous Delivery/Deployment, 지속적 배포)**
- 빌드가 완료된 코드를 자동 또는 반자동으로 서버에 배포
- Delivery: 배포 준비 완료 상태 유지 (수동 승인 후 배포)
- Deployment: 완전 자동 배포

이번 프로젝트 방식:
- `workflow_dispatch` = **수동 트리거** → CD의 Delivery 방식에 가까움
- develop 브랜치 병합 완료 후 개발자가 직접 배포 버튼을 누름

#### 브랜치 전략

```
feature/* → develop → [수동 배포: 홈서버 개발환경]
                ↓
              main → [태그 생성 후 자동 배포: 운영서버] (추후 구현)
```

---

### ⚙️ GitHub Actions란?

GitHub에서 제공하는 CI/CD 플랫폼. 코드 저장소와 통합되어 있어 별도 Jenkins 등 설치 없이 사용 가능.

#### 핵심 구조

```yaml
on:                          # 트리거: 언제 실행할지
  workflow_dispatch:         # 수동 실행

jobs:                        # 작업 단위
  deploy:
    runs-on: ubuntu-latest   # 어떤 Runner에서 실행할지

    steps:                   # 실행 단계
      - name: 단계명
        uses: 액션명         # 재사용 가능한 액션 (npm 패키지 개념과 유사)
        with:
          key: value         # 액션에 전달할 파라미터
```

#### GitHub Secrets

민감한 정보(서버 IP, SSH 키, 비밀번호 등)를 코드에 직접 쓰지 않고 GitHub에 안전하게 저장하는 기능.

```yaml
host: ${{ secrets.HOME_HOST }}  # 워크플로우에서 이렇게 참조
```

이번 프로젝트에서 사용한 Secrets:
| Secret | 용도 |
|---|---|
| `HOME_HOST` | 홈서버 IP 주소 |
| `HOME_USER` | SSH 접속 계정명 |
| `HOME_SSH_KEY` | SSH 개인키 (전체 내용) |
| `HOME_PORT` | SSH 포트 번호 |

---

### 🔑 SSH / Deploy Key란?

#### SSH (Secure Shell)

원격 서버에 **암호화된 채널**로 접속하는 프로토콜.

```
[클라이언트]  ──SSH──►  [서버]
  개인키 보유              공개키 등록
```

작동 방식:
1. 클라이언트가 개인키로 서명한 인증 정보 전송
2. 서버가 등록된 공개키로 검증
3. 일치하면 접속 허용 (비밀번호 불필요)

키 쌍 생성:
```bash
ssh-keygen -t ed25519 -C "용도 설명" -f ~/.ssh/키이름
# 결과: ~/.ssh/키이름 (개인키), ~/.ssh/키이름.pub (공개키)
```

- **개인키**: 절대 공유하면 안 됨. 내 컴퓨터/서버에만 보관.
- **공개키**: 접속하고 싶은 서버의 `~/.ssh/authorized_keys`에 등록.

#### GitHub Deploy Key vs 계정 SSH Key

| 구분 | Deploy Key | 계정 SSH Key |
|---|---|---|
| 등록 위치 | 특정 저장소 Settings | GitHub 계정 Settings |
| 접근 범위 | 해당 저장소만 | 계정의 모든 저장소 |
| 권한 | Read 또는 Read/Write 선택 | 전체 권한 |
| 용도 | 서버 자동 배포용 | 개발자 본인 사용 |
| 보안 | 더 안전 (최소 권한 원칙) | 유출 시 위험 범위 큼 |

**이번 프로젝트에서 Deploy Key를 사용한 이유:**
- 홈서버가 git clone/pull 만 하면 되므로 Read 권한만 부여
- 홈서버가 해킹당해도 해당 저장소의 읽기만 가능, 다른 저장소 영향 없음

---

## 3. 구축 과정 단계별 기록

### 3-0. Docker 설치 이슈 (ubuntu 기본 저장소 문제)

#### 문제 상황

홈서버에 Docker를 설치했음에도 `docker compose` 명령어가 동작하지 않았고,
플러그인을 추가 설치하려 하자 아래 오류가 발생했다.

```
E: Unable to locate package docker-compose-plugin
```

#### 원인

Ubuntu의 **기본 apt 저장소(ubuntu 공식 패키지 저장소)** 에 등록된 Docker 패키지는
오래된 버전이거나 Docker Inc. 공식 버전이 아닌 경우가 많다.

특히 `docker-compose-plugin` (Compose v2, `docker compose` 명령어) 은
**Docker 공식 저장소**를 등록해야만 설치할 수 있다.

```
Ubuntu 기본 저장소      Docker 공식 저장소
(packages.ubuntu.com)  (download.docker.com)
      │                        │
  구버전 Docker             최신 Docker CE
  docker.io 패키지          docker-ce 패키지
  Compose v1만 포함         docker-compose-plugin 포함
  (docker-compose 명령어)   (docker compose 명령어)
```

> **Compose v1 vs v2 차이:**
> - v1: `docker-compose` (하이픈 포함, Python 기반, 별도 설치)
> - v2: `docker compose` (공백, Go 기반, Docker CLI 플러그인으로 통합)
> - v1은 2023년 7월 공식 지원 종료. 현재는 v2가 표준.

#### 해결 — Docker 공식 저장소 등록 후 재설치

```bash
# 1. 패키지 목록 최신화
sudo apt update
```
> apt 패키지 목록을 서버에서 최신으로 갱신. 설치 전 항상 실행하는 습관 권장.

```bash
# 2. HTTPS 통신 및 GPG 관련 필수 패키지 설치
sudo apt install ca-certificates curl gnupg -y
```
> - `ca-certificates`: HTTPS 연결 시 SSL 인증서 검증에 필요
> - `curl`: URL에서 파일 다운로드
> - `gnupg`: GPG 키 처리 도구 (저장소 서명 검증용)

```bash
# 3. Docker GPG 키 저장 디렉토리 생성
sudo install -m 0755 -d /etc/apt/keyrings
```
> `/etc/apt/keyrings`는 apt 저장소의 서명 키를 보관하는 디렉토리.
> `-m 0755`: 디렉토리 권한을 755(소유자 rwx, 그룹/기타 rx)로 설정.
> `install -d`는 `mkdir`과 유사하지만 권한 설정을 한 번에 처리 가능.

```bash
# 4. Docker 공식 GPG 키 다운로드 및 등록
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
```
> - `curl -fsSL`: 파일 다운로드 (`-f` 오류 시 실패, `-s` 진행 표시 숨김, `-S` 오류 표시, `-L` 리다이렉트 따라가기)
> - `gpg --dearmor`: ASCII 형식의 GPG 키를 바이너리 형식으로 변환
> - `-o /etc/apt/keyrings/docker.gpg`: 변환된 키를 파일로 저장
>
> **GPG 키가 필요한 이유:**
> apt는 패키지 설치 전 저장소의 GPG 서명을 검증하여 위·변조 여부를 확인한다.
> Docker 공식 저장소를 신뢰하려면 Docker의 공개 GPG 키가 서버에 등록되어 있어야 한다.

```bash
# 5. GPG 키 읽기 권한 부여
sudo chmod a+r /etc/apt/keyrings/docker.gpg
```
> `a+r`: 모든 사용자(all)에게 읽기(read) 권한 추가.
> apt가 root 외 권한으로 이 키를 읽을 수 있도록 허용.

```bash
# 6. Docker 공식 apt 저장소 등록
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
```
> 한 줄씩 풀어서 이해하면:
> - `deb`: Debian/Ubuntu 계열 패키지 저장소 형식
> - `arch=$(dpkg --print-architecture)`: 현재 CPU 아키텍처 자동 감지 (amd64, arm64 등)
> - `signed-by=/etc/apt/keyrings/docker.gpg`: 위에서 등록한 GPG 키로 패키지 서명 검증
> - `https://download.docker.com/linux/ubuntu`: Docker 공식 저장소 주소
> - `$(. /etc/os-release && echo "$VERSION_CODENAME")`: Ubuntu 버전 코드명 자동 감지
>   - Ubuntu 22.04 → `jammy`
>   - Ubuntu 24.04 → `noble`
> - `stable`: 안정 버전 채널 (edge, test 채널도 존재)
> - `sudo tee /etc/apt/sources.list.d/docker.list`: 위 내용을 저장소 목록 파일로 저장
> - `> /dev/null`: tee의 표준 출력을 버림 (화면에 출력 안 함)

```bash
# 7. 저장소 등록 후 패키지 목록 다시 갱신
sudo apt update

# 8. Docker 및 Compose 플러그인 설치
sudo apt install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin -y
```
> - `docker-ce`: Docker Community Edition (핵심 엔진)
> - `docker-ce-cli`: Docker CLI 명령어 도구
> - `containerd.io`: 컨테이너 런타임 (Docker 엔진이 실제로 컨테이너를 실행하는 데 사용)
> - `docker-buildx-plugin`: BuildKit 기반 고급 빌드 기능 (`--mount=type=cache` 등)
> - `docker-compose-plugin`: `docker compose` 명령어 (v2)

#### 설치 확인

```bash
docker --version
# Docker version 27.x.x, build ...

docker compose version
# Docker Compose version v2.x.x
```

#### 추가: apt 저장소 구조 이해

```
/etc/apt/
├── sources.list              # 기본 Ubuntu 저장소 목록
└── sources.list.d/
    └── docker.list           # Docker 공식 저장소 (방금 등록)

/etc/apt/keyrings/
└── docker.gpg                # Docker 저장소 서명 키
```

`sources.list.d/` 디렉토리는 서드파티 저장소를 개별 파일로 관리하는 방식.
기본 `sources.list`를 건드리지 않아서 관리가 깔끔하고 제거도 쉬움.

---

### 3-1. 전략 설계

**기존 방식 (이전 deploy-dev.yml):**
```
GitHub Actions에서 JAR 빌드 + Frontend 빌드
→ SCP로 서버에 파일 전송
→ SSH로 서버 접속
→ systemctl restart cali-dev (서비스 재시작)
```

**새 방식 (Docker Compose 홈서버):**
```
GitHub Actions에서 SSH로 서버 접속
→ 서버에서 git pull (최신 코드 반영)
→ docker compose down
→ docker compose up -d --build (서버에서 빌드 + 실행)
```

**왜 새 방식을 선택했나:**
- 홈서버에 systemctl 서비스 등록, Nginx 설치 등 복잡한 초기 설정 불필요
- Docker만 있으면 어디서든 동일하게 실행 가능 (이식성)
- `docker compose down/up` 으로 전체 환경을 깨끗하게 교체 가능
- 향후 dashboard 등 다른 프로젝트도 동일한 패턴으로 추가 가능

---

### 3-2. Docker 파일 작성

#### backend/Dockerfile (멀티스테이지)

```dockerfile
# Stage 1: 빌드 (JDK + Gradle)
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /build

COPY gradlew gradlew
COPY gradle/ gradle/
RUN chmod +x gradlew

COPY build.gradle settings.gradle ./
# BuildKit 캐시: Gradle 의존성을 빌드 간 공유
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies --configuration compileClasspath 2>/dev/null || true

COPY src/ src/
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon clean bootJar -x test

# Stage 2: 실행 (JRE만)
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=builder /build/build/libs/*.jar app.jar
EXPOSE 8050
ENTRYPOINT ["java", "-jar", "-Dfile.encoding=UTF-8", "app.jar"]
```

**레이어 캐시 최적화 전략:**
1. `gradlew`, `gradle/` → `build.gradle` 순으로 복사
2. 소스(`src/`) 복사 전에 의존성 먼저 resolve
3. 소스만 바뀌면 의존성 레이어 캐시 재사용 → 빌드 시간 단축

#### frontend/Dockerfile (멀티스테이지)

```dockerfile
# Stage 1: Node.js 빌드
FROM node:20-alpine AS builder
WORKDIR /build
COPY package.json package-lock.json ./
# BuildKit 캐시: npm 패키지를 빌드 간 공유
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npm run build

# Stage 2: Nginx 서빙
FROM nginx:1.27-alpine
COPY --from=builder /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

#### frontend/nginx.conf

```nginx
server {
    listen 80;

    # React SPA: /admin/ 경로
    # try_files: React Router 지원 (새로고침 시 index.html 반환)
    location /admin/ {
        alias /usr/share/nginx/html/;
        try_files $uri $uri/ /admin/index.html;
    }

    # REST API 프록시: Docker 내부 네트워크로 backend 서비스에 접근
    location /api/ {
        proxy_pass http://backend:8050;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # Spring Security 처리
    location /logout {
        proxy_pass http://backend:8050;
    }

    # SSR 페이지 (Thymeleaf)
    location / {
        proxy_pass http://backend:8050;
    }
}
```

**proxy_set_header 설명:**
- `Host`: 원본 요청의 도메인 정보 전달
- `X-Real-IP`: 실제 클라이언트 IP 전달 (nginx가 중간에서 받으면 IP가 nginx IP로 바뀌기 때문)
- `X-Forwarded-For`: 프록시 경유 기록 (보안/로깅 목적)

#### docker-compose.yml

```yaml
services:
  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: cali-backend
    ports:
      - "8050:8050"
    volumes:
      - /opt/cali/application.properties:/app/application.properties:ro
      - /opt/cali/application-dev.properties:/app/application-dev.properties:ro
      - /opt/cali/logs:/app/logs
      - /opt/cali/temp:/app/temp
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - SPRING_CONFIG_LOCATION=optional:file:/app/application.properties,optional:file:/app/application-dev.properties
      - TZ=Asia/Seoul
    restart: unless-stopped
    networks:
      - cali-network

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    container_name: cali-frontend
    ports:
      - "8080:80"
    depends_on:
      - backend
    restart: unless-stopped
    networks:
      - cali-network

networks:
  cali-network:
    name: cali-network
```

---

### 3-3. 홈서버 초기 설정

```bash
# 배포 디렉토리 생성
sudo mkdir -p /opt/cali/{logs,temp}
sudo chown -R $USER:$USER /opt/cali
```

**`/opt` 디렉토리를 쓰는 이유:**
- Linux 관례상 `/opt`는 "optional software" 설치 경로
- 시스템 패키지(`/usr`)와 분리되어 관리가 깔끔함
- 홈 디렉토리(`~`)보다 서버 애플리케이션 배포에 적합

---

### 3-4. GitHub Deploy Key 설정

```bash
# 홈서버에서 키 쌍 생성
ssh-keygen -t ed25519 -C "cali-deploy-key" -f ~/.ssh/cali_deploy -N ""

# 공개키 확인
cat ~/.ssh/cali_deploy.pub
# 출력 예시: ssh-ed25519 AAAAC3Nza... cali-deploy-key
```

GitHub 저장소 → **Settings → Deploy keys → Add deploy key**
- Title: `cali-homeserver-deploy-key`
- Key: 공개키 전체 내용 붙여넣기
- Allow write access: **체크 안 함** (pull만 필요)

**GitHub Actions용 별도 키도 생성:**
```bash
ssh-keygen -t ed25519 -C "github-actions" -f ~/.ssh/actions_key -N ""
# 공개키 → 홈서버 ~/.ssh/authorized_keys에 추가
# 개인키 전체 내용 → GitHub Secrets HOME_SSH_KEY에 등록
```

---

### 3-5. 포트 22 차단 문제 해결

**문제:**
```
git clone git@github.com:계정명/저장소명.git /opt/cali/repo
→ ssh: connect to host github.com port 22: Connection timed out
```

**원인:**
- 국내 ISP(KT, SKT, LGU+ 등)는 가정용 회선에서 포트 22(SSH) 아웃바운드를 차단하는 경우가 많음
- 포트 22는 무차별 대입 공격(Brute Force Attack)이 많이 발생하는 포트
- ISP 입장에서 보안 사고 예방 + 가정용 요금제의 서버 운영 약관 제한

**해결:**
GitHub은 포트 22가 막힌 환경을 위해 `ssh.github.com:443` 우회 경로를 제공.

```bash
nano ~/.ssh/config
```

```
Host github.com
  Hostname ssh.github.com
  Port 443
  IdentityFile ~/.ssh/cali_deploy
  StrictHostKeyChecking no
```

**포트 443이 항상 열려 있는 이유:**
- 443은 HTTPS 표준 포트
- 웹 브라우징에 필수이므로 어떤 네트워크도 막지 않음
- GitHub이 이를 활용해 SSH 트래픽을 443으로 터널링

**연결 테스트:**
```bash
ssh -T -p 443 -i ~/.ssh/cali_deploy git@ssh.github.com
# 성공 시: Hi 계정명! You've successfully authenticated...
```

**`known_hosts` 메시지 의미:**
```
Permanently added '[ssh.github.com]:443' (ED25519) to the list of known hosts.
```
- `ssh.github.com:443`의 서버 공개키를 `~/.ssh/known_hosts` 파일에 저장
- "이 서버는 신뢰할 수 있다"고 기록하는 것
- 다음번 접속부터는 이 메시지 없이 바로 연결됨
- TOFU(Trust On First Use) 방식의 보안 모델

---

### 3-6. git clone 및 파일 배치

```bash
# 저장소 clone
git clone git@github.com:계정명/저장소명.git /opt/cali/repo
cd /opt/cali/repo
git checkout develop
```

**WinSCP로 설정 파일 업로드:**
- 프로토콜: SFTP
- 호스트: 홈서버 내부 IP
- 포트: 22
- 계정/비밀번호 입력

업로드 파일:
- `/opt/cali/application.properties` (base 설정, DB URL 등 포함)
- `/opt/cali/application-dev.properties` (개발 환경 override 값)

**Permission Denied 문제:**
```bash
# 원인: /opt/cali 소유자가 root
# WinSCP는 일반 계정으로 접속하므로 쓰기 권한 없음

# 해결: 소유자를 현재 계정으로 변경
sudo chown -R bada:bada /opt/cali
# bada 부분은 실제 계정명으로 대체
```

---

### 3-7. 배포 테스트 및 오류 해결

```bash
cd /opt/cali/repo
docker compose up -d --build
```

#### 오류 1: `no configuration file provided: not found`

**원인:**
- `docker-compose.yml`이 서버에 없음
- 새로 만든 파일들이 아직 git commit/push가 안 된 상태

**해결:**
개발 PC에서 먼저 commit + push 후, 서버에서 git pull:
```bash
# 개발 PC
git add docker-compose.yml backend/Dockerfile frontend/Dockerfile frontend/nginx.conf
git commit -m "Docker Compose 홈서버 배포 설정 추가"
git push origin develop

# 홈서버
git pull origin develop
```

#### 오류 2: `Failed to determine a suitable driver class` / `url attribute is not specified`

**원인:**
```
Spring Boot가 DB 연결 정보를 찾지 못함

원래 구조:
  application.properties      ← DB URL 등 base 설정 포함 (git에 없음!)
  application-dev.properties  ← 개발 환경 override 값만

Docker 빌드 시 JAR에 application.properties가 포함되지 않음
→ Spring Boot가 DB URL을 찾을 수 없음
```

**해결:**
`docker-compose.yml`에서 두 파일 모두 마운트 + `SPRING_CONFIG_LOCATION` 명시:

```yaml
volumes:
  - /opt/cali/application.properties:/app/application.properties:ro
  - /opt/cali/application-dev.properties:/app/application-dev.properties:ro
environment:
  - SPRING_CONFIG_LOCATION=optional:file:/app/application.properties,optional:file:/app/application-dev.properties
```

**`SPRING_CONFIG_ADDITIONAL_LOCATION` vs `SPRING_CONFIG_LOCATION` 차이:**

| 환경변수 | 동작 |
|---|---|
| `SPRING_CONFIG_ADDITIONAL_LOCATION` | 기본 classpath 설정에 **추가** |
| `SPRING_CONFIG_LOCATION` | 기본 설정을 **대체** (명시한 파일만 읽음) |

JAR 안에 `application.properties`가 없는 경우 `SPRING_CONFIG_LOCATION`을 써서 두 파일을 직접 지정해야 함.

**`optional:` 접두사의 의미:**
파일이 존재하지 않아도 에러 없이 무시. 파일이 있으면 읽음.
없으면 바로 실패해서 컨테이너가 계속 재시작하는 문제를 방지.

---

## 4. 발생한 문제 & 해결 과정 총정리

| # | 문제 | 원인 | 해결 |
|---|---|---|---|
| 1 | `Unable to locate package docker-compose-plugin` | Ubuntu 기본 저장소에는 Docker 공식 패키지 없음 | Docker 공식 GPG 키 + apt 저장소 등록 후 재설치 |
| 2 | `Connection timed out port 22` | ISP가 포트 22 아웃바운드 차단 | `~/.ssh/config`에 `ssh.github.com:443` 우회 설정 |
| 3 | Deploy Key 등록해도 계정 SSH 키 없다는 경고 | Deploy Key는 계정이 아닌 저장소에 등록 (정상) | 무시 (계정 SSH 키 페이지에 안 보이는 것이 정상) |
| 4 | WinSCP Permission Denied | `/opt/cali` 소유자가 root, WinSCP는 일반 계정 접속 | `chown -R bada:bada /opt/cali` |
| 5 | `no configuration file provided` | `docker-compose.yml`이 서버에 없음 (push 안 함) | 개발 PC에서 commit+push 후 서버에서 git pull |
| 6 | `Failed to determine a suitable driver class` | JAR에 `application.properties` 미포함 (gitignore) | 두 properties 파일 볼륨 마운트 + `SPRING_CONFIG_LOCATION` 명시 |

---

## 5. 완성된 파일 구조

```
cali/
├── .github/
│   └── workflows/
│       └── deploy-dev.yml        # GitHub Actions 워크플로우
├── backend/
│   ├── Dockerfile                # Spring Boot 멀티스테이지 빌드
│   └── src/ ...
├── frontend/
│   ├── Dockerfile                # React + Nginx 멀티스테이지 빌드
│   ├── nginx.conf                # Nginx 설정
│   └── src/ ...
└── docker-compose.yml            # 서비스 오케스트레이션

홈서버 /opt/cali/
├── application.properties        # base 설정 (DB URL 등, git 제외)
├── application-dev.properties    # dev override (git 제외)
├── logs/                         # 로그 볼륨
├── temp/                         # 임시 파일 볼륨
└── repo/                         # git clone 위치 (= cali/)
```

---

## 6. 배포 흐름 최종 정리

### 수동 배포 시 (GitHub Actions)

1. GitHub Actions 탭 → Deploy Dev → Run workflow
2. SSH로 홈서버 접속
3. `/opt/cali/repo`에서 `git fetch + reset --hard` (최신 코드 강제 반영)
4. `docker compose down` (기존 컨테이너 제거)
5. `docker compose up -d --build` (이미지 재빌드 + 실행)
6. Health check: `curl localhost:8050/actuator/health` → `{"status":"UP"}` 확인

### 서버 재부팅 후

- Docker 데몬이 자동 시작됨 (Ubuntu 기본값)
- `restart: unless-stopped` 정책에 의해 컨테이너 자동 재시작
- 별도 조치 불필요

### 접속 URL

| 대상 | URL |
|---|---|
| SSR 페이지 | `http://홈서버IP:8080/member/login` |
| React Admin | `http://홈서버IP:8080/admin/` |
| 헬스체크 (서버 내부) | `http://localhost:8050/actuator/health` |

---

## 7. 알아두면 좋은 추가 개념들

### BuildKit 캐시 마운트

```dockerfile
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar
```

- Docker BuildKit의 고급 기능
- `--mount=type=cache`로 지정한 디렉토리는 빌드 간 공유됨
- 컨테이너 이미지에는 포함되지 않지만, 다음 빌드 시 재사용
- Gradle 의존성, npm 패키지 등 반복 다운로드 방지 → 재빌드 시간 단축

### SFTP vs SCP vs SSH

| 프로토콜 | 용도 |
|---|---|
| SSH | 원격 터미널 접속, 명령 실행 |
| SCP | SSH 기반 파일 복사 (단방향) |
| SFTP | SSH 기반 파일 전송 (양방향, 탐색 가능) |

WinSCP는 SFTP를 사용 → 파일 탐색기처럼 서버 파일 시스템 탐색 가능

### Docker 네트워크 종류

| 타입 | 설명 |
|---|---|
| bridge | 컨테이너 간 통신용 가상 네트워크 (기본값, 이번 사용) |
| host | 호스트 네트워크 직접 사용 (포트 충돌 주의) |
| none | 네트워크 없음 (완전 격리) |
| overlay | 여러 서버에 걸친 컨테이너 네트워크 (Docker Swarm용) |

### 향후 포트 충돌 방지 전략

```
cali 프로젝트:
  backend  → 호스트 8050 : 컨테이너 8050
  frontend → 호스트 8080 : 컨테이너 80

dashboard 프로젝트 (추후):
  backend  → 호스트 8051 : 컨테이너 8050
  frontend → 호스트 8081 : 컨테이너 80
```

각 프로젝트의 `docker-compose.yml`에서 호스트 포트만 다르게 지정하면 됨.
컨테이너 내부 포트는 서로 독립적이므로 충돌 없음.

### `git reset --hard origin/develop` 사용 이유

```bash
git fetch origin develop
git reset --hard origin/develop
```

`git pull` 대신 이 방식을 사용하는 이유:
- `git pull`은 로컬 변경사항과 merge를 시도 → 서버에서 직접 수정한 파일(예: docker-compose.yml 임시 수정)과 충돌 발생 가능
- `reset --hard`는 로컬 상태를 완전히 remote 기준으로 초기화 → 항상 깨끗한 배포 환경 보장
- 배포 서버에서는 직접 코드 수정을 하지 않는 것이 원칙이므로 이 방식이 안전함

---

> 다음 단계로 공부하면 좋은 것들:
> - Docker Hub 또는 GitHub Container Registry(GHCR)를 이용한 이미지 push/pull 방식
> - Nginx 리버스 프록시 설정 심화 (HTTPS, Let's Encrypt)
> - 도메인 + DDNS를 이용한 홈서버 외부 접근 설정
> - Docker volume vs bind mount 차이
> - dashboard 프로젝트 동일 홈서버에 추가 배포
