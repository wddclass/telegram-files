ARG GIT_COMMIT=unknown
ARG GIT_COMMIT_SHORT=unknown
ARG APP_VERSION=0.3.1
ARG GIT_REF=custom-0.3.1

FROM gradle:8.10-jdk23-alpine AS api-builder

ARG GIT_COMMIT
ARG GIT_COMMIT_SHORT
ARG APP_VERSION
ARG GIT_REF
ENV APP_GIT_COMMIT=$GIT_COMMIT \
    APP_GIT_COMMIT_SHORT=$GIT_COMMIT_SHORT \
    APP_VERSION=$APP_VERSION \
    APP_GIT_REF=$GIT_REF

WORKDIR /app

COPY ./api/build.gradle ./api/settings.gradle ./
COPY ./api/gradle ./gradle
RUN gradle dependencies --no-daemon

COPY ./api .
RUN gradle shadowJar --no-daemon && \
    mkdir -p /app/build/libs && \
    cp /app/build/libs/*.jar /app/api.jar && \
    jdeps --print-module-deps --ignore-missing-deps /app/api.jar > /app/dependencies.txt

FROM eclipse-temurin:23-jdk-alpine AS runtime-builder

WORKDIR /custom-jre

COPY --from=api-builder /app/dependencies.txt .
RUN apk add --no-cache binutils && \
    jlink \
        --add-modules $(cat dependencies.txt) \
        --output jre \
        --strip-debug \
        --no-man-pages \
        --no-header-files \
        --compress=2 && \
    apk del binutils

FROM node:21-alpine AS web-builder

WORKDIR /web

ENV NEXT_PUBLIC_API_URL=/api \
    NEXT_PUBLIC_WS_URL=/ws \
    NEXT_TELEMETRY_DISABLED=1 \
    SKIP_ENV_VALIDATION=1

COPY ./web/package*.json ./
RUN npm ci --frozen-lockfile

COPY ./web .
RUN npm run build

FROM alpine:3.18.12 AS final

WORKDIR /app

ARG TARGETARCH
ARG RCLONE_RELEASE=v1.69.1
ARG GIT_COMMIT=unknown
ARG GIT_COMMIT_SHORT=unknown
ARG APP_VERSION=0.3.1
ARG GIT_REF=custom-0.3.1
ENV JAVA_HOME=/jre \
    APP_GIT_COMMIT=$GIT_COMMIT \
    APP_GIT_COMMIT_SHORT=$GIT_COMMIT_SHORT \
    APP_VERSION=$APP_VERSION \
    APP_GIT_REF=$GIT_REF \
    PATH="/jre/bin:$PATH" \
    LANG=C.UTF-8 \
    NGINX_PORT=80

RUN addgroup -S tf && \
    adduser -S -G tf tf && \
    apk add --no-cache nginx wget curl unzip tini su-exec gettext openssl3 libstdc++ gcompat libc6-compat && \
    case "$TARGETARCH" in \
      amd64) RCLONE_ARCH=amd64 ;; \
      arm64) RCLONE_ARCH=arm64 ;; \
      *) echo "unsupported architecture: $TARGETARCH" >&2; exit 1 ;; \
    esac && \
    wget -qO /tmp/rclone.zip "https://downloads.rclone.org/${RCLONE_RELEASE}/rclone-${RCLONE_RELEASE}-linux-${RCLONE_ARCH}.zip" && \
    unzip -q /tmp/rclone.zip -d /tmp && \
    install -m 755 /tmp/rclone-${RCLONE_RELEASE}-linux-${RCLONE_ARCH}/rclone /usr/bin/rclone && \
    rm -rf /tmp/rclone.zip /tmp/rclone-${RCLONE_RELEASE}-linux-${RCLONE_ARCH} && \
    rclone version && \
    rm -rf /tmp/* /var/tmp/* && \
    touch /run/nginx.pid && \
    chown -R tf:tf /app /etc/nginx /var/lib/nginx /var/log/nginx /run/nginx.pid && \
    printf '#!/bin/sh\njava -Djava.library.path=/app/tdlib -cp /app/api.jar telegram.files.Maintain "$@"\n' > /usr/bin/tfm && \
    chmod +x /usr/bin/tfm

COPY --from=runtime-builder --chown=tf:tf /custom-jre/jre /jre
COPY --from=api-builder --chown=tf:tf /app/api.jar /app/api.jar
COPY --from=web-builder --chown=tf:tf /web/out /app/web/

COPY --chown=tf:tf ./tdlib/linux_$TARGETARCH /app/tdlib
COPY --chown=tf:tf ./entrypoint.sh .
COPY --chown=tf:tf ./nginx.conf.template /etc/nginx/nginx.conf.template

EXPOSE $NGINX_PORT

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["/bin/sh", "./entrypoint.sh"]
