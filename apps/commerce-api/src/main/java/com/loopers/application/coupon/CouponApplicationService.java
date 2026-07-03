package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class CouponApplicationService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final CouponIssueRedisStore couponIssueRedisStore;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final TransactionTemplate transactionTemplate;

    public CouponIssueRequestInfo requestIssue(Long userId, Long couponTemplateId) {
        CouponModel template = couponRepository.findById(couponTemplateId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        if (template.isExpired(now)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급받을 수 없습니다.");
        }

        String requestId = UUID.randomUUID().toString();
        CouponIssueRedisStore.ReservationResult reservationResult = couponIssueRedisStore.reserve(
            couponTemplateId,
            userId,
            template.getTotalQuantity(),
            requestId
        );
        if (reservationResult == CouponIssueRedisStore.ReservationResult.DUPLICATE) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
        }
        if (reservationResult == CouponIssueRedisStore.ReservationResult.SOLD_OUT) {
            throw new CoreException(ErrorType.CONFLICT, "쿠폰이 모두 소진되었습니다.");
        }

        try {
            return Objects.requireNonNull(transactionTemplate.execute(status -> {
                registerReservationRollback(couponTemplateId, userId, requestId);
                CouponIssueRequest request = couponIssueRequestRepository.save(
                    CouponIssueRequest.accept(requestId, userId, couponTemplateId));
                applicationEventPublisher.publishEvent(
                    new CouponIssueRequestedEvent(request.getRequestId(), userId, couponTemplateId));
                return CouponIssueRequestInfo.from(request, null);
            }));
        } catch (CoreException e) {
            couponIssueRedisStore.cancelReservation(couponTemplateId, userId, requestId);
            throw e;
        } catch (RuntimeException e) {
            couponIssueRedisStore.cancelReservation(couponTemplateId, userId, requestId);
            throw new CoreException(ErrorType.SERVICE_UNAVAILABLE, "잠시 후 다시 시도해주세요.", e);
        }
    }

    private void registerReservationRollback(Long couponId, Long userId, String requestId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    couponIssueRedisStore.cancelReservation(couponId, userId, requestId);
                }
            }
        });
    }

    @Transactional(readOnly = true)
    public CouponIssueRequestInfo getIssueRequest(Long userId, String requestId) {
        CouponIssueRequest request = couponIssueRequestRepository.findByRequestId(requestId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급 요청을 찾을 수 없습니다."));
        if (!request.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "발급 요청을 찾을 수 없습니다.");
        }

        UserCouponInfo issued = null;
        if (request.getUserCouponId() != null) {
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
            issued = userCouponRepository.findById(request.getUserCouponId())
                .map(uc -> UserCouponInfo.from(uc, now))
                .orElse(null);
        }
        return CouponIssueRequestInfo.from(request, issued);
    }

    @Transactional(readOnly = true)
    public List<UserCouponInfo> getMyCoupons(Long userId) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        return userCouponRepository.findByUserId(userId).stream()
            .map(uc -> UserCouponInfo.from(uc, now))
            .toList();
    }

    @Transactional
    public CouponInfo createTemplate(String name, CouponType type, long value, Long minOrderAmount,
                                     ZonedDateTime expiredAt, Integer totalQuantity) {
        CouponModel coupon = new CouponModel(name, type, value, minOrderAmount, expiredAt, totalQuantity);
        return CouponInfo.from(couponRepository.save(coupon));
    }

    @Transactional(readOnly = true)
    public CouponInfo getTemplate(Long couponId) {
        return CouponInfo.from(findTemplateOrThrow(couponId));
    }

    @Transactional(readOnly = true)
    public List<CouponInfo> getTemplates(int page, int size) {
        return couponRepository.findAll(page, size).stream()
            .map(CouponInfo::from)
            .toList();
    }

    @Transactional
    public CouponInfo updateTemplate(Long couponId, String name, CouponType type, long value, Long minOrderAmount,
                                     ZonedDateTime expiredAt, Integer totalQuantity) {
        CouponModel coupon = findTemplateOrThrow(couponId);
        coupon.update(name, type, value, minOrderAmount, expiredAt, totalQuantity);
        return CouponInfo.from(couponRepository.save(coupon));
    }

    @Transactional
    public void deleteTemplate(Long couponId) {
        CouponModel coupon = findTemplateOrThrow(couponId);
        if (userCouponRepository.existsByCouponId(couponId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 발급된 쿠폰이 있어 삭제할 수 없습니다.");
        }
        couponRepository.delete(coupon);
    }

    @Transactional(readOnly = true)
    public List<UserCouponInfo> getIssues(Long couponId, int page, int size) {
        findTemplateOrThrow(couponId);
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        return userCouponRepository.findByCouponId(couponId, page, size).stream()
            .map(uc -> UserCouponInfo.from(uc, now))
            .toList();
    }

    private CouponModel findTemplateOrThrow(Long couponId) {
        return couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + couponId + "] 쿠폰을 찾을 수 없습니다."));
    }
}
