package com.loopers.infrastructure.ranking;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "ranking")
public record RankingCarryOverProperties(
        @DefaultValue("0.1") double carryOverRatio,
        @DefaultValue("true") boolean carryOverSchedulerEnabled
) {
}
