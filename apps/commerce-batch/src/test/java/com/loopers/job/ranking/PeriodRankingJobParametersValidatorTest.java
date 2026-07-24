package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.PeriodRankingJobParametersValidator;
import com.loopers.ranking.RankingPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.batch.core.JobParametersBuilder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PeriodRankingJobParametersValidatorTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @DisplayName("requestDate가 null, blank, 잘못된 형식이면 거절한다.")
    @Test
    void rejectsMissingOrMalformedRequestDate() {
        PeriodRankingJobParametersValidator validator = validator(
            RankingPeriod.WEEKLY,
            "2026-07-19T03:00:00Z"
        );

        assertThatThrownBy(() -> validator.validate(new JobParametersBuilder().toJobParameters()))
            .hasMessageContaining("requestDate");
        assertThatThrownBy(() -> validator.validate(new JobParametersBuilder()
            .addString("requestDate", " ")
            .toJobParameters()))
            .hasMessageContaining("requestDate");
        assertThatThrownBy(() -> validator.validate(new JobParametersBuilder()
            .addString("requestDate", "20260712")
            .toJobParameters()))
            .hasMessageContaining("requestDate");
    }

    @DisplayName("주간·월간 모두 종료일이 오늘이면 거절하고 어제까지 종료된 기간은 허용한다.")
    @ParameterizedTest(name = "{0} requestDate={1}, today={2}, completed={3}")
    @MethodSource("completionCases")
    void validatesCompletedPeriod(
        RankingPeriod period,
        String requestDate,
        String now,
        boolean completed
    ) {
        PeriodRankingJobParametersValidator validator = validator(period, now);
        var parameters = new JobParametersBuilder()
            .addString("requestDate", requestDate)
            .toJobParameters();

        if (completed) {
            assertThatCode(() -> validator.validate(parameters)).doesNotThrowAnyException();
            return;
        }
        assertThatThrownBy(() -> validator.validate(parameters))
            .hasMessageContaining("종료");
    }

    private static Stream<Arguments> completionCases() {
        return Stream.of(
            Arguments.of(RankingPeriod.WEEKLY, "2026-07-15", "2026-07-19T03:00:00Z", false),
            Arguments.of(RankingPeriod.WEEKLY, "2026-07-08", "2026-07-13T03:00:00Z", true),
            Arguments.of(RankingPeriod.MONTHLY, "2026-07-10", "2026-07-31T03:00:00Z", false),
            Arguments.of(RankingPeriod.MONTHLY, "2026-06-10", "2026-07-01T03:00:00Z", true)
        );
    }

    private PeriodRankingJobParametersValidator validator(RankingPeriod period, String now) {
        return new PeriodRankingJobParametersValidator(
            period,
            Clock.fixed(Instant.parse(now), SEOUL)
        );
    }
}
