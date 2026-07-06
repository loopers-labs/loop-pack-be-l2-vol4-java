package com.loopers.product.infrastructure.kafka;

import com.loopers.confg.kafka.KafkaTopic;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class CatalogEventsTopicConfig {

    @Bean
    public NewTopic catalogEventsTopic() {
        return TopicBuilder.name(KafkaTopic.CATALOG_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
