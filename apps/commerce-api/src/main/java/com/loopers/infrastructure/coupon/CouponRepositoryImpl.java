package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponJpaRepository couponJpaRepository;

    /**
     * 순수 도메인 ↔ JPA 엔티티 경계.
     * - 신규(id == null): INSERT.
     * - 기존(id != null): managed 엔티티를 로드해 가변 상태만 복사 → dirty checking UPDATE.
     *   soft delete 상태(deletedAt)는 도메인 기준으로 delete()/restore() 동기화(둘 다 멱등).
     */
    @Override
    public CouponModel save(CouponModel coupon) {
        if (coupon.getId() == null) {
            CouponEntity saved = couponJpaRepository.save(CouponEntityMapper.toEntity(coupon));
            return CouponEntityMapper.toDomain(saved);
        }
        CouponEntity entity = couponJpaRepository.findById(coupon.getId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + coupon.getId() + "] 쿠폰을 찾을 수 없습니다."));
        entity.applyState(coupon.getName(), coupon.getValue(), coupon.getMinOrderAmount(), coupon.getExpiredAt());
        if (coupon.isActive()) {
            entity.restore();
        } else {
            entity.delete();
        }
        return CouponEntityMapper.toDomain(couponJpaRepository.save(entity));
    }

    @Override
    public Optional<CouponModel> find(Long id) {
        return couponJpaRepository.findById(id).map(CouponEntityMapper::toDomain);
    }

    @Override
    public List<CouponModel> findAll(int page, int size) {
        return couponJpaRepository.findAllByOrderByIdDesc(PageRequest.of(page, size)).stream()
                .map(CouponEntityMapper::toDomain)
                .toList();
    }

    @Override
    public List<CouponModel> findByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return couponJpaRepository.findAllById(ids).stream()
                .map(CouponEntityMapper::toDomain)
                .toList();
    }
}
