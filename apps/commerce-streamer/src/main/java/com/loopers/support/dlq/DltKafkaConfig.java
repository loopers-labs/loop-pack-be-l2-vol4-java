package com.loopers.support.dlq;

import com.loopers.confg.kafka.KafkaTopic;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Dead Letter Topic 발행용 설정. 실패 레코드(원본 payload 문자열)를 그대로 싣도록 StringSerializer 를 쓴다.
 * auto.create.topics.enable=false 이므로 각 DLT 를 명시적으로 생성한다.
 */
@Configuration
public class DltKafkaConfig {

    @Bean
    public KafkaTemplate<String, String> dltKafkaTemplate(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    @Bean
    public NewTopic orderEventsDlt() {
        return TopicBuilder.name(KafkaTopic.deadLetterOf(KafkaTopic.ORDER_EVENTS)).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic catalogEventsDlt() {
        return TopicBuilder.name(KafkaTopic.deadLetterOf(KafkaTopic.CATALOG_EVENTS)).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic couponIssueRequestsDlt() {
        return TopicBuilder.name(KafkaTopic.deadLetterOf(KafkaTopic.COUPON_ISSUE_REQUESTS)).partitions(1).replicas(1).build();
    }
}
