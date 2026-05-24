# Stage 1: Build frontend
FROM node:20-alpine AS frontend-build
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Stage 2: Build backend
FROM eclipse-temurin:25-jdk-alpine AS backend-build
WORKDIR /backend
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts backend/gradle.properties ./
COPY backend/gradle/ gradle/
RUN chmod +x gradlew && sed -i '/org.gradle.java.home/d' gradle.properties 2>/dev/null || true
RUN ./gradlew dependencies --no-daemon -q
COPY backend/src/ src/
COPY --from=frontend-build /frontend/dist src/main/resources/static/
RUN ./gradlew bootJar --no-daemon

# Stage 3: Runtime
FROM eclipse-temurin:25-jre-noble
WORKDIR /app
COPY --from=backend-build /backend/build/libs/*.jar app.jar
RUN mkdir -p /app/sources/errors /app/wiki
EXPOSE 7777
ENTRYPOINT ["java", "-jar", "app.jar"]
