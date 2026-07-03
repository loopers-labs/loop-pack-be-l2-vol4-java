package com.loopers.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic catalogEventsTopic() {
        return TopicBuilder.name("catalog-events")
            .partitions(1)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name("order-events")
            .partitions(1)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic couponIssueRequestsTopic() {
        return TopicBuilder.name("coupon-issue-requests")
            .partitions(3)
            .replicas(1)
            .build();
    }
}
