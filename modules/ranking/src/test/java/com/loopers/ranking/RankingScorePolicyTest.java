package com.loopers.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.junit.jupiter.api.Assertions.assertAll;

class RankingScorePolicyTest {

    private final RankingScorePolicy policy = new RankingScorePolicy(0.1, 0.2, 0.7, 10_000);

    @DisplayName("상품 조회 한 건은 0.1점으로 계산한다.")
    @Test
    void calculatesViewScore() {
        // act
        double score = policy.viewScore();

        // assert
        assertThat(score).isCloseTo(0.1, offset(1.0e-10));
    }

    @DisplayName("좋아요 한 건은 0.2점으로 계산한다.")
    @Test
    void calculatesLikeScore() {
        // act
        double score = policy.likeScore(1);

        // assert
        assertThat(score).isCloseTo(0.2, offset(1.0e-10));
    }

    @DisplayName("좋아요 취소 한 건은 -0.2점으로 계산한다.")
    @Test
    void calculatesUnlikeScore() {
        // act
        double score = policy.likeScore(-1);

        // assert
        assertThat(score).isCloseTo(-0.2, offset(1.0e-10));
    }

    @DisplayName("주문 금액은 10,000원 단위로 정규화한 뒤 0.7 가중치를 적용한다.")
    @Test
    void calculatesOrderScore() {
        // act
        double score = policy.orderScore(25_000);

        // assert
        assertThat(score).isCloseTo(1.75, offset(1.0e-10));
    }

    @DisplayName("Raw Metric 합계로 실시간 점수와 같은 총점을 계산한다.")
    @Test
    void calculatesTotalScore() {
        // act
        double score = policy.totalScore(10, 3, 25_000);

        // assert
        assertThat(score).isCloseTo(3.35, offset(1.0e-10));
    }

    @DisplayName("점수 정책 버전과 실제 설정값을 제공한다.")
    @Test
    void exposesVersionAndSettings() {
        // act & assert
        assertAll(
            () -> assertThat(policy.version()).isEqualTo("V1"),
            () -> assertThat(policy.viewWeight()).isEqualTo(0.1),
            () -> assertThat(policy.likeWeight()).isEqualTo(0.2),
            () -> assertThat(policy.orderWeight()).isEqualTo(0.7),
            () -> assertThat(policy.orderAmountUnit()).isEqualTo(10_000)
        );
    }

    @DisplayName("지원하지 않는 버전으로 점수 정책을 복원할 수 없다.")
    @Test
    void rejectsUnsupportedVersion() {
        // act & assert
        assertThatThrownBy(() -> RankingScorePolicy.from("V2", 0.1, 0.2, 0.7, 10_000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("V2");
    }

    @DisplayName("주문 금액 단위가 0 이하면 점수 정책을 생성할 수 없다.")
    @Test
    void rejectsNonPositiveOrderAmountUnit() {
        // act & assert
        assertThatThrownBy(() -> new RankingScorePolicy(0.1, 0.2, 0.7, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("orderAmountUnit");
    }

    @DisplayName("가중치가 유한한 숫자가 아니면 점수 정책을 생성할 수 없다.")
    @MethodSource("nonFiniteWeights")
    @ParameterizedTest
    void rejectsNonFiniteWeight(double viewWeight, double likeWeight, double orderWeight) {
        // act & assert
        assertThatThrownBy(() -> new RankingScorePolicy(viewWeight, likeWeight, orderWeight, 10_000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("weight");
    }

    private static Stream<Arguments> nonFiniteWeights() {
        return Stream.of(
            Arguments.of(Double.NaN, 0.2, 0.7),
            Arguments.of(0.1, Double.POSITIVE_INFINITY, 0.7),
            Arguments.of(0.1, 0.2, Double.NEGATIVE_INFINITY)
        );
    }
}
