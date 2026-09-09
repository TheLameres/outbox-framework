package io.txbox.consumer.kafka.dispatcher;

import io.txbox.consumer.annotation.InboxEventHandler;
import io.txbox.consumer.annotation.InboxEventListener;
import io.txbox.consumer.model.InboundEventContext;
import io.txbox.consumer.outcome.ProcessingOutcome;
import io.txbox.consumer.kafka.util.ProcessingOutcomeClassifier;
import io.txbox.core.model.InboxMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Диспетчер событий: находит и вызывает handler-методы по типу события.
 *
 * <p>Инициализируется один раз при старте приложения:
 * 1. Сканирует все @InboxEventListener классы
 * 2. Выуживает методы с @InboxEventHandler
 * 3. Индексирует по (source, eventType)
 *
 * <p>На обработку события:
 * 1. Ищет handlers по (source, eventType) включая wildcard
 * 2. Вызывает их по порядку (via @InboxEventHandler.order)
 * 3. Классифицирует исключения через {@link ProcessingOutcomeClassifier}
 */
@Slf4j
@Component
public class InboxEventDispatcher {

    private final Map<String, List<HandlerMethod>> handlersIndex = new LinkedHashMap<>();
    private final ApplicationContext context;

    @Autowired
    public InboxEventDispatcher(ApplicationContext context) {
        this.context = context;
        initializeHandlers();
    }

    /**
     * Инициализирует индекс handlers'ов.
     * Вызывается один раз при старте.
     */
    private void initializeHandlers() {
        Map<String, Object> listeners = context.getBeansWithAnnotation(InboxEventListener.class);

        for (Object listener : listeners.values()) {
            InboxEventListener listenerAnnotation = listener.getClass()
                    .getAnnotation(InboxEventListener.class);
            String sourceFilter = listenerAnnotation.source();

            for (Method method : listener.getClass().getDeclaredMethods()) {
                InboxEventHandler handlerAnnotation = method.getAnnotation(InboxEventHandler.class);
                if (handlerAnnotation == null) continue;

                String eventType = handlerAnnotation.eventType();
                int order = handlerAnnotation.order();

                // Ключ индекса: "source:eventType" (source может быть "*" для wildcard)
                String indexKey = formatKey(sourceFilter, eventType);
                handlersIndex.computeIfAbsent(indexKey, k -> new ArrayList<>())
                        .add(new HandlerMethod(listener, method, order, handlerAnnotation.idempotent()));

                log.debug("Registered handler: source={}, eventType={}, method={}, order={}",
                        sourceFilter, eventType, method.getName(), order);
            }
        }

        // Сортируем по order в каждом списке
        handlersIndex.values().forEach(list -> list.sort(Comparator.comparingInt(h -> h.order)));
    }

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
                // Проверка идемпотентности (если включена)
                if (handler.idempotent) {
                    boolean alreadyProcessed = context.messageId() != null
                            && message.headers().containsKey("txbox-already-processed");
                    if (alreadyProcessed) {
                        log.debug("Handler idempotent check: skipping duplicate for messageId={}",
                                context.messageId());
                        continue;
                    }
                }

                // Вызываем handler
                invokeHandler(handler, message, context);

                lastOutcome = new ProcessingOutcome.Success(message.messageId());

            } catch (Exception e) {
                // Классифицируем исключение
                lastOutcome = ProcessingOutcomeClassifier.classify(message.messageId(), e);

                // Если Fatal — прерываем цепь
                if (lastOutcome instanceof ProcessingOutcome.Fatal) {
                    log.error("Handler fatal error: {}",
                            lastOutcome, e);
                    break;
                }

                // Если Retryable — логируем, но продолжаем цепь
                log.warn("Handler retryable error: messageId={}, handler={}",
                        message.messageId(), handler.method.getName(), e);
            }
        }

        return lastOutcome != null ? lastOutcome
                : new ProcessingOutcome.Skipped(message.messageId(), "no successful handler");
    }

    /**
     * Вызывает handler-метод с правильными параметрами.
     * Параметры:
     * - десериализованное событие по типу (первый параметр)
     * - {@link InboundEventContext} (опционально)
     */
    private void invokeHandler(HandlerMethod handler, InboxMessage message,
                               InboundEventContext context)
            throws InvocationTargetException, IllegalAccessException {

        Method method = handler.method;
        Object listener = handler.listener;

        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] args = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> paramType = paramTypes[i];
            if (paramType == InboundEventContext.class) {
                args[i] = context;
            } else {
                // Попытка десериализовать payload в тип первого параметра
                // (реальная логика зависит от ObjectMapper и eventType)
                args[i] = deserializePayload(message, paramType);
            }
        }

        method.invoke(listener, args);
    }

    /**
     * Десериализует payload сообщения в целевой тип.
     * Упрощённая реализация — в реальности используется Jackson ObjectMapper.
     */
    private Object deserializePayload(InboxMessage message, Class<?> targetType) {
        // TODO: ObjectMapper + content-type detection
        return null;  // placeholder
    }

    /**
     * Находит handlers'ы по source и eventType.
     * Поддерживает wildcard "*" для eventType.
     */
    private List<HandlerMethod> findHandlers(String source, String eventType) {
        List<HandlerMethod> result = new ArrayList<>();

        // Точное совпадение
        List<HandlerMethod> exact = handlersIndex.get(formatKey(source, eventType));
        if (exact != null) result.addAll(exact);

        // Wildcard eventType
        List<HandlerMethod> wildcard = handlersIndex.get(formatKey(source, "*"));
        if (wildcard != null) result.addAll(wildcard);

        return result;
    }

    private String formatKey(String source, String eventType) {
        return source + ":" + eventType;
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Внутренний класс для хранения metadata handler-метода.
     */
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
            method.setAccessible(true);
        }
    }
}
