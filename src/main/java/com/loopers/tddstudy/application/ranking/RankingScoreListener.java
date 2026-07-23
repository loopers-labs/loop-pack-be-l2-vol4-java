package com.loopers.tddstudy.application.ranking;

import com.loopers.tddstudy.domain.ranking.RankingKey;
import com.loopers.tddstudy.domain.ranking.RankingPolicy;
import com.loopers.tddstudy.domain.ranking.RankingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;

@Component
public class RankingScoreListener {

    private static final Logger log = LoggerFactory.getLogger(RankingScoreListener.class);

    private final RankingRepository rankingRepository;

    public RankingScoreListener(RankingRepository rankingRepository) {
        this.rankingRepository = rankingRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(RankingScoreEvent event) {
        double delta = RankingPolicy.deltaOf(event.eventType());
        if (delta == 0.0) {
            return;   // 랭킹 대상이 아닌 이벤트 타입
        }
        LocalDate date = RankingKey.dateOf(event.occurredAt());
        try {
            rankingRepository.incrementScore(date, event.productId(), delta);
        } catch (Exception e) {
            // AFTER_COMMIT 리스너의 예외는 호출자에게 전파되지 않고 삼켜짐
            // → 최소한 흔적은 남겨야 유실을 인지할 수 있다 (과소 집계 감수 결정의 대가)
            log.error("랭킹 점수 반영 실패: productId={}, type={}, date={}",
                    event.productId(), event.eventType(), date, e);
        }
    }
}
