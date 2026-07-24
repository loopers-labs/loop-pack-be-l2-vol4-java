package com.loopers.application.metrics;

import com.loopers.domain.metrics.EventHandledModel;
import com.loopers.infrastructure.metrics.EventHandledJpaRepository;
import com.loopers.infrastructure.metrics.ProductMetricJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Kafka로 소비한 이벤트를 product_metrics에 멱등하게 반영한다.
 * <p>
 * 각 이벤트는 event_id로 한 번만 반영된다(at-least-once 중복 제거). 처리 단위는 이벤트 1건 = 트랜잭션 1개라,
 * 배치 도중 실패해도 이미 커밋된 건은 재전송 시 event_handled로 걸러진다.
 */
@RequiredArgsConstructor
@Service
public class ProductMetricsService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ProductMetricJpaRepository productMetricJpaRepository;
    private final EventHandledJpaRepository eventHandledJpaRepository;

    /** 좋아요/취소 반영. likeDelta = +1(LIKED) / -1(UNLIKED). */
    @Transactional
    public void applyLike(String eventId, Long productId, long likeDelta) {
        if (alreadyHandled(eventId)) {
            return;
        }
        productMetricJpaRepository.upsert(today(), productId, likeDelta, 0L);
        eventHandledJpaRepository.save(new EventHandledModel(eventId));
    }

    /** 주문 완료 반영. 상품 라인마다 판매량을 더한다. */
    @Transactional
    public void applySales(String eventId, List<SalesLine> lines) {
        if (alreadyHandled(eventId)) {
            return;
        }
        LocalDate statDate = today();
        for (SalesLine line : lines) {
            productMetricJpaRepository.upsert(statDate, line.productId(), 0L, line.quantity());
        }
        eventHandledJpaRepository.save(new EventHandledModel(eventId));
    }

    /** 집계일자 = 이벤트를 소비한 시점의 KST 날짜. */
    private LocalDate today() {
        return LocalDate.now(KST);
    }

    private boolean alreadyHandled(String eventId) {
        return eventHandledJpaRepository.existsById(eventId);
    }

    public record SalesLine(Long productId, long quantity) {
    }
}
