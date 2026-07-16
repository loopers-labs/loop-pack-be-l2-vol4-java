package com.loopers.domain.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class RankingService {

    private final RankingRepository rankingRepository;
    private final RankingWeightRepository rankingWeightRepository;

    // 배치 내 커맨드를 productId 기준으로 메모리에서 먼저 합산한 뒤 한 번에 반영해,
    // 메시지 건수만큼 발생하던 ZSET 연산을 배치당 고유 상품 수만큼으로 줄인다.
    // 가중치도 이벤트 건마다 조회하지 않고 배치당 한 번만 조회해 재사용한다.
    // 일간 랭킹과 시간별 랭킹은 서로 다른 윈도우일 뿐 같은 이벤트에서 파생되므로 같은 배치에서 둘 다 반영한다.
    public void applyBatch(List<RankingCommand.UpdateRanking> commands) {
        if (commands.isEmpty()) return;

        RankingWeights weights = rankingWeightRepository.findCurrent();
        Map<Long, Double> deltas = new HashMap<>();
        for (RankingCommand.UpdateRanking command : commands) {
            deltas.merge(command.productId(), score(command, weights), Double::sum);
        }
        LocalDateTime now = LocalDateTime.now();
        rankingRepository.incrementScores(now.toLocalDate(), deltas);
        rankingRepository.incrementHourlyScores(now, deltas);
    }

    // 콜드 스타트 완화: from 날짜의 점수에 ratio를 곱해 to 날짜 키에 미리 반영해둔다.
    public void carryOverScores(LocalDate from, LocalDate to, double ratio) {
        rankingRepository.carryOverScores(from, to, ratio);
    }

    // 콜드 스타트 완화(시간 단위): from 시간의 점수에 ratio를 곱해 to 시간 키에 미리 반영해둔다.
    public void carryOverHourlyScores(LocalDateTime from, LocalDateTime to, double ratio) {
        rankingRepository.carryOverHourlyScores(from, to, ratio);
    }

    private double score(RankingCommand.UpdateRanking command, RankingWeights weights) {
        return switch (command.type()) {
            case VIEW -> weights.viewScore();
            case LIKE -> weights.likeScore();
            case UNLIKE -> weights.unlikeScore();
            case ORDER -> weights.orderScore(command.price(), command.quantity());
        };
    }
}
