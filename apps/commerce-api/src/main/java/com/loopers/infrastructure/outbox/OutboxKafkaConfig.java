package com.loopers.infrastructure.outbox;

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
 * relay 전용 KafkaTemplate. 공유 템플릿은 value 직렬화기가 {@code JsonSerializer} 라, outbox 에 이미 JSON 문자열로
 * 저장된 payload 를 그대로 보내면 <b>이중 인코딩</b>(문자열을 다시 JSON 화)된다. relay 는 저장된 바이트를 <b>원문 그대로</b>
 * 실어야 하므로 key/value 모두 {@code StringSerializer} 인 별도 템플릿을 쓴다. acks/idempotence 등 신뢰성 설정은
 * {@code kafka.yml} 의 producer 속성을 그대로 상속한다.
 */
@Configuration
public class OutboxKafkaConfig {

    @Bean
    public KafkaTemplate<String, String> outboxKafkaTemplate(KafkaProperties kafkaProperties) {
        Map<String, Object> config = new HashMap<>(kafkaProperties.buildProducerProperties());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }
}
