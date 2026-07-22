package com.loopers.ranking.application;

import com.loopers.ranking.domain.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 콜드 스타트 완화 — 오늘 판의 점수에 작은 가중치를 곱해 내일 판에 미리 심는다(Score Carry-Over).
 * 작은 가중치라 내일의 실제 활동이 금방 위로 올라온다. 23:50 스케줄러가 호출한다.
 */
@Service
@RequiredArgsConstructor
public class RankingCarryOverService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final double CARRY_OVER_WEIGHT = 0.1;

    private final RankingRepository rankingRepository;

    public void seedNextDay() {
        LocalDate today = LocalDate.now(SEOUL);
        rankingRepository.carryOver(today, today.plusDays(1), CARRY_OVER_WEIGHT);
    }
}
