package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingKey;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 일간 랭킹을 실시간 Redis ZSET 에서 읽는다(R9 경로). 버킷은 그 날짜의 랭킹판 키 그대로다.
 */
@Component
@RequiredArgsConstructor
public class DailyRankingReader implements RankingReader {

    private final RankingRepository rankingRepository;

    @Override
    public RankingPeriod period() {
        return RankingPeriod.DAILY;
    }

    @Override
    public List<RankingEntry> topN(LocalDate date, int page, int size) {
        return rankingRepository.topN(RankingKey.of(date), page, size);
    }
}
