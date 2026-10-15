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

# ---- Этап 2: запуск ----
# Только JRE: компилятора и Maven в итоговом образе нет — меньше размер и меньше лишнего внутри.
FROM eclipse-temurin:25-jre

# Отдельный системный пользователь без домашней папки и без пароля. Если в приложении найдут
# уязвимость, атакующий получит права этого пользователя, а не root.
RUN groupadd --system --gid 10001 auf-id \
 && useradd --system --uid 10001 --gid auf-id --no-create-home --shell /usr/sbin/nologin auf-id

WORKDIR /app
COPY --from=build /workspace/target/auf-id-*.jar auf-id.jar

USER auf-id

# По умолчанию JVM берёт под память лишь четверть того, что выделено контейнеру; серверу
# приложений этого мало. Переопределяется переменной окружения при запуске.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

EXPOSE 8080

# Запись в виде списка: java становится главным процессом контейнера и сама получает сигнал
# остановки от `docker stop` — Spring успевает корректно закрыть соединения.
ENTRYPOINT ["java", "-jar", "/app/auf-id.jar"]
