package com.loopers.fixture;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;

import java.util.UUID;

/**
 * 주문 테스트 픽스처 — Object Mother 패턴
 */
public class OrderFixture {

    public static final String RECEIVER_NAME  = "홍길동";
    public static final String RECEIVER_PHONE = "010-1234-5678";
    public static final String ZIP_CODE       = "12345";
    public static final String ADDRESS        = "서울시 강남구 테헤란로 1";
    public static final String DETAIL_ADDRESS = "101호";

    public static OrderModel createModel(UUID userId) {
        return new OrderModel(userId, RECEIVER_NAME, RECEIVER_PHONE, ZIP_CODE, ADDRESS, DETAIL_ADDRESS);
    }

    public static OrderItemModel createItem(UUID productId) {
        return new OrderItemModel(productId, ProductFixture.NAME, BrandFixture.NAME, ProductFixture.PRICE, 2);
    }
}
