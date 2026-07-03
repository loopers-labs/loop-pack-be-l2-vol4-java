package com.loopers.confg.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

// auto.create.topics.enable=false이므로 KafkaAdmin(Spring Boot 자동구성)이 기동 시 명시적으로 생성한다.
// 파티션 키 = aggregateId(productId/orderId)로 같은 상품/주문 이벤트가 항상 같은 파티션에 몰려 순서가 보장된다.
// 로컬/테스트 모두 단일 브로커라 replicas는 1로 고정한다.
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic catalogEventsTopic() {
        return TopicBuilder.name("catalog-events").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name("order-events").partitions(3).replicas(1).build();
    }
}
