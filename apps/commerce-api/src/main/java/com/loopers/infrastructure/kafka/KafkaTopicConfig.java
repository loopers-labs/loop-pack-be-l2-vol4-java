package com.loopers.infrastructure.kafka;

import com.loopers.application.outbox.CatalogEventTopicProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic catalogEventsTopic(CatalogEventTopicProperties properties) {
        return TopicBuilder.name(properties.catalogEvents())
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic couponIssueRequestsTopic(CatalogEventTopicProperties properties) {
        return TopicBuilder.name(properties.couponIssueRequests())
            .partitions(3)
            .replicas(1)
            .build();
    }
}
