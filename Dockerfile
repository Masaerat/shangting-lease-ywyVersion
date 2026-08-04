FROM maven:3.9.9-eclipse-temurin-21 AS builder

ARG APP_MODULE
WORKDIR /workspace
COPY . .
RUN mvn -B -pl "${APP_MODULE}" -am -DskipTests package \
    && cp "${APP_MODULE}"/target/*.jar /opt/app.jar

FROM eclipse-temurin:21-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system lease \
    && useradd --system --gid lease --home-dir /app lease

WORKDIR /app
COPY --from=builder --chown=lease:lease /opt/app.jar app.jar
USER lease

EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
