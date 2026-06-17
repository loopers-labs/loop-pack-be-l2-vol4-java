package com.loopers.interfaces.consumer;

import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.infrastructure.product.ProductLikesUpdater;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 좋아요 변경 이벤트를 배치로 모아 상품별 델타를 합산(coalescing)한 뒤 한 번의 UPDATE로 반영한다.
 *
 * <p><b>hot row 해소의 핵심</b>: 같은 상품에 대한 +1/-1 N건이 이 컨슈머 메모리에서 하나의 델타로 합쳐져,
 * DB {@code UPDATE product SET likes_count = likes_count + Δ} 가 상품당 1회로 줄어든다. 행 잠금 획득이
 * N→1이 되어 인기 상품의 카운터 경합이 사라진다. (+1/-1은 교환법칙이 성립해 배치 내 순서 무관)
 *
 * <p>수동 커밋(at-least-once): UPDATE 성공 후에만 ack. 장애로 같은 배치가 재처리되면 델타가 이중 반영될 수
 * 있으나, reconcile 스케줄러가 진실원천(product_like COUNT)으로 주기 교정하므로 누적 오차는 수렴한다.
 */
@Component
@RequiredArgsConstructor
public class LikeCountConsumer {

    private static final Logger log = LoggerFactory.getLogger(LikeCountConsumer.class);

    private final ProductLikesUpdater productLikesUpdater;

    @KafkaListener(
            topics = "${like-events.topic}",
            groupId = "like-count-aggregator",
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<LikeChangedMessage> messages, Acknowledgment acknowledgment) {
        Map<Long, Long> deltaByProduct = messages.stream()
                .filter(m -> m.productId() != null)
                .collect(Collectors.groupingBy(
                        LikeChangedMessage::productId,
                        Collectors.summingLong(LikeChangedMessage::delta)));

        productLikesUpdater.applyDeltas(deltaByProduct);
        acknowledgment.acknowledge();

        log.debug("좋아요 배치 집계: events={}, products={}", messages.size(), deltaByProduct.size());
    }
}
