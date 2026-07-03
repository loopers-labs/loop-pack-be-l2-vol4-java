package com.loopers.outbox.infrastructure;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Outbox payload 는 이미 직렬화된 JSON 문자열이다. 모듈 기본 producer(JsonSerializer)로 보내면
 * 문자열이 한 번 더 JSON 인코딩되므로, key/value 모두 StringSerializer 로 그대로 싣는 전용 템플릿을 둔다.
 * acks=all / enable.idempotence 등 신뢰성 설정은 kafka.yml 의 producer 속성을 그대로 승계한다.
 */
@Configuration
public class OutboxKafkaConfig {

    @Bean
    public KafkaTemplate<String, String> outboxKafkaTemplate(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }
}
