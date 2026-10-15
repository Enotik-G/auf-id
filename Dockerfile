# syntax=docker/dockerfile:1

# ---- Этап 1: сборка jar ----
# Полный JDK нужен только здесь: в итоговый образ из этого этапа уйдёт лишь готовый jar.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY src/main src/main

# Тесты не запускаем: их уже прогнал CI, а в образ они не копируются (.dockerignore).
# Кеш ~/.m2 переживает пересборки образа, но в сам образ не попадает: после правки кода
# зависимости не скачиваются заново.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --no-transfer-progress package -DskipTests
