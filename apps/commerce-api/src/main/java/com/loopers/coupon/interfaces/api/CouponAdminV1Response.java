package com.loopers.coupon.interfaces.api;

import com.loopers.coupon.application.CouponResult;
import com.loopers.coupon.domain.CouponType;

import java.time.ZonedDateTime;
import java.util.List;

public class CouponAdminV1Response {

    public record Detail(
            Long id,
            String name,
            CouponType type,
            long value,
            Long minOrderAmount,
            ZonedDateTime expiredAt
    ) {
        public static Detail from(CouponResult.Detail result) {
            return new Detail(
                    result.id(),
                    result.name(),
                    result.type(),
                    result.value(),
                    result.minOrderAmount(),
                    result.expiredAt()
            );
        }
    }

    public record Page(
            List<Detail> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static Page from(org.springframework.data.domain.Page<CouponResult.Detail> page) {
            return new Page(
                    page.getContent().stream().map(Detail::from).toList(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages()
            );
        }
    }
}
