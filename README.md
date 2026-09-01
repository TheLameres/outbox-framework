# Outbox Framework

Transactional outbox для сервисов компании. Java 21, Spring Boot 4.1.1, Gradle 9.7.1 (Kotlin DSL).

## Матрица версий (актуально на 31.08.2026)

| Компонент        | Версия  | Примечание                                              |
|------------------|---------|---------------------------------------------------------|
| Spring Boot      | 4.1.1   | GA от 20.08.2026. Следующий minor — 4.2.0-M1            |
| Spring Framework | 7.1.x   | приходит транзитивно с Boot 4.1                         |
| Java             | 21 LTS  | baseline Boot 4 — 17; рекомендуемый — 25                |
| Gradle           | 9.7.1   | Kotlin DSL, configuration cache включён                 |
| Jackson          | 3.x     | пакет `tools.jackson`, исключения unchecked             |
| Hibernate        | 7.x     | нативный маппинг JSON через `@JdbcTypeCode(SqlTypes.JSON)` |
| Lombok           | 1.18.46 | только для JPA-сущностей и `@Slf4j`                     |

> Spring Boot 3.5 ушёл с open-source поддержки 30.06.2026 — новые сервисы стартуют только на 4.x.

## Модули

```
outbox-core                  records + sealed-контракты, без Spring
outbox-jpa                   Hibernate-хранилище, SKIP LOCKED
outbox-kafka                 KafkaOutboxPublisher + классификация ошибок
outbox-spring-boot-starter   автоконфигурация, поллер, метрики, health
outbox-sample-service        эталонный сервис-потребитель
```

## Быстрый старт

```kotlin
dependencies {
    implementation("com.company.outbox:outbox-spring-boot-starter:1.0.0")
}
```

```java
@Transactional
public UUID createOrder(UUID customerId, BigDecimal total) {
    var order = OrderEntity.create(customerId, total);
    orderRepository.save(order);
    outbox.publish(OrderEvent.Created.now(order.getId(), customerId, total),
                   "Order", order.getId().toString());
    return order.getId();
}
```

## Команды

```bash
./gradlew checkAll                      # сборка + тесты всех модулей
./gradlew :outbox-sample-service:bootRun
./gradlew publishAllPublicationsToNexusRepository
./gradlew :wrapper --gradle-version=9.7.1
```
