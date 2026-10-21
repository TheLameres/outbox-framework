package io.txbox.consumer.kafka.dispatcher;

import io.txbox.consumer.annotation.InboxEventHandler;
import io.txbox.consumer.annotation.InboxEventListener;
import io.txbox.consumer.kafka.configuration.InboxConfiguration;
import io.txbox.consumer.kafka.configuration.InboxKafkaConfiguration;
import io.txbox.consumer.model.InboundEventContext;
import io.txbox.consumer.outcome.ProcessingOutcome;
import io.txbox.core.event.DomainEvent;
import io.txbox.core.model.InboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InboxEventDispatcherTest {

    static final List<String> CALLS = Collections.synchronizedList(new ArrayList<>());

    @DomainEvent
    public record Ping(String text) {
    }

    /** Слушатель без source — «принимать от всех». */
    @InboxEventListener
    public static class AnySourceListener {
        @InboxEventHandler(eventType = "Ping")
        public void onPing(Ping e) {
            CALLS.add("any:" + e.text());
        }
    }

    /** Слушатель с конкретным source. */
    @InboxEventListener(source = "Order")
    public static class OrderListener {
        @InboxEventHandler(eventType = "Ping", order = 1)
        public void onPing(Ping e, InboundEventContext ctx) {
            CALLS.add("order:" + e.text());
        }

        @InboxEventHandler(eventType = "*", order = 2)
        public void onAnything(InboundEventContext ctx) {
            CALLS.add("order-wildcard");
        }
    }

    @InboxEventListener(source = "Stock")
    public static class IdempotenceListener {
        @InboxEventHandler(eventType = "Ping", idempotent = true, order = 1)
        public void guarded(Ping e) {
            CALLS.add("guarded");
        }

        @InboxEventHandler(eventType = "Ping", idempotent = false, order = 2)
        public void always(Ping e) {
            CALLS.add("always");
        }
    }

    /** Слушатель, чьи handler'ы бросают исключения разных видов. */
    @InboxEventListener(source = "Boom")
    public static class FailingListener {
        @InboxEventHandler(eventType = "Ping", order = 1)
        public void permanent(Ping e) {
            throw new IllegalArgumentException("bad input");
        }
    }

    @InboxEventListener(source = "Flaky")
    public static class TransientListener {
        @InboxEventHandler(eventType = "Ping")
        public void transientFailure(Ping e) {
            throw new RuntimeException("db hiccup");
        }
    }

    @Configuration
    static class Cfg {
        @Bean AnySourceListener anySourceListener() { return new AnySourceListener(); }
        @Bean OrderListener orderListener() { return new OrderListener(); }
        @Bean IdempotenceListener idempotenceListener() { return new IdempotenceListener(); }
        @Bean FailingListener failingListener() { return new FailingListener(); }
        @Bean TransientListener transientListener() { return new TransientListener(); }
    }

    InboxEventDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        CALLS.clear();
        var ctx = new AnnotationConfigApplicationContext(Cfg.class);
        InboxConfiguration cfg = new InboxConfiguration() {
            @Override public InboxKafkaConfiguration kafka() { return null; }
            @Override public List<String> domainEventBasePackages() {
                return List.of("io.txbox.consumer.kafka.dispatcher");
            }
        };
        dispatcher = new InboxEventDispatcher(ctx, JsonMapper.builder().build(), cfg);
        dispatcher.afterPropertiesSet();
    }

    private static InboxMessage msg(String source) {
        return new InboxMessage(UUID.randomUUID(), source, "id-1", "Ping",
                "{\"text\":\"hi\"}", new LinkedHashMap<>(), Instant.now(), Instant.now(), 0, 1L);
    }

    private static InboundEventContext ctx(InboxMessage m) {
        return new InboundEventContext(m.messageId(), m.source(), m.sourceId(), m.eventType(),
                m.headers(), m.timestamp(), m.receivedAt());
    }

    // ---- Баг №1: @InboxEventListener без source ---------------------------------------

    @Test
    void listenerWithoutSource_receivesEventsFromAnySource() {
        var m = msg("Payment");   // ни один из listeners не объявляет source = "Payment"

        var outcome = dispatcher.dispatch(m, ctx(m));

        assertThat(outcome).isInstanceOf(ProcessingOutcome.Success.class);
        assertThat(CALLS).containsExactly("any:hi");
    }

    @Test
    void exactSourceAndAnySourceHandlers_bothInvoked_orderedByOrder() {
        var m = msg("Order");

        var outcome = dispatcher.dispatch(m, ctx(m));

        assertThat(outcome).isInstanceOf(ProcessingOutcome.Success.class);
        assertThat(CALLS).containsExactlyInAnyOrder("any:hi", "order:hi", "order-wildcard");
        assertThat(CALLS.indexOf("order:hi")).isLessThan(CALLS.indexOf("order-wildcard"));
    }

    // ---- Баг №2: idempotent=true должен реально работать ------------------------------

    @Test
    void duplicate_skipsIdempotentHandlers_runsNonIdempotent() {
        var m = msg("Stock");

        var outcome = dispatcher.dispatch(m, ctx(m), true);

        assertThat(outcome).isInstanceOf(ProcessingOutcome.Success.class);
        assertThat(CALLS).doesNotContain("guarded").contains("always");
    }

    @Test
    void firstDelivery_runsAllHandlers() {
        var m = msg("Stock");

        dispatcher.dispatch(m, ctx(m), false);

        assertThat(CALLS).contains("guarded", "always");
    }

    @Test
    void duplicate_whenAllHandlersIdempotent_isSkipped() {
        var m = msg("Payment");   // только any:Ping (idempotent по умолчанию)

        var outcome = dispatcher.dispatch(m, ctx(m), true);

        assertThat(outcome).isInstanceOf(ProcessingOutcome.Skipped.class);
        assertThat(CALLS).isEmpty();
    }

    @Test
    void noHandlers_isSkipped() {
        var m = new InboxMessage(UUID.randomUUID(), "X", "1", "Unknown", "{}",
                new LinkedHashMap<>(), Instant.now(), Instant.now(), 0, 1L);
        assertThat(dispatcher.dispatch(m, ctx(m))).isInstanceOf(ProcessingOutcome.Skipped.class);
    }

    // ---- Исключение handler'а должно классифицироваться по причине, а не по обёртке --

    @Test
    void permanentHandlerFailure_isFatal_notRetryable() {
        var m = msg("Boom");

        var outcome = dispatcher.dispatch(m, ctx(m));

        // Method.invoke() оборачивает IllegalArgumentException в InvocationTargetException.
        // Без раскрытия обёртки классификатор вернул бы Retryable.
        assertThat(outcome).isInstanceOf(ProcessingOutcome.Fatal.class);
    }

    @Test
    void transientHandlerFailure_isRetryable() {
        var m = msg("Flaky");

        var outcome = dispatcher.dispatch(m, ctx(m));

        assertThat(outcome).isInstanceOf(ProcessingOutcome.Retryable.class);
    }
}
