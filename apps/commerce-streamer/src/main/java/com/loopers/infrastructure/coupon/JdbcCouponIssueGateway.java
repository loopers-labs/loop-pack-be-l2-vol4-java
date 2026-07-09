package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * api 소유 테이블을 JdbcTemplate SQL 로 직접 다룬다. 호출자(@Transactional)의 트랜잭션에 참여한다.
 * 수량 차감은 원자 조건부 UPDATE 라, 동기 발급 경로 등 다른 writer 와 동시에도 초과 차감이 없다.
 */
@Component
@RequiredArgsConstructor
public class JdbcCouponIssueGateway implements CouponIssueGateway {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean alreadyIssued(Long userId, Long couponId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_coupons WHERE user_id = ? AND coupon_id = ? AND deleted_at IS NULL",
                Integer.class, userId, couponId
        );
        return count != null && count > 0;
    }

    @Override
    public boolean decreaseQuantity(Long couponId) {
        int updated = jdbcTemplate.update(
                "UPDATE coupons SET quantity = quantity - 1 WHERE id = ? AND quantity > 0",
                couponId
        );
        return updated > 0;
    }

    @Override
    public void insertUserCoupon(Long userId, Long couponId) {
        jdbcTemplate.update(
                "INSERT INTO user_coupons (user_id, coupon_id, used, created_at, updated_at) VALUES (?, ?, false, NOW(), NOW())",
                userId, couponId
        );
    }

    @Override
    public void markIssued(String requestId) {
        jdbcTemplate.update(
                "UPDATE coupon_issue_request SET status = 'ISSUED', updated_at = NOW() WHERE request_id = ?",
                requestId
        );
    }

    @Override
    public void markRejected(String requestId, String reason) {
        jdbcTemplate.update(
                "UPDATE coupon_issue_request SET status = 'REJECTED', reason = ?, updated_at = NOW() WHERE request_id = ?",
                reason, requestId
        );
    }
}