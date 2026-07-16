package com.loopers.application.ranking;

import com.loopers.application.metrics.CatalogEventMessage;
import com.loopers.ranking.DailyRankingKey;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class CatalogRankingEventProcessor {
    private final RankingScorePolicy rankingScorePolicy;
    private final RankingScoreWriter rankingScoreWriter;
    private final MeterRegistry meterRegistry;

    public void process(List<CatalogEventMessage> events) {
        events.forEach(rankingScorePolicy::validate);

        Map<RankingTarget, Double> scoreByTarget = new LinkedHashMap<>();
        for (CatalogEventMessage event : events) {
            double score = rankingScorePolicy.score(event);
            if (score == 0.0) {
                continue;
            }
            RankingTarget target = new RankingTarget(DailyRankingKey.from(event.occurredAt()), event.productId());
            scoreByTarget.merge(target, score, Double::sum);
            meterRegistry.counter("ranking_event_consume_total", "eventType", event.eventType()).increment();
            meterRegistry.counter(
                "ranking_score_total",
                "eventType", event.eventType(),
                "direction", score > 0 ? "added" : "removed"
            ).increment(Math.abs(score));
        }

        scoreByTarget.forEach((target, score) -> {
            if (score != 0.0) {
                try {
                    rankingScoreWriter.increment(target.rankingKey(), target.productId(), score);
                    meterRegistry.counter("ranking_redis_update_total", "result", "success").increment();
                } catch (RuntimeException e) {
                    meterRegistry.counter("ranking_redis_update_total", "result", "failure").increment();
                    throw e;
                }
            }
        });
    }

    private record RankingTarget(String rankingKey, Long productId) {
    }
}
