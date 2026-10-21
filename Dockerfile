# 数创星光外卖 - 多阶段构建
# 构建阶段不执行测试：Testcontainers 需要 Docker daemon，镜像构建环境不具备。
# 测试由 `mvn test` 独立执行，镜像只做编译与打包。
ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-17
ARG JRE_IMAGE=eclipse-temurin:17-jre-alpine

FROM ${MAVEN_IMAGE} AS build
ARG MAVEN_MIRROR_URL=
# 可选：为构建阶段注入 Maven 镜像，避免把环境专属仓库地址写死在镜像里
RUN if [ -n "$MAVEN_MIRROR_URL" ]; then \
      mkdir -p /root/.m2 && \
      printf '%s' "<settings><mirrors><mirror><id>build-mirror</id><mirrorOf>central</mirrorOf><url>${MAVEN_MIRROR_URL}</url></mirror></mirrors></settings>" \
        > /root/.m2/settings.xml; \
    fi
WORKDIR /build
COPY pom.xml ./
COPY solo-eats-common/pom.xml solo-eats-common/
COPY solo-eats-marketing/pom.xml solo-eats-marketing/
COPY solo-eats-product/pom.xml solo-eats-product/
COPY solo-eats-delivery/pom.xml solo-eats-delivery/
COPY solo-eats-order/pom.xml solo-eats-order/
COPY solo-eats-payment/pom.xml solo-eats-payment/
COPY solo-eats-settlement/pom.xml solo-eats-settlement/
COPY solo-eats-app/pom.xml solo-eats-app/
COPY . .
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -Dmaven.test.skip=true package

FROM ${JRE_IMAGE}
# 运行时基础镜像的 OS 包安全更新（如 Alpine zlib）
RUN apk upgrade --no-cache \
    && addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build --chown=app:app /build/solo-eats-app/target/solo-eats-app-*.jar /app/app.jar
USER app
EXPOSE 8080
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseG1GC -Duser.timezone=Asia/Shanghai -Djava.security.egd=file:/dev/./urandom"
# exec 保证 SIGTERM 直达 JVM，配合 server.shutdown=graceful 完成优雅停机
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
