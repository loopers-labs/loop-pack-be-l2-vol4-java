package com.loopers.ranking.application;

import com.loopers.ranking.domain.RankingRepository;
import com.loopers.ranking.domain.RankingScoreDelta;
import com.loopers.ranking.domain.RankingScorePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 랭킹 이벤트 배치를 (날짜, 상품)별로 접어(fold) 점수를 합산한 뒤 한 번에 반영한다.
 * 날짜 판정·가드는 컨슈머가 이미 끝냈고, 여기 오는 건 반영 대상뿐이다.
 */
@Service
@RequiredArgsConstructor
public class RankingService {

    private final RankingRepository rankingRepository;

    public void apply(List<RankingEvent> events) {
        Map<Bucket, Double> folded = new LinkedHashMap<>();
        for (RankingEvent event : events) {
            Bucket bucket = new Bucket(event.date(), event.productId());
            double score = RankingScorePolicy.score(event.signal(), event.delta());
            folded.merge(bucket, score, Double::sum);
        }

        List<RankingScoreDelta> deltas = folded.entrySet().stream()
                .map(e -> new RankingScoreDelta(e.getKey().date(), e.getKey().productId(), e.getValue()))
                .toList();
        rankingRepository.incrBy(deltas);
    }

    private record Bucket(LocalDate date, long productId) {
    }
}
