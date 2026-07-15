package com.loopers.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.converter.BatchMessagingMessageConverter;
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter;

import java.util.HashMap;
import java.util.Map;

/**
 * 메트릭/랭킹 집계 전용 배치 리스너 팩토리.
 *
 * <p>공용 {@code KafkaConfig.BATCH_LISTENER}(max-poll-records=3000, fetch-max-wait=5s)와 달리
 * 이 파이프라인은 배치를 즉시 합산·반영하는 것이 목적이라 작은 배치를 짧은 대기로 자주 받는다.
 * {@code max-poll-records}/{@code fetch-max-wait} 는 {@code spring.kafka.consumer.*}(application.yml)에서
 * 읽어(=500/500ms) 코드에 박지 않고, 안정성 관련 타임아웃만 여기서 고정한다. manual ack 는 유지.
 */
@Configuration
public class MetricsKafkaListenerConfig {

    public static final String METRICS_BATCH_LISTENER = "METRICS_BATCH_LISTENER";

    private static final int SESSION_TIMEOUT_MS = 60 * 1000;
    private static final int HEARTBEAT_INTERVAL_MS = 20 * 1000;
    private static final int MAX_POLL_INTERVAL_MS = 2 * 60 * 1000;

    @Bean(name = METRICS_BATCH_LISTENER)
    public ConcurrentKafkaListenerContainerFactory<Object, Object> metricsBatchListenerContainerFactory(
        KafkaProperties kafkaProperties,
        ByteArrayJsonMessageConverter converter
    ) {
        // max-poll-records / fetch-max-wait 는 yml(spring.kafka.consumer.*)에서 반영된다.
        Map<String, Object> consumerConfig = new HashMap<>(kafkaProperties.buildConsumerProperties());
        consumerConfig.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, SESSION_TIMEOUT_MS);
        consumerConfig.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, HEARTBEAT_INTERVAL_MS);
        consumerConfig.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, MAX_POLL_INTERVAL_MS);

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(consumerConfig));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setBatchMessageConverter(new BatchMessagingMessageConverter(converter));
        factory.setConcurrency(3);
        factory.setBatchListener(true);
        return factory;
    }
}
