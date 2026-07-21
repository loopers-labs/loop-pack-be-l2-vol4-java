package com.loopers.application.queue;

import com.loopers.domain.queue.QueueRedisStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 대기열 입장 스케줄러 — 매 틱마다 게이트 대상 상품별로 대기열 앞에서부터 배치 크기만큼 꺼내 토큰을 발급한다.
 *
 * <p><strong>재고 판정은 하지 않는다</strong> — 순서(FIFO)만 보장하고, 실제로 살 수 있는지는
 * 다운스트림({@code OrderTransactionService}의 견적 단계, {@code OrderTransactionService#bindResources}의
 * 원자적 재고 차감)이 그대로 판정한다. 이 스케줄러가 재고보다 많은 사람을 입장시켜도 초과 판매는
 * 발생하지 않고, 뒤늦게 들어온 사용자가 {@code StockShortageException} 으로 안내받을 뿐이다.
 * 재고를 매 틱 조회해 발급 상한을 계산하는 방식(오버부킹 마진 추정 포함)도 검토했지만, 그 조회
 * 비용/복잡도에 비해 얻는 이득(헛수고 방지)이 크지 않다고 판단해 여기서는 순서 보장만 맡긴다.
 *
 * <p><strong>알려진 한계</strong>: {@code @Scheduled} 는 다중 인스턴스 환경에서 중복 실행된다.
 * 실서비스에서는 ShedLock 같은 분산 락이 필요하지만 과제 범위(단일 인스턴스)에서는 다루지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class QueueAdmissionScheduler {

    private final QueueRedisStore queueRedisStore;
    private final QueueProperties queueProperties;

    @Scheduled(fixedRate = 100)
    public void admit() {
        for (Long productId : queueProperties.targetProductIds()) {
            try {
                admitOne(productId);
            } catch (Exception e) {
                log.warn("[QueueAdmission] 처리 실패 — productId={}", productId, e);
            }
        }
    }

    private void admitOne(Long productId) {
        List<Long> admittedUserIds = queueRedisStore.admitBatch(productId, queueProperties.batchSize());
        if (admittedUserIds.isEmpty()) {
            return;
        }

        Duration ttl = Duration.ofSeconds(queueProperties.tokenTtlSeconds());
        for (Long userId : admittedUserIds) {
            queueRedisStore.issueToken(productId, userId, ttl);
        }
        log.info("[QueueAdmission] 입장 처리 완료. productId={}, admitted={}", productId, admittedUserIds.size());
    }
}
