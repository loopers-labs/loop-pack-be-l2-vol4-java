package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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

    // 선착순 발급 가능 총 수량. 실제 잔여량 판정(issuedQuantity < totalQuantity)은
    // CouponTemplateRepository의 원자적 UPDATE(increaseIssuedQuantityIfAvailable)가 담당한다.
    private int totalQuantity;

    // 지금까지 발급된 수량. 애플리케이션에서 직접 증가시키지 않고 원자적 UPDATE로만 증가한다.
    private int issuedQuantity;

    // increaseIssuedQuantityIfAvailable(원자적 UPDATE)이 issuedQuantity와 함께 이 값도 올린다.
    // 그래야 관리자가 findById 후 update()/delete()로 save()하는 read-modify-write 경로가
    // 그 사이의 원자적 증가분을 낙관적 락 충돌(CONFLICT)로 감지해 잃어버리지 않는다.
    @Version
    private long version;

    protected CouponTemplateModel() {
    }

    public CouponTemplateModel(String name, CouponType type, BigDecimal value, BigDecimal minOrderAmount,
                                ZonedDateTime expiredAt, int totalQuantity) {
        validate(name, expiredAt, totalQuantity);
        this.name = name;
        this.discountPolicy = new DiscountPolicy(type, value);
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = 0;
    }

    public void update(String name, CouponType type, BigDecimal value, BigDecimal minOrderAmount,
                        ZonedDateTime expiredAt, int totalQuantity) {
        validate(name, expiredAt, totalQuantity);
        if (totalQuantity < this.issuedQuantity) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 발급된 수량보다 적게 설정할 수 없습니다.");
        }
        this.name = name;
        this.discountPolicy = new DiscountPolicy(type, value);
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        this.totalQuantity = totalQuantity;
    }

    private void validate(String name, ZonedDateTime expiredAt, int totalQuantity) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 이름은 비어있을 수 없습니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일은 필수입니다.");
        }
        if (totalQuantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "발급 가능 수량은 1개 이상이어야 합니다.");
        }
    }

    public boolean isExpired() {
        return ZonedDateTime.now().isAfter(expiredAt);
    }

    public void validateApplicability(BigDecimal originalPrice) {
        if (isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
        if (minOrderAmount != null && originalPrice.compareTo(minOrderAmount) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액을 충족하지 않습니다.");
        }
    }

    public BigDecimal calculateDiscountAmount(BigDecimal originalPrice) {
        return discountPolicy.calculateDiscountAmount(originalPrice);
    }

}
