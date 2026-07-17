package com.loopers.ranking.application;

import com.loopers.ranking.domain.RankingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RankingCarryOverServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final RankingRepository rankingRepository = mock(RankingRepository.class);
    private final RankingCarryOverService service = new RankingCarryOverService(rankingRepository);

    @DisplayName("오늘 판을 가중치 0.1 로 내일 판에 carry-over 한다")
    @Test
    void seedNextDay_carriesTodayToTomorrow() {
        LocalDate today = LocalDate.now(SEOUL);

        service.seedNextDay();

        verify(rankingRepository).carryOver(today, today.plusDays(1), 0.1);
    }
}
