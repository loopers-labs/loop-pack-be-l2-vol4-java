package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Getter
@Entity
@Table(name = "coupon_template")
@SQLRestriction("deleted_at IS NULL")
public class CouponTemplateModel extends BaseEntity {

    private String name;

    @Embedded
    private DiscountPolicy discountPolicy;

    private BigDecimal minOrderAmount;

    private ZonedDateTime expiredAt;

    protected CouponTemplateModel() {
    }

    public CouponTemplateModel(String name, CouponType type, BigDecimal value, BigDecimal minOrderAmount, ZonedDateTime expiredAt) {
        validate(name, expiredAt);
        this.name = name;
        this.discountPolicy = new DiscountPolicy(type, value);
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public void update(String name, CouponType type, BigDecimal value, BigDecimal minOrderAmount, ZonedDateTime expiredAt) {
        validate(name, expiredAt);
        this.name = name;
        this.discountPolicy = new DiscountPolicy(type, value);
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    private void validate(String name, ZonedDateTime expiredAt) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 이름은 비어있을 수 없습니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일은 필수입니다.");
        }
    }

    public boolean isExpired() {
        return ZonedDateTime.now().isAfter(expiredAt);
    }

}
