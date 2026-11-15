# syntax=docker/dockerfile:1.7

# ==========================================
# 1. BUILD
# ==========================================
FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /src

ARG MODULE

COPY . .

RUN --mount=type=cache,target=/root/.m2 \
    test -n "$MODULE" && \
    mvn -B -pl "$MODULE" -am package -DskipTests

# ==========================================
# 2. CUSTOM JRE
# ==========================================
FROM eclipse-temurin:17-jdk-alpine AS jre-builder

RUN apk add --no-cache binutils

RUN jlink \
    --add-modules java.base,java.logging,java.naming,java.sql,java.xml,java.management,java.instrument,java.desktop,java.security.jgss,java.transaction.xa,java.compiler,java.net.http,jdk.crypto.ec,jdk.unsupported \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=2 \
    --output /customjre

# ==========================================
# 3. RUNTIME
# ==========================================
FROM alpine:3.20

WORKDIR /app

RUN addgroup -S -g 1001 app && \
    adduser -S -D -H -u 1001 -G app app

COPY --from=jre-builder /customjre /opt/java

ARG MODULE

COPY --from=build --chown=1001:1001 \
    /src/${MODULE}/target/app.jar ./app.jar

ENV JAVA_HOME=/opt/java
ENV PATH="${JAVA_HOME}/bin:${PATH}"

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

USER 1001:1001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
