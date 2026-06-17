package com.loopers.infrastructure.like;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 좋아요 변경 토픽을 부팅 시 생성한다(kafka.yml의 auto.create.topics.enable=false 이므로 명시 생성).
 * KafkaAdmin(스프링 부트 자동설정)이 이 NewTopic 빈을 보고 브로커에 토픽을 만든다.
 *
 * <p>파티션을 여러 개 둬서 productId 해시로 분산 → 컨슈머 concurrency를 활용해 hot 상품 외 처리량 확보.
 * 단일 브로커(로컬)라 복제본은 1.
 */
@Configuration
public class LikeKafkaTopicConfig {

    @Bean
    public NewTopic likeChangedTopic(
            @Value("${like-events.topic}") String topic,
            @Value("${like-events.partitions:3}") int partitions
    ) {
        return TopicBuilder.name(topic)
                .partitions(partitions)
                .replicas(1)
                .build();
    }
}
