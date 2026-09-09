package io.txbox.core.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.*;

/**
 * Сканирует classpath в заданных пакетах и строит реестр доменных событий:
 * {@code eventType → Class<?>}.
 *
 * <p>Обнаруживает все классы и records, помеченные {@link DomainEvent}, без
 * каких-либо требований к Spring-контексту для самих классов событий. Это
 * позволяет использовать plain records и value objects как события без
 * аннотации {@code @Component}.
 *
 * <p>Сканер намеренно изолирован от {@code ApplicationContext} — его можно
 * протестировать в unit-тестах без поднятия Spring-контекста.
 *
 * <p>Правило именования ключа реестра совпадает с правилом в
 * {@code OutboxTemplate.resolveEventType()}: если {@code @DomainEvent.eventType()}
 * задан явно — используется он, иначе {@code getSimpleName()} класса.
 *
 * <p>При коллизии (два класса с одинаковым eventType) первый найденный
 * выигрывает и логируется предупреждение. Явный {@code eventType} в аннотации
 * позволяет разрешить конфликт без переименования классов.
 *
 * <pre>{@code
 * var scanner = new DomainEventScanner(
 *         List.of("io.myservice.events"),
 *         getClass().getClassLoader());
 * Map<String, Class<?>> types = scanner.scan();
 * // types → {"OrderCreated" → OrderCreated.class, ...}
 * }</pre>
 */
@Slf4j
public class DomainEventScanner {

    private final List<String> basePackages;
    private final ClassLoader classLoader;

    public DomainEventScanner(List<String> basePackages, ClassLoader classLoader) {
        this.basePackages = List.copyOf(basePackages);
        this.classLoader = classLoader;
    }

    /**
     * Запускает сканирование и возвращает неизменяемый реестр типов.
     *
     * @return Map&lt;eventType, Class&lt;?&gt;&gt; — все найденные @DomainEvent классы
     */
    public Map<String, Class<?>> scan() {
        Map<String, Class<?>> result = new LinkedHashMap<>();

        // useDefaultFilters=false: отключаем стандартные фильтры (@Component и т.д.),
        // чтобы сканировать только то, что явно добавим ниже.
        ClassPathScanningCandidateComponentProvider provider =
                new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(DomainEvent.class));

        for (String basePackage : basePackages) {
            Set<BeanDefinition> candidates = provider.findCandidateComponents(basePackage);

            for (BeanDefinition bd : candidates) {
                String className = bd.getBeanClassName();
                if (className == null) continue;

                try {
                    // initialize=false: не вызывать static-блоки — безопасно
                    // для records и immutable value objects без static state.
                    Class<?> clazz = Class.forName(className, false, classLoader);

                    DomainEvent annotation = clazz.getAnnotation(DomainEvent.class);
                    if (annotation == null) continue;

                    String eventType = annotation.eventType().isBlank()
                            ? clazz.getSimpleName()
                            : annotation.eventType();

                    if (result.containsKey(eventType)) {
                        log.warn(
                                "DomainEvent type collision: eventType='{}' already registered " +
                                "as {}. Ignoring {}. " +
                                "Use @DomainEvent(eventType=\"...\") to disambiguate.",
                                eventType,
                                result.get(eventType).getName(),
                                clazz.getName());
                        continue;
                    }

                    result.put(eventType, clazz);
                    log.debug("Registered domain event type: eventType={}, class={}",
                            eventType, clazz.getName());

                } catch (ClassNotFoundException e) {
                    log.error("Failed to load @DomainEvent class: {}", className, e);
                }
            }
        }

        log.info("DomainEventScanner: found {} type(s) in packages {}", result.size(), basePackages);

        // TODO: SchemaRegistry integration
        // При регистрации каждого @DomainEvent-типа необходимо:
        //   1. Сгенерировать JSON Schema из Class<?> через Jackson SchemaGenerator
        //      или jsonschema-generator (victools), либо Avro Schema через Avro reflect API.
        //   2. Зарегистрировать схему в SchemaRegistry под subject "{eventType}-value"
        //      через SchemaRegistryClient (io.confluent:kafka-schema-registry-client
        //      или Apicurio registry client).
        //   3. При десериализации в InboxEventDispatcher получить schema ID из заголовка
        //      Kafka-сообщения (magic byte + schema ID в первых 5 байтах, если producer
        //      использует KafkaAvroSerializer / KafkaJsonSchemaSerializer).
        //   4. Проверить совместимость входящей схемы с локальным классом перед маппингом.
        //   5. Поддержать schema evolution:
        //      - BACKWARD: consumer читает данные, записанные старым producer'ом.
        //      - FORWARD: старый consumer читает данные нового producer'а.
        //      - FULL: совместимость в обе стороны — рекомендуемый режим для production.
        //   6. Рассмотреть переход с plain JSON на Avro/Protobuf:
        //      - KafkaAvroDeserializer + SpecificRecord — строгая типизация, компактный формат.
        //      - Protobuf — кросс-языковая совместимость, поддержка gRPC.

        return Collections.unmodifiableMap(result);
    }
}
