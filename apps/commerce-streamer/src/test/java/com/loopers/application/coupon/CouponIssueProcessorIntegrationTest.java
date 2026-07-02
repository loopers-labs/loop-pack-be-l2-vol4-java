package com.loopers.application.coupon;

import com.loopers.interfaces.consumer.CouponIssueMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 선착순 발급 processor 의 결정적 통합 테스트 — 실제 MySQL(Testcontainer)에 JdbcTemplate SQL 을 실행해
 * SUCCESS/SOLD_OUT/ALREADY_ISSUED/FAILED/멱등을 못박는다.
 *
 * <p>coupon 테이블은 commerce-api 소유라 streamer 엔티티가 없어 ddl-auto 가 만들어주지 않는다 → 테스트가 직접
 * DDL 로 생성·초기화한다(shared DB 전제를 테스트에서 재현). Kafka 왕복(api producer → streamer consumer)은
 * 단일 JVM 밖이라 여기서는 processor 를 직접 호출해 발급 로직·SQL 만 검증한다.</p>
 */
@SpringBootTest
class CouponIssueProcessorIntegrationTest {

    private static final LocalDateTime SEED_TIME = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Autowired
    private CouponIssueProcessor couponIssueProcessor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS coupon_templates (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    discount_type VARCHAR(20) NOT NULL,
                    discount_value BIGINT NOT NULL,
                    min_order_amount BIGINT NOT NULL DEFAULT 0,
                    valid_days INT NOT NULL,
                    issue_limit INT NULL,
                    issued_count INT NOT NULL DEFAULT 0,
                    deleted_at DATETIME(6) NULL,
                    created_at DATETIME(6) NOT NULL,
                    updated_at DATETIME(6) NOT NULL
                )""");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS coupon_issue_requests (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    request_id VARCHAR(36) NOT NULL UNIQUE,
                    user_id BIGINT NOT NULL,
                    template_id BIGINT NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    deleted_at DATETIME(6) NULL,
                    created_at DATETIME(6) NOT NULL,
                    updated_at DATETIME(6) NOT NULL
                )""");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_coupons (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    template_id BIGINT NOT NULL,
                    coupon_name VARCHAR(100) NOT NULL,
                    discount_type VARCHAR(20) NOT NULL,
                    discount_value BIGINT NOT NULL,
                    min_order_amount BIGINT NOT NULL DEFAULT 0,
                    status VARCHAR(20) NOT NULL,
                    expires_at DATETIME(6) NOT NULL,
                    used_at DATETIME(6) NULL,
                    order_id BIGINT NULL,
                    version BIGINT NOT NULL DEFAULT 0,
                    deleted_at DATETIME(6) NULL,
                    created_at DATETIME(6) NOT NULL,
                    updated_at DATETIME(6) NOT NULL,
                    CONSTRAINT uk_user_coupons_user_template UNIQUE (user_id, template_id)
                )""");
        jdbcTemplate.update("DELETE FROM user_coupons");
        jdbcTemplate.update("DELETE FROM coupon_issue_requests");
        jdbcTemplate.update("DELETE FROM coupon_templates");
    }

    @DisplayName("한도가 남아 있으면 발급하고 SUCCESS 로 전이한다.")
    @Test
    void success_whenSlotAvailable() {
        long templateId = seedLimitedTemplate(2);
        seedRequest("req-A", 100L, templateId);

        couponIssueProcessor.handle(message("req-A", 100L, templateId));

        assertThat(statusOf("req-A")).isEqualTo("SUCCESS");
        assertThat(issuedCountOf(templateId)).isEqualTo(1);
        assertThat(userCouponCount(100L, templateId)).isEqualTo(1);
    }

    @DisplayName("한도 소진 후의 요청은 SOLD_OUT 이고 발급/카운터 증가가 없다.")
    @Test
    void soldOut_whenLimitReached() {
        long templateId = seedLimitedTemplate(1);
        seedRequest("req-A", 100L, templateId);
        seedRequest("req-B", 200L, templateId);

        couponIssueProcessor.handle(message("req-A", 100L, templateId));
        couponIssueProcessor.handle(message("req-B", 200L, templateId));

        assertThat(statusOf("req-B")).isEqualTo("SOLD_OUT");
        assertThat(issuedCountOf(templateId)).isEqualTo(1);
        assertThat(userCouponCount(200L, templateId)).isZero();
    }

    @DisplayName("이미 발급받은 유저의 재요청은 ALREADY_ISSUED 이고 슬롯을 소모하지 않는다.")
    @Test
    void alreadyIssued_whenAlreadyHasCoupon() {
        long templateId = seedLimitedTemplate(5);
        seedRequest("req-A", 100L, templateId);
        seedRequest("req-A2", 100L, templateId);

        couponIssueProcessor.handle(message("req-A", 100L, templateId));
        couponIssueProcessor.handle(message("req-A2", 100L, templateId));

        assertThat(statusOf("req-A2")).isEqualTo("ALREADY_ISSUED");
        assertThat(issuedCountOf(templateId)).as("중복은 슬롯 미소모").isEqualTo(1);
    }

    @DisplayName("템플릿이 존재하지 않으면(결정적 실패) FAILED 로 전이한다.")
    @Test
    void failed_whenTemplateMissing() {
        long missingTemplateId = 999_999L;
        seedRequest("req-X", 100L, missingTemplateId);

        couponIssueProcessor.handle(message("req-X", 100L, missingTemplateId));

        assertThat(statusOf("req-X")).isEqualTo("FAILED");
        assertThat(userCouponCount(100L, missingTemplateId)).isZero();
    }

    @DisplayName("이미 처리된 요청을 재전달해도(멱등) 두 번 발급하지 않는다.")
    @Test
    void idempotent_whenRedelivered() {
        long templateId = seedLimitedTemplate(5);
        seedRequest("req-A", 100L, templateId);
        CouponIssueMessage message = message("req-A", 100L, templateId);

        couponIssueProcessor.handle(message);
        couponIssueProcessor.handle(message);

        assertThat(statusOf("req-A")).isEqualTo("SUCCESS");
        assertThat(issuedCountOf(templateId)).as("재전달로 카운터가 두 번 증가하면 안 된다").isEqualTo(1);
        assertThat(userCouponCount(100L, templateId)).isEqualTo(1);
    }

    private long seedLimitedTemplate(int issueLimit) {
        jdbcTemplate.update(
                "INSERT INTO coupon_templates "
                        + "(name, discount_type, discount_value, min_order_amount, valid_days, issue_limit, issued_count, created_at, updated_at) "
                        + "VALUES ('선착순', 'FIXED', 1000, 0, 30, ?, 0, ?, ?)",
                issueLimit, SEED_TIME, SEED_TIME);
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id;
    }

    private void seedRequest(String requestId, long userId, long templateId) {
        jdbcTemplate.update(
                "INSERT INTO coupon_issue_requests (request_id, user_id, template_id, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'PENDING', ?, ?)",
                requestId, userId, templateId, SEED_TIME, SEED_TIME);
    }

    private CouponIssueMessage message(String requestId, long userId, long templateId) {
        return new CouponIssueMessage(requestId, userId, templateId,
                ZonedDateTime.now(ZoneOffset.UTC));
    }

    private String statusOf(String requestId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM coupon_issue_requests WHERE request_id = ?", String.class, requestId);
    }

    private int issuedCountOf(long templateId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT issued_count FROM coupon_templates WHERE id = ?", Integer.class, templateId);
        return count == null ? 0 : count;
    }

    private int userCouponCount(long userId, long templateId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_coupons WHERE user_id = ? AND template_id = ?",
                Integer.class, userId, templateId);
        return count == null ? 0 : count;
    }
}
