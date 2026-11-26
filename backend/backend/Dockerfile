# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# Tectonic compiles the LaTeX CV template to PDF (static binary, fetches TeX packages on first use).
FROM alpine:3.20 AS tectonic
ARG TECTONIC_VERSION=0.17.0
ADD https://github.com/tectonic-typesetting/tectonic/releases/download/tectonic%40${TECTONIC_VERSION}/tectonic-${TECTONIC_VERSION}-x86_64-unknown-linux-musl.tar.gz /tmp/tectonic.tar.gz
RUN tar -xzf /tmp/tectonic.tar.gz -C /usr/local/bin tectonic && chmod +x /usr/local/bin/tectonic

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 --create-home --home-dir /home/cvpilot cvpilot
COPY --from=tectonic /usr/local/bin/tectonic /usr/local/bin/tectonic
COPY --from=build /src/target/backend-*.jar app.jar
USER cvpilot
ENV LATEX_COMPILER=tectonic
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
