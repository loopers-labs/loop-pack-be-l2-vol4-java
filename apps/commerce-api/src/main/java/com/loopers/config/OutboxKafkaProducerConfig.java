package com.loopers.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.HashMap;
import java.util.Map;

/**
 * Outbox relay 전용 Kafka 프로듀서 설정.
 * <p>
 * outbox payload는 이미 JSON 문자열로 저장돼 있으므로 <b>StringSerializer</b>로 그대로 전송한다
 * (JsonSerializer면 문자열을 한 번 더 감싸 이중 인코딩된다). at-least-once 보장을 위해
 * {@code acks=all} + {@code enable.idempotence=true}로 브로커 중복/유실을 막는다.
 * <p>
 * {@code @EnableScheduling}은 {@link com.loopers.application.outbox.OutboxRelay} 폴러 구동에 필요하다.
 */
@Configuration
@EnableScheduling
public class OutboxKafkaProducerConfig {

    @Bean
    public ProducerFactory<String, String> outboxProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> config = new HashMap<>(kafkaProperties.buildProducerProperties());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, String> outboxKafkaTemplate(ProducerFactory<String, String> outboxProducerFactory) {
        return new KafkaTemplate<>(outboxProducerFactory);
    }
}
