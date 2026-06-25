package com.loopers.application.payment;

public final class PaymentOrderIdMapper {

    private static final long PG_ORDER_ID_OFFSET = 1_000_000L;

    private PaymentOrderIdMapper() {
    }

    public static String toPgOrderId(Long orderId) {
        return String.valueOf(orderId + PG_ORDER_ID_OFFSET);
    }

    public static Long toOrderId(String pgOrderId) {
        long parsedOrderId = Long.parseLong(pgOrderId);
        if (parsedOrderId > PG_ORDER_ID_OFFSET) {
            return parsedOrderId - PG_ORDER_ID_OFFSET;
        }
        return parsedOrderId;
    }
}
