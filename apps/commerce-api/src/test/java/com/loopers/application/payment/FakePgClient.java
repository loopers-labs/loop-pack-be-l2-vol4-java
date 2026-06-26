package com.loopers.application.payment;

import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgRequestException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 테스트용 PG 시뮬레이션:
 * - request: 시나리오(GRANT / REJECT / TIMEOUT) 에 따라 응답
 * - getByTransactionKey / findByOrderId: 등록된 transaction 을 반환
 * <p>
 * Resilience4j 가 동작하는 통합 테스트가 아니라, Facade 의 비즈니스 흐름 검증용.
 */
public class FakePgClient implements PgClient {

    public enum Scenario { GRANT, REJECT, TIMEOUT }

    public Scenario scenario = Scenario.GRANT;
    public final Map<String, PgTransactionView> byKey = new HashMap<>();
    public final Map<Long, List<PgTransactionView>> byOrder = new HashMap<>();
    public final AtomicInteger requestCount = new AtomicInteger(0);
    public final AtomicInteger getCount = new AtomicInteger(0);

    private final AtomicInteger keySeq = new AtomicInteger(0);

    @Override
    public PgRequestResponse request(PgRequestCommand command) {
        requestCount.incrementAndGet();
        switch (scenario) {
            case TIMEOUT:
                throw new PgRequestException("PG 타임아웃 시뮬레이션");
            case REJECT:
                throw new PgRequestException("PG 거절 시뮬레이션");
            case GRANT:
            default:
                String key = "TX-" + keySeq.incrementAndGet();
                PgTransactionView view = new PgTransactionView(
                    key, command.orderId(), PgTransactionStatus.PENDING, null);
                byKey.put(key, view);
                byOrder.computeIfAbsent(command.orderId(), id -> new ArrayList<>()).add(view);
                return new PgRequestResponse(key, PgTransactionStatus.PENDING);
        }
    }

    @Override
    public Optional<PgTransactionView> getByTransactionKey(String transactionKey) {
        getCount.incrementAndGet();
        return Optional.ofNullable(byKey.get(transactionKey));
    }

    @Override
    public List<PgTransactionView> findByOrderId(Long orderId) {
        getCount.incrementAndGet();
        return byOrder.getOrDefault(orderId, List.of());
    }

    /** 테스트에서 PG 측의 최종 상태를 강제로 설정. */
    public void setExternalResult(String transactionKey, PgTransactionStatus status, String reason) {
        PgTransactionView old = byKey.get(transactionKey);
        if (old == null) return;
        PgTransactionView updated = new PgTransactionView(
            old.transactionKey(), old.orderId(), status, reason);
        byKey.put(transactionKey, updated);
        byOrder.computeIfPresent(old.orderId(), (id, list) -> {
            list.replaceAll(v -> v.transactionKey().equals(transactionKey) ? updated : v);
            return list;
        });
    }
}
