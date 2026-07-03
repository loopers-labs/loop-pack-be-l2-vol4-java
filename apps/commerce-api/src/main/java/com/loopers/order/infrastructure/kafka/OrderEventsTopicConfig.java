package com.loopers.order.infrastructure.kafka;

import com.loopers.confg.kafka.KafkaTopic;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * order-events 토픽 정의. auto.create.topics.enable=false 이므로 KafkaAdmin 이 기동 시 명시적으로 생성한다.
 * - partitions=3 : 컨슈머 동시성(concurrency=3)과 맞춰 병렬 처리 여지를 둔다.
 * - replicas=1   : 로컬 단일 브로커(운영에선 3으로).
 */
@Configuration
public class OrderEventsTopicConfig {

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(KafkaTopic.ORDER_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
