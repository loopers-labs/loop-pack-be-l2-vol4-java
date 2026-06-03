package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private static final PageRequest FIRST_ONE = PageRequest.of(0, 1);

    private final UserCouponJpaRepository userCouponJpaRepository;

    /**
     * 순수 도메인 ↔ JPA 엔티티 경계.
     * - 신규(id == null): INSERT.
     * - 기존(id != null): managed 엔티티를 로드해 usedAt만 복사 → dirty checking UPDATE.
     *   같은 트랜잭션에서 findFirstAvailable로 이미 로드된 엔티티는 1차 캐시에서 반환되어
     *   최초 읽은 version이 유지된다 → @Version UPDATE의 WHERE version 절로 동시성 보장(UC-20 §5-A).
     */
    @Override
    public UserCouponModel save(UserCouponModel userCoupon) {
        if (userCoupon.getId() == null) {
            UserCouponEntity saved = userCouponJpaRepository.save(UserCouponEntityMapper.toEntity(userCoupon));
            return UserCouponEntityMapper.toDomain(saved);
        }
        UserCouponEntity entity = userCouponJpaRepository.findById(userCoupon.getId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + userCoupon.getId() + "] 발급 쿠폰을 찾을 수 없습니다."));
        entity.applyState(userCoupon.getUsedAt());
        return UserCouponEntityMapper.toDomain(userCouponJpaRepository.save(entity));
    }

    @Override
    public Optional<UserCouponModel> find(Long id) {
        return userCouponJpaRepository.findById(id).map(UserCouponEntityMapper::toDomain);
    }

    @Override
    public List<UserCouponModel> findByUserId(Long userId, int page, int size) {
        return userCouponJpaRepository.findByUserIdOrderByIdDesc(userId, PageRequest.of(page, size)).stream()
                .map(UserCouponEntityMapper::toDomain)
                .toList();
    }

    @Override
    public List<UserCouponModel> findByCouponId(Long couponId, int page, int size) {
        return userCouponJpaRepository.findByCouponIdOrderByIdDesc(couponId, PageRequest.of(page, size)).stream()
                .map(UserCouponEntityMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<UserCouponModel> findFirstAvailable(Long userId, Long couponId) {
        return userCouponJpaRepository.findAvailable(userId, couponId, FIRST_ONE).stream()
                .findFirst()
                .map(UserCouponEntityMapper::toDomain);
    }

    @Override
    public Optional<UserCouponModel> findFirstAvailableForUpdate(Long userId, Long couponId) {
        return userCouponJpaRepository.findAvailableForUpdate(userId, couponId, FIRST_ONE).stream()
                .findFirst()
                .map(UserCouponEntityMapper::toDomain);
    }
}
