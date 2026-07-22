package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingPeriod;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 기간 → 조회 전략 라우팅 테이블. 등록된 RankingReader 들을 period 로 색인해, Facade 가 if 분기 없이
 * get(period) 한 번으로 알맞은 저장소(Redis/MV)로 위임하게 한다.
 */
@Component
public class RankingReaderRouter {

    private final Map<RankingPeriod, RankingReader> readersByPeriod;

    public RankingReaderRouter(List<RankingReader> readers) {
        this.readersByPeriod = readers.stream()
            .collect(Collectors.toMap(RankingReader::period, Function.identity()));
    }

    public RankingReader route(RankingPeriod period) {
        return readersByPeriod.get(period);
    }
}
