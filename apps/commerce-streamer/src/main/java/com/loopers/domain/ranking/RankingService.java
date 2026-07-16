package com.loopers.domain.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class RankingService {

    private final RankingRepository rankingRepository;

    // 배치 내 커맨드를 productId 기준으로 메모리에서 먼저 합산한 뒤 한 번에 반영해,
    // 메시지 건수만큼 발생하던 ZSET 연산을 배치당 고유 상품 수만큼으로 줄인다.
    public void applyBatch(List<RankingCommand.UpdateRanking> commands) {
        if (commands.isEmpty()) return;

        Map<Long, Double> deltas = new HashMap<>();
        for (RankingCommand.UpdateRanking command : commands) {
            deltas.merge(command.productId(), score(command), Double::sum);
        }
        rankingRepository.incrementScores(LocalDate.now(), deltas);
    }

    private double score(RankingCommand.UpdateRanking command) {
        return switch (command.type()) {
            case VIEW -> RankingScorePolicy.viewScore();
            case LIKE -> RankingScorePolicy.likeScore();
            case UNLIKE -> RankingScorePolicy.unlikeScore();
            case ORDER -> RankingScorePolicy.orderScore(command.price(), command.quantity());
        };
    }
}
