# 商家中心 / 平台运营后台 - 静态资源构建 + nginx 托管
ARG NODE_IMAGE=node:20-alpine
ARG NGINX_IMAGE=nginx:1.27-alpine

FROM ${NODE_IMAGE} AS build
ARG NPM_REGISTRY=
WORKDIR /build
# package-lock.json 锁版本，npm ci 保证可重复构建
COPY solo-eats-web/package.json solo-eats-web/package-lock.json ./
RUN if [ -n "$NPM_REGISTRY" ]; then npm config set registry "$NPM_REGISTRY"; fi \
    && npm ci --no-audit --no-fund
COPY solo-eats-web/ ./
# vue-tsc --noEmit && vite build；类型错误直接让镜像构建失败
RUN npm run build

FROM ${NGINX_IMAGE}
RUN rm -f /etc/nginx/conf.d/default.conf
COPY solo-eats-web/nginx.conf /etc/nginx/conf.d/solo-eats.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
    CMD wget -qO- http://127.0.0.1/nginx-health >/dev/null 2>&1 || exit 1
