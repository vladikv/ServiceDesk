FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY src src

RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system servicedesk \
    && useradd --system --gid servicedesk --home-dir /app servicedesk

COPY --from=build --chown=servicedesk:servicedesk \
    /workspace/target/service-desk-0.1.0-SNAPSHOT.jar /app/service-desk.jar

USER servicedesk
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/service-desk.jar"]
