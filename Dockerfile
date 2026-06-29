# syntax=docker/dockerfile:1.7

FROM maven:3.9-eclipse-temurin-26 AS deps
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -DskipTests dependency:go-offline

FROM deps AS builder
WORKDIR /workspace
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -DskipTests clean package

FROM eclipse-temurin:26-jre-jammy AS runtime
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 pacs \
    && useradd --system --uid 10001 --gid pacs --home /app --shell /usr/sbin/nologin pacs \
    && mkdir -p /app/config /app/logs /home/Images \
    && chown -R pacs:pacs /app /home/Images

COPY --from=builder /workspace/target/*.jar /app/app.jar

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-XX:+UseContainerSupport", "-jar", "/app/app.jar"]
