package com.loopers.infrastructure.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "loopers.kafka.topics")
public record CatalogEventTopicProperties(String catalogEvents, String couponIssueRequests) {
}
