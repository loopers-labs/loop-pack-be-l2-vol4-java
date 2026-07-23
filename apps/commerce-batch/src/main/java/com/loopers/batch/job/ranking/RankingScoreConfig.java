package com.loopers.batch.job.ranking;

import com.loopers.ranking.domain.RankingScoreWeights;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 가중치는 SQL 에 박지 않고 설정에서 읽어 바인딩 파라미터로 넣는다.
 * 기본값은 streamer 의 RankingScorePolicy 와 같은 값이다 — 공유 위치가 정해지면 이 프로퍼티 이름 그대로 옮긴다.
 */
@Configuration
public class RankingScoreConfig {

    @Bean
    public RankingScoreWeights rankingScoreWeights(
            @Value("${ranking.score.view:0.1}") double view,
            @Value("${ranking.score.like:0.2}") double like,
            @Value("${ranking.score.order:0.6}") double order) {
        return new RankingScoreWeights(view, like, order);
    }
}
