package com.loopers.domain.ranking;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingPeriodTest {

    @DisplayName("period 파라미터를 파싱할 때")
    @Nested
    class From {

        @DisplayName("값이 없으면 일간(DAILY)으로 본다")
        @Test
        void defaultsToDaily_whenBlank() {
            assertThat(RankingPeriod.from(null)).isEqualTo(RankingPeriod.DAILY);
            assertThat(RankingPeriod.from("  ")).isEqualTo(RankingPeriod.DAILY);
        }

        @DisplayName("대소문자를 가리지 않고 주간·월간을 인식한다")
        @Test
        void parsesCaseInsensitively() {
            assertThat(RankingPeriod.from("weekly")).isEqualTo(RankingPeriod.WEEKLY);
            assertThat(RankingPeriod.from("Monthly")).isEqualTo(RankingPeriod.MONTHLY);
        }

        @DisplayName("알 수 없는 값이면 원인 예외를 보존한 BAD_REQUEST를 던진다")
        @Test
        void throwsBadRequest_whenUnknown() {
            assertThatThrownBy(() -> RankingPeriod.from("yearly"))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                .cause().isInstanceOf(IllegalArgumentException.class);
        }
    }

    @DisplayName("MV period_key를 만들 때")
    @Nested
    class MvPeriodKey {

        // 2026-07-15은 수요일 → ISO주 월2026-07-13 ~ 일2026-07-19, 달 2026-07.
        private static final LocalDate WED = LocalDate.of(2026, 7, 15);

        @DisplayName("주간은 같은 ISO주의 어떤 날이든 같은 yyyy-Www key를 준다 (배치 Period와 동일 규칙)")
        @Test
        void weeklyKeyIsIsoWeekAndStable() {
            // 정답값은 production 헬퍼로 계산하지 않고 직접 명시한다 — 배치↔API 계약을 독립적으로 못박기 위함이다.
            assertThat(RankingPeriod.WEEKLY.mvPeriodKey(WED)).isEqualTo("2026-W29");
            assertThat(RankingPeriod.WEEKLY.mvPeriodKey(LocalDate.of(2026, 7, 13))).isEqualTo("2026-W29"); // 월
            assertThat(RankingPeriod.WEEKLY.mvPeriodKey(LocalDate.of(2026, 7, 19))).isEqualTo("2026-W29"); // 일
            assertThat(RankingPeriod.WEEKLY.mvPeriodKey(LocalDate.of(2026, 7, 20))).isEqualTo("2026-W30"); // 다음 주
        }

        @DisplayName("ISO week-based-year라 연초가 지난해 마지막 주로 묶이면 그 해의 key를 준다")
        @Test
        void weeklyKeyUsesIsoWeekBasedYearAtBoundary() {
            // 2021-01-01(금)은 ISO로 2020-W53에 속한다(2021-W01은 2021-01-04 월요일부터).
            assertThat(RankingPeriod.WEEKLY.mvPeriodKey(LocalDate.of(2021, 1, 1))).isEqualTo("2020-W53");
        }

        @DisplayName("월간은 yyyy-MM key를 준다")
        @Test
        void monthlyKeyIsYearMonth() {
            assertThat(RankingPeriod.MONTHLY.mvPeriodKey(WED)).isEqualTo("2026-07");
        }

        @DisplayName("일간은 MV를 쓰지 않으므로 key 호출 시 예외를 던진다")
        @Test
        void dailyHasNoMvKey() {
            assertThatThrownBy(() -> RankingPeriod.DAILY.mvPeriodKey(WED))
                .isInstanceOf(IllegalStateException.class);
        }
    }
}
