package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 선착순 쿠폰의 발급 재고. 쿠폰 "정의"(CouponModel)와 분리해 재고(핫 카운터)를 격리한다.
 * <p>
 * 이 모델은 {@code issued <= quota} 불변식의 "집"이다. 하지만 동시 발급 상황에서의
 * 초과 방지는 이 메모리 증가가 아니라 커밋 3의 원자적 조건부 UPDATE
 * ({@code UPDATE ... SET issued = issued + 1 WHERE issued < quota})가 책임진다.
 */
@Entity
@Table(name = "coupon_stock")
public class CouponStockModel extends BaseEntity {

    @Column(name = "coupon_id", nullable = false, unique = true)
    private Long couponId;

    @Column(nullable = false)
    private int quota;

    @Column(nullable = false)
    private int issued;

    protected CouponStockModel() {}

    public CouponStockModel(Long couponId, int quota) {
        this.couponId = couponId;
        this.quota = quota;
        this.issued = 0;
        guard();
    }

    @Override
    protected void guard() {
        if (couponId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰은 필수입니다.");
        }
        if (quota <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "발급 수량은 0보다 커야 합니다.");
        }
        if (issued < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "발급 수는 0 이상이어야 합니다.");
        }
        if (issued > quota) {
            throw new CoreException(ErrorType.CONFLICT, "발급 수가 수량을 초과했습니다.");
        }
    }

    public boolean isSoldOut() {
        return issued >= quota;
    }

    /**
     * 한 장 발급한다 (도메인 규칙 표현: 소진 상태면 발급 불가, 아니면 issued + 1).
     *
     * TODO(human): 아래 규칙대로 이 메서드 본문을 채워주세요.
     *   1) 이미 소진(isSoldOut())이면 발급하면 안 됩니다.
     *      -> throw new CoreException(ErrorType.CONFLICT, "쿠폰이 모두 소진되었습니다.");
     *   2) 아니면 issued 를 1 증가시킵니다.
     *
     *   힌트: 위에서 만든 isSoldOut() 을 그대로 쓰면 됩니다.
     *   생각해볼 점: 이 메서드는 "혼자" 호출될 때만 안전합니다. 두 스레드가 동시에
     *   isSoldOut()==false 를 통과하면 quota+1 이 될 수 있죠 (우리가 계속 얘기한 그 race).
     *   그래서 동시 발급 경로에서는 이 메서드가 아니라 커밋 3의 조건부 UPDATE 를 씁니다.
     *   이 메서드는 규칙의 "명세"이자 단위 테스트의 검증 대상입니다.
     */
    public void issue() {
        if (isSoldOut()) {
            throw new CoreException(ErrorType.CONFLICT, "쿠폰이 모두 소진되었습니다.");
        }
        issued++;
    }

    public Long getCouponId() {
        return couponId;
    }

    public int getQuota() {
        return quota;
    }

    public int getIssued() {
        return issued;
    }
}
