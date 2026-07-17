package com.loopers.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RankingScoreFormulaTest {

    @DisplayName("조회, 좋아요, 판매 수에 공통 가중치를 적용한다.")
    @Test
    void calculatesWeightedScore() {
        double score = RankingScoreFormula.calculate(10, 5, 2);

        assertThat(score).isEqualTo(3.4);
    }
}
