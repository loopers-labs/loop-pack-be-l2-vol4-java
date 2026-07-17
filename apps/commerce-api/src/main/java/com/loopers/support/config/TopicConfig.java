package com.loopers.support.config;

import com.loopers.infrastructure.kafka.KafkaOutboxRelay;
import com.loopers.infrastructure.outbox.OutboxService;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@Configuration
public class TopicConfig {

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(OutboxService.ORDER_EVENTS_TOPIC)
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic catalogEventsTopic() {
        return TopicBuilder.name(KafkaOutboxRelay.CATALOG_TOPIC)
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic couponIssueRequestsTopic() {
        return TopicBuilder.name(OutboxService.COUPON_ISSUE_REQUESTS_TOPIC)
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic likeEventsTopic() {
        return TopicBuilder.name(OutboxService.LIKE_EVENTS_TOPIC)
            .partitions(3)
            .replicas(1)
            .build();
    }
}
