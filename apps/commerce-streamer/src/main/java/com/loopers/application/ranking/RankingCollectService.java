package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingKeys;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingSignal;
import com.loopers.interfaces.consumer.CatalogEventMessage;
import com.loopers.interfaces.consumer.OrderEventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 이벤트 → raw 신호 델타 번역 (Collect 구간). 가중치는 여기 없다 — 신호를 점수로 해석하는 건
 * Score 구간(합성)의 몫이고, Collect 는 일어난 사실만 기록한다.
 *
 * <p>멱등 장치를 두지 않는다(metrics 의 event_handled 미사용): 재전달 이중 가산은 근사 지표 예산으로
 * 수용하고, 대신 consumer group offset 리셋만으로 보드 재구축이 가능한 성질을 얻는다
 * (dedup 원장은 중복 방지 장치인 동시에 재생 차단 장치다).</p>
 *
 * <p>Redis 예외는 그대로 전파한다 — 전용 consumer group 의 offset 미커밋 재시도가 폴백이다.</p>
 */
@Service
@RequiredArgsConstructor
public class RankingCollectService {

    private final RankingRepository rankingRepository;

    public void collect(CatalogEventMessage message) {
        LocalDate bucket = RankingKeys.bucketOf(message.occurredAt());
        switch (message.type()) {
            case PRODUCT_VIEWED -> rankingRepository.increment(RankingSignal.VIEW, bucket, message.productId(), 1);
            case PRODUCT_LIKED -> rankingRepository.increment(RankingSignal.LIKE, bucket, message.productId(), 1);
            // 취소를 감점하지 않으면 "좋아요→취소 반복" 이 공짜 점수 펌프가 된다. 전일 좋아요의 당일 취소로
            // 생기는 음수 점수는 클램프하지 않는다 — 모든 +1 이 자기 -1 을 만나는 회계의 흔적일 뿐이고,
            // 음수는 최하위로 가라앉아 Top-N 에 나타나지 않는다.
            case PRODUCT_UNLIKED -> rankingRepository.increment(RankingSignal.LIKE, bucket, message.productId(), -1);
        }
    }

    public void collect(OrderEventMessage message) {
        LocalDate bucket = RankingKeys.bucketOf(message.occurredAt());
        switch (message.type()) {
            // 건수(구매 결정 횟수)와 수량은 상호 유도 불가능한 정보라 각자의 보드에 보존한다.
            // 수량을 점수에 얼마나 반영할지는 합성 가중치(W_qty)의 몫 — raw 에서 버리면 소급 복구 불가.
            case PRODUCT_SOLD -> {
                rankingRepository.increment(RankingSignal.ORDER_COUNT, bucket, message.productId(), 1);
                rankingRepository.increment(RankingSignal.ORDER_QTY, bucket, message.productId(), message.quantity());
            }
        }
    }
}
