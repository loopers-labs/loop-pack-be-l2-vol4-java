package com.loopers.ranking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("commerce.ranking.score")
public record RankingScoreProperties(
    @DefaultValue("V1") String policyVersion,
    @DefaultValue("0.1") double viewWeight,
    @DefaultValue("0.2") double likeWeight,
    @DefaultValue("0.7") double orderWeight,
    @DefaultValue("10000") long orderAmountUnit
) {
}
