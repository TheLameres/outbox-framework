# TxBox — Transactional Outbox/Inbox для Spring Boot

Фреймворк, который переносит паттерны **Transactional Outbox** и **Transactional Inbox** на уровень
переиспользуемой библиотеки для микросервисов на Spring Boot. Бизнес-код публикует и обрабатывает
события, не думая о доставке, дедупликации и согласованности между базой и брокером.

> Исследовательский проект. Цель — показать, как паттерны надёжной доставки сообщений
> оформляются в виде фреймворка, и обкатать их на примере.

---

## Зачем это нужно

Классическая проблема микросервисов: нужно **атомарно** изменить данные в своей базе и
отправить событие в брокер. Распределённой транзакции между PostgreSQL и Kafka нет, поэтому:

- если сначала писать в брокер, а потом в базу — при падении останется событие без данных;
- если наоборот — данные без события.

**Outbox** решает это так: событие пишется в ту же БД, в той же транзакции, что и бизнес-данные.
Отдельный поллер потом читает outbox-таблицу и публикует события в брокер.

**Inbox** — зеркальная половина на стороне потребителя: входящее сообщение сначала сохраняется
в inbox-таблицу (дедупликация), а бизнес-обработка и отметка «обработано» идут в одной транзакции.

Вместе это даёт **at-least-once доставку + идемпотентную обработку = effectively-once** эффект.

---

## Стек

| Компонент    | Версия                                 |
|--------------|----------------------------------------|
| Java         | 25                                     |
| Spring Boot  | 4.1.1                                  |
| Spring Kafka | 4.1.1                                  |
| Jackson      | 3.x (`tools.jackson`)                  |
| PostgreSQL   | 17 (`jsonb`, `FOR UPDATE SKIP LOCKED`) |
| Liquibase    | схема outbox/inbox                     |
| Сборка       | Maven, многомодульный реактор          |

---

## Структура модулей

```
outbox-framework (pom)
├── txbox-core            — модель сообщений, @DomainEvent, DomainEventScanner, DestinationResolver
├── txbox-jpa             — общая JPA-база: AbstractMessageEntity, AbstractJpaMessageStore
├── txbox-kafka           — TxBoxHeaders, конвертеры Message ↔ Kafka ProducerRecord
│
├── txbox-producer (pom)  — ИСХОДЯЩАЯ сторона (outbox)
│   ├── txbox-producer-api       — OutboxStore, OutboxPublisher, PublishOutcome
│   ├── txbox-producer-jpa       — JpaOutboxStore, OutboxEventEntity
│   ├── txbox-producer-kafka     — KafkaOutboxPublisher
│   └── txbox-producer-starter   — автоконфигурация: OutboxTemplate, OutboxPoller, maintenance, health, metrics
│
├── txbox-consumer (pom)  — ВХОДЯЩАЯ сторона (inbox)
│   ├── txbox-consumer-api       — InboxStore, @InboxEventListener, @InboxEventHandler, ProcessingOutcome
│   ├── txbox-consumer-jpa       — JpaInboxStore, InboxMessageEntity
│   ├── txbox-consumer-kafka     — InboxKafkaListener, InboxEventDispatcher
│   └── txbox-consumer-starter   — автоконфигурация inbox
│
└── txbox-examples (pom)
    ├── txbox-full-example       — продюсер и консьюмер в одном процессе (демо механизма)
    └── txbox-coffeeshop-example — распределённый пример: сага из трёх сервисов
```

Сервис подключает ровно то, что нужно: `txbox-producer-starter`, `txbox-consumer-starter` или оба.

---

## Как это работает

### Исходящая сторона (outbox)

```java

@DomainEvent(eventType = "order.placed")
public record OrderPlaced(UUID orderId, BigDecimal total, Instant placedAt) {
}

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orders;
    private final OutboxTemplate outbox;

    @Transactional                                   // TX#1 — одна транзакция
    public void placeOrder(UUID id, BigDecimal total) {
        orders.save(new OrderEntity(id, total));     // бизнес-данные
        outbox.publish(                              // + запись в outbox-таблицу
                new OrderPlaced(id, total, Instant.now()),
                "Order",                                 // aggregateType
                id.toString());                          // aggregateId → ключ партиции Kafka
    }
}
```

