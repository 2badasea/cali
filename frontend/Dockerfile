# ═══════════════════════════════════════════════════════════════
# frontend/Dockerfile
# React SPA 멀티스테이지 빌드
#   - Stage 1 (builder): Node.js로 Vite 빌드
#   - Stage 2 (runtime): Nginx로 정적 파일 서빙 + API 프록시
# ═══════════════════════════════════════════════════════════════

# ── Stage 1: Build ───────────────────────────────────────────────
FROM node:20-alpine AS builder
WORKDIR /build

# 의존성 파일 먼저 복사 → node_modules 레이어 캐시 최대화
COPY package.json package-lock.json ./

# BuildKit 캐시 마운트: npm 캐시를 빌드 간 공유 → 반복 배포 시 패키지 재다운로드 방지
RUN --mount=type=cache,target=/root/.npm \
    npm ci

# 소스 복사 및 빌드
# vite.config.js base: "/admin/" → dist/ 결과물의 자산 경로가 /admin/assets/... 로 생성됨
COPY . .
RUN npm run build

# ── Stage 2: Runtime (Nginx) ──────────────────────────────────────
FROM nginx:1.27-alpine
WORKDIR /usr/share/nginx/html

# Vite 빌드 결과물 복사
# dist/index.html, dist/assets/ → /usr/share/nginx/html/ 로 복사
# nginx.conf에서 /admin/ → alias /usr/share/nginx/html/ 로 서빙
COPY --from=builder /build/dist ./

# Nginx 설정 복사
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80