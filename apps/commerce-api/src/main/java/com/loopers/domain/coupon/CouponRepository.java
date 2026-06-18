package com.loopers.domain.coupon;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CouponRepository {
    CouponModel save(CouponModel coupon);
    Optional<CouponModel> find(Long id);

    /** Admin 템플릿 목록 — 최신순 페이지 (UC-15). */
    List<CouponModel> findAll(int page, int size);

    /** 여러 템플릿 일괄 조회 — 내 쿠폰 목록의 이름/만료시각 batch 조합용 (UC-14, N+1 회피). */
    List<CouponModel> findByIds(Collection<Long> ids);
}
