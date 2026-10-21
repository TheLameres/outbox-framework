# syntax=docker/dockerfile:1

# ─────────────────────────────────────────────────────────────────────────────
# Один Dockerfile на три сервиса. Отличаются они только MODULE_PATH.
#
# Контекст сборки — корень репозитория, а не каталог сервиса: сервис зависит от
# модулей фреймворка (txbox-core, txbox-producer-*, txbox-consumer-*) и от
# txbox-coffeeshop-domain. Собирать их из каталога сервиса нечем — фреймворк
# не опубликован в Maven Central, он собирается из исходников этим же вызовом
# (-am добавляет все зависимости реактора).
# ─────────────────────────────────────────────────────────────────────────────

ARG MAVEN_IMAGE=maven:3.9.16-eclipse-temurin-25-noble
ARG JRE_IMAGE=eclipse-temurin:25-jre-alpine

# ── Stage 1: сборка ──────────────────────────────────────────────────────────
FROM ${MAVEN_IMAGE} AS build

WORKDIR /workspace

# Сначала только pom'ы: слой зависимостей переиспользуется, пока не менялись
# версии. Копируем весь pom-набор — Maven реактор читает их целиком.
COPY pom.xml lombok.config ./
COPY txbox-core/pom.xml txbox-core/
COPY txbox-jpa/pom.xml txbox-jpa/
COPY txbox-kafka/pom.xml txbox-kafka/
COPY txbox-producer/pom.xml txbox-producer/
COPY txbox-producer/txbox-producer-api/pom.xml txbox-producer/txbox-producer-api/
COPY txbox-producer/txbox-producer-jpa/pom.xml txbox-producer/txbox-producer-jpa/
COPY txbox-producer/txbox-producer-kafka/pom.xml txbox-producer/txbox-producer-kafka/
COPY txbox-producer/txbox-producer-starter/pom.xml txbox-producer/txbox-producer-starter/
COPY txbox-consumer/pom.xml txbox-consumer/
COPY txbox-consumer/txbox-consumer-api/pom.xml txbox-consumer/txbox-consumer-api/
COPY txbox-consumer/txbox-consumer-jpa/pom.xml txbox-consumer/txbox-consumer-jpa/
COPY txbox-consumer/txbox-consumer-kafka/pom.xml txbox-consumer/txbox-consumer-kafka/
COPY txbox-consumer/txbox-consumer-starter/pom.xml txbox-consumer/txbox-consumer-starter/
COPY txbox-examples/pom.xml txbox-examples/
COPY txbox-examples/txbox-full-example/pom.xml txbox-examples/txbox-full-example/
COPY txbox-examples/txbox-coffeeshop-example/pom.xml txbox-examples/txbox-coffeeshop-example/
COPY txbox-examples/txbox-coffeeshop-example/txbox-coffeeshop-domain/pom.xml txbox-examples/txbox-coffeeshop-example/txbox-coffeeshop-domain/
COPY txbox-examples/txbox-coffeeshop-example/txbox-coffeeshop-services/pom.xml txbox-examples/txbox-coffeeshop-example/txbox-coffeeshop-services/
COPY txbox-examples/txbox-coffeeshop-example/txbox-coffeeshop-services/txbox-coffeeshop-order-service/pom.xml txbox-examples/txbox-coffeeshop-example/txbox-coffeeshop-services/txbox-coffeeshop-order-service/
COPY txbox-examples/txbox-coffeeshop-example/txbox-coffeeshop-services/txbox-coffeeshop-inventory-service/pom.xml txbox-examples/txbox-coffeeshop-example/txbox-coffeeshop-services/txbox-coffeeshop-inventory-service/
COPY txbox-examples/txbox-coffeeshop-example/txbox-coffeeshop-services/txbox-coffeeshop-barista-service/pom.xml txbox-examples/txbox-coffeeshop-example/txbox-coffeeshop-services/txbox-coffeeshop-barista-service/

# Кеш Maven монтируется только на этот шаг: ~200 МБ зависимостей не попадают
# в образ и переиспользуются между сборками трёх сервисов.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -N install

# Теперь исходники
COPY txbox-core/src txbox-core/src
COPY txbox-jpa/src txbox-jpa/src
COPY txbox-kafka/src txbox-kafka/src
COPY txbox-producer/txbox-producer-api/src txbox-producer/txbox-producer-api/src
COPY txbox-producer/txbox-producer-jpa/src txbox-producer/txbox-producer-jpa/src
COPY txbox-producer/txbox-producer-kafka/src txbox-producer/txbox-producer-kafka/src
COPY txbox-producer/txbox-producer-starter/src txbox-producer/txbox-producer-starter/src
COPY txbox-consumer/txbox-consumer-api/src txbox-consumer/txbox-consumer-api/src
COPY txbox-consumer/txbox-consumer-jpa/src txbox-consumer/txbox-consumer-jpa/src
COPY txbox-consumer/txbox-consumer-kafka/src txbox-consumer/txbox-consumer-kafka/src
COPY txbox-consumer/txbox-consumer-starter/src txbox-consumer/txbox-consumer-starter/src
COPY txbox-examples/txbox-coffeeshop-example/txbox-coffeeshop-domain/src txbox-examples/txbox-coffeeshop-example/txbox-coffeeshop-domain/src
COPY txbox-examples/txbox-coffeeshop-example/txbox-coffeeshop-services/txbox-coffeeshop-order-service/src txbox-examples/txbox-coffeeshop-example/txbox-coffeeshop-services/txbox-coffeeshop-order-service/src
COPY txbox-examples/txbox-coffeeshop-example/txbox-coffeeshop-services/txbox-coffeeshop-inventory-service/src txbox-examples/txbox-coffeeshop-example/txbox-coffeeshop-services/txbox-coffeeshop-inventory-service/src
COPY txbox-examples/txbox-coffeeshop-example/txbox-coffeeshop-services/txbox-coffeeshop-barista-service/src txbox-examples/txbox-coffeeshop-example/txbox-coffeeshop-services/txbox-coffeeshop-barista-service/src

ARG MODULE_PATH
RUN --mount=type=cache,target=/root/.m2 \
    test -n "${MODULE_PATH}" || (echo "MODULE_PATH build-arg is required" && exit 1) && \
    mvn -B -q -pl "${MODULE_PATH}" -am -DskipTests package && \
    cp "${MODULE_PATH}/target/app.jar" /workspace/service.jar

# ── Stage 2: рантайм ─────────────────────────────────────────────────────────
FROM ${JRE_IMAGE}

# Отдельный непривилегированный пользователь: приложение не должно работать от root.
RUN addgroup -S app && adduser -S -G app app

WORKDIR /app
COPY --from=build /workspace/service.jar /app/app.jar

USER app
EXPOSE 8080

# MaxRAMPercentage вместо -Xmx: размер кучи считается от лимита контейнера,
# а не от памяти хоста — иначе JVM возьмёт четверть всей машины.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# exec (shell-форма с exec) — чтобы SIGTERM дошёл до JVM и сработало
# server.shutdown=graceful, а не убийство процесса через 10 секунд.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
