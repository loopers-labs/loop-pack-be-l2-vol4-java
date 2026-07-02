package com.loopers.interfaces.api.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 앱 부팅 시 KafkaAdmin 이 없으면 생성하는 토픽 선언.
 *
 * <p>{@code auto.create.topics.enable=false} 라 누군가는 토픽을 만들어야 한다. 코드 선언을 택해
 * 로컬/테스트가 부팅만으로 재현되게 한다(운영은 보통 인프라팀이 수동 통제 — 파티션 수가 되돌리기 어려운 결정이라).
 * partitions=3 은 consumer 병렬성 상한이며, 순서는 <b>파티션 내부(=키 단위)</b>에서만 보장된다.
 * key=aggregateId 로 발행하면 같은 애그리거트 이벤트는 항상 같은 파티션 → 키 단위 순서 보존.</p>
 */
@Configuration
public class KafkaTopicConfig {

    public static final String CATALOG_EVENTS = "catalog-events";
    public static final String ORDER_EVENTS = "order-events";
    public static final String COUPON_ISSUE_REQUESTS = "coupon-issue-requests";

    private static final int PARTITIONS = 3;
    private static final short REPLICAS = 1;

    @Bean
    public NewTopic catalogEventsTopic() {
        return TopicBuilder.name(CATALOG_EVENTS).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(ORDER_EVENTS).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    /**
     * 선착순 발급 요청 토픽. key=templateId 로 발행되어 같은 템플릿 요청이 한 파티션에 모이고,
     * 단일 소비자 스레드가 순차 처리해 락 없이 한도를 강제한다(파티션 직렬화). 실패 격리용 {@code .DLT} 도 함께 선언.
     */
    @Bean
    public NewTopic couponIssueRequestsTopic() {
        return TopicBuilder.name(COUPON_ISSUE_REQUESTS).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    @Bean
    public NewTopic couponIssueRequestsDltTopic() {
        return TopicBuilder.name(COUPON_ISSUE_REQUESTS + ".DLT").partitions(PARTITIONS).replicas(REPLICAS).build();
    }
}
