FROM node:20-alpine AS frontend-build
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY backend/pom.xml ./
COPY backend/src ./src
COPY --from=frontend-build /frontend/dist ./src/main/resources/static
RUN mvn --batch-mode -Dmaven.test.skip=true package

FROM eclipse-temurin:17-jre-jammy
RUN groupadd --system drakkar \
    && useradd --system --gid drakkar drakkar \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /opt/drakkar
COPY --from=build /workspace/target/drakkar-erp-backend-0.1.0.jar ./app.jar
USER drakkar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/opt/drakkar/app.jar"]
