package com.loopers.interfaces.consumer;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * collector 가 소비하는 토픽을 부팅 시 선언한다. 토픽의 <b>진짜 소유자는 producer(commerce-api)</b>지만,
 * {@code auto.create.topics.enable=false} 환경에서 streamer 를 단독 부팅/테스트할 때 리스너가 구독 즉시 붙도록
 * (구독 이후 생성된 토픽은 metadata 갱신까지 수 분 지연될 수 있음) 동일 스펙으로 함께 선언한다.
 * KafkaAdmin 은 이미 있으면 생성을 건너뛰므로 양쪽 선언은 충돌하지 않는다(partitions=3 스펙 일치).
 */
@Configuration
public class KafkaTopicConfig {

    private static final int PARTITIONS = 3;
    private static final short REPLICAS = 1;

    @Bean
    public NewTopic catalogEventsTopic() {
        return TopicBuilder.name(CatalogEventConsumer.CATALOG_EVENTS).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(OrderSalesConsumer.ORDER_EVENTS).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    /**
     * 각 소스 토픽의 DLQ. 이름은 {@link KafkaErrorHandlingConfig} 상수로 recoverer 와 공유해 드리프트를 막는다.
     * auto.create 가 꺼져 있어 선언이 필요하다.
     */
    @Bean
    public NewTopic catalogEventsDltTopic() {
        return TopicBuilder.name(KafkaErrorHandlingConfig.CATALOG_EVENTS_DLT).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    @Bean
    public NewTopic orderEventsDltTopic() {
        return TopicBuilder.name(KafkaErrorHandlingConfig.ORDER_EVENTS_DLT).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    /**
     * 선착순 발급 요청 토픽 + DLQ. producer(commerce-api)가 진짜 소유자지만, streamer 단독 부팅/테스트 시 리스너가
     * 즉시 붙도록 동일 스펙(partitions=3)으로 함께 선언한다(이미 있으면 KafkaAdmin 이 건너뜀).
     */
    @Bean
    public NewTopic couponIssueRequestsTopic() {
        return TopicBuilder.name(CouponIssueConsumer.COUPON_ISSUE_REQUESTS).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    @Bean
    public NewTopic couponIssueRequestsDltTopic() {
        return TopicBuilder.name(KafkaErrorHandlingConfig.COUPON_ISSUE_REQUESTS_DLT).partitions(PARTITIONS).replicas(REPLICAS).build();
    }
}
