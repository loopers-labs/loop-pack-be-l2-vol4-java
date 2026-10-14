package com.loopers.domain.coupon;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class FakeCouponIssueRequestRepository implements CouponIssueRequestRepository {

    private final List<CouponIssueRequest> store = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public CouponIssueRequest save(CouponIssueRequest request) {
        if (request.getId() == null || request.getId() == 0L) {
            assignId(request, sequence.incrementAndGet());
            store.add(request);
        }
        return request;
    }

    @Override
    public Optional<CouponIssueRequest> findByRequestId(String requestId) {
        return store.stream()
            .filter(r -> r.getRequestId().equals(requestId))
            .findFirst();
    }

    private static void assignId(Object entity, Long id) {
        try {
            Class<?> clazz = entity.getClass();
            while (clazz != null && !clazz.getSimpleName().equals("BaseEntity")) {
                clazz = clazz.getSuperclass();
            }
            Field idField = clazz.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException("Fake Repository: ID 주입 실패", e);
        }
    }
}
