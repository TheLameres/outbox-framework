package io.txbox.consumer.kafka.dispatcher;

import io.txbox.consumer.annotation.InboxEventHandler;
import io.txbox.consumer.annotation.InboxEventListener;
import io.txbox.consumer.kafka.configuration.InboxConfiguration;
import io.txbox.consumer.model.InboundEventContext;
import io.txbox.consumer.outcome.ProcessingOutcome;
import io.txbox.consumer.kafka.util.ProcessingOutcomeClassifier;
import io.txbox.core.event.DomainEventScanner;
import io.txbox.core.model.InboxMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.ApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Диспетчер событий: находит и вызывает handler-методы по типу события.
 *
 * <p>Инициализируется один раз при старте приложения ({@link #afterPropertiesSet()}):
 * 1. Сканирует все бины с {@code @InboxEventListener} и индексирует их методы
 *    по ключу {@code "source:eventType"}.
 * 2. Запускает {@link DomainEventScanner} по настроенным пакетам и строит
 *    {@link #registeredTypes} — реестр {@code eventType → Class<?>}, который
 *    используется при десериализации payload.
 *
 * <p>На обработку события:
 * 1. Ищет handlers по (source, eventType) включая wildcard {@code "*"}.
 * 2. Резолвит targetType из {@link #registeredTypes} по eventType сообщения
 *    (если handler не объявил конкретный тип параметра).
 * 3. Десериализует payload через Jackson ObjectMapper в нужный тип.
 * 4. Вызывает handlers по порядку (via {@code @InboxEventHandler.order}).
 * 5. Классифицирует исключения через {@link ProcessingOutcomeClassifier}.
 */
@Slf4j
public class InboxEventDispatcher implements InitializingBean {

    private final Map<String, List<HandlerMethod>> handlersIndex = new LinkedHashMap<>();

    /**
     * Реестр доменных событий: eventType → Class<?>.
     *
     * <p>Заполняется при старте через {@link DomainEventScanner}, который сканирует
     * classpath по настроенным пакетам и находит все классы/records с {@code @DomainEvent}.
     * Не требует, чтобы event-классы были Spring-бинами.
     *
     * <p>Ключ совпадает с eventType, который producer кладёт в заголовок
     * {@code txbox-event-type}: явное значение из {@code @DomainEvent.eventType()}
     * либо {@code getSimpleName()} класса.
     */
    private final Map<String, Class<?>> registeredTypes = new LinkedHashMap<>();

    private final ApplicationContext context;
    private final ObjectMapper objectMapper;
    private final InboxConfiguration properties;

    public InboxEventDispatcher(ApplicationContext context,
                                ObjectMapper objectMapper,
                                InboxConfiguration properties) {
        this.context = context;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Инициализирует handlersIndex и registeredTypes.
     * Вызывается один раз Spring'ом после сборки контекста.
     */
    @Override
    public void afterPropertiesSet() {
        initializeHandlers();
        initializeRegisteredTypes();
    }

    // ── Инициализация ────────────────────────────────────────────────────────

    /**
     * Сканирует бины с {@code @InboxEventListener} и строит handlersIndex.
     * Аннотация снимается через getSuperclass() на случай CGLIB-прокси.
     */
    private void initializeHandlers() {
        Map<String, Object> listeners = context.getBeansWithAnnotation(InboxEventListener.class);

        for (Object listener : listeners.values()) {
            Class<?> targetClass = listener.getClass();
            InboxEventListener listenerAnnotation = targetClass.getAnnotation(InboxEventListener.class);
            if (listenerAnnotation == null) {
                targetClass = targetClass.getSuperclass();
                if (targetClass == null) continue;
                listenerAnnotation = targetClass.getAnnotation(InboxEventListener.class);
            }
            if (listenerAnnotation == null) continue;

            String sourceFilter = listenerAnnotation.source();

            for (Method method : targetClass.getDeclaredMethods()) {
                InboxEventHandler handlerAnnotation = method.getAnnotation(InboxEventHandler.class);
                if (handlerAnnotation == null) continue;

                String eventType = handlerAnnotation.eventType();
                int order = handlerAnnotation.order();

                String indexKey = formatKey(sourceFilter, eventType);
                handlersIndex.computeIfAbsent(indexKey, k -> new ArrayList<>())
                        .add(new HandlerMethod(listener, method, order, handlerAnnotation.idempotent()));

                log.debug("Registered handler: source={}, eventType={}, method={}, order={}",
                        sourceFilter, eventType, method.getName(), order);
            }
        }

        handlersIndex.values().forEach(list -> list.sort(Comparator.comparingInt(h -> h.order)));
    }

    /**
     * Запускает {@link DomainEventScanner} и заполняет {@link #registeredTypes}.
     *
     * <p>Пакеты для сканирования берутся из {@code txbox.consumer.domain-event-base-packages}.
     * Если список пуст — используется fallback на {@code AutoConfigurationPackages},
     * которые регистрирует аннотация {@code @SpringBootApplication}.
     */
    private void initializeRegisteredTypes() {
        List<String> packages = resolveBasePackages();
        DomainEventScanner scanner = new DomainEventScanner(packages, getClass().getClassLoader());
        registeredTypes.putAll(scanner.scan());

        log.info("InboxEventDispatcher initialized: {} handler(s), {} domain type(s)",
                handlersIndex.values().stream().mapToLong(List::size).sum(),
                registeredTypes.size());
    }

    /**
     * Определяет пакеты для сканирования {@code @DomainEvent} классов.
     * Приоритет: явная конфигурация → AutoConfigurationPackages.
     */
    private List<String> resolveBasePackages() {
        List<String> configured = properties.domainEventBasePackages();
        if (!configured.isEmpty()) {
            return configured;
        }
        try {
            return AutoConfigurationPackages.get(context);
        } catch (IllegalStateException e) {
            log.warn("AutoConfigurationPackages not available; " +
                     "set txbox.consumer.domain-event-base-packages explicitly.");
            return List.of();
        }
    }

    // ── Диспетчеризация ──────────────────────────────────────────────────────

    /**
     * Диспетчеризирует сообщение к подходящим handlers'ам.
     * Возвращает первый non-Retryable исход или последний Retryable.
     */
    public ProcessingOutcome dispatch(InboxMessage message, InboundEventContext context) {
        List<HandlerMethod> handlers = findHandlers(message.source(), message.eventType());

        if (handlers.isEmpty()) {
            log.warn("No handlers found for source={}, eventType={}",
                    message.source(), message.eventType());
            return new ProcessingOutcome.Skipped(message.messageId(), "no handlers registered");
        }

        ProcessingOutcome lastOutcome = null;

        for (HandlerMethod handler : handlers) {
            try {
                if (handler.idempotent) {
                    boolean alreadyProcessed = context.messageId() != null
                            && message.headers().containsKey("txbox-already-processed");
                    if (alreadyProcessed) {
                        log.debug("Handler idempotent check: skipping duplicate for messageId={}",
                                context.messageId());
                        continue;
                    }
                }

                invokeHandler(handler, message, context);
                lastOutcome = new ProcessingOutcome.Success(message.messageId());

            } catch (Exception e) {
                lastOutcome = ProcessingOutcomeClassifier.classify(message.messageId(), e);

                if (lastOutcome instanceof ProcessingOutcome.Fatal) {
                    log.error("Handler fatal error: {}", lastOutcome, e);
                    break;
                }

                log.warn("Handler retryable error: messageId={}, handler={}",
                        message.messageId(), handler.method.getName(), e);
            }
        }

        return lastOutcome != null ? lastOutcome
                : new ProcessingOutcome.Skipped(message.messageId(), "no successful handler");
    }

    // ── Вызов handler-метода ─────────────────────────────────────────────────

    /**
     * Собирает аргументы и вызывает handler-метод через reflection.
     *
     * <p>Для каждого параметра метода:
     * <ul>
     *   <li>{@link InboundEventContext} — подставляем напрямую;</li>
     *   <li>любой другой тип — десериализуем payload через {@link #deserializePayload}.</li>
     * </ul>
     */
    private void invokeHandler(HandlerMethod handler, InboxMessage message,
                               InboundEventContext context)
            throws InvocationTargetException, IllegalAccessException {

        Method method = handler.method;
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] args = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            if (paramTypes[i] == InboundEventContext.class) {
                args[i] = context;
            } else {
                args[i] = deserializePayload(message, paramTypes[i]);
            }
        }

        method.invoke(handler.listener, args);
    }

    /**
     * Десериализует payload сообщения в целевой тип.
     *
     * <p>Алгоритм:
     * <ol>
     *   <li>Если handler объявил конкретный тип (не {@code Object.class}) —
     *       используем его напрямую, не обращаясь к реестру.</li>
     *   <li>Если тип {@code Object.class} — ищем в {@link #registeredTypes}
     *       по {@code message.eventType()}.</li>
     *   <li>Если тип не найден — бросаем {@code IllegalStateException}.</li>
     * </ol>
     */
    private Object deserializePayload(InboxMessage message, Class<?> handlerParamType) {
        Class<?> targetType;

        if (handlerParamType != Object.class) {
            targetType = handlerParamType;
        } else {
            targetType = registeredTypes.get(message.eventType());
            if (targetType == null) {
                throw new IllegalStateException(
                        "No @DomainEvent class registered for eventType='" + message.eventType()
                        + "'. Annotate the event class with @DomainEvent and ensure its package "
                        + "is covered by txbox.consumer.domain-event-base-packages "
                        + "or @SpringBootApplication base package.");
            }
        }

        try {
            return objectMapper.readValue(message.payload(), targetType);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to deserialize payload for eventType='" + message.eventType()
                    + "', targetType=" + targetType.getName()
                    + ", messageId=" + message.messageId(), e);
        }
    }

    // ── Поиск handlers ───────────────────────────────────────────────────────

    private List<HandlerMethod> findHandlers(String source, String eventType) {
        List<HandlerMethod> result = new ArrayList<>();

        List<HandlerMethod> exact = handlersIndex.get(formatKey(source, eventType));
        if (exact != null) result.addAll(exact);

        List<HandlerMethod> wildcard = handlersIndex.get(formatKey(source, "*"));
        if (wildcard != null) result.addAll(wildcard);

        return result;
    }

    private String formatKey(String source, String eventType) {
        return source + ":" + eventType;
    }

    // ── Внутренний класс ─────────────────────────────────────────────────────

    private static class HandlerMethod {
        final Object listener;
        final Method method;
        final int order;
        final boolean idempotent;

        HandlerMethod(Object listener, Method method, int order, boolean idempotent) {
            this.listener = listener;
            this.method = method;
            this.order = order;
            this.idempotent = idempotent;
        }
    }
}