Дальше работает `OutboxPoller` — три независимые транзакции, это осознанное решение:

1. **claim** (`REQUIRES_NEW`): захватывает пачку `PENDING` через `FOR UPDATE SKIP LOCKED`,
   помечает `IN_FLIGHT`. `SKIP LOCKED` позволяет запускать несколько поллеров без конкуренции.
2. **publish** (вне транзакции): публикует в Kafka, параллельно на виртуальных потоках.
   Ключ партиции = `aggregateId`, поэтому порядок событий одного агрегата сохраняется.
3. **applyOutcomes** (`REQUIRES_NEW`): по результату публикации — `PROCESSED`, назад в `PENDING`
   для повтора, либо `FAILED` после исчерпания попыток.

Плюс `OutboxMaintenance` по расписанию: `reclaimStale` возвращает зависшие `IN_FLIGHT`,
`purgeProcessed` чистит старые завершённые записи.

### Входящая сторона (inbox)

```java

@InboxEventListener(source = "Order")                // фильтр по aggregateType
@RequiredArgsConstructor
public class OrderInboxListener {

    private final InventoryService inventory;

    @InboxEventHandler(eventType = "order.placed")
    public void onPlaced(OrderPlaced event, InboundEventContext ctx) {
        inventory.reserve(event.orderId());
    }
}
```

`InboxKafkaListener` в одной транзакции: дедуплицирует → сохраняет в inbox (`RECEIVED`) →
`InboxEventDispatcher` находит обработчики по `(source, eventType)` и десериализует payload →
применяет исход.

Исход — sealed-тип `ProcessingOutcome`:

| Исход       | Offset                           | Статус в inbox                           |
|-------------|----------------------------------|------------------------------------------|
| `Success`   | commit                           | `PROCESSED`                              |
| `Skipped`   | commit                           | `SKIPPED` (дубликат или нет обработчика) |
| `Fatal`     | commit                           | `FAILED` (постоянная ошибка)             |
| `Retryable` | **исключение → перемотка назад** | остаётся `RECEIVED`, повтор              |

> **Важно про `@InboxEventHandler`:** метод обработчика не помечается `@Transactional`.
> Транзакцию уже открывает `InboxKafkaListener`, а `@Transactional` на методе превратил бы
> бин в CGLIB-прокси, у которого диспетчер не найдёт аннотаций обработчиков.

### Доменные события

`@DomainEvent` помечает запись или класс как событие. `eventType` задаётся явно и едет в
заголовке `txbox-event-type`. Явное имя важно: при публикации в общий топик несколько сервисов
могут столкнуться короткими именами. `DomainEventScanner` на старте строит реестр
`eventType → Class<?>` по пакетам из `txbox.consumer.domain-event-base-packages`.

---

## Примеры

### txbox-full-example

Продюсер и консьюмер в одном процессе — демонстрация самого механизма. Заказ → `order.placed`
→ Kafka → inbox → смена статуса. REST на `/api/v1/orders`, запуск через Testcontainers.

### txbox-coffeeshop-example

Распределённая **сага-хореография** из трёх сервисов, каждый со своей базой:

```
order-service ──order.placed──▶ inventory-service ──inventory.reserved──▶ barista-service
     ▲                                │                                        │
     │                                └──inventory.rejected──┐                 │
     └──────── barista.drink-prepared ◀──────────────────────┴─────────────────┘
     └──────── order.cancelled (компенсация) ──▶ inventory освобождает резерв
```

| Сервис              | Пишет (outbox)                         | Читает (inbox)             |
|---------------------|----------------------------------------|----------------------------|
| `order-service`     | `order.placed`, `order.cancelled`      | `inventory.*`, `barista.*` |
| `inventory-service` | `inventory.reserved/rejected/released` | `order.*`                  |
| `barista-service`   | `barista.drink-prepared/failed`        | `inventory.reserved`       |

Каждый сервис держит **обе** стороны паттерна, но каждая транзакция локальна для своей БД.
Это и есть предмет исследования: outbox и inbox как строительные блоки распределённой саги
без оркестратора и без распределённых транзакций.

