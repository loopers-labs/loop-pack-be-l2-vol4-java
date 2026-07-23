package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingWeight;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;

/**
 * 이벤트 유형별 랭킹 점수를 오늘의 랭킹판에 반영한다 — 반드시 DB 트랜잭션 커밋 후(afterCommit)에만.
 * <p>
 * ZINCRBY는 멱등이 아니므로 트랜잭션 안에서 반영하면 커밋 실패 후 Kafka 재시도 시 같은 이벤트가 중복 가산된다.
 * 커밋 후에는 event_handled가 재시도를 skip시켜 중복 가산이 없고, Redis 반영 실패는
 * 점수 유실로 수용한다(랭킹은 근사 지표) — 로그만 남기고 메시지 처리는 성공으로 간주한다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class RankingScoreUpdater {

    private final RankingRepository rankingRepository;

    public void onProductViewed(Long productId) {
        accumulateAfterCommit(productId, RankingWeight.view());
    }

    public void onProductLiked(Long productId) {
        accumulateAfterCommit(productId, RankingWeight.like());
    }

    public void onProductUnliked(Long productId) {
        accumulateAfterCommit(productId, RankingWeight.unlike());
    }

    public void onOrderPaid(Long productId, int unitPrice, int quantity) {
        accumulateAfterCommit(productId, RankingWeight.order(unitPrice, quantity));
    }

    private void accumulateAfterCommit(Long productId, double delta) {
        if (delta == 0.0) {
            // price 필드가 없던 구버전 주문 이벤트는 0으로 파싱된다 — 0점 멤버로 랭킹판을 오염시키지 않도록 스킵
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    accumulate(productId, delta);
                }
            });
        } else {
            accumulate(productId, delta);
        }
    }

    private void accumulate(Long productId, double delta) {
        try {
            rankingRepository.incrementScore(LocalDate.now(), productId, delta);
        } catch (Exception e) {
            log.warn("랭킹 점수 반영 실패 — 유실 허용. productId={}, delta={}", productId, delta, e);
        }
    }
}
