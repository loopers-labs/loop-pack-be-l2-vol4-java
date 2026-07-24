package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingProperties;
import com.loopers.domain.ranking.RankingScorePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 도메인 정책 빈 조립 — RankingScorePolicy 는 순수 도메인이라 설정 주입을 여기서 한다. */
@Configuration
public class RankingConfig {

    @Bean
    public RankingScorePolicy rankingScorePolicy(RankingProperties properties) {
        return new RankingScorePolicy(properties.viewWeight(), properties.likeWeight(), properties.orderWeight());
    }
}
