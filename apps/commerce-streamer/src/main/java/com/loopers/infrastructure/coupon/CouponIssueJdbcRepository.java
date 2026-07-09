package com.loopers.infrastructure.coupon;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * 선착순 발급 소비자가 쿠폰 테이블에 직접 쓰는 JdbcTemplate 리포지토리.
 *
 * <p>쿠폰 도메인(불변식·엔티티)의 소유자는 여전히 {@code commerce-api} 다. streamer 는 그 도메인을 복제하지 않고,
 * 발급 유스케이스가 필요로 하는 <b>최소 쿼리만</b> 같은 MySQL(shared DB)에 실행한다. 대신 발급 규칙(스냅샷 복사·만료
 * 계산·한도 검사)이 여기 SQL 로 재표현되므로, api 의 {@code UserCoupon.issue()}/{@code CouponTemplate.issueOne()}
 * 이 바뀌면 이 SQL 도 함께 맞춰야 한다(컴파일러가 잡아주지 않는 드리프트 지점).</p>
 *
 * <p><b>타임존</b>: api 는 Hibernate {@code timezone.default_storage=NORMALIZE_UTC} / {@code jdbc.time_zone=UTC} 로
 * timestamp 를 UTC wall-clock 으로 저장·조회한다. JdbcTemplate 쓰기는 이 설정을 타지 않으므로, 모든 시각을
 * {@link #utc(ZonedDateTime)} 로 UTC 로 변환해 바인딩한다(그래야 주문 흐름의 만료 판정 등 api JPA 조회와 어긋나지 않는다).</p>
 */
@Repository
public class CouponIssueJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public CouponIssueJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 발급 요청 조회 — 처리 대상(userId·templateId)과 멱등 판정(status)의 진실 원본. */
    public Optional<RequestRow> findRequest(String requestId) {
        try {
            RequestRow row = jdbcTemplate.queryForObject(
                    "SELECT user_id, template_id, status FROM coupon_issue_requests WHERE request_id = ?",
                    (rs, n) -> new RequestRow(rs.getLong("user_id"), rs.getLong("template_id"), rs.getString("status")),
                    requestId);
            return Optional.ofNullable(row);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /** 1인 1매 — 이미 발급받은 유저인지. */
    public boolean existsUserCoupon(Long userId, Long templateId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_coupons WHERE user_id = ? AND template_id = ?",
                Integer.class, userId, templateId);
        return count != null && count > 0;
    }

    /** 삭제되지 않은 템플릿 조회(스냅샷 원본). 한도 없음/삭제됨 판정은 호출부가 한다. */
    public Optional<TemplateRow> findTemplate(Long templateId) {
        try {
            TemplateRow row = jdbcTemplate.queryForObject(
                    "SELECT id, name, discount_type, discount_value, min_order_amount, valid_days, issue_limit "
                            + "FROM coupon_templates WHERE id = ? AND deleted_at IS NULL",
                    (rs, n) -> {
                        int issueLimit = rs.getInt("issue_limit");
                        boolean limited = !rs.wasNull();
                        return new TemplateRow(
                                rs.getLong("id"),
                                rs.getString("name"),
                                rs.getString("discount_type"),
                                rs.getLong("discount_value"),
                                rs.getLong("min_order_amount"),
                                rs.getInt("valid_days"),
                                limited);
                    },
                    templateId);
            return Optional.ofNullable(row);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * 발급 슬롯을 원자적으로 확보한다 — {@code issued_count < issue_limit} 일 때만 카운터를 1 증가시킨다.
     * 조건부 UPDATE 라 파티션 직렬화가 없어도 초과 발급이 불가능하다(재고 원자 UPDATE 와 같은 패턴).
     *
     * @return 슬롯을 확보(영향 행 1)했으면 {@code true}, 한도 소진(영향 행 0)이면 {@code false}
     */
    public boolean tryConsumeSlot(Long templateId, ZonedDateTime now) {
        int affected = jdbcTemplate.update(
                "UPDATE coupon_templates SET issued_count = issued_count + 1, updated_at = ? "
                        + "WHERE id = ? AND deleted_at IS NULL AND issue_limit IS NOT NULL AND issued_count < issue_limit",
                utc(now), templateId);
        return affected == 1;
    }

    /** 발급 — 템플릿의 혜택·이름을 복사(스냅샷)하고 만료일(발급시각 + 유효일수)을 확정해 내 쿠폰을 INSERT 한다. */
    public void insertUserCoupon(Long userId, TemplateRow template, ZonedDateTime now) {
        LocalDateTime nowUtc = utc(now);
        LocalDateTime expiresAt = utc(now.plusDays(template.validDays()));
        jdbcTemplate.update(
                "INSERT INTO user_coupons "
                        + "(user_id, template_id, coupon_name, discount_type, discount_value, min_order_amount, "
                        + " status, expires_at, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'AVAILABLE', ?, 0, ?, ?)",
                userId, template.id(), template.name(), template.discountType(),
                template.discountValue(), template.minOrderAmount(), expiresAt, nowUtc, nowUtc);
    }

    /** 요청 상태 전이(터미널 확정). */
    public void updateStatus(String requestId, String status, ZonedDateTime now) {
        jdbcTemplate.update(
                "UPDATE coupon_issue_requests SET status = ?, updated_at = ? WHERE request_id = ?",
                status, utc(now), requestId);
    }

    /** api(Hibernate)가 UTC wall-clock 으로 저장하므로, JdbcTemplate 바인딩도 UTC LocalDateTime 으로 맞춘다. */
    private static LocalDateTime utc(ZonedDateTime time) {
        return time.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    public record RequestRow(Long userId, Long templateId, String status) {
    }

    public record TemplateRow(Long id, String name, String discountType, long discountValue,
                              long minOrderAmount, int validDays, boolean limited) {
    }
}
