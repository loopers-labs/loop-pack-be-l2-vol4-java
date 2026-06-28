package com.loopers.support.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class EventMetrics {

    private static final String UNKNOWN = "UNKNOWN";

    private final MeterRegistry meterRegistry;

    public EventMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordOutboxRelaySuccess(String topic, String eventType, Duration duration) {
        Tags tags = eventTags(topic, eventType).and("result", "success");
        counter("loopers.outbox.relay.success.count", tags).increment();
        timer("loopers.outbox.relay.duration", tags).record(duration);
    }

    public void recordOutboxRelayFailure(String topic, String eventType, Duration duration) {
        Tags tags = eventTags(topic, eventType).and("result", "failure");
        counter("loopers.outbox.relay.failure.count", tags).increment();
        timer("loopers.outbox.relay.duration", tags).record(duration);
    }

    public void recordKafkaConsumerSuccess(String topic, String eventType) {
        counter("loopers.kafka.consumer.success.count", eventTags(topic, eventType).and("result", "success")).increment();
    }

    public void recordKafkaConsumerFailure(String topic, String eventType) {
        counter("loopers.kafka.consumer.failure.count", eventTags(topic, eventType).and("result", "failure")).increment();
    }

    public void recordKafkaConsumerDuplicate(String topic, String eventType) {
        counter("loopers.kafka.consumer.duplicate.count", eventTags(topic, eventType).and("result", "duplicate")).increment();
    }

    public void recordProductMetricsUpdate(String eventType) {
        counter(
            "loopers.product_metrics.update.count",
            Tags.of("topic", "catalog-events", "eventType", safe(eventType), "result", "success")
        ).increment();
    }

    private Counter counter(String name, Tags tags) {
        return Counter.builder(name)
            .tags(tags)
            .register(meterRegistry);
    }

    private Timer timer(String name, Tags tags) {
        return Timer.builder(name)
            .tags(tags)
            .register(meterRegistry);
    }

    private Tags eventTags(String topic, String eventType) {
        return Tags.of("topic", safe(topic), "eventType", safe(eventType));
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }
}
