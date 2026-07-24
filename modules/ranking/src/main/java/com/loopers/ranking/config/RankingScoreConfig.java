package com.loopers.ranking.config;

import com.loopers.ranking.RankingScorePolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RankingScoreProperties.class)
public class RankingScoreConfig {

    @Bean
    public RankingScorePolicy rankingScorePolicy(RankingScoreProperties properties) {
        return RankingScorePolicy.from(
            properties.policyVersion(),
            properties.viewWeight(),
            properties.likeWeight(),
            properties.orderWeight(),
            properties.orderAmountUnit()
        );
    }
}
