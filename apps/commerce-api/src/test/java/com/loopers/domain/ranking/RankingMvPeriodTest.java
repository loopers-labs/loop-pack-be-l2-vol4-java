package com.loopers.domain.ranking;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingMvPeriodTest {

    @DisplayName("from()을 실행할 때,")
    @Nested
    class From {

        @DisplayName("WEEKLY/MONTHLY 문자열이면 해당 enum을 반환한다.")
        @Test
        void returnsMatchingEnum() {
            assertThat(RankingMvPeriod.from("WEEKLY")).isEqualTo(RankingMvPeriod.WEEKLY);
            assertThat(RankingMvPeriod.from("MONTHLY")).isEqualTo(RankingMvPeriod.MONTHLY);
        }

        @DisplayName("지원하지 않는 값이면 CoreException을 던진다.")
        @Test
        void throwsCoreException_whenValueIsUnsupported() {
            assertThatThrownBy(() -> RankingMvPeriod.from("DAILY")).isInstanceOf(CoreException.class);
        }

        @DisplayName("null이면 CoreException을 던진다.")
        @Test
        void throwsCoreException_whenValueIsNull() {
            assertThatThrownBy(() -> RankingMvPeriod.from(null)).isInstanceOf(CoreException.class);
        }
    }
}
