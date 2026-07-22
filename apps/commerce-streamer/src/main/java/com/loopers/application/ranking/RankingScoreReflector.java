package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingKey;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/**
 * 배치 트랜잭션이 커밋된 뒤에만 누적 점수 델타를 랭킹판에 ZINCRBY 로 반영한다(guide 결정 #4).
 * TX 안에서 쓰면 배치 롤백 시 이미 나간 Redis 쓰기가 재배달로 이중가산되므로, 커밋된 것만 반영한다.
 * 도메인이 아니라 여기(응용, 트랜잭션 외곽)에서 Spring 의 커밋 후 훅을 다룬다.
 */
@Component
@RequiredArgsConstructor
public class RankingScoreReflector {

    private final RankingRepository rankingRepository;

    public void reflectAfterCommit(RankingKey key, Map<Long, Double> scoreDeltas) {
        if (scoreDeltas.isEmpty()) {
            return;
        }
        Map<Long, Double> snapshot = Map.copyOf(scoreDeltas);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                snapshot.forEach((productId, delta) -> rankingRepository.incrementScore(key, productId, delta));
            }
        });
    }
}
