package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_coupons", uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "template_id"}))
public class UserCouponModel extends BaseEntity {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Version
    @Column(name = "version")
    private int version;

    protected UserCouponModel() {}

    public UserCouponModel(Long memberId, Long templateId) {
        if (memberId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 필수입니다.");
        }
        if (templateId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿 ID는 필수입니다.");
        }
        this.memberId = memberId;
        this.templateId = templateId;
    }

    public void use() {
        if (this.usedAt != null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 사용된 쿠폰입니다.");
        }
        this.usedAt = LocalDateTime.now();
    }

    public CouponStatus getStatus(LocalDateTime expiredAt, boolean templateBlocked) {
        if (templateBlocked) {
            return CouponStatus.BLOCKED;
        }
        if (expiredAt.isBefore(LocalDateTime.now())) {
            return CouponStatus.EXPIRED;
        }
        if (this.usedAt != null) {
            return CouponStatus.USED;
        }
        return CouponStatus.AVAILABLE;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

}
