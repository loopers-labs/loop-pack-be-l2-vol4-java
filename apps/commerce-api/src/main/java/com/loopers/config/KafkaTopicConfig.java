package com.loopers.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka 토픽 선언 — 브로커의 {@code auto.create.topics.enable=false} 환경에서
 * 앱 기동 시 KafkaAdmin 이 토픽을 생성한다(이미 있으면 무시).
 *
 * <p>파티션 3 — 파티션 키(productId/orderId) 기준 분산 + 키 단위 순서 보장.
 * 복제 1 — 로컬 단일 브로커 기준(운영에선 3 권장).
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic catalogEventsTopic(@Value("${commerce-events.topics.catalog}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderEventsTopic(@Value("${commerce-events.topics.order}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic viewEventsTopic(@Value("${commerce-events.topics.view}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic couponIssueRequestsTopic(@Value("${commerce-events.topics.coupon-issue}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic couponIssueRequestsDlqTopic(@Value("${commerce-events.topics.coupon-issue}") String name) {
        return TopicBuilder.name(name + ".DLQ").partitions(3).replicas(1).build();
    }
}
