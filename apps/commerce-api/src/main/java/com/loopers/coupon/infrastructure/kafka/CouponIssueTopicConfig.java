package com.loopers.coupon.infrastructure.kafka;

import com.loopers.confg.kafka.KafkaTopic;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class CouponIssueTopicConfig {

    @Bean
    public NewTopic couponIssueRequestsTopic() {
        return TopicBuilder.name(KafkaTopic.COUPON_ISSUE_REQUESTS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
