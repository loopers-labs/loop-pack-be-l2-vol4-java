package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 주문 조회 기간 값객체. 시작일·종료일은 inclusive 날짜이며,
 * 조회는 {@code [fromAtStartOfDay, toExclusive)} 반개구간으로 변환한다.
 */
public record OrderPeriod(LocalDate from, LocalDate to) {

    public OrderPeriod {
        if (from == null || to == null) {
            throw new CoreException(ErrorType.INVALID_ORDER_PERIOD, "조회 기간의 시작일과 종료일은 필수입니다.");
        }
        if (from.isAfter(to)) {
            throw new CoreException(ErrorType.INVALID_ORDER_PERIOD, "시작일은 종료일보다 늦을 수 없습니다.");
        }
    }

    public static OrderPeriod of(LocalDate from, LocalDate to) {
        return new OrderPeriod(from, to);
    }

    public ZonedDateTime fromAtStartOfDay(ZoneId zone) {
        return from.atStartOfDay(zone);
    }

    public ZonedDateTime toExclusive(ZoneId zone) {
        return to.plusDays(1).atStartOfDay(zone);
    }
}
