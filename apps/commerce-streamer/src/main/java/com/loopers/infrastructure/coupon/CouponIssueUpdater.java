package com.loopers.infrastructure.coupon;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 선착순 쿠폰 발급의 DB 원자 연산 (Slice 4, Step3). commerce-streamer는 commerce-api의 JPA 엔티티를 공유하지 않으므로
 * 공유 DB 테이블(coupon / user_coupon / coupon_issue_request)에 직접 SQL로 쓴다(product_metrics와 동일한 2-writer 패턴).
 *
 * <p>선착순 불변식 {@code issued_count <= total_quantity}는 {@link #reserve}의 조건부 UPDATE가 보장한다 —
 * 어떤 동시성에서도 영향 행 합이 total_quantity를 넘지 않는다(행 X-lock으로 직렬화). key=couponId 파티셔닝으로
 * 같은 쿠폰 요청이 같은 파티션/컨슈머 스레드에 직렬화되어 경합 범위도 좁다.
 */
@Component
@RequiredArgsConstructor
public class CouponIssueUpdater {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 수량을 원자적으로 1 확보한다. 남은 수량이 있으면 1(발급 가능), 소진됐으면 0(SOLD_OUT)을 반환한다.
     */
    public int reserve(long couponId) {
        return jdbcTemplate.update(
                "UPDATE coupon SET issued_count = issued_count + 1, updated_at = now() "
                        + "WHERE id = ? AND issued_count < total_quantity",
                couponId);
    }

    /** 발급분 user_coupon 행을 INSERT한다(id는 auto-increment, version은 낙관적 락 초기값 0). */
    public void insertUserCoupon(long userId, long couponId) {
        jdbcTemplate.update(
                "INSERT INTO user_coupon (user_id, coupon_id, issued_at, version, created_at, updated_at) "
                        + "VALUES (?, ?, now(), 0, now(), now())",
                userId, couponId);
    }

    /** 요청 상태를 확정한다(ISSUED / SOLD_OUT). */
    public void markRequest(long requestId, String status) {
        jdbcTemplate.update(
                "UPDATE coupon_issue_request SET status = ?, processed_at = now(), updated_at = now() WHERE id = ?",
                status, requestId);
    }
}