---

## Сборка и запуск

### Требования

- JDK 25
- Maven 3.9+
- Docker (для интеграционных тестов на Testcontainers и для docker-compose кофейни)

### Сборка

```bash
mvn clean install                 # весь реактор с тестами
mvn clean install -DskipTests     # без тестов
```

Интеграционные тесты (`txbox-consumer-starter`, примеры) поднимают PostgreSQL и Kafka через
Testcontainers — нужен запущенный Docker.

### Кофейня в Docker

```bash
cd txbox-examples/txbox-coffeeshop-example
docker compose up -d --build
```

Поднимаются Kafka (KRaft), три базы PostgreSQL и три сервиса (порты 8080–8082). Пример запроса:

```bash
curl -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"lines":[{"menuItemCode":"FLAT_WHITE","quantity":2}]}'
```

---

## Конфигурация

### Продюсер (`txbox.producer.*`)

```yaml
txbox:
  producer:
    enabled: true
    polling:
      interval: 1s            # период поллера
      batch-size: 100         # размер пачки claim
      virtual-threads: true   # параллельная публикация на виртуальных потоках
      concurrency: 64
      in-flight-timeout: 5m   # через сколько зависший IN_FLIGHT вернуть в PENDING
    retry:
      max-attempts: 5
      send-timeout: 10s
    maintenance:
      retention: 7d           # сколько хранить завершённые записи
      interval: 1h
    kafka:
      routing: FIXED          # SUFFIX | FIXED | BY_EVENT_TYPE
      fixed-topic: domain.events
```

Маршрутизация (`DestinationResolver`):

- `SUFFIX` — `aggregateType` + суффикс: `Order` → `order-events`;
- `FIXED` — все события в один топик;
- `BY_EVENT_TYPE` — явная карта `eventType → топик`.

### Консьюмер (`txbox.consumer.*`)

```yaml
txbox:
  consumer:
    kafka:
      topic: domain.events
      group-id: my-service
      concurrency: 1
      poll-timeout-ms: 3000
    domain-event-base-packages:
      - com.example.events       # где сканировать @DomainEvent
```

---

## Жизненный цикл сообщения

```
PENDING ──poller claim──▶ IN_FLIGHT ──publish ok──▶ PROCESSED
   ▲                          │
   └────── retry (applyOutcomes) ──── publish fail
                              │
                              └── attempts exhausted ──▶ FAILED

inbox: RECEIVED ──handler ok──▶ PROCESSED
                 ──duplicate──▶ SKIPPED
                 ──fatal──────▶ FAILED
                 ──retryable──▶ (остаётся RECEIVED, повтор)
```

---

## Гарантии и ограничения

**Гарантии:**

- атомарность бизнес-данных и события (одна локальная транзакция);
- at-least-once доставка от outbox до inbox;
- дедупликация inbox по `(partition, offset)` и по `messageId`;
- порядок событий одного агрегата (ключ партиции = `aggregateId`).

**Ограничения (текущее состояние):**

- транспорт только Kafka; RabbitMQ в проработке;
- хранилище только PostgreSQL (`jsonb`, `SKIP LOCKED`);
- дедупликация inbox завязана на Kafka-координаты `partition/offset`;
- DLQ для `Fatal`-исходов пока не настраивается автоматически в стартере.

---

## Тесты

- **Юнит** (`txbox-consumer-kafka`): диспетчеризация, классификация исходов, поведение listener'а.
- **Контрактные** (`txbox-coffeeshop-domain`): события переживают JSON-раундтрип и находятся
  сканером по тем же `eventType`, что уезжают в Kafka.
- **Интеграционные** (`txbox-consumer-starter`, `IntegrationRetryableTest`): реальные Kafka +
  PostgreSQL через Testcontainers, сквозной сценарий «временная ошибка → повтор → успех».

```bash
mvn -pl txbox-consumer/txbox-consumer-starter -am test     # нужен Docker
```

---

## Структура пакетов

Корневой namespace фреймворка — `io.txbox`. Примеры живут отдельно:
`com.somecompany.examples` (full-example) и `io.coffeeshop` (кофейня).
