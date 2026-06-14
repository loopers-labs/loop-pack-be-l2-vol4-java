package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

public interface CouponRepository {
    Coupon save(Coupon coupon);
    Optional<Coupon> findById(Long id);          // 활성(미삭제) 템플릿만 — 발급/상세
    List<Coupon> findAllById(List<Long> ids);    // 전체(삭제 포함) — 이미 발급된 쿠폰 해석용
    List<Coupon> findAll(int page, int size);    // admin 목록 — 활성만
    long count();                                 // admin 목록 카운트 — 활성만
}
