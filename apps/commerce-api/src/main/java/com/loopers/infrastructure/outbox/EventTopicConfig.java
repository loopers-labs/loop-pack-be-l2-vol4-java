package com.loopers.infrastructure.outbox;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * week7 신규 토픽을 KafkaAdmin으로 자동 생성한다(kafka.yml의 auto.create.topics.enable=false 이므로 명시 생성).
 * 파티션 키(productId/orderId/couponId) 단위로 순서를 보장하고, 같은 키를 같은 파티션에 모아 경합 범위를 좁힌다.
 * 단일 브로커(로컬)이므로 복제본은 1.
 */
@Configuration
public class EventTopicConfig {

    @Bean
    public NewTopic catalogEventsTopic(@Value("${event-topics.catalog.partitions:3}") int partitions) {
        return TopicBuilder.name(EventTopics.CATALOG_EVENTS).partitions(partitions).replicas(1).build();
    }

    @Bean
    public NewTopic orderEventsTopic(@Value("${event-topics.order.partitions:3}") int partitions) {
        return TopicBuilder.name(EventTopics.ORDER_EVENTS).partitions(partitions).replicas(1).build();
    }

    @Bean
    public NewTopic couponIssueRequestsTopic(@Value("${event-topics.coupon-issue.partitions:3}") int partitions) {
        return TopicBuilder.name(EventTopics.COUPON_ISSUE_REQUESTS).partitions(partitions).replicas(1).build();
    }
}
