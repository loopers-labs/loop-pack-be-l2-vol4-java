package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingCommand;
import com.loopers.domain.ranking.RankingService;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

// test 프로필에서는 ranking.carry-over-scheduler-enabled=false 로 자동 tick 이 꺼져 있다(application.yml).
// 이 테스트는 carryOver()를 직접 호출해 검증하므로 23:50 스케줄 타이밍과 경합할 일이 없다.
@SpringBootTest
class RankingCarryOverSchedulerIntegrationTest {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HOURLY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    @Autowired
    private RankingCarryOverScheduler rankingCarryOverScheduler;

    @Autowired
    private RankingService rankingService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    private String keyOf(LocalDate date) {
        return "ranking:all:" + date.format(DATE_FORMAT);
    }

    private Double scoreOf(LocalDate date, Long productId) {
        return redisTemplate.opsForZSet().score(keyOf(date), String.valueOf(productId));
    }

    private String hourlyKeyOf(LocalDateTime dateTime) {
        return "ranking:hourly:" + dateTime.format(HOURLY_DATE_FORMAT);
    }

    private Double hourlyScoreOf(LocalDateTime dateTime, Long productId) {
        return redisTemplate.opsForZSet().score(hourlyKeyOf(dateTime), String.valueOf(productId));
    }

    @DisplayName("carryOver를 호출할 때,")
    @Nested
    class CarryOver {

        @DisplayName("오늘 점수의 기본 비율(0.1)만큼 내일 키에 미리 반영된다.")
        @Test
        void copiesTenPercentOfTodayScore_intoTomorrowKey() {
            // given
            Long productId = 1L;
            rankingService.applyBatch(List.of(RankingCommand.UpdateRanking.order(productId, 1L, BigDecimal.valueOf(1_000))));
            // 오늘 점수: 0.6 * 1000 * 1 = 600

            // when
            rankingCarryOverScheduler.carryOver();

            // then: 600 * 0.1 = 60
            LocalDate tomorrow = LocalDate.now().plusDays(1);
            assertThat(scoreOf(tomorrow, productId)).isCloseTo(60.0, within(1e-6));
        }

        @DisplayName("오늘 랭킹이 비어있으면 내일 키에도 아무 것도 생기지 않는다.")
        @Test
        void createsNothing_whenTodayRankingIsEmpty() {
            // when
            rankingCarryOverScheduler.carryOver();

            // then
            LocalDate tomorrow = LocalDate.now().plusDays(1);
            assertThat(redisTemplate.opsForZSet().zCard(keyOf(tomorrow))).isZero();
        }
    }

    @DisplayName("hourlyCarryOver를 호출할 때,")
    @Nested
    class HourlyCarryOver {

        @DisplayName("이번 시간 점수의 기본 비율(0.1)만큼 다음 시간 키에 미리 반영된다.")
        @Test
        void copiesTenPercentOfThisHourScore_intoNextHourKey() {
            // given
            Long productId = 2L;
            rankingService.applyBatch(List.of(RankingCommand.UpdateRanking.order(productId, 1L, BigDecimal.valueOf(1_000))));
            // 이번 시간 점수: 0.6 * 1000 * 1 = 600

            // when
            rankingCarryOverScheduler.hourlyCarryOver();

            // then: 600 * 0.1 = 60
            LocalDateTime nextHour = LocalDateTime.now().plusHours(1);
            assertThat(hourlyScoreOf(nextHour, productId)).isCloseTo(60.0, within(1e-6));
        }

        @DisplayName("이번 시간 랭킹이 비어있으면 다음 시간 키에도 아무 것도 생기지 않는다.")
        @Test
        void createsNothing_whenThisHourRankingIsEmpty() {
            // when
            rankingCarryOverScheduler.hourlyCarryOver();

            // then
            LocalDateTime nextHour = LocalDateTime.now().plusHours(1);
            assertThat(redisTemplate.opsForZSet().zCard(hourlyKeyOf(nextHour))).isZero();
        }
    }
}
