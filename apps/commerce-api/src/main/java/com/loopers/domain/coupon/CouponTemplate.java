package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.ZonedDateTime;

/**
 * 쿠폰 템플릿 — 어드민이 정의하는 쿠폰의 원형이자, 선착순 발급의 자원 풀(한도/발급수)이다.
 * 발급 시 {@link UserCoupon} 이 이 템플릿의 할인 정책·이름·만료일을 복사(스냅샷)하므로,
 * 이후 템플릿이 수정·삭제돼도 이미 발급된 쿠폰의 가치는 변하지 않는다.
 *
 * <p><b>선착순 한도</b>: {@code issueLimit} 이 있으면(=한정) 발급은 async 발급요청 경로로만 진행된다({@code null} 이면
 * 무제한 — 기존 동기 발급 경로). {@link #isLimited()} 가 이 경로 판정에 쓰인다. 실제 슬롯 확보({@code issued_count <
 * issue_limit} 강제)는 발급 소비자(commerce-streamer)가 <b>조건부 원자 UPDATE</b>로 수행하므로 이 엔티티에는 증가
 * 메서드를 두지 않는다(한도 규칙이 소비자 SQL 에 있음).</p>
 *
 * <p><b>{@code @DynamicUpdate}</b>: 어드민의 정의 수정(name 등)과 소비자의 카운터 증가(issued_count)가 서로 다른
 * 컬럼만 UPDATE 하도록 해, 두 쓰기가 교차해도 서로의 컬럼을 덮어쓰지 않게 한다(cross-column lost update 방지).</p>
 */
@Getter
@Entity
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "coupon_templates")
public class CouponTemplate extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Embedded
    private DiscountPolicy discountPolicy;

    @Column(name = "valid_days", nullable = false)
    private int validDays;

    /** 선착순 발급 한도. {@code null} = 무제한(동기 발급 경로). */
    @Column(name = "issue_limit")
    private Integer issueLimit;

    /** 지금까지 발급된 수. 발급 소비자(commerce-streamer)가 조건부 원자 UPDATE로 증가시킨다. */
    @Column(name = "issued_count", nullable = false)
    private int issuedCount;

    private CouponTemplate(String name, DiscountPolicy discountPolicy, int validDays, Integer issueLimit) {
        validateName(name);
        validateDiscountPolicy(discountPolicy);
        validateValidDays(validDays);
        validateIssueLimit(issueLimit);
        this.name = name;
        this.discountPolicy = discountPolicy;
        this.validDays = validDays;
        this.issueLimit = issueLimit;
        this.issuedCount = 0;
    }

    /** 무제한 템플릿을 만든다(동기 발급 경로). */
    public static CouponTemplate create(String name, DiscountPolicy discountPolicy, int validDays) {
        return new CouponTemplate(name, discountPolicy, validDays, null);
    }

    /** 선착순 한정 템플릿을 만든다(async 발급요청 경로). */
    public static CouponTemplate create(String name, DiscountPolicy discountPolicy, int validDays, Integer issueLimit) {
        return new CouponTemplate(name, discountPolicy, validDays, issueLimit);
    }

    /** 선착순 한도가 걸린 템플릿인지 — 동기 발급 경로가 이 템플릿을 거부하는 판정에도 쓰인다. */
    public boolean isLimited() {
        return issueLimit != null;
    }

    public void modify(String name, DiscountPolicy discountPolicy, int validDays) {
        validateName(name);
        validateDiscountPolicy(discountPolicy);
        validateValidDays(validDays);
        this.name = name;
        this.discountPolicy = discountPolicy;
        this.validDays = validDays;
    }

    /**
     * 발급 시각 기준으로 발급될 쿠폰의 만료 시각을 계산한다(발급일 + 유효일수).
     */
    public ZonedDateTime issueExpiresAt(ZonedDateTime issuedAt) {
        if (issuedAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "발급 시각은 비어있을 수 없습니다.");
        }
        return issuedAt.plusDays(validDays);
    }

    public boolean isDeleted() {
        return getDeletedAt() != null;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 비어있을 수 없습니다.");
        }
    }

    private void validateDiscountPolicy(DiscountPolicy discountPolicy) {
        if (discountPolicy == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 정책은 비어있을 수 없습니다.");
        }
    }

    private void validateValidDays(int validDays) {
        if (validDays < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유효일수는 1 이상이어야 합니다.");
        }
    }

    private void validateIssueLimit(Integer issueLimit) {
        if (issueLimit != null && issueLimit < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "발급 한도는 1 이상이어야 합니다.");
        }
    }
}
