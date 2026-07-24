package com.loopers.batch.job.ranking;

import com.loopers.ranking.domain.RankingScoreWeights;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 가중치를 빈으로 제공한다. streamer 의 RankingScorePolicy 와 같은 상수다 —
 * 거의 고정인 값이라 설정으로 빼지 않는다(설정 있는 척하지 않는다). 값 일치는 두 앱의 계약 테스트가 지킨다.
 */
@Configuration
public class RankingScoreConfig {

    @Bean
    public RankingScoreWeights rankingScoreWeights() {
        return RankingScoreWeights.standard();
    }
}
